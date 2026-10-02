package net.inpvp.dawnrewards.user;

import org.jspecify.annotations.NullMarked;

import java.time.Instant;

@NullMarked
public record ClaimUpdate(int currentDay, int currentStreak, int highestStreak, Instant claimedAt) {
}
