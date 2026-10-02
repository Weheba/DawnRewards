package net.inpvp.dawnrewards.dawn;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.dawn.api.ClientPresence;
import net.inpvp.dawnrewards.dawn.api.DawnClient;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@NullMarked
public class DawnPlayerTracker implements Listener {

    private static final NamespacedKey COOKIE_KEY = new NamespacedKey("dawn", "client_info");
    private static final long LOOKUP_TIMEOUT_SECONDS = 3;

    private final Map<UUID, DawnPlayerPresence> presences = new ConcurrentHashMap<>();

    private final DawnRewards plugin;
    private final DawnClient client;

    public DawnPlayerTracker(DawnRewards plugin, DawnClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public boolean isDawnConnected(UUID playerId) {
        return getPresence(playerId).isDawnConnected();
    }

    public DawnPlayerPresence getPresence(UUID playerId) {
        return presences.getOrDefault(playerId, DawnPlayerPresence.UNKNOWN);
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        try {
            var cookie = validateCookie(event);
            var presence = validatePresence(event.getUniqueId());

            var result = cookie.thenCombine(presence, DawnPlayerPresence::new)
                    .exceptionally(_ -> new DawnPlayerPresence(false, false))
                    .join();

            presences.put(event.getUniqueId(), result);
        } catch (Throwable error) {
            plugin.getLogger().warning("Could not resolve the Dawn state of " + event.getName() + ": " + error.getMessage());
        }
    }

    @EventHandler
    public void onPlayerConnectionClose(PlayerConnectionCloseEvent event) {
        presences.remove(event.getPlayerUniqueId());
    }

    private CompletableFuture<Boolean> validateCookie(AsyncPlayerPreLoginEvent event) {
        return event.getConnection()
                .retrieveCookie(COOKIE_KEY)
                .thenApply(bytes -> parseCookie(event.getName(), bytes))
                .orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(_ -> false);
    }

    private CompletableFuture<Boolean> validatePresence(UUID playerId) {
        return client.getPresence(playerId)
                .thenApply(ClientPresence::onDawn)
                .orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(error -> {
                    plugin.getLogger().fine(() -> "Dawn presence lookup failed for " + playerId + ": " + error.getMessage());
                    return false;
                });
    }

    private boolean parseCookie(String playerName, byte @Nullable [] bytes) {
        if (bytes == null) {
            return false;
        }

        try {
            DawnCookie.parse(bytes);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not parse the Dawn cookie for " + playerName + ": " + e.getMessage());
            return false;
        }
    }
}
