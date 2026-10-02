package net.inpvp.dawnrewards.storage;

import net.inpvp.dawnrewards.config.StorageType;
import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.SQLException;

@NullMarked
public enum SqlDialect {
    SQLITE("sqlite"),
    MARIADB("mariadb");

    private static final String LOCK_NAME = "dawnrewards_migrations";
    private static final int LOCK_TIMEOUT_SECONDS = 60;

    private final String directory;

    SqlDialect(String directory) {
        this.directory = directory;
    }

    public static SqlDialect of(StorageType type) {
        return type == StorageType.MARIADB ? MARIADB : SQLITE;
    }

    public String migrationResource(int version) {
        return "storage/" + directory + "/migrations/dawn_migration_" + version + ".sql";
    }

    public String queryResource(String name) {
        return "storage/" + directory + "/queries/" + name + ".sql";
    }

    public void acquireLock(Connection connection) throws SQLException {
        if (this != MARIADB) {
            return;
        }

        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, LOCK_NAME);
            statement.setInt(2, LOCK_TIMEOUT_SECONDS);

            try (var results = statement.executeQuery()) {
                if (!results.next() || results.getInt(1) != 1) {
                    throw new SQLException("Timed out waiting for another server to finish migrating");
                }
            }
        }
    }

    public void releaseLock(Connection connection) throws SQLException {
        if (this != MARIADB) {
            return;
        }

        try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, LOCK_NAME);
            statement.execute();
        }
    }
}
