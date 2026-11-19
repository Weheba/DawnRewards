package net.inpvp.dawnrewards.grants;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.dawn.api.DawnClient;
import net.inpvp.dawnrewards.storage.GrantRepository;
import net.inpvp.dawnrewards.util.DurationFormatter;
import net.inpvp.dawnrewards.util.Schedulers;
import net.inpvp.dawnrewards.util.Text;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Cosmetic grants bought on a store such as Tebex. The store runs a console command once per purchase,
 * the grant is stored first and then sent to Dawn, so a failed request is retried instead of lost.
 */
@NullMarked
public class GrantService implements GrantProcessor.Listener {

    private static final long PASS_INTERVAL_TICKS = 30 * 20;
    private static final int LIST_LIMIT = 15;

    private final DawnRewards plugin;
    private final GrantProcessor processor;
    private final GrantRepository repository;

    private @Nullable ScheduledTask task;
    private volatile boolean warnedMissingToken;

    public GrantService(DawnRewards plugin, DawnClient client, GrantRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.processor = new GrantProcessor(repository,
                grant -> client.grantCosmetic(token(), grant.playerName(), grant.cosmetic(), grant.deliveryIdOrNull()),
                this, Clock.systemUTC(), plugin.getLogger());
    }

    public synchronized void start() {
        stop();

        Schedulers.async(plugin, () -> {
            try {
                processor.resumeAfterStartup();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not resume the cosmetic grant queue", e);
            }
            runPass();
        });
        task = Schedulers.globalTimer(plugin, () -> Schedulers.async(plugin, this::runPass), PASS_INTERVAL_TICKS, PASS_INTERVAL_TICKS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean hasToken() {
        return !token().isBlank();
    }

    /**
     * Queues a purchase and sends it right away. Answers the sender as soon as it is stored.
     */
    public void submit(CommandSender sender, String playerName, String cosmetic, @Nullable String deliveryId) {
        var messages = plugin.getMessages();
        var delivery = deliveryId == null ? "" : deliveryId;

        if (!hasToken()) {
            sender.sendMessage(messages.prefixed("<red>cosmetic-grant-token is not set in config.yml, so "
                    + Text.escape(cosmetic) + " for " + Text.escape(playerName) + " was not queued. Set the token, run "
                    + "dawnrewards reload, then run this command again."));
            return;
        }

        var invalid = GrantInput.validate(playerName, cosmetic, delivery);
        if (invalid != null) {
            plugin.getLogger().severe("Refused cosmetic grant " + cosmetic + " for " + playerName + ": " + invalid);
            sender.sendMessage(messages.prefixed("<red>" + Text.escape(invalid)));
            return;
        }

        Schedulers.async(plugin, () -> {
            EnqueueResult result;
            try {
                result = processor.enqueue(playerName, cosmetic, delivery);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not queue cosmetic grant " + cosmetic + " for " + playerName
                        + (delivery.isEmpty() ? "" : " (delivery " + delivery + ")") + ", run the command again once the database works", e);
                sender.sendMessage(messages.prefixed("<red>Could not store the grant, check the console."));
                return;
            }

            var grant = result.grant();
            var what = "<#F97316>" + Text.escape(grant.cosmetic()) + "</#F97316> for <#F97316>" + Text.escape(grant.playerName()) + "</#F97316>";
            sender.sendMessage(messages.prefixed(switch (result.outcome()) {
                case QUEUED -> "<gray>Granting " + what + ".";
                case REQUEUED -> "<gray>Granting " + what + " again after an earlier failure.";
                case ALREADY_QUEUED -> "<gray>" + what + " is already queued, attempt " + (grant.attempts() + 1) + " "
                        + whenText(grant.nextAttemptAt()) + ".";
                case ALREADY_GRANTED -> "<gray>" + what + " was already granted for this delivery.";
            }));

            if (result.outcome() == EnqueueResult.Outcome.QUEUED || result.outcome() == EnqueueResult.Outcome.REQUEUED) {
                processor.attempt(grant);
            }
        });
    }

    /**
     * Sends every pending grant now and gives failed and expired ones another retry window.
     */
    public void retryNow(CommandSender sender) {
        var messages = plugin.getMessages();
        if (!hasToken()) {
            sender.sendMessage(messages.prefixed("<red>cosmetic-grant-token is not set in config.yml."));
            return;
        }

        Schedulers.async(plugin, () -> {
            try {
                var count = processor.retryAll();
                sender.sendMessage(messages.prefixed("<gray>Retrying <#F97316>" + count + "</#F97316> cosmetic grants now."));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not requeue cosmetic grants", e);
                sender.sendMessage(messages.prefixed("<red>Could not requeue the grants, check the console."));
                return;
            }
            runPass();
        });
    }

    public void list(CommandSender sender) {
        var messages = plugin.getMessages();

        Schedulers.async(plugin, () -> {
            try {
                var counts = repository.countByStatus();
                var open = repository.findOpen(LIST_LIMIT);

                sender.sendMessage(messages.prefixed("<gray>Cosmetic grants: <white>" + counts.get(GrantStatus.PENDING)
                        + "</white> pending, <white>" + counts.get(GrantStatus.FAILED) + "</white> failed, <white>"
                        + counts.get(GrantStatus.EXPIRED) + "</white> given up, <white>" + counts.get(GrantStatus.GRANTED)
                        + "</white> granted" + (hasToken() ? "" : " <red>(cosmetic-grant-token not set)")));

                for (var grant : open) {
                    sender.sendMessage(Text.parse("  " + describeLine(grant)));
                }

                var shown = open.size();
                var total = counts.get(GrantStatus.PENDING) + counts.get(GrantStatus.FAILED) + counts.get(GrantStatus.EXPIRED);
                if (total > shown) {
                    sender.sendMessage(Text.parse("  <gray>and " + (total - shown) + " more"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not list cosmetic grants", e);
                sender.sendMessage(messages.prefixed("<red>Could not read the grants, check the console."));
            }
        });
    }

    @Override
    public void granted(CosmeticGrant grant, boolean alreadyGranted) {
        plugin.getLogger().info((alreadyGranted ? "Dawn already had cosmetic " : "Granted Dawn cosmetic ") + grant.describe());
        notifyPlayer(grant);
    }

    @Override
    public void retrying(CosmeticGrant grant, FailureKind kind, String error, Instant nextAttempt) {
        var in = DurationFormatter.format(Duration.between(Instant.now(), nextAttempt));
        if (kind == FailureKind.NO_ACCOUNT) {
            plugin.getLogger().info("No Dawn account holds the name " + grant.playerName() + " yet, retrying cosmetic "
                    + grant.describe() + " in " + in);
            return;
        }

        plugin.getLogger().warning("Could not grant Dawn cosmetic " + grant.describe() + ", retrying in " + in + ": " + error);
    }

    @Override
    public void failed(CosmeticGrant grant, int statusCode, String error) {
        var logger = plugin.getLogger();
        logger.severe("==================================================");
        logger.severe("Dawn refused cosmetic grant " + grant.describe() + ", it will NOT be retried.");
        logger.severe("Reason: " + error);
        if (statusCode == 401 || statusCode == 403) {
            logger.severe("Dawn rejected the cosmetic-grant-token. Fix it in config.yml, run 'dawnrewards reload', then 'dawnrewards grants retry'.");
        } else {
            logger.severe("Check the player name and that Dawn lets this token grant the cosmetic. Once fixed, run 'dawnrewards grants retry'.");
        }
        logger.severe("==================================================");
    }

    @Override
    public void expired(CosmeticGrant grant, String error) {
        plugin.getLogger().severe("Gave up on Dawn cosmetic grant " + grant.describe() + " after "
                + RetryPolicy.RETRY_WINDOW.toDays() + " days. Last error: " + error
                + ". Run 'dawnrewards grants retry' to try for another " + RetryPolicy.RETRY_WINDOW.toDays() + " days.");
    }

    private void runPass() {
        if (!hasToken()) {
            if (!warnedMissingToken) {
                warnedMissingToken = true;
                try {
                    var pending = repository.countByStatus().get(GrantStatus.PENDING);
                    if (pending > 0) {
                        plugin.getLogger().warning(pending + " cosmetic grants are waiting, but cosmetic-grant-token is not set in config.yml");
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not count pending cosmetic grants", e);
                }
            }
            return;
        }

        warnedMissingToken = false;
        processor.processDue();
    }

    private void notifyPlayer(CosmeticGrant grant) {
        if (!plugin.isEnabled()) {
            return;
        }

        Schedulers.global(plugin, () -> {
            var player = plugin.getServer().getPlayerExact(grant.playerName());
            if (player == null) {
                return;
            }

            var message = plugin.getMessages().cosmeticGranted(grant.cosmetic());
            if (message != null) {
                Schedulers.player(plugin, player, () -> player.sendMessage(message));
            }
        });
    }

    private String token() {
        return plugin.getCfg().getCosmeticGrantToken().strip();
    }

    private static String describeLine(CosmeticGrant grant) {
        var line = new StringBuilder()
                .append("<#F97316>").append(Text.escape(grant.playerName())).append("</#F97316> ")
                .append("<white>").append(Text.escape(grant.cosmetic())).append("</white>");
        if (!grant.deliveryId().isEmpty()) {
            line.append(" <dark_gray>").append(Text.escape(grant.deliveryId())).append("</dark_gray>");
        }

        line.append(switch (grant.status()) {
            case PENDING -> " <yellow>pending</yellow> <gray>" + grant.attempts() + " attempts, next " + whenText(grant.nextAttemptAt());
            case FAILED -> " <red>failed</red><gray>";
            case EXPIRED -> " <red>given up</red><gray>";
            case GRANTED -> " <green>granted</green><gray>";
        });

        if (grant.lastError() != null) {
            line.append(": ").append(Text.escape(grant.lastError()));
        }
        return line.toString();
    }

    private static String whenText(Instant at) {
        var until = Duration.between(Instant.now(), at);
        return until.isNegative() || until.isZero() ? "now" : "in " + DurationFormatter.format(until);
    }
}
