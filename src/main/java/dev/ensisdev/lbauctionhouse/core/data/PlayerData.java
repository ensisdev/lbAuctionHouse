package dev.ensisdev.lbauctionhouse.core.data;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Oyuncu verisi modeli — UUID bazlı temel oyuncu bilgilerini yönetir.
 * <p>
 * Veritabanında {@code players} tablosunda saklanır.
 * Addon'lar kendi sütunlarını eklemek için migration kullanabilir.
 * <p>


 */
public class PlayerData {

    private static final String TABLE = "players";

    private final DataManager dataManager;
    private final Logger logger;

    public PlayerData(DataManager dataManager, Logger logger) {
        this.dataManager = dataManager;
        this.logger = logger;
    }

    private boolean isMySQL() {
        return dataManager.getAdapter() instanceof MySQLAdapter;
    }

    /**
     * Oyuncu tablosunu oluşturur (eğer yoksa).
     * Sütun tanımları veritabanı türüne göre seçilir.
     */
    public void initTable() {
        String columns = isMySQL()
                ? "uuid VARCHAR(36) PRIMARY KEY, " +
                  "last_name VARCHAR(255) NOT NULL, " +
                  "first_joined DATETIME NOT NULL DEFAULT NOW(), " +
                  "last_seen DATETIME NOT NULL DEFAULT NOW(), " +
                  "playtime INT NOT NULL DEFAULT 0"
                : "uuid TEXT PRIMARY KEY, " +
                  "last_name TEXT NOT NULL, " +
                  "first_joined TEXT NOT NULL DEFAULT (datetime('now')), " +
                  "last_seen TEXT NOT NULL DEFAULT (datetime('now')), " +
                  "playtime INTEGER NOT NULL DEFAULT 0";
        try {
            dataManager.getAdapter().createTableIfNotExists(TABLE, columns);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "players tablosu oluşturulamadı", e);
        }
    }

    /**
     * Oyuncuyu veritabanına kaydeder (yoksa ekle, varsa güncelle).
     */
    public void savePlayer(UUID uuid, String name) {
        try {
            if (isMySQL()) {
                dataManager.getAdapter().execute(
                    "INSERT INTO " + TABLE + " (uuid, last_name, last_seen) VALUES (?, ?, NOW()) " +
                    "ON DUPLICATE KEY UPDATE last_name = VALUES(last_name), last_seen = NOW()",
                    uuid.toString(), name
                );
            } else {
                dataManager.getAdapter().execute(
                    "INSERT INTO " + TABLE + " (uuid, last_name, last_seen) VALUES (?, ?, datetime('now')) " +
                    "ON CONFLICT(uuid) DO UPDATE SET last_name = ?, last_seen = datetime('now')",
                    uuid.toString(), name, name
                );
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu kaydedilemedi: " + uuid, e);
        }
    }

    /**
     * Oyuncunun son görülme zamanını günceller.
     */
    public void updateLastSeen(UUID uuid) {
        try {
            String nowExpr = isMySQL() ? "NOW()" : "datetime('now')";
            dataManager.getAdapter().execute(
                "UPDATE " + TABLE + " SET last_seen = " + nowExpr + " WHERE uuid = ?",
                uuid.toString()
            );
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Son görülme güncellenemedi: " + uuid, e);
        }
    }

    /**
     * Oynama süresini artırır (dakika).
     */
    public void addPlaytime(UUID uuid, int minutes) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE " + TABLE + " SET playtime = playtime + ? WHERE uuid = ?",
                minutes, uuid.toString()
            );
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyun süresi güncellenemedi: " + uuid, e);
        }
    }

    /**
     * Oyuncunun verilerini döndürür (ham map).
     * @return sütun adı → değer, yoksa null
     */
    public Map<String, Object> getPlayerData(UUID uuid) {
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT * FROM " + TABLE + " WHERE uuid = ?", uuid.toString()
            );
            return results.isEmpty() ? null : results.get(0);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu verisi alınamadı: " + uuid, e);
            return null;
        }
    }

    /**
     * Veritabanındaki toplam oyuncu sayısı.
     */
    public int getPlayerCount() {
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS count FROM " + TABLE
            );
            if (!results.isEmpty()) {
                return ((Number) results.get(0).get("count")).intValue();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu sayısı alınamadı", e);
        }
        return 0;
    }

    /**
     * Tablodaki tüm UUID'leri döndürür.
     */
    public java.util.List<String> getAllUUIDs() {
        try {
            var results = dataManager.getAdapter().queryList("SELECT uuid FROM " + TABLE);
            return results.stream()
                    .map(row -> (String) row.get("uuid"))
                    .toList();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "UUID listesi alınamadı", e);
            return java.util.Collections.emptyList();
        }
    }
}
