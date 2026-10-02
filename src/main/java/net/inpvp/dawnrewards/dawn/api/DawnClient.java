package net.inpvp.dawnrewards.dawn.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@NullMarked
public class DawnClient {

    public static final int MAX_ROSTER_SIZE = 1000;

    private static final String PRESENCE_BASE = "https://api.dawn.gg/v2/profile/client-presence/";
    private static final String AFFILIATE_BASE = "https://api.dawn.gg/v2/server-affiliates/";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String pluginToken;

    public DawnClient(String pluginToken) {
        this.pluginToken = pluginToken;
    }

    public CompletableFuture<ClientPresence> getPresence(UUID playerId) {
        var request = get(URI.create(PRESENCE_BASE + playerId), false);
        return send(request, ClientPresence.class);
    }

    public CompletableFuture<HeartbeatResponse> sendHeartbeat(Collection<UUID> playerIds) {
        requireCredential();

        if (playerIds.size() > MAX_ROSTER_SIZE) {
            throw new IllegalArgumentException("Roster of " + playerIds.size() + " exceeds the maximum of " + MAX_ROSTER_SIZE);
        }

        var players = playerIds.stream().map(RosterEntry::new).toList();
        var request = post(affiliateUri("heartbeat"), new HeartbeatRequest(players));
        return send(request, HeartbeatResponse.class);
    }

    private void requireCredential() {
        if (pluginToken.isBlank()) {
            throw new IllegalStateException("No Dawn plugin token is configured");
        }
    }

    private URI affiliateUri(String path) {
        return URI.create(AFFILIATE_BASE + path);
    }

    private HttpRequest get(URI uri, boolean authenticated) {
        return builder(uri, authenticated).GET().build();
    }

    private HttpRequest post(URI uri, Object body) {
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize the Dawn request body", e);
        }

        return builder(uri, true)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest.Builder builder(URI uri, boolean authenticated) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");

        if (authenticated) {
            builder.header("Authorization", "Bearer " + pluginToken);
        }
        return builder;
    }

    private <T> CompletableFuture<T> send(HttpRequest request, Class<T> type) {
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw toException(response);
                    }

                    try {
                        return OBJECT_MAPPER.readValue(response.body(), type);
                    } catch (Exception e) {
                        throw new DawnApiException(response.statusCode(), "Malformed response body", null);
                    }
                });
    }

    private DawnApiException toException(HttpResponse<String> response) {
        String message;
        try {
            var error = OBJECT_MAPPER.readTree(response.body()).get("error");
            message = error != null && error.isTextual() ? error.asText() : truncate(response.body());
        } catch (Exception e) {
            message = truncate(response.body());
        }

        return new DawnApiException(response.statusCode(), message, retryAfter(response));
    }

    private static Duration retryAfter(HttpResponse<String> response) {
        return response.headers()
                .firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(value.trim()));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private static String truncate(String body) {
        var trimmed = body.strip();
        if (trimmed.isEmpty()) {
            return "no response body";
        }
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }

    private record RosterEntry(UUID uuid) {
    }

    private record HeartbeatRequest(List<RosterEntry> players) {
    }

}
