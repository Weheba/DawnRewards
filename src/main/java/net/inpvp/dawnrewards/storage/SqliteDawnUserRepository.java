package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.user.ClaimUpdate;
import net.inpvp.dawnrewards.user.DawnUser;
import org.jspecify.annotations.NullMarked;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@NullMarked
public class SqliteDawnUserRepository implements DawnUserRepository {

    private static final String INSERT_USER = SqlResources.load(SqlDialect.SQLITE.queryResource("insert_user"));
    private static final String SELECT_USER = SqlResources.load(SqlDialect.SQLITE.queryResource("select_user"));
    private static final String CLAIM_USER = SqlResources.load(SqlDialect.SQLITE.queryResource("claim_user"));
    private static final String RESET_USER = SqlResources.load(SqlDialect.SQLITE.queryResource("reset_user"));

    private final DataSource dataSource;

    public SqliteDawnUserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DawnUser load(UUID playerId) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(INSERT_USER)) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }

            try (var statement = connection.prepareStatement(SELECT_USER)) {
                statement.setString(1, playerId.toString());

                try (var results = statement.executeQuery()) {
                    if (!results.next()) {
                        throw new SQLException("No dawnrewards_players row for " + playerId + " after insert");
                    }
                    return DawnUserMapper.map(playerId, results);
                }
            }
        }
    }

    @Override
    public void reset(UUID playerId) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(RESET_USER)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean applyClaim(UUID playerId, ClaimUpdate update, Instant claimableBefore) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(CLAIM_USER)) {
            DawnUserMapper.bindClaim(statement, playerId, update, claimableBefore);

            return statement.executeUpdate() == 1;
        }
    }
}
