package dev.ensisdev.lbauctionhouse.core.data;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.core.config.ConfigManager;
import dev.ensisdev.lbauctionhouse.core.event.DataLoadEvent;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Veri yönetim katmanı — tüm veritabanı işlemlerinin merkezi.
 * <p>
 * Config'de {@code storage.type: sqlite} (ve ileride mysql) ile adaptör seçilir.
 * Başlangıçta SQLite kullanılır, HikariCP bağlantı havuzu ile.
 * <p>
 * Migration'lar {@code runMigrations()} üzerinden sırayla çalıştırılır.
 * Her migration bir versiyon numarasına sahiptir ve sadece bir kez çalışır.
 */
public class DataManager {

    private final LbAuctionHouse plugin;
    private final Logger logger;

    private StorageAdapter adapter;
    private AsyncStorageWrapper asyncWrapper;
    private PlayerData playerData;
    private boolean ready;

    public DataManager(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Veritabanı bağlantısını başlatır.
     * Config'deki {@code storage.type} değerine göre adaptör seçer.
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("storage.type", "sqlite");

        logger.info("[DataManager] Veritabanı başlatılıyor — tip: " + type);

        switch (type.toLowerCase()) {
            case "sqlite" -> this.adapter = new SQLiteAdapter(plugin);
            case "mysql" -> this.adapter = new MySQLAdapter(plugin);
            default -> {
                logger.warning("Bilinmeyen storage tipi: " + type + " — SQLite kullanılıyor.");
                this.adapter = new SQLiteAdapter(plugin);
            }
        }

        try {
            adapter.connect();
            this.ready = true;

            // Migration'ları çalıştır
            runMigrations();

            // PlayerData tablosunu oluştur
            this.playerData = new PlayerData(this, logger);
            playerData.initTable();

            // Eğer config'de debug varsa, oyuncu tablosunu doğrula
            if (isDebug()) {
                int playerCount = playerData.getPlayerCount();
                logger.info("[DataManager] Oyuncu tablosu hazır (" + playerCount + " kayıtlı oyuncu).");
            }

            this.asyncWrapper = new AsyncStorageWrapper(adapter);
            logger.info("[DataManager] Async DB wrapper hazır.");
            Bukkit.getPluginManager().callEvent(new DataLoadEvent(this, true));

        } catch (SQLException e) {
            this.ready = false;
            logger.log(Level.SEVERE, "[DataManager] Veritabanı başlatılamadı!", e);
            Bukkit.getPluginManager().callEvent(new DataLoadEvent(this, false));
        }
    }

    /**
     * Bağlantıyı kapatır.
     */
    public void shutdown() {
        if (asyncWrapper != null) {
            asyncWrapper.shutdown();
        }
        if (adapter != null) {
            adapter.disconnect();
        }
        this.ready = false;
        logger.info("[DataManager] Veritabanı bağlantısı kapatıldı.");
    }

    /**
     * Veritabanı kullanıma hazır mı?
     */
    public boolean isReady() {
        return ready && adapter != null && adapter.isConnected();
    }

    /**
     * Kullanılan storage adaptörü.
     */
    public StorageAdapter getAdapter() {
        return adapter;
    }

    /**
     * Async sorgular için wrapper — main thread'i bloklamaz.
     */
    public AsyncStorageWrapper async() {
        return asyncWrapper;
    }

    /**
     * Oyuncu verisi yöneticisi.
     */
    public PlayerData getPlayerData() {
        return playerData;
    }

    // ---- Migration Sistemi ----

    /**
     * Migration'ları sırayla çalıştırır.
     * Her migration sadece bir kez uygulanır.
     */
    public void runMigrations() {
        try {
            adapter.ensureMigrationTable();

            int currentVersion = getCurrentVersion();
            List<Migration> pending = getPendingMigrations(currentVersion);

            for (Migration migration : pending) {
                logger.info("[Migration] v" + migration.version + " uygulanıyor: " + migration.description);
                try {
                    migration.run();
                    adapter.execute("INSERT INTO _migrations (version, applied_at) VALUES (?, datetime('now'))",
                            migration.version);
                    logger.info("[Migration] v" + migration.version + " başarıyla uygulandı.");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[Migration] v" + migration.version + " başarısız!", e);
                    throw e; // Migration hatası kritik — başlatmayı durdur
                }
            }

            if (pending.isEmpty()) {
                logger.info("[Migration] Tüm migration'lar güncel (v" + currentVersion + ").");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Migration] Migration sistemi çalıştırılamadı!", e);
        }
    }

    private int getCurrentVersion() throws SQLException {
        var results = adapter.queryList("SELECT COALESCE(MAX(version), 0) AS v FROM _migrations");
        if (!results.isEmpty()) {
            return ((Number) results.get(0).get("v")).intValue();
        }
        return 0;
    }

    private List<Migration> getPendingMigrations(int currentVersion) {
        List<Migration> migrations = new ArrayList<>();

        // Her yeni eklenti buraya migration ekler
        // migrations.add(new Migration(1, "Initial schema: players + economy_log", () -> { ... }));

        return migrations.stream()
                .filter(m -> m.version > currentVersion)
                .sorted((a, b) -> Integer.compare(a.version, b.version))
                .toList();
    }

    // ---- Migration Kaydı ----

    /**
     * Bir veritabanı migration'ını temsil eder.
     * Her migration tek bir versiyon numarasına sahiptir ve bir kez çalışır.
     */
    public record Migration(int version, String description, Runnable task) {
        public void run() {
            task.run();
        }
    }

    // ---- Yardımcı ----

    private boolean isDebug() {
        return plugin.getConfig().getBoolean("settings.debug", false);
    }
}
