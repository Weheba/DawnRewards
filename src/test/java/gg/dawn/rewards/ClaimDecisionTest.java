package gg.dawn.rewards;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ClaimDecisionTest {
    @Test void onlyExplicitNewEligibleGrantCanDispatch() {
        assertTrue(ClaimDecision.isNewGrant(JsonParser.parseString("{\"eligible\":true,\"granted\":true,\"alreadyClaimed\":false,\"claimId\":\"claim-1\"}").getAsJsonObject()));
        for (String json : new String[]{"{}", "{\"eligible\":true}",
                "{\"eligible\":true,\"granted\":true,\"claimId\":\"claim-1\"}",
                "{\"eligible\":true,\"granted\":true,\"alreadyClaimed\":\"false\",\"claimId\":\"claim-1\"}",
                "{\"eligible\":true,\"granted\":true,\"alreadyClaimed\":true,\"claimId\":\"claim-1\"}",
                "{\"eligible\":false,\"granted\":true,\"claimId\":\"claim-1\"}",
                "{\"eligible\":\"true\",\"granted\":true,\"claimId\":\"claim-1\"}",
                "{\"eligible\":true,\"granted\":true,\"claimId\":\"invalid receipt\"}"}) {
            assertFalse(ClaimDecision.isNewGrant(JsonParser.parseString(json).getAsJsonObject()), json);
        }
    }
}
