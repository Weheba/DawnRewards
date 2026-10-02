package net.inpvp.dawnrewards;

import co.aikar.commands.PaperCommandManager;
import lombok.Getter;
import net.inpvp.dawnrewards.claim.ClaimService;
import net.inpvp.dawnrewards.commands.AdminCommands;
import net.inpvp.dawnrewards.commands.PlayerCommands;
import net.inpvp.dawnrewards.config.Config;
import net.inpvp.dawnrewards.config.ConfigLoader;
import net.inpvp.dawnrewards.config.Rewards;
import net.inpvp.dawnrewards.dawn.DawnPlayerTracker;
import net.inpvp.dawnrewards.dawn.HeartbeatTask;
import net.inpvp.dawnrewards.dawn.api.DawnClient;
import net.inpvp.dawnrewards.gui.RewardsMenu;
import net.inpvp.dawnrewards.notifications.RewardNotifier;
import net.inpvp.dawnrewards.config.Messages;
import net.inpvp.dawnrewards.placeholders.DawnRewardsPlaceholderExpansion;
import net.inpvp.dawnrewards.storage.Database;
import net.inpvp.dawnrewards.storage.DawnUserRepository;
import net.inpvp.dawnrewards.user.DawnUserCache;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.xenondevs.invui.InvUI;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.logging.Level;

@NullMarked
@Getter
public class DawnRewards extends JavaPlugin {

    private DawnClient dawnClient;
    private DawnPlayerTracker dawnPlayerTracker;
    private HeartbeatTask heartbeatTask;

    private ConfigLoader<Config> configLoader;
    private ConfigLoader<Rewards> rewardsLoader;
    private ConfigLoader<Messages> messagesLoader;
    private PaperCommandManager commandManager;
    private Database database;
    private DawnUserCache dawnUserCache;
    private ClaimService claimService;
    private RewardsMenu rewardsMenu;
    private RewardNotifier rewardNotifier;

    private boolean startupComplete;
    private DawnUserRepository userRepository;

    @Override
    public void onEnable() {
        InvUI.getInstance().setPlugin(this);

        if (!loadConfigs() || !hasPluginToken() || !setupDatabase()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dawnClient = new DawnClient(getCfg().getPluginToken());
        dawnPlayerTracker = new DawnPlayerTracker(this, dawnClient);
        heartbeatTask = new HeartbeatTask(this, dawnClient);

        userRepository = database.createUserRepository();
        dawnUserCache = new DawnUserCache(this, userRepository);
        claimService = new ClaimService(this, dawnPlayerTracker, dawnUserCache, userRepository);
        rewardsMenu = new RewardsMenu(this);
        rewardNotifier = new RewardNotifier(this);

        registerListeners();
        registerCommands();
        registerPlaceholders();

        rewardNotifier.start();
        heartbeatTask.start();

        startupComplete = true;
    }

    @Override
    public void onDisable() {
        if (rewardNotifier != null) {
            rewardNotifier.stop();
        }

        if (heartbeatTask != null) {
            heartbeatTask.stop();
        }

        if (database != null) {
            database.close();
        }
    }

    @Override
    public FileConfiguration getConfig() {
        if (startupComplete) {
            throw new UnsupportedOperationException("DawnRewards uses configlib, use getCfg() instead");
        }

        return super.getConfig();
    }

    public Config getCfg() {
        return configLoader.get();
    }

    public Rewards getRewards() {
        return rewardsLoader.get();
    }

    public Messages getMessages() {
        return messagesLoader.get();
    }

    public void reloadConfigs() {
        configLoader.reload();
        rewardsLoader.reload();
        messagesLoader.reload();

        rewardNotifier.start();
    }

    private boolean loadConfigs() {
        var dataFolder = getDataFolder().toPath();

        try {
            configLoader = new ConfigLoader<>(Config.class, dataFolder.resolve("config.yml"));
            rewardsLoader = new ConfigLoader<>(Rewards.class, dataFolder.resolve("rewards.yml"), Rewards::validate);
            messagesLoader = new ConfigLoader<>(Messages.class, dataFolder.resolve("messages.yml"));
            return true;
        } catch (RuntimeException e) {
            getLogger().severe("Could not load the configuration files: " + e.getMessage());
            return false;
        }
    }

    private boolean hasPluginToken() {
        if (getCfg().getPluginToken().isBlank()) {
            getLogger().severe("No plugin-token is set in config.yml, get one from the Dawn account website");
            return false;
        }

        return true;
    }

    private boolean setupDatabase() {
        try {
            database = new Database(this);
            database.migrate();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not set up the database", e);
            return false;
        }
    }

    private void registerPlaceholders() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }

        new DawnRewardsPlaceholderExpansion(this).register();
        getLogger().info("Registered the PlaceholderAPI expansion");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(dawnPlayerTracker, this);
        getServer().getPluginManager().registerEvents(dawnUserCache, this);
        getServer().getPluginManager().registerEvents(rewardNotifier, this);
    }

    private void registerCommands() {
        commandManager = new PaperCommandManager(this);
        commandManager.enableUnstableAPI("help");

        commandManager.registerCommand(new PlayerCommands(this));
        commandManager.registerCommand(new AdminCommands(this));
    }
}
