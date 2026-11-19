package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.grants.EnqueueResult.Outcome;
import net.inpvp.dawnrewards.grants.GrantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGrantRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofDays(7));

    @TempDir
    Path dir;

    private SqlGrantRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = TestDatabase.grants(dir.resolve("dawnrewards.db"));
    }

    @Test
    void queuesANewGrant() throws Exception {
        var result = repository.enqueue("Notch", "donut-bf-cape", "tbx-1", NOW, EXPIRES);

        assertEquals(Outcome.QUEUED, result.outcome());
        var grant = result.grant();
        assertEquals("Notch", grant.playerName());
        assertEquals("donut-bf-cape", grant.cosmetic());
        assertEquals("tbx-1", grant.deliveryId());
        assertEquals(GrantStatus.PENDING, grant.status());
        assertEquals(0, grant.attempts());
        assertEquals(NOW, grant.createdAt());
        assertEquals(EXPIRES, grant.expiresAt());
        assertEquals(NOW, grant.nextAttemptAt());
        assertNull(grant.lastError());
        assertEquals(grant, repository.find(grant.id()).orElseThrow());
    }

    @Test
    void redeliveryDoesNotQueueTwice() throws Exception {
        var first = repository.enqueue("Notch", "donut-bf-cape", "tbx-1", NOW, EXPIRES);
        var again = repository.enqueue("notch", "donut-bf-cape", "tbx-1", NOW.plusSeconds(5), EXPIRES.plusSeconds(5));

        assertEquals(Outcome.ALREADY_QUEUED, again.outcome());
        assertEquals(first.grant().id(), again.grant().id());
        assertEquals(1, repository.countByStatus().get(GrantStatus.PENDING));
    }

    @Test
    void otherDeliveriesAndCosmeticsAreSeparateGrants() throws Exception {
        var a = repository.enqueue("Notch", "donut-bf-cape", "tbx-1", NOW, EXPIRES).grant();
        var b = repository.enqueue("Notch", "donut-bf-cape", "tbx-2", NOW, EXPIRES).grant();
        var c = repository.enqueue("Notch", "donut-halo", "tbx-1", NOW, EXPIRES).grant();
        var d = repository.enqueue("Jeb_", "donut-bf-cape", "tbx-1", NOW, EXPIRES).grant();

        assertEquals(4, java.util.Set.of(a.id(), b.id(), c.id(), d.id()).size());
    }

    @Test
    void grantsWithoutDeliveryIdDedupeOnPlayerAndCosmetic() throws Exception {
        var first = repository.enqueue("Notch", "donut-bf-cape", "", NOW, EXPIRES);
        var again = repository.enqueue("NOTCH", "donut-bf-cape", "", NOW, EXPIRES);

        assertEquals(Outcome.QUEUED, first.outcome());
        assertEquals(Outcome.ALREADY_QUEUED, again.outcome());
    }

    @Test
    void grantedDeliveryIsRecognised() throws Exception {
        var grant = repository.enqueue("Notch", "donut-bf-cape", "tbx-1", NOW, EXPIRES).grant();
        repository.finish(grant.id(), GrantStatus.GRANTED, 1, NOW, null, NOW);

        var again = repository.enqueue("Notch", "donut-bf-cape", "tbx-1", NOW.plusSeconds(60), EXPIRES);
        assertEquals(Outcome.ALREADY_GRANTED, again.outcome());
        assertEquals(GrantStatus.GRANTED, again.grant().status());
    }

    @Test
    void failedDeliveryIsReopenedWhenRunAgain() throws Exception {
        var grant = repository.enqueue("Notch", "bad-slug", "tbx-1", NOW, EXPIRES).grant();
        repository.finish(grant.id(), GrantStatus.FAILED, 1, NOW, "HTTP 400", NOW);

        var later = NOW.plus(Duration.ofDays(2));
        var again = repository.enqueue("Notch", "bad-slug", "tbx-1", later, later.plus(Duration.ofDays(7)));

        assertEquals(Outcome.REQUEUED, again.outcome());
        assertEquals(grant.id(), again.grant().id());
        assertEquals(GrantStatus.PENDING, again.grant().status());
        assertEquals(0, again.grant().attempts());
        assertEquals(later, again.grant().nextAttemptAt());
        assertEquals(later.plus(Duration.ofDays(7)), again.grant().expiresAt());
        assertNull(again.grant().lastError());
    }

    @Test
    void findsOnlyDuePendingGrantsInOrder() throws Exception {
        var later = repository.enqueue("A", "c", "", NOW.plusSeconds(30), EXPIRES).grant();
        var sooner = repository.enqueue("B", "c", "", NOW, EXPIRES).grant();
        var failed = repository.enqueue("C", "c", "", NOW, EXPIRES).grant();
        repository.finish(failed.id(), GrantStatus.FAILED, 1, NOW, "HTTP 400", NOW);

        assertEquals(java.util.List.of(sooner.id()), ids(repository.findDue(NOW, 10)));
        assertEquals(java.util.List.of(sooner.id(), later.id()), ids(repository.findDue(NOW.plusSeconds(30), 10)));
        assertEquals(1, repository.findDue(NOW.plusSeconds(30), 1).size());
    }

    @Test
    void onlyOneClaimWins() throws Exception {
        var grant = repository.enqueue("Notch", "c", "", NOW, EXPIRES).grant();
        var lease = NOW.plus(Duration.ofMinutes(5));

        assertTrue(repository.claim(grant.id(), grant.nextAttemptAt(), lease));
        assertFalse(repository.claim(grant.id(), grant.nextAttemptAt(), lease));
        assertTrue(repository.findDue(NOW.plusSeconds(60), 10).isEmpty());
        assertEquals(1, repository.findDue(lease, 10).size());
    }

    @Test
    void finishStoresTheOutcomeOfAPendingGrantOnly() throws Exception {
        var grant = repository.enqueue("Notch", "c", "", NOW, EXPIRES).grant();
        var next = NOW.plus(Duration.ofMinutes(1));

        repository.finish(grant.id(), GrantStatus.PENDING, 1, next, "HTTP 503: down", NOW);
        var retried = repository.find(grant.id()).orElseThrow();
        assertEquals(1, retried.attempts());
        assertEquals(next, retried.nextAttemptAt());
        assertEquals("HTTP 503: down", retried.lastError());

        repository.finish(grant.id(), GrantStatus.GRANTED, 2, next, null, next);
        // A late outcome for a grant that is no longer pending changes nothing.
        repository.finish(grant.id(), GrantStatus.FAILED, 3, next, "late", next);

        var granted = repository.find(grant.id()).orElseThrow();
        assertEquals(GrantStatus.GRANTED, granted.status());
        assertEquals(2, granted.attempts());
        assertNull(granted.lastError());
    }

    @Test
    void listsOpenGrantsAndCounts() throws Exception {
        var pending = repository.enqueue("A", "c", "", NOW, EXPIRES).grant();
        var failed = repository.enqueue("B", "c", "", NOW.plusSeconds(1), EXPIRES).grant();
        var expired = repository.enqueue("C", "c", "", NOW.plusSeconds(2), EXPIRES).grant();
        var granted = repository.enqueue("D", "c", "", NOW.plusSeconds(3), EXPIRES).grant();
        repository.finish(failed.id(), GrantStatus.FAILED, 1, NOW, "HTTP 401", NOW);
        repository.finish(expired.id(), GrantStatus.EXPIRED, 9, NOW, "HTTP 404", NOW);
        repository.finish(granted.id(), GrantStatus.GRANTED, 1, NOW, null, NOW);

        assertEquals(java.util.List.of(expired.id(), failed.id(), pending.id()), ids(repository.findOpen(10)));

        var counts = repository.countByStatus();
        assertEquals(1, counts.get(GrantStatus.PENDING));
        assertEquals(1, counts.get(GrantStatus.FAILED));
        assertEquals(1, counts.get(GrantStatus.EXPIRED));
        assertEquals(1, counts.get(GrantStatus.GRANTED));
    }

    @Test
    void requeueMakesPendingGrantsDueNow() throws Exception {
        var grant = repository.enqueue("A", "c", "", NOW, EXPIRES).grant();
        repository.finish(grant.id(), GrantStatus.PENDING, 3, NOW.plus(Duration.ofHours(1)), "HTTP 503", NOW);

        var restart = NOW.plusSeconds(10);
        assertEquals(1, repository.requeuePending(restart));
        var due = repository.findDue(restart, 10);
        assertEquals(1, due.size());
        assertEquals(3, due.get(0).attempts());
    }

    @Test
    void reopenFailedGivesAFreshWindow() throws Exception {
        var failed = repository.enqueue("A", "c", "", NOW, EXPIRES).grant();
        var expired = repository.enqueue("B", "c", "", NOW, EXPIRES).grant();
        var granted = repository.enqueue("C", "c", "", NOW, EXPIRES).grant();
        repository.finish(failed.id(), GrantStatus.FAILED, 1, NOW, "HTTP 401", NOW);
        repository.finish(expired.id(), GrantStatus.EXPIRED, 40, NOW, "HTTP 404", NOW);
        repository.finish(granted.id(), GrantStatus.GRANTED, 1, NOW, null, NOW);

        var later = NOW.plus(Duration.ofDays(8));
        assertEquals(2, repository.reopenFailed(later, later.plus(Duration.ofDays(7))));

        for (var id : java.util.List.of(failed.id(), expired.id())) {
            var grant = repository.find(id).orElseThrow();
            assertEquals(GrantStatus.PENDING, grant.status());
            assertEquals(0, grant.attempts());
            assertEquals(later.plus(Duration.ofDays(7)), grant.expiresAt());
            assertEquals(later, grant.nextAttemptAt());
        }
        assertEquals(GrantStatus.GRANTED, repository.find(granted.id()).orElseThrow().status());
    }

    @Test
    void prunesOldGrantedRowsOnly() throws Exception {
        var old = repository.enqueue("A", "c", "", NOW, EXPIRES).grant();
        var recent = repository.enqueue("B", "c", "", NOW, EXPIRES).grant();
        var pending = repository.enqueue("C", "c", "", NOW, EXPIRES).grant();
        repository.finish(old.id(), GrantStatus.GRANTED, 1, NOW, null, NOW);
        repository.finish(recent.id(), GrantStatus.GRANTED, 1, NOW, null, NOW.plus(Duration.ofDays(20)));

        assertEquals(1, repository.pruneGranted(NOW.plus(Duration.ofDays(10))));
        assertTrue(repository.find(old.id()).isEmpty());
        assertTrue(repository.find(recent.id()).isPresent());
        assertTrue(repository.find(pending.id()).isPresent());
    }

    @Test
    void survivesAReopenOfTheDatabase() throws Exception {
        var grant = repository.enqueue("Notch", "donut-bf-cape", "tbx-1", NOW, EXPIRES).grant();

        // Same file, migrations run again like on the next server start.
        var reopened = TestDatabase.grants(dir.resolve("dawnrewards.db"));
        assertEquals(grant, reopened.find(grant.id()).orElseThrow());
        assertEquals(Outcome.ALREADY_QUEUED, reopened.enqueue("Notch", "donut-bf-cape", "tbx-1", NOW, EXPIRES).outcome());
        assertNotEquals(grant.id(), reopened.enqueue("Notch", "donut-bf-cape", "tbx-9", NOW, EXPIRES).grant().id());
    }

    private static java.util.List<Long> ids(java.util.List<net.inpvp.dawnrewards.grants.CosmeticGrant> grants) {
        return grants.stream().map(net.inpvp.dawnrewards.grants.CosmeticGrant::id).toList();
    }
}
