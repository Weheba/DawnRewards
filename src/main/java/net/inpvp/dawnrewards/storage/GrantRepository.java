package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.grants.CosmeticGrant;
import net.inpvp.dawnrewards.grants.EnqueueResult;
import net.inpvp.dawnrewards.grants.GrantStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cosmetic grants waiting for, or done with, delivery to Dawn. A grant is unique per player name
 * (case-insensitive), cosmetic and delivery id, so a store redelivering a purchase never queues it twice.
 */
@NullMarked
public interface GrantRepository {

    EnqueueResult enqueue(String playerName, String cosmetic, String deliveryId, Instant now, Instant expiresAt) throws SQLException;

    Optional<CosmeticGrant> find(long id) throws SQLException;

    List<CosmeticGrant> findDue(Instant now, int limit) throws SQLException;

    /**
     * Takes a due grant for one attempt by pushing its next attempt to {@code leaseUntil}. Only one caller
     * wins, which keeps two passes (or two servers sharing a database) from sending it at once.
     */
    boolean claim(long id, Instant expectedNextAttempt, Instant leaseUntil) throws SQLException;

    /**
     * Records the outcome of an attempt on a pending grant.
     */
    void finish(long id, GrantStatus status, int attempts, Instant nextAttemptAt, @Nullable String error, Instant now) throws SQLException;

    /**
     * Every grant that is not granted, newest first.
     */
    List<CosmeticGrant> findOpen(int limit) throws SQLException;

    Map<GrantStatus, Integer> countByStatus() throws SQLException;

    /**
     * Makes every pending grant due now.
     */
    int requeuePending(Instant now) throws SQLException;

    /**
     * Puts failed and expired grants back in the queue with a fresh retry window.
     */
    int reopenFailed(Instant now, Instant expiresAt) throws SQLException;

    /**
     * Deletes granted rows last touched before {@code before}.
     */
    int pruneGranted(Instant before) throws SQLException;
}
