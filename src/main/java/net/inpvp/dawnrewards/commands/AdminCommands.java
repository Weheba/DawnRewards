package net.inpvp.dawnrewards.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.sql.SQLException;
import java.util.logging.Level;

@NullMarked
@CommandAlias("dawnrewards|dawn")
public class AdminCommands extends BaseCommand {

    private static final String DISCORD_URL = "https://discord.gg/feather";

    private final DawnRewards plugin;

    public AdminCommands(DawnRewards plugin) {
        this.plugin = plugin;
    }

    @HelpCommand
    @Description("Lists every available command")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("reset")
    @CommandPermission("dawnrewards.commands.reset")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @Description("Resets a players progress back to day 1")
    public void onReset(CommandSender sender, OnlinePlayer target) {
        var player = target.getPlayer();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getUserRepository().reset(player.getUniqueId());

                var user = plugin.getDawnUserCache().get(player.getUniqueId());
                if (user != null) {
                    user.reset();
                }

                sender.sendMessage(plugin.getMessages().prefixed("<gray>Reset the progress of <#F97316>" + player.getName() + "</#F97316>."));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not reset " + player.getName(), e);
                sender.sendMessage(plugin.getMessages().prefixed("<red>Could not reset that player, check the console."));
            }
        });
    }

    @Subcommand("give")
    @CommandPermission("dawnrewards.commands.give")
    @Syntax("<day> <player>")
    @CommandCompletion("@nothing @players")
    @Description("Runs the reward commands for a day without progressing the player")
    public void onGive(CommandSender sender, int day, OnlinePlayer target) {
        var length = plugin.getRewards().getLength();

        if (day < 1 || day > length) {
            sender.sendMessage(plugin.getMessages().prefixed("<gray>Day must be between <#F97316>1</#F97316> and <#F97316>" + length + "</#F97316>."));
            return;
        }

        plugin.getClaimService().giveRewards(target.getPlayer(), day);
        sender.sendMessage(plugin.getMessages().prefixed("<gray>Ran the day <#F97316>" + day + "</#F97316> rewards for <#F97316>" + target.getPlayer().getName() + "</#F97316>."));
    }

    @Subcommand("info")
    @CommandPermission("dawnrewards.commands.info")
    @Description("Shows the version, author and support information")
    public void onInfo(CommandSender sender) {
        var meta = plugin.getPluginMeta();

        var messages = plugin.getMessages();

        var website = meta.getWebsite();

        sender.sendMessage(messages.prefixed("<gray>Version: <white>" + meta.getVersion()));
        sender.sendMessage(messages.prefixed("<gray>Authors: <white>" + String.join(", ", meta.getAuthors())));

        if (website != null) {
            sender.sendMessage(messages.prefixed("<gray>Website: " + link(website)));
        }

        sender.sendMessage(messages.prefixed("<gray>Discord: " + link(DISCORD_URL)));
        sender.sendMessage(messages.prefixed("<gray>Rewards configured: <white>" + plugin.getRewards().getLength() + " days"));
        sender.sendMessage(messages.prefixed("<gray>Storage: <white>" + plugin.getCfg().getStorage().getType().name()));
    }

    private String link(String url) {
        return "<click:open_url:'" + url + "'><#F97316>" + url + "</#F97316></click>";
    }

    @Subcommand("player")
    @CommandPermission("dawnrewards.commands.player")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @Description("Shows the Dawn connection state of a player")
    public void onPlayer(CommandSender sender, OnlinePlayer target) {
        var messages = plugin.getMessages();
        var player = target.getPlayer();
        var presence = plugin.getDawnPlayerTracker().getPresence(player.getUniqueId());

        sender.sendMessage(messages.prefixed("<white>" + player.getName() + " "
                + (presence.isDawnConnected() ? "<green>(Eligible)" : "<red>(Ineligible)")));
        sender.sendMessage(requirement(presence.isCookieValidated(), "Using Dawn Client"));
        sender.sendMessage(requirement(presence.isPresenceValidated(), "Dawn Session Confirmed"));
    }

    private Component requirement(boolean met, String label) {
        return Text.parse(met
                ? "  <green>✔ <gray>" + label
                : "  <red>✘ <gray>" + label);
    }

    @Subcommand("reload")
    @CommandPermission("dawnrewards.commands.reload")
    @Description("Reloads the configuration files from disk")
    public void onReload(CommandSender sender) {
        try {
            plugin.reloadConfigs();
            sender.sendMessage(plugin.getMessages().prefixed("<gray>Reloaded the configuration files."));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not reload the configuration files", e);
            sender.sendMessage(plugin.getMessages().prefixed("<red>Could not reload the configuration files, check the console."));
        }
    }
}
