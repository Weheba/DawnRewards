package net.inpvp.dawnrewards.notifications;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.util.Schedulers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class RewardNotifier implements Listener {

    private static final long TICKS_PER_SECOND = 20;

    private final DawnRewards plugin;

    private volatile @Nullable ScheduledTask task;

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
        task = Schedulers.globalTimer(plugin, this::notifyOnlinePlayers, ticks, ticks);
    }

    public void stop() {
        var current = task;
        if (current != null) {
            current.cancel();
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
        // On Folia the global region owns no players, so each check hops to that player's region.
        for (var player : plugin.getServer().getOnlinePlayers()) {
            Schedulers.player(plugin, player, () -> notifyIfReady(player));
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
