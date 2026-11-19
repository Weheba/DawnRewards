package net.inpvp.dawnrewards.grants;

import net.inpvp.dawnrewards.dawn.api.DawnApiException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@NullMarked
public final class GrantFailures {

    private static final int MAX_ERROR_LENGTH = 500;

    private GrantFailures() {
    }

    public static FailureKind classify(Throwable error) {
        var cause = unwrap(error);
        if (cause instanceof DawnApiException dawnError) {
            return classifyStatus(dawnError.getStatusCode());
        }
        // Timeouts, refused connections, DNS, TLS and anything else that never got an answer.
        return FailureKind.TRANSIENT;
    }

    public static FailureKind classifyStatus(int status) {
        if (status == 404) {
            return FailureKind.NO_ACCOUNT;
        }
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return FailureKind.TRANSIENT;
        }
        if (status >= 400) {
            return FailureKind.PERMANENT;
        }
        // A 2xx with a body we could not read, or a redirect: the grant is idempotent, so asking again is safe.
        return FailureKind.TRANSIENT;
    }

    public static @Nullable Duration retryAfter(Throwable error) {
        return unwrap(error) instanceof DawnApiException dawnError ? dawnError.getRetryAfter() : null;
    }

    public static int statusCode(Throwable error) {
        return unwrap(error) instanceof DawnApiException dawnError ? dawnError.getStatusCode() : 0;
    }

    public static String describe(Throwable error) {
        var cause = unwrap(error);
        var message = cause.getMessage();
        String text;
        if (message == null || message.isBlank()) {
            text = cause.getClass().getSimpleName();
        } else if (cause instanceof DawnApiException) {
            text = message;
        } else {
            text = cause.getClass().getSimpleName() + ": " + message;
        }
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    static Throwable unwrap(Throwable error) {
        var current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
