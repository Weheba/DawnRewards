package net.inpvp.dawnrewards.dawn.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
import java.util.concurrent.atomic.AtomicLong;

@NullMarked
public class DawnClient {

    public static final int MAX_ROSTER_SIZE = 1000;

    private static final String PRESENCE_BASE = "https://api.dawn.gg/v2/profile/client-presence/";
    private static final String AFFILIATE_BASE = "https://api.dawn.gg/v2/server-affiliates/";
    private static final String PARTNER_GRANTS = "https://api.dawn.gg/v2/integrations/partner-grants";

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

    // Dawn rejects a heartbeat whose sequence is not above the last accepted one, restarts included.
    // Wall-clock milliseconds stay ahead of anything this server sent before without persisting a counter.
    private final AtomicLong heartbeatSequence = new AtomicLong();

    private final String pluginToken;

    public DawnClient(String pluginToken) {
        this.pluginToken = pluginToken;
    }

    public CompletableFuture<ClientPresence> getPresence(UUID playerId) {
        var request = get(URI.create(PRESENCE_BASE + playerId), false);
        return send(request, ClientPresence.class);
    }

    /**
     * Replaces the roster Dawn holds for one instance. Rosters of different instances add up, which
     * is how a network shares one plugin token across all of its servers.
     */
    public CompletableFuture<HeartbeatResponse> sendHeartbeat(String instance, Collection<UUID> playerIds) {
        requireCredential();

        if (playerIds.size() > MAX_ROSTER_SIZE) {
            throw new IllegalArgumentException("Roster of " + playerIds.size() + " exceeds the maximum of " + MAX_ROSTER_SIZE);
        }

        var players = playerIds.stream().map(RosterEntry::new).toList();
        var sequence = heartbeatSequence.updateAndGet(last -> Math.max(last + 1, System.currentTimeMillis()));
        var request = post(affiliateUri("heartbeat"), new HeartbeatRequest(instance, sequence, players));
        return send(request, HeartbeatResponse.class);
    }

    /**
     * Registers the daily claim of a player with Dawn. A player only counts toward the earnings of
     * the server after their first successful claim, and Dawn refuses alts sharing a device or IP.
     */
    public CompletableFuture<RewardClaimResponse> claimReward(UUID playerId) {
        requireCredential();

        var request = post(affiliateUri("rewards/claim"), new RewardClaimRequest(playerId, "daily"));
        return send(request, RewardClaimResponse.class);
    }

    /**
     * Grants a cosmetic to the Dawn account holding a Minecraft name. Uses the partner grant token, a
     * separate credential from the plugin token. Dawn grants each cosmetic once per player, so repeating
     * a request answers {@link PartnerGrantResponse#ALREADY_GRANTED} instead of granting twice.
     */
    public CompletableFuture<PartnerGrantResponse> grantCosmetic(String grantToken, String minecraftUsername, String cosmetic, @Nullable String deliveryId) {
        if (grantToken.isBlank()) {
            throw new IllegalStateException("No Dawn cosmetic grant token is configured");
        }

        var body = new PartnerGrantRequest(minecraftUsername, cosmetic, deliveryId);
        return send(post(URI.create(PARTNER_GRANTS), body, grantToken), PartnerGrantResponse.class);
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
        return post(uri, body, pluginToken);
    }

    private HttpRequest post(URI uri, Object body, String token) {
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize the Dawn request body", e);
        }

        return builder(uri, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest.Builder builder(URI uri, boolean authenticated) {
        return builder(uri, authenticated ? pluginToken : null);
    }

    private HttpRequest.Builder builder(URI uri, @Nullable String token) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
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

    private record HeartbeatRequest(String instance, long sequence, List<RosterEntry> players) {
    }

    private record RewardClaimRequest(UUID uuid, String period) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PartnerGrantRequest(
            @JsonProperty("minecraft_username") String minecraftUsername,
            String cosmetic,
            @JsonProperty("delivery_id") @Nullable String deliveryId) {
    }

}
