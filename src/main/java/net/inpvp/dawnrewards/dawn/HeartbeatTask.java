package net.inpvp.dawnrewards.dawn;

import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.dawn.api.DawnApiException;
import net.inpvp.dawnrewards.dawn.api.DawnClient;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NullMarked
public class HeartbeatTask {

    private static final long INTERVAL_TICKS = 15 * 20;
    // Below the limit of one heartbeat, so an uneven hash split still fits.
    private static final int PART_SIZE = 800;

    // New on every boot: servers cloned from one template never share an instance, and nothing
    // needs configuring. Dawn forgets an instance an hour after its last heartbeat.
    private final String instanceId = UUID.randomUUID().toString();

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
        var tracker = plugin.getDawnPlayerTracker();
        var online = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();

        // A server too big for one heartbeat reports its Dawn players only, they are the ones who can earn.
        var roster = online.size() <= DawnClient.MAX_ROSTER_SIZE
                ? online
                : online.stream().filter(tracker::isDawnConnected).toList();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> sendInParts(roster));
    }

    private void sendInParts(List<UUID> roster) {
        // Each part is its own instance on Dawn. A player always hashes to the same part, so their
        // time keeps counting as long as the number of parts does not change.
        var parts = Math.max(1, Math.ceilDiv(roster.size(), PART_SIZE));
        var rosters = new ArrayList<List<UUID>>(parts);
        for (var part = 0; part < parts; part++) {
            rosters.add(new ArrayList<>());
        }
        for (var playerId : roster) {
            var part = rosters.get(Math.floorMod(playerId.hashCode(), parts));
            if (part.size() < DawnClient.MAX_ROSTER_SIZE) {
                part.add(playerId);
            }
        }

        for (var part = 0; part < parts; part++) {
            send(instanceId + "-" + part, rosters.get(part));
        }
    }

    private void send(String instance, List<UUID> roster) {
        client.sendHeartbeat(instance, roster).exceptionally(error -> {
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
