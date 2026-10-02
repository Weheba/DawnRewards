package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.user.ClaimUpdate;
import net.inpvp.dawnrewards.user.DawnUser;
import org.jspecify.annotations.NullMarked;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@NullMarked
final class DawnUserMapper {

    private DawnUserMapper() {
    }

    static DawnUser map(UUID playerId, ResultSet results) throws SQLException {
        var claimedAt = results.getLong("last_claim_at");
        var lastClaimAt = results.wasNull() ? null : Instant.ofEpochMilli(claimedAt);

        return new DawnUser(
                playerId,
                results.getInt("current_day"),
                results.getInt("current_streak"),
                results.getInt("highest_streak"),
                lastClaimAt);
    }

    static void bindClaim(PreparedStatement statement, UUID playerId, ClaimUpdate update, Instant claimableBefore) throws SQLException {
        statement.setInt(1, update.currentDay());
        statement.setInt(2, update.currentStreak());
        statement.setInt(3, update.highestStreak());
        statement.setLong(4, update.claimedAt().toEpochMilli());
        statement.setString(5, playerId.toString());
        statement.setLong(6, claimableBefore.toEpochMilli());
    }
}
