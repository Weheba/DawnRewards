package net.inpvp.dawnrewards.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Getter
@ToString
@AllArgsConstructor
@NullMarked
public class DawnUser {

    private final UUID playerId;

    private int currentDay;
    private int currentStreak;
    private int highestStreak;
    private @Nullable Instant lastClaimAt;

    public void reset() {
        this.currentDay = 0;
        this.currentStreak = 0;
        this.lastClaimAt = null;
    }

    public void apply(ClaimUpdate update) {
        this.currentDay = update.currentDay();
        this.currentStreak = update.currentStreak();
        this.highestStreak = update.highestStreak();
        this.lastClaimAt = update.claimedAt();
    }
}
