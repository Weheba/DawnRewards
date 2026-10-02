package net.inpvp.dawnrewards.user;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.storage.DawnUserRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@NullMarked
public class DawnUserCache implements Listener {

    private final Map<UUID, DawnUser> users = new ConcurrentHashMap<>();

    private final DawnRewards plugin;
    private final DawnUserRepository repository;

    public DawnUserCache(DawnRewards plugin, DawnUserRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public DawnUser get(Player player) {
        var user = users.get(player.getUniqueId());
        if (user == null) {
            throw new IllegalStateException("No cached DawnUser for the online player " + player.getName());
        }

        return user;
    }

    public @Nullable DawnUser get(UUID playerId) {
        return users.get(playerId);
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        var playerId = event.getUniqueId();

        try {
            users.put(playerId, repository.load(playerId));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load the Dawn user for " + event.getName(), e);
        }
    }

    @EventHandler
    public void onPlayerConnectionClose(PlayerConnectionCloseEvent event) {
        users.remove(event.getPlayerUniqueId());
    }
}
