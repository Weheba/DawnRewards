package net.inpvp.dawnrewards.dawn.api;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ClientPresence(
        boolean onDawn,
        String verificationLevel,
        boolean rewardEligible,
        int maxStalenessSeconds
) {
}
