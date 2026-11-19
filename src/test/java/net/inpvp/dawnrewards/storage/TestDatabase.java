package net.inpvp.dawnrewards.storage;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * A migrated SQLite file, the default storage of the plugin.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static SQLiteDataSource open(Path file) throws SQLException {
        var config = new SQLiteConfig();
        config.enforceForeignKeys(true);

        var dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + file);

        new MigrationRunner(Logger.getLogger("test"), dataSource, SqlDialect.SQLITE).migrate();
        return dataSource;
    }

    public static SqlGrantRepository grants(Path file) throws SQLException {
        return new SqlGrantRepository(open(file), SqlDialect.SQLITE);
    }
}
