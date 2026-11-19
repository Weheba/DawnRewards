package net.inpvp.dawnrewards.grants;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One purchase to deliver. The delivery id is empty when the store passed none.
 */
@NullMarked
public record CosmeticGrant(
        long id,
        String playerName,
        String cosmetic,
        String deliveryId,
        GrantStatus status,
        int attempts,
        Instant createdAt,
        Instant expiresAt,
        Instant nextAttemptAt,
        Instant updatedAt,
        @Nullable String lastError
) {

    public @Nullable String deliveryIdOrNull() {
        return deliveryId.isEmpty() ? null : deliveryId;
    }

    public String describe() {
        return cosmetic + " for " + playerName + (deliveryId.isEmpty() ? "" : " (delivery " + deliveryId + ")");
    }
}
