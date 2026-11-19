package net.inpvp.dawnrewards.grants;

import net.inpvp.dawnrewards.dawn.api.PartnerGrantResponse;
import net.inpvp.dawnrewards.storage.GrantRepository;
import org.jspecify.annotations.NullMarked;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivers queued cosmetic grants to Dawn and decides what happens after each attempt. Has no Bukkit
 * dependency and blocks on the database, so callers run it off the server threads.
 */
@NullMarked
public class GrantProcessor {

    /** How long an attempt holds a grant. Far above the HTTP timeouts, so it only matters after a crash. */
    static final Duration LEASE = Duration.ofMinutes(5);
    /** Granted rows are kept this long so a late store redelivery is still recognised. */
    static final Duration KEEP_GRANTED = Duration.ofDays(30);
    static final int BATCH_SIZE = 25;
    /** Caps one pass, so rows that keep failing to claim cannot spin it. The next pass picks up the rest. */
    static final int MAX_BATCHES_PER_PASS = 20;

    /**
     * Sends one grant to Dawn.
     */
    @FunctionalInterface
    public interface GrantApi {
        CompletableFuture<PartnerGrantResponse> grant(CosmeticGrant grant);
    }

    /**
     * Outcomes of attempts. Called on whatever thread finished the attempt.
     */
    public interface Listener {
        void granted(CosmeticGrant grant, boolean alreadyGranted);

        void retrying(CosmeticGrant grant, FailureKind kind, String error, Instant nextAttempt);

        void failed(CosmeticGrant grant, int statusCode, String error);

        void expired(CosmeticGrant grant, String error);
    }

    private final GrantRepository repository;
    private final GrantApi api;
    private final Listener listener;
    private final Clock clock;
    private final Logger logger;

    private final AtomicBoolean passRunning = new AtomicBoolean();

    public GrantProcessor(GrantRepository repository, GrantApi api, Listener listener, Clock clock, Logger logger) {
        this.repository = repository;
        this.api = api;
        this.listener = listener;
        this.clock = clock;
        this.logger = logger;
    }

    public EnqueueResult enqueue(String playerName, String cosmetic, String deliveryId) throws SQLException {
        var now = clock.instant();
        return repository.enqueue(playerName, cosmetic, deliveryId, now, now.plus(RetryPolicy.RETRY_WINDOW));
    }

    /**
     * Runs one attempt for a grant whose turn it is. Completes once the outcome is stored; never completes
     * exceptionally. Does nothing when the grant is not due or another pass already took it.
     */
    public CompletableFuture<Void> attempt(CosmeticGrant grant) {
        var now = clock.instant();
        if (grant.status() != GrantStatus.PENDING || grant.nextAttemptAt().isAfter(now)) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (!repository.claim(grant.id(), grant.nextAttemptAt(), now.plus(LEASE))) {
                return CompletableFuture.completedFuture(null);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not take cosmetic grant " + grant.describe() + " from the queue, it stays queued", e);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<PartnerGrantResponse> request;
        try {
            request = api.grant(grant);
        } catch (RuntimeException e) {
            request = CompletableFuture.failedFuture(e);
        }

        return request.handle((response, error) -> {
            try {
                if (error == null) {
                    onResponse(grant, response);
                } else {
                    onFailure(grant, error);
                }
            } catch (SQLException e) {
                // The lease runs out and the grant is picked up again, nothing is lost.
                logger.log(Level.SEVERE, "Could not store the outcome of cosmetic grant " + grant.describe() + ", it will be retried", e);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Unexpected error after cosmetic grant " + grant.describe(), e);
            }
            return null;
        });
    }

    /**
     * Attempts every grant that is due, a batch at a time, and waits for the outcomes. Returns how many
     * grants were looked at. A pass that starts while another runs returns 0 straight away.
     */
    public int processDue() {
        if (!passRunning.compareAndSet(false, true)) {
            return 0;
        }

        try {
            var processed = 0;
            for (var batch = 0; batch < MAX_BATCHES_PER_PASS; batch++) {
                var due = repository.findDue(clock.instant(), BATCH_SIZE);
                if (due.isEmpty()) {
                    return processed;
                }

                var attempts = new ArrayList<CompletableFuture<Void>>(due.size());
                for (var grant : due) {
                    attempts.add(attempt(grant));
                }
                CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new)).join();
                processed += due.size();

                if (due.size() < BATCH_SIZE) {
                    return processed;
                }
            }
            return processed;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not read the cosmetic grant queue", e);
            return 0;
        } finally {
            passRunning.set(false);
        }
    }

    /**
     * Startup housekeeping: pending grants are tried right away, old granted rows are dropped.
     */
    public void resumeAfterStartup() throws SQLException {
        var now = clock.instant();
        repository.requeuePending(now);
        repository.pruneGranted(now.minus(KEEP_GRANTED));
    }

    /**
     * Makes pending grants due now and reopens failed and expired ones with a fresh retry window.
     */
    public int retryAll() throws SQLException {
        var now = clock.instant();
        return repository.requeuePending(now) + repository.reopenFailed(now, now.plus(RetryPolicy.RETRY_WINDOW));
    }

    private void onResponse(CosmeticGrant grant, PartnerGrantResponse response) throws SQLException {
        if (!response.isGranted() && !response.isAlreadyGranted()) {
            onFailure(grant, new IllegalStateException("Unexpected grant result " + response.result()));
            return;
        }

        var now = clock.instant();
        repository.finish(grant.id(), GrantStatus.GRANTED, grant.attempts() + 1, now, null, now);
        listener.granted(grant, response.isAlreadyGranted());
    }

    private void onFailure(CosmeticGrant grant, Throwable error) throws SQLException {
        var now = clock.instant();
        var attempts = grant.attempts() + 1;
        var kind = GrantFailures.classify(error);
        var message = GrantFailures.describe(error);

        if (kind == FailureKind.PERMANENT) {
            repository.finish(grant.id(), GrantStatus.FAILED, attempts, now, message, now);
            listener.failed(grant, GrantFailures.statusCode(error), message);
            return;
        }

        var next = RetryPolicy.nextAttempt(kind, attempts, now, grant.expiresAt(), GrantFailures.retryAfter(error));
        if (next.isEmpty()) {
            repository.finish(grant.id(), GrantStatus.EXPIRED, attempts, now, message, now);
            listener.expired(grant, message);
            return;
        }

        repository.finish(grant.id(), GrantStatus.PENDING, attempts, next.get(), message, now);
        listener.retrying(grant, kind, message, next.get());
    }
}
