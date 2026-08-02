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
    private static final String COLUMNS =
            "uuid TEXT PRIMARY KEY, " +
            "last_name TEXT NOT NULL, " +
            "first_joined TEXT NOT NULL DEFAULT (datetime('now')), " +
            "last_seen TEXT NOT NULL DEFAULT (datetime('now')), " +
            "playtime INTEGER NOT NULL DEFAULT 0";

    private final DataManager dataManager;
    private final Logger logger;

    public PlayerData(DataManager dataManager, Logger logger) {
        this.dataManager = dataManager;
        this.logger = logger;
    }

    /**
     * Oyuncu tablosunu oluşturur (eğer yoksa).
     */
    public void initTable() {
        try {
            dataManager.getAdapter().createTableIfNotExists(TABLE, COLUMNS);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "players tablosu oluşturulamadı", e);
        }
    }

    /**
     * Oyuncuyu veritabanına kaydeder (yoksa ekle, varsa güncelle).
     */
    public void savePlayer(UUID uuid, String name) {
        try {
            dataManager.getAdapter().execute(
                "INSERT INTO " + TABLE + " (uuid, last_name, last_seen) VALUES (?, ?, datetime('now')) " +
                "ON CONFLICT(uuid) DO UPDATE SET last_name = ?, last_seen = datetime('now')",
                uuid.toString(), name, name
            );
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu kaydedilemedi: " + uuid, e);
        }
    }

    /**
     * Oyuncunun son görülme zamanını günceller.
     */
    public void updateLastSeen(UUID uuid) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE " + TABLE + " SET last_seen = datetime('now') WHERE uuid = ?",
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
