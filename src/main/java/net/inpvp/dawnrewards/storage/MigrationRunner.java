package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.DawnRewards;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@NullMarked
public class MigrationRunner {

    private static final String MIGRATIONS_TABLE = "dawnrewards_migrations";

    private final DawnRewards plugin;
    private final DataSource dataSource;
    private final SqlDialect dialect;

    public MigrationRunner(DawnRewards plugin, DataSource dataSource, SqlDialect dialect) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public void migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            dialect.acquireLock(connection);
            try {
                applyPending(connection);
            } finally {
                dialect.releaseLock(connection);
            }
        }
    }

    private void applyPending(Connection connection) throws SQLException {
        var applied = readApplied(connection);

        for (int version = 1; ; version++) {
            var sql = readMigration(version);
            if (sql == null) {
                if (version <= applied.size()) {
                    throw new SQLException("Database is at migration " + applied.size()
                            + " but this build only ships " + (version - 1) + ", downgrade is not supported");
                }
                return;
            }

            var checksum = checksum(sql);
            var recorded = applied.get(version);

            if (recorded != null) {
                if (!recorded.equals(checksum)) {
                    throw new SQLException("Migration " + version + " changed after it was applied, expected checksum "
                            + recorded + " but found " + checksum);
                }
                continue;
            }

            apply(connection, version, sql, checksum);
        }
    }

    private Map<Integer, String> readApplied(Connection connection) throws SQLException {
        var applied = new HashMap<Integer, String>();

        if (!migrationsTableExists(connection)) {
            return applied;
        }

        try (var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT version, checksum FROM dawnrewards_migrations")) {
            while (results.next()) {
                applied.put(results.getInt("version"), results.getString("checksum"));
            }
        }
        return applied;
    }

    private void apply(Connection connection, int version, String sql, String checksum) throws SQLException {
        plugin.getLogger().info("Applying database migration " + version);

        var autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        try {
            try (var statement = connection.createStatement()) {
                for (var part : split(sql)) {
                    statement.execute(part);
                }
            }

            try (var statement = connection.prepareStatement(
                    "INSERT INTO dawnrewards_migrations (version, checksum, applied_at) VALUES (?, ?, ?)")) {
                statement.setInt(1, version);
                statement.setString(2, checksum);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Migration " + version + " failed and was rolled back", e);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private boolean migrationsTableExists(Connection connection) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, null, MIGRATIONS_TABLE, null)) {
            return tables.next();
        }
    }

    private @Nullable String readMigration(int version) {
        return SqlResources.find(dialect.migrationResource(version));
    }

    private static List<String> split(String sql) {
        var statements = new ArrayList<String>();

        for (var part : sql.split(";")) {
            var trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }

    private static String checksum(String sql) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sql.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not checksum the migration", e);
        }
    }
}
