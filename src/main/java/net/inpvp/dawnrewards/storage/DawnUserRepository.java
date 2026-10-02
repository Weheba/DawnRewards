package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.user.ClaimUpdate;
import net.inpvp.dawnrewards.user.DawnUser;
import org.jspecify.annotations.NullMarked;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@NullMarked
public interface DawnUserRepository {

    DawnUser load(UUID playerId) throws SQLException;

    void reset(UUID playerId) throws SQLException;

    boolean applyClaim(UUID playerId, ClaimUpdate update, Instant claimableBefore) throws SQLException;
}
