package net.inpvp.dawnrewards.grants;

import net.inpvp.dawnrewards.dawn.api.DawnApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantFailuresTest {

    @ParameterizedTest
    @ValueSource(ints = {408, 425, 429, 500, 502, 503, 504})
    void transientStatuses(int status) {
        assertEquals(FailureKind.TRANSIENT, GrantFailures.classifyStatus(status));
        assertEquals(FailureKind.TRANSIENT, GrantFailures.classify(api(status)));
    }

    @Test
    void notFoundMeansNoAccountYet() {
        assertEquals(FailureKind.NO_ACCOUNT, GrantFailures.classifyStatus(404));
        assertEquals(FailureKind.NO_ACCOUNT, GrantFailures.classify(new CompletionException(api(404))));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409, 422})
    void permanentStatuses(int status) {
        assertEquals(FailureKind.PERMANENT, GrantFailures.classifyStatus(status));
        assertEquals(FailureKind.PERMANENT, GrantFailures.classify(new CompletionException(api(status))));
    }

    @Test
    void malformedSuccessBodyIsRetried() {
        assertEquals(FailureKind.TRANSIENT, GrantFailures.classifyStatus(200));
    }

    @Test
    void networkErrorsAreTransient() {
        assertEquals(FailureKind.TRANSIENT, GrantFailures.classify(new CompletionException(new HttpTimeoutException("request timed out"))));
        assertEquals(FailureKind.TRANSIENT, GrantFailures.classify(new CompletionException(new ConnectException())));
        assertEquals(FailureKind.TRANSIENT, GrantFailures.classify(new IOException("connection reset")));
    }

    @Test
    void readsRetryAfterAndStatusThroughWrappers() {
        var error = new CompletionException(new DawnApiException(429, "slow down", Duration.ofSeconds(90)));
        assertEquals(Duration.ofSeconds(90), GrantFailures.retryAfter(error));
        assertEquals(429, GrantFailures.statusCode(error));
        assertNull(GrantFailures.retryAfter(new IOException()));
        assertEquals(0, GrantFailures.statusCode(new IOException()));
    }

    @Test
    void describesErrors() {
        assertEquals("Dawn API returned HTTP 400: cosmetic not grantable", GrantFailures.describe(new CompletionException(api(400))));
        assertEquals("HttpTimeoutException: request timed out", GrantFailures.describe(new HttpTimeoutException("request timed out")));
        assertEquals("ConnectException", GrantFailures.describe(new ConnectException()));
        assertTrue(GrantFailures.describe(new IOException("x".repeat(2000))).length() <= 500);
    }

    private static DawnApiException api(int status) {
        return new DawnApiException(status, status == 400 ? "cosmetic not grantable" : "error", null);
    }
}
