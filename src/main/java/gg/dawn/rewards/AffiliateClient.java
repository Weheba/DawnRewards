package gg.dawn.rewards;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** A bounded client for Dawn's affiliate API. Never logs tokens or response bodies. */
final class AffiliateClient {
    private static final Set<String> PATHS = Set.of("register", "verify", "heartbeat", "rewards/claim", "invoices", "aliases", "aliases/verify");
    private final URI base;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Gson gson = new Gson();

    AffiliateClient() { this(URI.create("https://api.dawn.gg/v2/server-affiliates/")); }
    AffiliateClient(URI base) { this.base = base; }

    JsonObject post(String path, String credential, JsonObject body) throws IOException, InterruptedException {
        if (!PATHS.contains(path)) throw new IllegalArgumentException("Unknown affiliate operation");
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header("User-Agent", "DawnRewards/0.1.0")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
        if (!credential.isBlank()) request.header("Authorization", "Bearer " + credential);
        var operation = http.sendAsync(request.build(), info -> new BoundedBody());
        try {
            HttpResponse<byte[]> response = operation.get(10, TimeUnit.SECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Affiliate API returned HTTP " + response.statusCode());
            try {
                return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException malformed) {
                throw new IOException("Invalid affiliate response", malformed);
            }
        } catch (ExecutionException | TimeoutException failure) {
            operation.cancel(true);
            throw new IOException("Affiliate request did not complete", failure);
        } catch (InterruptedException interrupted) {
            operation.cancel(true);
            throw interrupted;
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > 65_536 - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Affiliate response exceeds size limit"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
