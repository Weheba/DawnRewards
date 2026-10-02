package net.inpvp.dawnrewards.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@Configuration
public class Config {

    @Comment("Plugin token from the Dawn account website")
    private String pluginToken = "";

    private StorageConfig storage = new StorageConfig();

    @Comment("Tells Dawn players in chat when their reward is ready to claim")
    private NotificationConfig notifications = new NotificationConfig();

    @Getter
    @ToString
    @NoArgsConstructor
    @Configuration
    public static class StorageConfig {
        @Comment("Options: SQLITE, MARIADB")
        private StorageType type = StorageType.SQLITE;

        private SqliteConfig sqlite = new SqliteConfig();
        private MariadbConfig mariadb = new MariadbConfig();
    }

    @Getter
    @ToString
    @NoArgsConstructor
    @Configuration
    public static class SqliteConfig {
        private String file = "dawnrewards.db";
    }

    @Getter
    @ToString
    @NoArgsConstructor
    @Configuration
    public static class MariadbConfig {
        private String host = "127.0.0.1";
        private int port = 3306;
        private String database = "dawnrewards";
        private String username = "root";
        private String password = "";
        private int maximumPoolSize = 10;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    @Configuration
    public static class NotificationConfig {
        private boolean notifyOnJoin = true;

        @Comment("Seconds between reminders, 0 or lower disables them")
        private long notifyRepeatInterval = 1800;
    }
}
