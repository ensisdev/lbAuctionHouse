package dev.ensisdev.lbauctionhouse.cluster;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import org.bukkit.configuration.ConfigurationSection;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

import java.util.UUID;

/**
 * Redis tabanlı cluster — çoklu sunucu arası senkronizasyon.
 */
public class RedisClusterBridge implements ClusterBridge {

    private final LbAuctionHouse plugin;
    private final CollectionEntry data;
    private final AddonLogger logger;
    private final boolean master;
    private final String host;
    private final int port;
    private final String password;
    private final int dbIndex;

    private JedisPool pool;
    private volatile boolean running;
    private volatile java.util.function.BiConsumer<String, UUID> cacheInvalidator;

    public RedisClusterBridge(LbAuctionHouse plugin, CollectionEntry data,
                               AddonLogger logger, ConfigurationSection config) {
        this.plugin = plugin;
        this.data = data;
        this.logger = logger;
        this.master = config.getBoolean("master", true);
        this.host = config.getString("host", "localhost");
        this.port = config.getInt("port", 6379);
        this.password = config.getString("password", "");
        this.dbIndex = config.getInt("database", 0);
    }

    @Override
    public void enable() {
        try {
            var poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(2);
            pool = new JedisPool(poolConfig, host, port, 2000, password, dbIndex);

            try {
                pool.getResource().ping(); // test
            } catch (Exception pingEx) {
                // İlk bağlantı başarısız olsa bile subscriber thread'i reconnect
                // backoff döngüsüyle yeniden bağlanmayı deneyecek.
                logger.warn("Redis ilk bağlantı denemesi başarısız: " + pingEx.getMessage()
                        + " (arka planda yeniden bağlanılacak)");
            }
            running = true;

            Thread sub = new Thread(this::listen, "lbAuctionHouse-Redis");
            sub.setDaemon(true);
            sub.start();

            logger.info("Redis cluster: " + host + ":" + port + (master ? " (master)" : " (slave)"));
        } catch (Exception e) {
            logger.warn("Redis bağlantı hatası: " + e.getMessage());
        }
    }

    @Override
    public void disable() {
        running = false;
        if (pool != null) pool.close();
    }

    @Override public boolean isMaster() { return master; }

    @Override
    public void onListingCreated(AuctionListing listing) {
        publish("create", listing.id().toString());
    }

    @Override
    public void onListingSold(AuctionListing listing, String buyerName) {
        publish("sold", listing.id() + "|" + buyerName);
    }

    @Override
    public void onListingRemoved(AuctionListing listing) {
        publish("remove", listing.id().toString());
    }

    @Override
    public void onBidPlaced(AuctionListing listing, String bidderName, double amount) {
        publish("bid", listing.id() + "|" + bidderName + "|" + amount);
    }

    /**
     * Çapraz sunucu listing kilidi — SET NX PX ile atomik kilit edinir.
     * Redis erişilemezse yerel davranışa geri döner (kilit başarılı sayılır),
     * böylece Redis kesintisi satın alma akışını tamamen durdurmaz.
     */
    @Override
    public boolean tryAcquireListingLock(UUID listingId) {
        if (pool == null || !running) return true; // local fallback
        try (Jedis j = pool.getResource()) {
            String reply = j.set(LOCK_PREFIX + listingId,
                    String.valueOf(System.nanoTime()),
                    SetParams.setParams().nx().px(LOCK_TTL_SECONDS * 1000));
            return "OK".equals(reply);
        } catch (Exception e) {
            logger.warn("Redis kilit alınamadı (" + listingId + "): " + e.getMessage());
            return true; // fallback: local davranış
        }
    }

    @Override
    public void releaseListingLock(UUID listingId) {
        if (pool == null || !running) return;
        try (Jedis j = pool.getResource()) {
            j.del(LOCK_PREFIX + listingId);
        } catch (Exception ignored) {}
    }

    @Override
    public void setCacheInvalidator(java.util.function.BiConsumer<String, UUID> invalidator) {
        this.cacheInvalidator = invalidator;
    }

    /** Diğer sunucudan gelen event'te yerel cache'i bozar (varsa). */
    private void invalidateCache(String channel, UUID listingId) {
        java.util.function.BiConsumer<String, UUID> inv = cacheInvalidator;
        if (inv != null) inv.accept(channel, listingId);
    }

    private void publish(String type, String msg) {
        if (pool == null) return;
        try (Jedis j = pool.getResource()) {
            j.publish("lbsmp:ah:" + type, msg);
        } catch (Exception ignored) {}
    }

    /** Bağlantı koptuğunda yeniden bağlanma denemeleri arası maksimum bekleme (ms). */
    private static final long MAX_BACKOFF_MS = 60_000L;
    /** Yeniden bağlanma denemeleri arası ilk bekleme (ms). */
    private static final long INITIAL_BACKOFF_MS = 1_000L;

    /** Çapraz sunucu kilit anahtarı öneki. */
    private static final String LOCK_PREFIX = "lbsmp:ah:lock:";
    /** Redis kilidinin otomatik süresi (sn) — işlem anormal sonlanırsa kilit kendiliğinden düşer. */
    private static final long LOCK_TTL_SECONDS = 30L;

    private void listen() {
        if (pool == null) return;
        long backoffMs = INITIAL_BACKOFF_MS;
        while (running) {
            try (Jedis j = pool.getResource()) {
                if (running) logger.info("Redis subscriber bağlandı (" + host + ":" + port + ").");
                backoffMs = INITIAL_BACKOFF_MS; // başarılı bağlantı → backoff sıfırla

                j.subscribe(new redis.clients.jedis.JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String msg) {
                        if (!running) unsubscribe();
                        String[] parts = msg.split("\\|");
                        if (parts.length < 1) return;
                        UUID lid = UUID.fromString(parts[0]);

                        plugin.getScheduler().runTask(() -> {
                            switch (channel.replace("lbsmp:ah:", "")) {
                                case "create" -> {
                                    // Başka sunucuda yeni ilan açıldı → yerel cache'i boz
                                    invalidateCache("create", lid);
                                }
                                case "sold" -> {
                                    if (parts.length >= 2)
                                        data.markSold(lid, parts[1], null);
                                    invalidateCache("sold", lid);
                                }
                                case "remove" -> {
                                    data.deleteListing(lid);
                                    invalidateCache("remove", lid);
                                }
                                case "bid" -> invalidateCache("bid", lid);
                            }
                        });
                    }
                }, "lbsmp:ah:create", "lbsmp:ah:sold", "lbsmp:ah:remove", "lbsmp:ah:bid");
            } catch (Exception e) {
                if (!running) break;
                logger.warn("Redis subscriber bağlantısı koptu: " + e.getMessage()
                        + " — " + backoffMs + "ms sonra yeniden deneniyor.");
            }

            if (!running) break;
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
    }
}
