package net.inpvp.dawnrewards.dawn.api;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record RewardClaimResponse(boolean eligible, boolean granted, boolean alreadyClaimed, String reason) {

    /** Dawn accounting is switched off, so claims are not being recorded at all. */
    public boolean isAccountingDisabled() {
        return "accounting_disabled".equals(reason);
    }

    public boolean isNotOnDawn() {
        return "not_on_dawn".equals(reason);
    }

    /** Another Dawn account already claimed on this server from the same device or IP. */
    public boolean isIdentityAlreadyUsed() {
        return "identity_already_used".equals(reason);
    }
}
