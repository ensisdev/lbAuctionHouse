package dev.ensisdev.lbauctionhouse.core.data;

import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * StorageAdapter için async wrapper — veritabanı işlemlerini arka planda çalıştırır,
 * sonuçları ana thread'e döndürür.
 * <p>
 * Kullanım:
 * <pre>
 * AsyncStorageWrapper async = new AsyncStorageWrapper(adapter);
 * async.queryListAsync("SELECT * FROM players").thenAccept(rows -> {
 *     // main thread'de çalışır
 * });
 * </pre>
 */
public class AsyncStorageWrapper {

    private final StorageAdapter delegate;
    private final ExecutorService executor;

    public AsyncStorageWrapper(StorageAdapter delegate) {
        this.delegate = delegate;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "lbAuctionHouse-DB");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Async INSERT/UPDATE/DELETE.
     */
    public CompletableFuture<Void> executeAsync(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try {
                delegate.execute(sql, params);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Async SELECT — sonuçları ana thread'de işlemek için thenAccept() kullan.
     */
    public CompletableFuture<List<Map<String, Object>>> queryListAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.queryList(sql, params);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Executor'ı kapatır.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Senkron metotlara erişim (gerektiğinde).
     */
    public StorageAdapter sync() {
        return delegate;
    }
}
