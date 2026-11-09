package net.inpvp.dawnrewards.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

/**
 * Paper's region schedulers, which exist on every supported Paper version and are the only ones Folia allows.
 * On Paper the global region and every player run on the main thread, so the direct paths keep the old timing.
 */
@NullMarked
public final class Schedulers {

    // Paper 1.20.6 has no isGlobalTickThread(), Folia always has it. On Paper the main thread is the global region.
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private Schedulers() {
    }

    public static void async(Plugin plugin, Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    /**
     * Runs on the global region, right away when already there. Console commands belong here on Folia.
     */
    public static void global(Plugin plugin, Runnable task) {
        var server = plugin.getServer();
        if (FOLIA ? server.isGlobalTickThread() : server.isPrimaryThread()) {
            task.run();
            return;
        }

        server.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static ScheduledTask globalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, scheduled -> task.run(), delayTicks, periodTicks);
    }

    /**
     * Runs on the thread that owns the player, right away when already there. Skipped once the player is gone.
     */
    public static void player(Plugin plugin, Player player, Runnable task) {
        if (plugin.getServer().isOwnedByCurrentRegion(player)) {
            task.run();
            return;
        }

        player.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Schedulers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
