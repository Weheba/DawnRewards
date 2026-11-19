package net.inpvp.dawnrewards.grants;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    private static final Instant START = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void transientBackoffDoublesFromOneMinuteUpToAnHour() {
        var delays = new ArrayList<Long>();
        for (var failures = 1; failures <= 9; failures++) {
            delays.add(RetryPolicy.delay(FailureKind.TRANSIENT, failures, null).toMinutes());
        }
        assertEquals(List.of(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L, 60L), delays);
    }

    @Test
    void noAccountBackoffIsSlower() {
        var delays = new ArrayList<Long>();
        for (var failures = 1; failures <= 8; failures++) {
            delays.add(RetryPolicy.delay(FailureKind.NO_ACCOUNT, failures, null).toMinutes());
        }
        assertEquals(List.of(15L, 30L, 60L, 120L, 240L, 360L, 360L, 360L), delays);
    }

    @Test
    void largeFailureCountsDoNotOverflow() {
        assertEquals(Duration.ofHours(1), RetryPolicy.delay(FailureKind.TRANSIENT, Integer.MAX_VALUE, null));
        assertEquals(Duration.ofHours(6), RetryPolicy.delay(FailureKind.NO_ACCOUNT, 64, null));
    }

    @Test
    void retryAfterOnlyLengthensUpToTheCap() {
        assertEquals(Duration.ofMinutes(5), RetryPolicy.delay(FailureKind.TRANSIENT, 1, Duration.ofMinutes(5)));
        assertEquals(Duration.ofMinutes(2), RetryPolicy.delay(FailureKind.TRANSIENT, 2, Duration.ofSeconds(10)));
        assertEquals(Duration.ofHours(1), RetryPolicy.delay(FailureKind.TRANSIENT, 1, Duration.ofHours(5)));
    }

    @Test
    void permanentIsNeverRetried() {
        assertEquals(Optional.empty(), RetryPolicy.nextAttempt(FailureKind.PERMANENT, 1, START, START.plus(RetryPolicy.RETRY_WINDOW), null));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.delay(FailureKind.PERMANENT, 1, null));
    }

    @Test
    void nextAttemptIsClampedToTheEndOfTheWindow() {
        var expires = START.plus(RetryPolicy.RETRY_WINDOW);
        var almost = expires.minus(Duration.ofMinutes(10));

        assertEquals(Optional.of(expires), RetryPolicy.nextAttempt(FailureKind.TRANSIENT, 20, almost, expires, null));
        assertEquals(Optional.of(START.plusSeconds(60)), RetryPolicy.nextAttempt(FailureKind.TRANSIENT, 1, START, expires, null));
    }

    @Test
    void givesUpOnceTheWindowIsOver() {
        var expires = START.plus(RetryPolicy.RETRY_WINDOW);
        assertEquals(Optional.empty(), RetryPolicy.nextAttempt(FailureKind.TRANSIENT, 5, expires, expires, null));
        assertEquals(Optional.empty(), RetryPolicy.nextAttempt(FailureKind.NO_ACCOUNT, 5, expires.plusSeconds(1), expires, null));
    }

    @Test
    void sevenDaysOfTransientFailuresTakeAboutOneAttemptAnHour() {
        var expires = START.plus(RetryPolicy.RETRY_WINDOW);
        var now = START;
        var failures = 0;

        while (true) {
            failures++;
            var next = RetryPolicy.nextAttempt(FailureKind.TRANSIENT, failures, now, expires, null);
            if (next.isEmpty()) {
                break;
            }
            assertTrue(next.get().isAfter(now));
            now = next.get();
        }

        assertEquals(expires, now);
        // 7 quick retries in the first hour, then one an hour until the 7 days are up.
        // 63 minutes of quick retries, 167 hourly ones (the last cut short at the mark), then the give-up.
        assertEquals(174, failures);
    }
}
