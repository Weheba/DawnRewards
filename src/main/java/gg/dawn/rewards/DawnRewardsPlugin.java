package gg.dawn.rewards;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Daily and weekly rewards authorized by Dawn, with no trust in client brand strings. */
public final class DawnRewardsPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final String CHANNEL = "dawn:affiliate";
    private final Map<UUID, String> proofs = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final AffiliateClient api = new AffiliateClient();
    private ThreadPoolExecutor worker;
    private ThreadPoolExecutor heartbeatWorker;
    private RewardJournal journal;
    private long sequence;
    private boolean heartbeatPending;
    private boolean adminPending;

    @Override public void onEnable() {
        if (!getServer().getOnlineMode()) {
            getLogger().severe("DawnRewards requires online-mode authenticated UUIDs. Offline-mode and proxy forwarding are not supported.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        try {
            journal = new RewardJournal(getDataFolder().toPath().resolve("reward-grants.log"));
        } catch (IOException failure) {
            getLogger().severe("Cannot read reward journal. Disabling DawnRewards to prevent duplicate grants.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        sequence = getConfig().getLong("sequence", 0);
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), runnable -> {
            Thread thread = new Thread(runnable, "DawnRewards-API");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        heartbeatWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, "DawnRewards-Heartbeat");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getScheduler().runTaskTimer(this, this::heartbeat, 100, 600);
        getLogger().info("DawnRewards loaded. Payout eligibility requires the Dawn proof issuer and accounting rollout.");
    }

    @Override public void onDisable() {
        if (worker != null) worker.shutdownNow();
        if (heartbeatWorker != null) heartbeatWorker.shutdownNow();
        proofs.clear();
        pending.clear();
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!CHANNEL.equals(channel) || bytes.length == 0 || bytes.length > 4096) return;
        String proof = new String(bytes, StandardCharsets.US_ASCII);
        if (!proof.matches("[A-Za-z0-9_.-]{32,4096}")) return;
        // The plugin cannot mint or validate Dawn proofs. The backend binds them to this UUID and server.
        proofs.put(player.getUniqueId(), proof);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        proofs.remove(event.getPlayer().getUniqueId());
    }

    private void heartbeat() {
        if (heartbeatPending || credential().isBlank() || proofs.isEmpty()) return;
        JsonArray players = new JsonArray();
        List<UUID> sent = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : proofs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            JsonObject row = new JsonObject();
            row.addProperty("uuid", entry.getKey().toString());
            row.addProperty("proof", entry.getValue());
            players.add(row);
            sent.add(entry.getKey());
            if (players.size() == 1000) break;
        }
        if (players.isEmpty()) return;
        // Persist before sending so restarts cannot accidentally replay sequence numbers.
        if (sequence == Long.MAX_VALUE) return;
        getConfig().set("sequence", ++sequence);
        saveConfig();
        JsonObject body = new JsonObject();
        body.addProperty("sequence", sequence);
        body.add("players", players);
        String token = credential();
        heartbeatPending = true;
        if (submitHeartbeat(() -> {
            try { api.post("heartbeat", token, body); }
            catch (IOException failure) { getLogger().warning("Dawn heartbeat unavailable; no local CCU is credited."); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { main(() -> heartbeatPending = false); }
        })) sent.forEach(proofs::remove);
        else heartbeatPending = false;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("daily") || action.equals("weekly")) {
            if (sender instanceof Player player) claim(player, action);
            else message(sender, "player-only");
        } else if (Set.of("register", "verify", "status", "invoice", "alias", "verifyalias").contains(action)) {
            if (!sender.hasPermission("dawnrewards.admin")) return true;
            admin(sender, action, args);
        } else message(sender, "help");
        return true;
    }

    private void claim(Player player, String period) {
        if (credential().isBlank()) { message(player, "not-ready"); return; }
        List<String> commands = List.copyOf(getConfig().getStringList("rewards." + period));
        if (commands.isEmpty()) { message(player, "disabled"); return; }
        UUID uuid = player.getUniqueId();
        if (!pending.add(uuid)) { message(player, "pending"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid.toString());
        body.addProperty("period", period);
        String token = credential();
        if (!submit(() -> {
            try {
                JsonObject response = api.post("rewards/claim", token, body);
                boolean granted = ClaimDecision.isNewGrant(response);
                boolean eligible = ClaimDecision.flag(response, "eligible");
                String result = !eligible ? "proof-required" : "already-claimed";
                if (granted && eligible && journal.reserve(response.get("claimId").getAsString())) {
                    main(() -> deliver(uuid, commands));
                } else mainMessage(uuid, result);
            } catch (IOException | RuntimeException failure) { mainMessage(uuid, "unavailable"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { main(() -> pending.remove(uuid)); }
        })) {
            pending.remove(uuid);
            message(player, "unavailable");
        }
    }

    private void deliver(UUID uuid, List<String> commands) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            getLogger().warning("Reward reserved while player disconnected; manual reconciliation required.");
            return;
        }
        String name = player.getName();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return;
        for (String configured : commands) {
            String command = configured.replace("{player}", name).replace("{uuid}", uuid.toString());
            if (command.startsWith("/")) command = command.substring(1);
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                getLogger().warning("Reward command failed after reservation; manual reconciliation required.");
                message(player, "unavailable");
                return;
            }
        }
        message(player, "granted");
    }

    private void admin(CommandSender sender, String action, String[] args) {
        if (action.equals("status")) {
            sender.sendMessage("DawnRewards: " + (credential().isBlank() ? "not registered" : "registered")
                    + ". Public statistics: https://dawn.gg/servers");
            return;
        }
        // Tokens and DNS registration material are shown only in the server console.
        if (sender instanceof Player) { sender.sendMessage("Run this command in the server console."); return; }
        if (adminPending) { message(sender, "pending"); return; }
        if (action.equals("register") && !credential().isBlank()) {
            sender.sendMessage("Already registered. Preserve your credential; do not re-register to reset accounting.");
            return;
        }
        JsonObject body = new JsonObject();
        String operation = action;
        String token = "";
        if (action.equals("register")) body.addProperty("hostname", getConfig().getString("hostname", ""));
        if (action.equals("verify")) {
            body.addProperty("serverId", getConfig().getString("server-id", ""));
            body.addProperty("registrationToken", getConfig().getString("registration-token", ""));
        }
        if (action.equals("invoice")) {
            if (args.length != 2 || !args[1].matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
                sender.sendMessage("Usage: dawnrewards invoice YYYY-MM (completed UTC month)"); return;
            }
            body.addProperty("month", args[1]); operation = "invoices"; token = credential();
        }
        if (action.equals("alias") || action.equals("verifyalias")) {
            if (args.length != 2 || credential().isBlank()) {
                sender.sendMessage("Usage after registration: dawnrewards " + action + " play.example.com"); return;
            }
            body.addProperty("hostname", args[1]);
            operation = action.equals("alias") ? "aliases" : "aliases/verify"; token = credential();
        }
        String path = operation;
        String auth = token;
        adminPending = true;
        if (!submit(() -> {
            try {
                JsonObject response = api.post(path, auth, body);
                main(() -> {
                    if (action.equals("register")) {
                        getConfig().set("server-id", response.get("serverId").getAsString());
                        getConfig().set("registration-token", response.get("registrationToken").getAsString());
                        saveConfig();
                        sender.sendMessage("Create DNS TXT " + response.get("txtName").getAsString()
                                + " = " + response.get("txtValue").getAsString());
                        sender.sendMessage("Then run dawnrewards verify. Registration expires at "
                                + response.get("expiresAt").getAsString());
                    } else if (action.equals("verify")) {
                        getConfig().set("credential", response.get("pluginToken").getAsString());
                        getConfig().set("registration-token", null);
                        saveConfig();
                        sender.sendMessage("Dawn server ownership verified. Credential saved privately in config.yml.");
                    } else if (action.equals("alias")) {
                        sender.sendMessage("Create DNS TXT " + response.get("txtName").getAsString()
                                + " = " + response.get("txtValue").getAsString());
                        sender.sendMessage("Then run dawnrewards verifyalias " + args[1]);
                    } else if (action.equals("verifyalias")) {
                        sender.sendMessage("Alias verified. Its statistics now belong to the same server.");
                    } else {
                        sender.sendMessage("Invoice reference: " + response.get("reference").getAsString());
                        sender.sendMessage("Estimated USD: " + response.get("amountUsd").getAsString()
                                + ". Submit your invoice through Dawn support. This command does not pay funds.");
                    }
                });
            } catch (IOException | RuntimeException failure) { main(() -> message(sender, "unavailable")); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { main(() -> adminPending = false); }
        })) { adminPending = false; message(sender, "unavailable"); }
    }

    private String credential() { return getConfig().getString("credential", ""); }
    private void message(CommandSender sender, String key) { sender.sendMessage(getConfig().getString("messages." + key, key)); }
    private void mainMessage(UUID uuid, String key) { main(() -> { Player p = Bukkit.getPlayer(uuid); if (p != null) message(p, key); }); }
    private void main(Runnable task) { if (isEnabled()) Bukkit.getScheduler().runTask(this, task); }
    private boolean submit(Runnable task) {
        try { worker.execute(task); return true; }
        catch (RejectedExecutionException full) { return false; }
    }
    private boolean submitHeartbeat(Runnable task) {
        try { heartbeatWorker.execute(task); return true; }
        catch (RejectedExecutionException full) { return false; }
    }
}
