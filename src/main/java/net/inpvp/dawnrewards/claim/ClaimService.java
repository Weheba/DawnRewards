package net.inpvp.dawnrewards.claim;

import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.dawn.DawnPlayerTracker;
import net.inpvp.dawnrewards.storage.DawnUserRepository;
import net.inpvp.dawnrewards.user.ClaimUpdate;
import net.inpvp.dawnrewards.user.DawnUser;
import net.inpvp.dawnrewards.user.DawnUserCache;
import net.inpvp.dawnrewards.util.DurationFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;

@NullMarked
public class ClaimService {

    private static final Duration CLAIM_COOLDOWN = Duration.ofHours(24);
    private static final Duration CLAIM_WINDOW = Duration.ofHours(24);

    private final DawnRewards plugin;
    private final DawnPlayerTracker tracker;
    private final DawnUserCache cache;
    private final DawnUserRepository repository;

    public ClaimService(DawnRewards plugin, DawnPlayerTracker tracker, DawnUserCache cache, DawnUserRepository repository) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.cache = cache;
        this.repository = repository;
    }

    public boolean isClaimable(DawnUser user) {
        return isClaimable(user, Instant.now());
    }

    public Duration timeUntilClaimable(DawnUser user) {
        var lastClaimAt = user.getLastClaimAt();
        if (lastClaimAt == null) {
            return Duration.ZERO;
        }

        return Duration.between(Instant.now(), lastClaimAt.plus(CLAIM_COOLDOWN));
    }

    public void claim(Player player) {
        var playerId = player.getUniqueId();

        if (!tracker.isDawnConnected(playerId)) {
            player.sendMessage(plugin.getMessages().notOnDawn());
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var result = tryClaim(playerId);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (result == ClaimResult.GRANTED) {
                    giveRewards(player, cache.get(player).getCurrentDay());
                }

                player.sendMessage(describe(player, result));
            });
        });
    }

    private Component describe(Player player, ClaimResult result) {
        var messages = plugin.getMessages();

        return switch (result) {
            case GRANTED -> messages.claimed(cache.get(player).getCurrentDay());
            case NOT_ON_DAWN -> messages.notOnDawn();
            case ON_COOLDOWN -> messages.onCooldown(DurationFormatter.format(timeUntilClaimable(cache.get(player))));
            case FAILED -> messages.claimFailed();
        };
    }

    private ClaimResult tryClaim(UUID playerId) {
        var now = Instant.now();

        try {
            var user = repository.load(playerId);
            if (!isClaimable(user, now)) {
                return ClaimResult.ON_COOLDOWN;
            }

            var update = nextClaim(user, now);
            if (!repository.applyClaim(playerId, update, claimableBefore(now))) {
                return ClaimResult.ON_COOLDOWN;
            }

            var cached = cache.get(playerId);
            if (cached != null) {
                cached.apply(update);
            }

            return ClaimResult.GRANTED;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not claim the reward for " + playerId, e);
            return ClaimResult.FAILED;
        }
    }

    private boolean isClaimable(DawnUser user, Instant now) {
        var lastClaimAt = user.getLastClaimAt();
        return lastClaimAt == null || !lastClaimAt.isAfter(claimableBefore(now));
    }

    private ClaimUpdate nextClaim(DawnUser user, Instant now) {
        var lastClaimAt = user.getLastClaimAt();
        var streakBroken = lastClaimAt == null || lastClaimAt.isBefore(streakLostBefore(now));

        var streak = streakBroken ? 1 : user.getCurrentStreak() + 1;
        var day = streakBroken ? 1 : nextDay(user.getCurrentDay());

        return new ClaimUpdate(day, streak, Math.max(user.getHighestStreak(), streak), now);
    }

    private int nextDay(int currentDay) {
        var next = currentDay + 1;
        return next > plugin.getRewards().getLength() ? 1 : next;
    }

    private Instant claimableBefore(Instant now) {
        return now.minus(CLAIM_COOLDOWN);
    }

    private Instant streakLostBefore(Instant now) {
        return now.minus(CLAIM_COOLDOWN).minus(CLAIM_WINDOW);
    }

    public void giveRewards(Player player, int day) {
        var reward = plugin.getRewards().getDay(day);

        if (reward == null) {
            plugin.getLogger().warning("No reward is configured for day " + day);
            return;
        }

        var console = plugin.getServer().getConsoleSender();
        for (var command : reward.getCommands()) {
            plugin.getServer().dispatchCommand(console, command.replace("{player}", player.getName()));
        }
    }

}
