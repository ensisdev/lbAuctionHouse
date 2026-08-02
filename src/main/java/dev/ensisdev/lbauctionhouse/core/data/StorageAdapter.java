package dev.ensisdev.lbauctionhouse.core.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Veritabanı adaptörü interface — SQLite ve MySQL arasında soyutlama sağlar.
 * <p>
 * Her implementasyon bağlantı yönetimi, sorgu çalıştırma ve tablo oluşturma
 * işlemlerini kendi yöntemiyle gerçekleştirir.
 */
public interface StorageAdapter {

    /**
     * Veritabanına bağlanır.
     */
    void connect() throws SQLException;

    /**
     * Bağlantıyı kapatır.
     */
    void disconnect();

    /**
     * Bağlantı durumu.
     */
    boolean isConnected();

    /**
     * Bir SQL sorgusu çalıştırır (INSERT, UPDATE, DELETE, CREATE, vb.).
     *
     * @param sql SQL sorgusu (parametreler ? ile belirtilir)
     * @param params sorgu parametreleri
     */
    default void execute(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParams(stmt, params);
            stmt.executeUpdate();
        }
    }

    /**
     * Sorgu çalıştırır ve sonuçları işlemek için consumer alır.
     */
    default void query(String sql, Consumer<ResultSet> consumer, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                consumer.accept(rs);
            }
        }
    }

    /**
     * Sorgu çalıştırır ve sonuçları liste olarak döndürür.
     * Her satır bir Map (columnName → value) olarak temsil edilir.
     */
    default List<Map<String, Object>> queryList(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        query(sql, rs -> {
            try {
                var meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, params);
        return results;
    }

    /**
     * Tablo yoksa oluşturur.
     *
     * @param table tablo adı
     * @param columns sütun tanımları (örn: "id INTEGER PRIMARY KEY, name TEXT NOT NULL")
     */
    default void createTableIfNotExists(String table, String columns) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" + columns + ")";
        execute(sql);
    }

    /**
     * Migration tablosunun varlığını kontrol eder/oluşturur.
     * Her implementasyon kendi migration yöntemini belirler.
     */
    default void ensureMigrationTable() throws SQLException {
        createTableIfNotExists("_migrations",
                "version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT (datetime('now'))");
    }

    /**
     * Bağlantı nesnesini döndürür (her çağrıda yeni veya havuzdan).
     */
    Connection getConnection() throws SQLException;

    // ---- Yardımcı ----

    private void setParams(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }
}
