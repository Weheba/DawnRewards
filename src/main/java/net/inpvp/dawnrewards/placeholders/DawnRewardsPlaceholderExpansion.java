package net.inpvp.dawnrewards.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.util.DurationFormatter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public class DawnRewardsPlaceholderExpansion extends PlaceholderExpansion {

    private final DawnRewards plugin;

    public DawnRewardsPlaceholderExpansion(DawnRewards plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dawnrewards";
    }

    @Override
    public @NotNull String getAuthor() {
        return "InPvP";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        var user = plugin.getDawnUserCache().get(player);

        return switch (params) {
            case "connected" -> String.valueOf(plugin.getDawnPlayerTracker().isDawnConnected(player.getUniqueId()));
            case "reward_ready" -> String.valueOf(plugin.getClaimService().isClaimable(user));
            case "time_remaining" -> DurationFormatter.format(plugin.getClaimService().timeUntilClaimable(user));
            case "streak" -> String.valueOf(user.getCurrentStreak());
            default -> null;
        };
    }
}
