package net.inpvp.dawnrewards.claim;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum ClaimResult {
    GRANTED,
    NOT_ON_DAWN,
    ON_COOLDOWN,
    FAILED
}
