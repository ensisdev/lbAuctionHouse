package dev.ensisdev.lbauctionhouse.core.data;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * MySQL veritabanı adaptörü — HikariCP bağlantı havuzu kullanır.
 * <p>
 * Bağlantı bilgileri config.yml üzerinden okunur:
 * <pre>
 * storage:
 *   type: mysql
 *   mysql:
 *     host: localhost
 *     port: 3306
 *     database: lbsmpcore
 *     username: root
 *     password: ""
 *     pool-size: 10
 * </pre>
 */
public class MySQLAdapter implements StorageAdapter {

    private final LbAuctionHouse plugin;
    private final Logger logger;

    private HikariDataSource dataSource;
    private boolean connected;

    public MySQLAdapter(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public void connect() throws SQLException {
        try {
            var mysql = plugin.getConfig().getConfigurationSection("storage.mysql");
            if (mysql == null) {
                throw new SQLException("config.yml'de 'storage.mysql' bölümü bulunamadı.");
            }

            String host = mysql.getString("host", "localhost");
            int port = mysql.getInt("port", 3306);
            String database = mysql.getString("database", "lbsmpcore");
            String username = mysql.getString("username", "root");
            String password = mysql.getString("password", "");
            int poolSize = mysql.getInt("pool-size", 10);

            // JDBC URL with SSL and timezone config
            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setPoolName("lbSmpCore-MySQL");
            config.setMaximumPoolSize(poolSize);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(5000);
            config.setIdleTimeout(30000);
            config.setMaxLifetime(600000);

            // MySQL önerilen ayarlar
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            config.setInitializationFailTimeout(10000);

            this.dataSource = new HikariDataSource(config);
            this.connected = true;

            try (Connection conn = dataSource.getConnection()) {
                logger.info("MySQL bağlantısı başarılı: " + host + ":" + port + "/" + database);
            }

            ensureMigrationTable();

        } catch (Exception e) {
            this.connected = false;
            throw new SQLException("MySQL bağlantısı kurulamadı: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            this.connected = false;
            logger.info("MySQL bağlantısı kapatıldı.");
        }
    }

    @Override
    public boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("MySQL bağlantısı kapalı.");
        }
        return dataSource.getConnection();
    }
}
