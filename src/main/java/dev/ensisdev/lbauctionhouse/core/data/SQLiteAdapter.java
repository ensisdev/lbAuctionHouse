package dev.ensisdev.lbauctionhouse.core.data;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * SQLite veritabanı adaptörü — HikariCP bağlantı havuzu kullanır.
 * <p>
 * Veritabanı dosyası: {@code <plugin-klasörü>/data/database.db}
 * <p>
 * HikariCP sayesinde her sorgu için yeni bağlantı açılmaz,
 * hazır havuzdan alınır ve iade edilir.
 */
public class SQLiteAdapter implements StorageAdapter {

    private final LbAuctionHouse plugin;
    private final Logger logger;
    private final File databaseFile;

    private HikariDataSource dataSource;
    private boolean connected;

    public SQLiteAdapter(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.databaseFile = new File(plugin.getDataFolder(), "data" + File.separator + "database.db");
    }

    @Override
    public void connect() throws SQLException {
        // Veritabanı klasörünü oluştur
        File dataDir = databaseFile.getParentFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            config.setPoolName("lbAuctionHouse-SQLite");
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(5000);
            config.setIdleTimeout(30000);
            config.setMaxLifetime(60000);

            // SQLite özel ayarlar
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("busy_timeout", "3000");
            config.addDataSourceProperty("foreign_keys", "ON");

            // HikariCP'nin kendi logger'ını sustur (SLF4J yoksa)
            config.setInitializationFailTimeout(10000);

            this.dataSource = new HikariDataSource(config);
            this.connected = true;

            // Bağlantıyı test et
            try (Connection conn = dataSource.getConnection()) {
                logger.info("SQLite bağlantısı başarılı: " + databaseFile.getAbsolutePath());
            }

            // Migration tablosunu oluştur
            ensureMigrationTable();

        } catch (Exception e) {
            this.connected = false;
            throw new SQLException("SQLite bağlantısı kurulamadı: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            this.connected = false;
            logger.info("SQLite bağlantısı kapatıldı.");
        }
    }

    @Override
    public boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("SQLite bağlantısı kapalı.");
        }
        return dataSource.getConnection();
    }
}
