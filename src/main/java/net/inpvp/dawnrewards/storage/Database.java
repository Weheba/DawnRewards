package net.inpvp.dawnrewards.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.config.Config;
import net.inpvp.dawnrewards.config.StorageType;
import org.jspecify.annotations.NullMarked;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

@NullMarked
public class Database {

    private static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_$]+");

    private final DawnRewards plugin;

    @Getter
    private final SqlDialect dialect;

    @Getter
    private final HikariDataSource dataSource;

    public Database(DawnRewards plugin) {
        this.plugin = plugin;

        var storage = plugin.getCfg().getStorage();
        this.dialect = SqlDialect.of(storage.getType());

        if (storage.getType() == StorageType.MARIADB) {
            createDatabaseIfMissing(storage.getMariadb());
        }

        this.dataSource = new HikariDataSource(buildConfig(storage));
    }

    public DawnUserRepository createUserRepository() {
        return switch (dialect) {
            case SQLITE -> new SqliteDawnUserRepository(dataSource);
            case MARIADB -> new MariadbDawnUserRepository(dataSource);
        };
    }

    public void migrate() throws SQLException {
        new MigrationRunner(plugin, dataSource, dialect).migrate();
    }

    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void createDatabaseIfMissing(Config.MariadbConfig mariadb) {
        var database = mariadb.getDatabase();

        if (!DATABASE_NAME.matcher(database).matches()) {
            throw new IllegalArgumentException("Database name " + database + " may only contain letters, digits, underscores and dollar signs");
        }

        var url = "jdbc:mariadb://%s:%d/".formatted(mariadb.getHost(), mariadb.getPort());

        try {
            Class.forName(MARIADB_DRIVER);

            try (var connection = DriverManager.getConnection(url, mariadb.getUsername(), mariadb.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database + "`");
            }
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().warning("Could not create the database " + database + ", assuming it already exists: " + e.getMessage());
        }
    }

    private HikariConfig buildConfig(Config.StorageConfig storage) {
        var config = new HikariConfig();
        config.setPoolName("DawnRewards");

        switch (storage.getType()) {
            case MARIADB -> {
                var mariadb = storage.getMariadb();

                config.setDriverClassName(MARIADB_DRIVER);
                config.setJdbcUrl("jdbc:mariadb://%s:%d/%s".formatted(mariadb.getHost(), mariadb.getPort(), mariadb.getDatabase()));
                config.setUsername(mariadb.getUsername());
                config.setPassword(mariadb.getPassword());
                config.setMaximumPoolSize(mariadb.getMaximumPoolSize());
            }
            case SQLITE -> {
                var file = plugin.getDataFolder().toPath().resolve(storage.getSqlite().getFile());

                config.setDriverClassName(SQLITE_DRIVER);
                config.setJdbcUrl("jdbc:sqlite:" + file);
                config.setMaximumPoolSize(1);
                config.addDataSourceProperty("foreign_keys", "true");
            }
        }

        return config;
    }
}
