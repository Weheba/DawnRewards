package net.inpvp.dawnrewards.dawn.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record PartnerGrantResponse(
        @Nullable String result,
        @JsonProperty("minecraft_username") @Nullable String minecraftUsername,
        @Nullable String cosmetic
) {

    public static final String GRANTED = "PARTNER_GRANT_RESULT_GRANTED";
    public static final String ALREADY_GRANTED = "PARTNER_GRANT_RESULT_ALREADY_GRANTED";

    public boolean isGranted() {
        return GRANTED.equals(result);
    }

    public boolean isAlreadyGranted() {
        return ALREADY_GRANTED.equals(result);
    }
}
