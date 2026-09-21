package gg.dawn.rewards;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AffiliateClientTest {
    @Test void sendsCredentialAndJsonToExpectedRoute() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rewards/claim", exchange -> {
            assertEquals("Bearer test-plugin-token", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("POST", exchange.getRequestMethod());
            assertTrue(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).contains("daily"));
            byte[] body = "{\"granted\":true,\"claimId\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            AffiliateClient client = new AffiliateClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            JsonObject request = new JsonObject(); request.addProperty("period", "daily");
            assertTrue(client.post("rewards/claim", "test-plugin-token", request).get("granted").getAsBoolean());
            assertThrows(IllegalArgumentException.class, () -> client.post("../other", "secret", request));
        } finally { server.stop(0); }
    }
    @Test void neverFollowsCredentialRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/heartbeat", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://example.com/steal");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        try {
            AffiliateClient client = new AffiliateClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            assertThrows(IOException.class, () -> client.post("heartbeat", "secret", new JsonObject()));
        } finally { server.stop(0); }
    }
}
