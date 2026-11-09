package net.inpvp.dawnrewards.claim;

import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.dawn.DawnPlayerTracker;
import net.inpvp.dawnrewards.dawn.api.DawnClient;
import net.inpvp.dawnrewards.dawn.api.RewardClaimResponse;
import net.inpvp.dawnrewards.storage.DawnUserRepository;
import net.inpvp.dawnrewards.user.ClaimUpdate;
import net.inpvp.dawnrewards.user.DawnUser;
import net.inpvp.dawnrewards.user.DawnUserCache;
import net.inpvp.dawnrewards.util.DurationFormatter;
import net.inpvp.dawnrewards.util.Schedulers;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
    private final DawnClient client;
    private final DawnPlayerTracker tracker;
    private final DawnUserCache cache;
    private final DawnUserRepository repository;

    public ClaimService(DawnRewards plugin, DawnClient client, DawnPlayerTracker tracker, DawnUserCache cache, DawnUserRepository repository) {
        this.plugin = plugin;
        this.client = client;
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

        Schedulers.async(plugin, () -> {
            var claim = tryClaim(playerId);

            Schedulers.player(plugin, player, () -> {
                if (!player.isOnline()) {
                    return;
                }

                // The granted day travels with the claim: a reset racing it must not change what is handed out.
                if (claim.result() == ClaimResult.GRANTED) {
                    giveRewards(player, claim.day(), () -> player.sendMessage(describe(claim)));
                    return;
                }

                player.sendMessage(describe(claim));
            });
        });
    }

    private Component describe(Claim claim) {
        var messages = plugin.getMessages();

        return switch (claim.result()) {
            case GRANTED -> messages.claimed(claim.day());
            case NOT_ON_DAWN -> messages.notOnDawn();
            case ON_COOLDOWN -> messages.onCooldown(DurationFormatter.format(claim.cooldown()));
            case DEVICE_ALREADY_USED -> messages.deviceAlreadyUsed();
            case FAILED -> messages.claimFailed();
        };
    }

    private Claim tryClaim(UUID playerId) {
        var now = Instant.now();

        try {
            var user = repository.load(playerId);
            if (!isClaimable(user, now)) {
                return Claim.onCooldown(timeUntilClaimable(user));
            }

            var denied = registerWithDawn(playerId);
            if (denied != null) {
                return Claim.of(denied);
            }

            var update = nextClaim(user, now);
            if (!repository.applyClaim(playerId, update, claimableBefore(now))) {
                // Another claim won the race; its row has the cooldown to show.
                return Claim.onCooldown(timeUntilClaimable(repository.load(playerId)));
            }

            var cached = cache.get(playerId);
            if (cached != null) {
                cached.apply(update);
            }

            return new Claim(ClaimResult.GRANTED, update.currentDay(), Duration.ZERO);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not claim the reward for " + playerId, e);
            return Claim.of(ClaimResult.FAILED);
        }
    }

    /**
     * Dawn decides whether this player may claim here. Returns the reason to refuse, or null to go ahead.
     * The reward and its streak stay local; the daily receipt on Dawn only matters for the first claim.
     */
    private @Nullable ClaimResult registerWithDawn(UUID playerId) {
        RewardClaimResponse response;
        try {
            response = client.claimReward(playerId).join();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Dawn could not verify the reward claim of " + playerId + ": " + e.getMessage());
            return ClaimResult.FAILED;
        }

        if (response.eligible() || response.isAccountingDisabled()) {
            return null;
        }
        if (response.isNotOnDawn()) {
            return ClaimResult.NOT_ON_DAWN;
        }
        if (response.isIdentityAlreadyUsed()) {
            return ClaimResult.DEVICE_ALREADY_USED;
        }

        // not_on_server until the next heartbeat lists the player, or the credential was rotated.
        return ClaimResult.FAILED;
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
        giveRewards(player, day, () -> {
        });
    }

    /**
     * Runs the reward commands, then {@code afterwards} on the player's thread. On Paper all of it happens right away.
     */
    private void giveRewards(Player player, int day, Runnable afterwards) {
        var reward = plugin.getRewards().getDay(day);

        if (reward == null) {
            plugin.getLogger().warning("No reward is configured for day " + day);
            afterwards.run();
            return;
        }

        var commands = reward.getCommands().stream()
                .map(command -> command.replace("{player}", player.getName()))
                .toList();

        // Folia only runs console commands on the global region.
        Schedulers.global(plugin, () -> {
            var console = plugin.getServer().getConsoleSender();
            try {
                for (var command : commands) {
                    // One broken command must not cost the player the rest of the reward.
                    try {
                        plugin.getServer().dispatchCommand(console, command);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.SEVERE, "Reward command failed for " + player.getName() + ": " + command, e);
                    }
                }
            } finally {
                Schedulers.player(plugin, player, afterwards);
            }
        });
    }

    /**
     * The outcome of a claim, with the day it granted or, on cooldown, how long until the next one. Both come from the
     * database row, so the message never depends on the cache, which is missing when the login load failed.
     */
    private record Claim(ClaimResult result, int day, Duration cooldown) {

        private static Claim of(ClaimResult result) {
            return new Claim(result, 0, Duration.ZERO);
        }

        private static Claim onCooldown(Duration cooldown) {
            return new Claim(ClaimResult.ON_COOLDOWN, 0, cooldown);
        }
    }
}
