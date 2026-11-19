package net.inpvp.dawnrewards.grants;

public enum FailureKind {
    /** Rate limits, server errors, timeouts, network trouble: retry with backoff. */
    TRANSIENT,
    /** No Dawn account holds the name yet. The buyer may still sign up, so retry slowly. */
    NO_ACCOUNT,
    /** Bad input, cosmetic not grantable or bad token: retrying cannot help. */
    PERMANENT
}
