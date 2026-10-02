package net.inpvp.dawnrewards.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import net.inpvp.dawnrewards.DawnRewards;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
@CommandAlias("dawnrewards|dawn")
public class PlayerCommands extends BaseCommand {

    private final DawnRewards plugin;

    public PlayerCommands(DawnRewards plugin) {
        this.plugin = plugin;
    }

    @Default
    @Description("Opens your reward progress")
    public void onProgress(Player player) {
        plugin.getRewardsMenu().open(player);
    }

    @Subcommand("claim")
    @Description("Claims the reward that is waiting for you")
    public void onClaim(Player player) {
        plugin.getClaimService().claim(player);
    }
}
