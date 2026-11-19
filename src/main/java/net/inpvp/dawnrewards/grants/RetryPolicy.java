package net.inpvp.dawnrewards.grants;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * When to try a failed grant again. Transient failures start at one minute and double up to an hour.
 * A buyer without a Dawn account is asked about less often, from 15 minutes doubling up to 6 hours,
 * since signing up can take days. Both stop once the grant is a week old, after one last attempt at that mark.
 */
@NullMarked
public final class RetryPolicy {

    public static final Duration RETRY_WINDOW = Duration.ofDays(7);

    static final Duration TRANSIENT_FIRST = Duration.ofMinutes(1);
    static final Duration TRANSIENT_MAX = Duration.ofHours(1);
    static final Duration NO_ACCOUNT_FIRST = Duration.ofMinutes(15);
    static final Duration NO_ACCOUNT_MAX = Duration.ofHours(6);

    private RetryPolicy() {
    }

    /**
     * Delay before the next attempt after {@code failures} failed attempts in a row (1 for the first failure).
     */
    public static Duration delay(FailureKind kind, int failures, @Nullable Duration retryAfter) {
        return switch (kind) {
            case TRANSIENT -> {
                var delay = doubling(TRANSIENT_FIRST, TRANSIENT_MAX, failures);
                // Honour a longer Retry-After from Dawn, but never wait past the regular cap.
                yield retryAfter != null && retryAfter.compareTo(delay) > 0 ? min(retryAfter, TRANSIENT_MAX) : delay;
            }
            case NO_ACCOUNT -> doubling(NO_ACCOUNT_FIRST, NO_ACCOUNT_MAX, failures);
            case PERMANENT -> throw new IllegalArgumentException("Permanent failures are not retried");
        };
    }

    /**
     * The next attempt, or empty when the grant has used up its retry window.
     */
    public static Optional<Instant> nextAttempt(FailureKind kind, int failures, Instant now, Instant expiresAt, @Nullable Duration retryAfter) {
        if (kind == FailureKind.PERMANENT || !now.isBefore(expiresAt)) {
            return Optional.empty();
        }

        var next = now.plus(delay(kind, failures, retryAfter));
        return Optional.of(next.isAfter(expiresAt) ? expiresAt : next);
    }

    private static Duration doubling(Duration first, Duration max, int failures) {
        var steps = Math.max(0, failures - 1);
        // 2^30 minutes is far beyond any cap, so stop shifting before it overflows.
        if (steps >= 30) {
            return max;
        }
        return min(first.multipliedBy(1L << steps), max);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
