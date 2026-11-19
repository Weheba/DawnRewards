package net.inpvp.dawnrewards.grants;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Limits on what a store command may pass, matching the storage columns and the Dawn API.
 */
@NullMarked
public final class GrantInput {

    public static final int MAX_PLAYER_NAME_LENGTH = 32;
    public static final int MAX_COSMETIC_LENGTH = 128;
    public static final int MAX_DELIVERY_ID_BYTES = 96;

    private GrantInput() {
    }

    /**
     * Returns why the input is refused, or null when it is fine.
     */
    public static @Nullable String validate(String playerName, String cosmetic, String deliveryId) {
        if (playerName.isBlank() || playerName.length() > MAX_PLAYER_NAME_LENGTH) {
            return "Player name must be 1 to " + MAX_PLAYER_NAME_LENGTH + " characters.";
        }
        if (cosmetic.isBlank() || cosmetic.length() > MAX_COSMETIC_LENGTH) {
            return "Cosmetic must be 1 to " + MAX_COSMETIC_LENGTH + " characters.";
        }
        if (deliveryId.getBytes(StandardCharsets.UTF_8).length > MAX_DELIVERY_ID_BYTES) {
            return "Delivery id must be at most " + MAX_DELIVERY_ID_BYTES + " bytes.";
        }
        return null;
    }
}
