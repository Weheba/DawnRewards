package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.grants.CosmeticGrant;
import net.inpvp.dawnrewards.grants.EnqueueResult;
import net.inpvp.dawnrewards.grants.GrantStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@NullMarked
public class SqlGrantRepository implements GrantRepository {

    private final DataSource dataSource;

    private final String insertGrant;
    private final String selectGrantByKey;
    private final String selectGrant;
    private final String selectDueGrants;
    private final String selectOpenGrants;
    private final String countGrants;
    private final String claimGrant;
    private final String finishGrant;
    private final String reopenGrant;
    private final String requeuePendingGrants;
    private final String reopenFailedGrants;
    private final String pruneGrants;

    public SqlGrantRepository(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;

        this.insertGrant = SqlResources.load(dialect.queryResource("insert_grant"));
        this.selectGrantByKey = SqlResources.load(dialect.queryResource("select_grant_by_key"));
        this.selectGrant = SqlResources.load(dialect.queryResource("select_grant"));
        this.selectDueGrants = SqlResources.load(dialect.queryResource("select_due_grants"));
        this.selectOpenGrants = SqlResources.load(dialect.queryResource("select_open_grants"));
        this.countGrants = SqlResources.load(dialect.queryResource("count_grants"));
        this.claimGrant = SqlResources.load(dialect.queryResource("claim_grant"));
        this.finishGrant = SqlResources.load(dialect.queryResource("finish_grant"));
        this.reopenGrant = SqlResources.load(dialect.queryResource("reopen_grant"));
        this.requeuePendingGrants = SqlResources.load(dialect.queryResource("requeue_pending_grants"));
        this.reopenFailedGrants = SqlResources.load(dialect.queryResource("reopen_failed_grants"));
        this.pruneGrants = SqlResources.load(dialect.queryResource("prune_grants"));
    }

    /**
     * Minecraft names are case-insensitive, so "Notch" and "notch" are one buyer.
     */
    public static String playerKey(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    @Override
    public EnqueueResult enqueue(String playerName, String cosmetic, String deliveryId, Instant now, Instant expiresAt) throws SQLException {
        var key = playerKey(playerName);

        try (var connection = dataSource.getConnection()) {
            int inserted;
            try (var statement = connection.prepareStatement(insertGrant)) {
                statement.setString(1, key);
                statement.setString(2, playerName);
                statement.setString(3, cosmetic);
                statement.setString(4, deliveryId);
                statement.setLong(5, now.toEpochMilli());
                statement.setLong(6, expiresAt.toEpochMilli());
                statement.setLong(7, now.toEpochMilli());
                statement.setLong(8, now.toEpochMilli());
                inserted = statement.executeUpdate();
            }

            var existing = selectByKey(connection, key, cosmetic, deliveryId);
            if (inserted == 1) {
                return new EnqueueResult(existing, EnqueueResult.Outcome.QUEUED);
            }

            return switch (existing.status()) {
                case PENDING -> new EnqueueResult(existing, EnqueueResult.Outcome.ALREADY_QUEUED);
                case GRANTED -> new EnqueueResult(existing, EnqueueResult.Outcome.ALREADY_GRANTED);
                case FAILED, EXPIRED -> {
                    // Running the command again after fixing the cause is how an admin asks for another try.
                    try (var statement = connection.prepareStatement(reopenGrant)) {
                        statement.setLong(1, expiresAt.toEpochMilli());
                        statement.setLong(2, now.toEpochMilli());
                        statement.setLong(3, now.toEpochMilli());
                        statement.setLong(4, existing.id());
                        statement.executeUpdate();
                    }
                    yield new EnqueueResult(selectByKey(connection, key, cosmetic, deliveryId), EnqueueResult.Outcome.REQUEUED);
                }
            };
        }
    }

    @Override
    public Optional<CosmeticGrant> find(long id) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(selectGrant)) {
            statement.setLong(1, id);
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<CosmeticGrant> findDue(Instant now, int limit) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(selectDueGrants)) {
            statement.setLong(1, now.toEpochMilli());
            statement.setInt(2, limit);
            return mapAll(statement.executeQuery());
        }
    }

    @Override
    public boolean claim(long id, Instant expectedNextAttempt, Instant leaseUntil) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(claimGrant)) {
            statement.setLong(1, leaseUntil.toEpochMilli());
            statement.setLong(2, id);
            statement.setLong(3, expectedNextAttempt.toEpochMilli());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void finish(long id, GrantStatus status, int attempts, Instant nextAttemptAt, @Nullable String error, Instant now) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(finishGrant)) {
            statement.setString(1, status.name());
            statement.setInt(2, attempts);
            statement.setLong(3, nextAttemptAt.toEpochMilli());
            statement.setLong(4, now.toEpochMilli());
            if (error == null) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, error);
            }
            statement.setLong(6, id);
            statement.executeUpdate();
        }
    }

    @Override
    public List<CosmeticGrant> findOpen(int limit) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(selectOpenGrants)) {
            statement.setInt(1, limit);
            return mapAll(statement.executeQuery());
        }
    }

    @Override
    public Map<GrantStatus, Integer> countByStatus() throws SQLException {
        var counts = new EnumMap<GrantStatus, Integer>(GrantStatus.class);
        for (var status : GrantStatus.values()) {
            counts.put(status, 0);
        }

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(countGrants);
             var results = statement.executeQuery()) {
            while (results.next()) {
                var status = parseStatus(results.getString("status"));
                if (status != null) {
                    counts.put(status, results.getInt("total"));
                }
            }
        }
        return counts;
    }

    @Override
    public int requeuePending(Instant now) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(requeuePendingGrants)) {
            statement.setLong(1, now.toEpochMilli());
            statement.setLong(2, now.toEpochMilli());
            return statement.executeUpdate();
        }
    }

    @Override
    public int reopenFailed(Instant now, Instant expiresAt) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(reopenFailedGrants)) {
            statement.setLong(1, expiresAt.toEpochMilli());
            statement.setLong(2, now.toEpochMilli());
            statement.setLong(3, now.toEpochMilli());
            return statement.executeUpdate();
        }
    }

    @Override
    public int pruneGranted(Instant before) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(pruneGrants)) {
            statement.setLong(1, before.toEpochMilli());
            return statement.executeUpdate();
        }
    }

    private CosmeticGrant selectByKey(Connection connection, String key, String cosmetic, String deliveryId) throws SQLException {
        try (var statement = connection.prepareStatement(selectGrantByKey)) {
            statement.setString(1, key);
            statement.setString(2, cosmetic);
            statement.setString(3, deliveryId);
            try (var results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("No cosmetic grant row for " + key + "/" + cosmetic + " after insert");
                }
                return map(results);
            }
        }
    }

    private static List<CosmeticGrant> mapAll(ResultSet results) throws SQLException {
        try (results) {
            var grants = new ArrayList<CosmeticGrant>();
            while (results.next()) {
                grants.add(map(results));
            }
            return grants;
        }
    }

    private static CosmeticGrant map(ResultSet results) throws SQLException {
        var status = parseStatus(results.getString("status"));
        return new CosmeticGrant(
                results.getLong("id"),
                results.getString("player_name"),
                results.getString("cosmetic"),
                results.getString("delivery_id"),
                status == null ? GrantStatus.FAILED : status,
                results.getInt("attempts"),
                Instant.ofEpochMilli(results.getLong("created_at")),
                Instant.ofEpochMilli(results.getLong("expires_at")),
                Instant.ofEpochMilli(results.getLong("next_attempt_at")),
                Instant.ofEpochMilli(results.getLong("updated_at")),
                results.getString("last_error"));
    }

    private static @Nullable GrantStatus parseStatus(String value) {
        try {
            return GrantStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
