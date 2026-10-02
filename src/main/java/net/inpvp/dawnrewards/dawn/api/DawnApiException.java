package net.inpvp.dawnrewards.dawn.api;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@Getter
@NullMarked
public class DawnApiException extends RuntimeException {

    private final int statusCode;
    private final @Nullable Duration retryAfter;

    public DawnApiException(int statusCode, String message, @Nullable Duration retryAfter) {
        super("Dawn API returned HTTP " + statusCode + ": " + message);

        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public boolean isCredentialProblem() {
        return statusCode == 401 || statusCode == 403;
    }

    public boolean isRetryable() {
        return statusCode == 429 || statusCode >= 500;
    }
}
