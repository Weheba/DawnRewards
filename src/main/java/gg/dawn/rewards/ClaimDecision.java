package gg.dawn.rewards;

import com.google.gson.JsonObject;

/** A response may authorize dispatch only when every required grant flag agrees. */
final class ClaimDecision {
    private ClaimDecision() {}
    static boolean isNewGrant(JsonObject response) {
        return flag(response, "eligible") && flag(response, "granted") && !flag(response, "alreadyClaimed")
                && response.has("claimId") && response.get("claimId").isJsonPrimitive()
                && response.get("claimId").getAsString().matches("[A-Za-z0-9_-]{1,128}");
    }
    static boolean flag(JsonObject response, String key) {
        return response.has(key) && response.get(key).isJsonPrimitive()
                && response.get(key).getAsJsonPrimitive().isBoolean() && response.get(key).getAsBoolean();
    }
}
