package net.inpvp.dawnrewards.dawn;

import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.dawn.api.DawnApiException;
import net.inpvp.dawnrewards.dawn.api.DawnClient;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NullMarked
public class HeartbeatTask {

    private static final long INTERVAL_TICKS = 15 * 20;

    private final DawnRewards plugin;
    private final DawnClient client;

    private @Nullable BukkitTask task;

    public HeartbeatTask(DawnRewards plugin, DawnClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void start() {
        stop();

        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::collectRoster, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void collectRoster() {
        var roster = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> sendInBatches(roster));
    }

    private void sendInBatches(List<UUID> roster) {
        for (var start = 0; start < roster.size(); start += DawnClient.MAX_ROSTER_SIZE) {
            var end = Math.min(start + DawnClient.MAX_ROSTER_SIZE, roster.size());
            send(roster.subList(start, end));
        }
    }

    private void send(List<UUID> roster) {
        client.sendHeartbeat(roster).exceptionally(error -> {
            handleFailure(error);
            return null;
        });
    }

    private void handleFailure(Throwable error) {
        var cause = error.getCause() == null ? error : error.getCause();

        if (cause instanceof DawnApiException dawnError && dawnError.isCredentialProblem()) {
            plugin.getLogger().severe("Dawn rejected the plugin token, stopping heartbeats: " + dawnError.getMessage());
            plugin.getServer().getScheduler().runTask(plugin, this::stop);
            return;
        }

        plugin.getLogger().warning("Dawn heartbeat failed: " + cause.getMessage());
    }
}
