package net.inpvp.dawnrewards.grants;

public enum GrantStatus {
    /** Waiting for its next attempt. */
    PENDING,
    /** Dawn holds the cosmetic on the buyer's account. */
    GRANTED,
    /** Dawn refused it for good (bad input, cosmetic not grantable, bad token). */
    FAILED,
    /** Still failing after the retry window, given up. */
    EXPIRED
}
