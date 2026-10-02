package net.inpvp.dawnrewards.notifications;

import net.inpvp.dawnrewards.DawnRewards;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class RewardNotifier implements Listener {

    private static final long TICKS_PER_SECOND = 20;

    private final DawnRewards plugin;

    private @Nullable BukkitTask task;

    public RewardNotifier(DawnRewards plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        var interval = plugin.getCfg().getNotifications().getNotifyRepeatInterval();
        if (interval <= 0) {
            return;
        }

        var ticks = interval * TICKS_PER_SECOND;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::notifyOnlinePlayers, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getCfg().getNotifications().isNotifyOnJoin()) {
            notifyIfReady(event.getPlayer());
        }
    }

    private void notifyOnlinePlayers() {
        for (var player : plugin.getServer().getOnlinePlayers()) {
            notifyIfReady(player);
        }
    }

    private void notifyIfReady(Player player) {
        if (!plugin.getDawnPlayerTracker().isDawnConnected(player.getUniqueId())) {
            return;
        }

        var user = plugin.getDawnUserCache().get(player.getUniqueId());
        if (user != null && plugin.getClaimService().isClaimable(user)) {
            player.sendMessage(plugin.getMessages().rewardReady());
        }
    }
}
