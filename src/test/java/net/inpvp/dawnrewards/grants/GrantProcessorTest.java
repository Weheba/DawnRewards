package net.inpvp.dawnrewards.grants;

import net.inpvp.dawnrewards.dawn.api.DawnApiException;
import net.inpvp.dawnrewards.dawn.api.PartnerGrantResponse;
import net.inpvp.dawnrewards.storage.SqlGrantRepository;
import net.inpvp.dawnrewards.storage.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantProcessorTest {

    private static final Instant START = Instant.parse("2026-11-20T12:00:00Z");

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(START);
    private final FakeApi api = new FakeApi();
    private final RecordingListener listener = new RecordingListener();

    private SqlGrantRepository repository;
    private GrantProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        repository = TestDatabase.grants(dir.resolve("dawnrewards.db"));
        processor = newProcessor(repository);
    }

    @Test
    void grantsAndRecordsSuccess() throws Exception {
        api.respond(granted());

        var grant = submit("Notch", "donut-bf-cape", "tbx-1");

        assertEquals(1, api.calls.size());
        assertEquals("Notch", api.calls.get(0).playerName());
        assertEquals("tbx-1", api.calls.get(0).deliveryIdOrNull());
        var stored = repository.find(grant.id()).orElseThrow();
        assertEquals(GrantStatus.GRANTED, stored.status());
        assertEquals(1, stored.attempts());
        assertEquals(List.of("granted Notch"), listener.events);
    }

    @Test
    void alreadyGrantedCountsAsDone() throws Exception {
        api.respond(() -> CompletableFuture.completedFuture(new PartnerGrantResponse(PartnerGrantResponse.ALREADY_GRANTED, "Notch", "c")));

        var grant = submit("Notch", "c", "");

        assertNull(api.calls.get(0).deliveryIdOrNull());
        assertEquals(GrantStatus.GRANTED, repository.find(grant.id()).orElseThrow().status());
        assertEquals(List.of("already Notch"), listener.events);
    }

    @Test
    void serverErrorIsRetriedWithBackoffThenSucceeds() throws Exception {
        api.respond(failure(500), failure(503), granted());

        var grant = submit("Notch", "c", "tbx-1");
        var stored = repository.find(grant.id()).orElseThrow();
        assertEquals(GrantStatus.PENDING, stored.status());
        assertEquals(1, stored.attempts());
        assertEquals(START.plus(Duration.ofMinutes(1)), stored.nextAttemptAt());
        assertTrue(stored.lastError().contains("HTTP 500"));

        clock.set(START.plusSeconds(30));
        assertEquals(0, processor.processDue());
        assertEquals(1, api.calls.size());

        clock.set(START.plus(Duration.ofMinutes(1)));
        assertEquals(1, processor.processDue());
        assertEquals(START.plus(Duration.ofMinutes(3)), repository.find(grant.id()).orElseThrow().nextAttemptAt());

        clock.set(START.plus(Duration.ofMinutes(3)));
        processor.processDue();
        assertEquals(GrantStatus.GRANTED, repository.find(grant.id()).orElseThrow().status());
        assertEquals(3, api.calls.size());
        assertEquals(List.of("retry TRANSIENT Notch", "retry TRANSIENT Notch", "granted Notch"), listener.events);
    }

    @Test
    void networkErrorsAndRateLimitsAreRetried() throws Exception {
        api.respond(
                () -> CompletableFuture.failedFuture(new HttpTimeoutException("request timed out")),
                () -> CompletableFuture.failedFuture(new DawnApiException(429, "slow down", Duration.ofMinutes(10))),
                () -> {
                    throw new IllegalStateException("thrown before any request");
                },
                granted());

        var grant = submit("Notch", "c", "");
        assertEquals(START.plus(Duration.ofMinutes(1)), repository.find(grant.id()).orElseThrow().nextAttemptAt());

        clock.set(START.plus(Duration.ofMinutes(1)));
        processor.processDue();
        // Retry-After of 10 minutes beats the 2 minute backoff.
        assertEquals(clock.instant().plus(Duration.ofMinutes(10)), repository.find(grant.id()).orElseThrow().nextAttemptAt());

        clock.advance(Duration.ofMinutes(10));
        processor.processDue();
        assertEquals(GrantStatus.PENDING, repository.find(grant.id()).orElseThrow().status());

        clock.advance(Duration.ofMinutes(4));
        processor.processDue();
        assertEquals(GrantStatus.GRANTED, repository.find(grant.id()).orElseThrow().status());
    }

    @Test
    void noAccountIsRetriedSlowlyUntilTheBuyerSignsUp() throws Exception {
        api.respond(failure(404), failure(404), granted());

        var grant = submit("NewPlayer", "c", "tbx-1");
        assertEquals(START.plus(Duration.ofMinutes(15)), repository.find(grant.id()).orElseThrow().nextAttemptAt());

        clock.set(START.plus(Duration.ofMinutes(15)));
        processor.processDue();
        assertEquals(clock.instant().plus(Duration.ofMinutes(30)), repository.find(grant.id()).orElseThrow().nextAttemptAt());

        clock.advance(Duration.ofMinutes(30));
        processor.processDue();
        assertEquals(GrantStatus.GRANTED, repository.find(grant.id()).orElseThrow().status());
        assertEquals(List.of("retry NO_ACCOUNT NewPlayer", "retry NO_ACCOUNT NewPlayer", "granted NewPlayer"), listener.events);
    }

    @Test
    void badInputIsPermanent() throws Exception {
        api.respond(failure(400));

        var grant = submit("Notch", "not-a-cosmetic", "tbx-1");

        var stored = repository.find(grant.id()).orElseThrow();
        assertEquals(GrantStatus.FAILED, stored.status());
        assertEquals(List.of("failed 400 Notch"), listener.events);

        clock.advance(Duration.ofDays(1));
        assertEquals(0, processor.processDue());
        assertEquals(1, api.calls.size());
    }

    @Test
    void badTokenIsPermanentUntilAnAdminRetries() throws Exception {
        api.respond(failure(401), granted());

        var grant = submit("Notch", "c", "tbx-1");
        assertEquals(GrantStatus.FAILED, repository.find(grant.id()).orElseThrow().status());
        assertEquals(List.of("failed 401 Notch"), listener.events);

        clock.advance(Duration.ofHours(2));
        assertEquals(1, processor.retryAll());
        processor.processDue();
        assertEquals(GrantStatus.GRANTED, repository.find(grant.id()).orElseThrow().status());
    }

    @Test
    void givesUpAfterSevenDaysOfTransientFailures() throws Exception {
        api.fallback = failure(503);

        var grant = submit("Notch", "c", "tbx-1");
        var attempts = runUntilSettled(grant.id());

        var stored = repository.find(grant.id()).orElseThrow();
        assertEquals(GrantStatus.EXPIRED, stored.status());
        assertEquals(START.plus(RetryPolicy.RETRY_WINDOW), clock.instant());
        assertEquals(174, attempts);
        assertEquals(174, stored.attempts());
        assertEquals("expired Notch", listener.events.get(listener.events.size() - 1));
    }

    @Test
    void givesUpAfterSevenDaysWithoutADawnAccount() throws Exception {
        api.fallback = failure(404);

        var grant = submit("Nobody", "c", "tbx-1");
        var attempts = runUntilSettled(grant.id());

        assertEquals(GrantStatus.EXPIRED, repository.find(grant.id()).orElseThrow().status());
        assertEquals(START.plus(RetryPolicy.RETRY_WINDOW), clock.instant());
        // 15m, 30m, 1h, 2h, 4h, then every 6h for the rest of the week.
        assertTrue(attempts > 30 && attempts < 40, "attempts=" + attempts);
    }

    @Test
    void expiredGrantsCanBeRetriedForAnotherWindow() throws Exception {
        api.fallback = failure(404);
        var grant = submit("Nobody", "c", "tbx-1");
        runUntilSettled(grant.id());

        api.fallback = granted();
        assertEquals(1, processor.retryAll());
        processor.processDue();
        assertEquals(GrantStatus.GRANTED, repository.find(grant.id()).orElseThrow().status());
    }

    @Test
    void pendingGrantSurvivesARestart() throws Exception {
        api.respond(failure(503));
        var grant = submit("Notch", "c", "tbx-1");
        assertEquals(GrantStatus.PENDING, repository.find(grant.id()).orElseThrow().status());

        // New server start: fresh repository and processor over the same file, a few seconds later.
        clock.advance(Duration.ofSeconds(5));
        var restartedRepository = TestDatabase.grants(dir.resolve("dawnrewards.db"));
        var restarted = newProcessor(restartedRepository);
        api.respond(granted());

        restarted.resumeAfterStartup();
        assertEquals(1, restarted.processDue());
        assertEquals(GrantStatus.GRANTED, restartedRepository.find(grant.id()).orElseThrow().status());
        assertEquals(2, api.calls.size());
    }

    @Test
    void redeliveryIsNotSentTwice() throws Exception {
        api.respond(granted());
        submit("Notch", "c", "tbx-1");

        var again = processor.enqueue("notch", "c", "tbx-1");
        assertEquals(EnqueueResult.Outcome.ALREADY_GRANTED, again.outcome());
        processor.attempt(again.grant()).join();
        processor.processDue();
        assertEquals(1, api.calls.size());
    }

    @Test
    void aGrantIsNotSentTwiceWhileInFlight() throws Exception {
        var pending = new CompletableFuture<PartnerGrantResponse>();
        api.respond(() -> pending);

        var grant = processor.enqueue("Notch", "c", "tbx-1").grant();
        var first = processor.attempt(grant);
        var second = processor.attempt(grant);
        assertTrue(second.isDone());
        assertEquals(0, processor.processDue());

        pending.complete(new PartnerGrantResponse(PartnerGrantResponse.GRANTED, "Notch", "c"));
        first.join();
        assertEquals(1, api.calls.size());
        assertEquals(GrantStatus.GRANTED, repository.find(grant.id()).orElseThrow().status());
    }

    @Test
    void unexpectedResultIsRetried() throws Exception {
        api.respond(() -> CompletableFuture.completedFuture(new PartnerGrantResponse("PARTNER_GRANT_RESULT_UNSPECIFIED", "Notch", "c")));

        var grant = submit("Notch", "c", "");
        var stored = repository.find(grant.id()).orElseThrow();
        assertEquals(GrantStatus.PENDING, stored.status());
        assertTrue(stored.lastError().contains("PARTNER_GRANT_RESULT_UNSPECIFIED"));
    }

    @Test
    void restartPrunesOldGrantedRows() throws Exception {
        api.respond(granted());
        var grant = submit("Notch", "c", "tbx-1");

        clock.advance(Duration.ofDays(31));
        processor.resumeAfterStartup();
        assertTrue(repository.find(grant.id()).isEmpty());
    }

    private CosmeticGrant submit(String player, String cosmetic, String deliveryId) throws Exception {
        var result = processor.enqueue(player, cosmetic, deliveryId);
        assertEquals(EnqueueResult.Outcome.QUEUED, result.outcome());
        processor.attempt(result.grant()).join();
        return result.grant();
    }

    /** Jumps the clock from one attempt to the next until the grant is no longer pending. */
    private int runUntilSettled(long id) throws Exception {
        var attempts = 0;
        for (var i = 0; i < 1000; i++) {
            var grant = repository.find(id).orElseThrow();
            attempts = grant.attempts();
            if (grant.status() != GrantStatus.PENDING) {
                return attempts;
            }
            clock.set(grant.nextAttemptAt());
            processor.processDue();
        }
        throw new AssertionError("grant never settled");
    }

    private GrantProcessor newProcessor(SqlGrantRepository repository) {
        return new GrantProcessor(repository, api, listener, clock, Logger.getLogger("test"));
    }

    private static Supplier<CompletableFuture<PartnerGrantResponse>> granted() {
        return () -> CompletableFuture.completedFuture(new PartnerGrantResponse(PartnerGrantResponse.GRANTED, "Notch", "c"));
    }

    private static Supplier<CompletableFuture<PartnerGrantResponse>> failure(int status) {
        return () -> CompletableFuture.failedFuture(new DawnApiException(status, "error " + status, null));
    }

    private static final class FakeApi implements GrantProcessor.GrantApi {
        final List<CosmeticGrant> calls = new ArrayList<>();
        final Deque<Supplier<CompletableFuture<PartnerGrantResponse>>> responses = new ArrayDeque<>();
        Supplier<CompletableFuture<PartnerGrantResponse>> fallback = () -> {
            throw new AssertionError("unexpected grant request");
        };

        @SafeVarargs
        final void respond(Supplier<CompletableFuture<PartnerGrantResponse>>... next) {
            responses.addAll(List.of(next));
        }

        @Override
        public CompletableFuture<PartnerGrantResponse> grant(CosmeticGrant grant) {
            calls.add(grant);
            var next = responses.isEmpty() ? fallback : responses.poll();
            return next.get();
        }
    }

    private static final class RecordingListener implements GrantProcessor.Listener {
        final List<String> events = new ArrayList<>();

        @Override
        public void granted(CosmeticGrant grant, boolean alreadyGranted) {
            events.add((alreadyGranted ? "already " : "granted ") + grant.playerName());
        }

        @Override
        public void retrying(CosmeticGrant grant, FailureKind kind, String error, Instant nextAttempt) {
            events.add("retry " + kind + " " + grant.playerName());
        }

        @Override
        public void failed(CosmeticGrant grant, int statusCode, String error) {
            events.add("failed " + statusCode + " " + grant.playerName());
        }

        @Override
        public void expired(CosmeticGrant grant, String error) {
            events.add("expired " + grant.playerName());
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
