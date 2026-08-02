package dev.ensisdev.lbauctionhouse.cluster;

import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis tabanlı cluster — çoklu sunucu arası senkronizasyon.
 */
public class RedisClusterBridge implements ClusterBridge {

    private final JavaPlugin plugin;
    private final AuctionData data;
    private final AddonLogger logger;
    private final boolean master;
    private final String host;
    private final int port;
    private final String password;
    private final int dbIndex;

    private JedisPool pool;
    private volatile boolean running;

    public RedisClusterBridge(JavaPlugin plugin, AuctionData data,
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

            pool.getResource().ping(); // test
            running = true;

            Thread sub = new Thread(this::listen, "lbSmpCore-Redis");
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

    private void publish(String type, String msg) {
        if (pool == null) return;
        try (Jedis j = pool.getResource()) {
            j.publish("lbsmp:ah:" + type, msg);
        } catch (Exception ignored) {}
    }

    private void listen() {
        if (pool == null) return;
        try (Jedis j = pool.getResource()) {
            j.subscribe(new redis.clients.jedis.JedisPubSub() {
                @Override
                public void onMessage(String channel, String msg) {
                    if (!running) unsubscribe();
                    String[] parts = msg.split("\\|");
                    if (parts.length < 1) return;
                    UUID lid = UUID.fromString(parts[0]);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (channel.replace("lbsmp:ah:", "")) {
                            case "sold" -> {
                                if (parts.length >= 2)
                                    data.markSold(lid, parts[1], null);
                            }
                            case "remove" -> data.deleteListing(lid);
                        }
                    });
                }
            }, "lbsmp:ah:create", "lbsmp:ah:sold", "lbsmp:ah:remove", "lbsmp:ah:bid");
        } catch (Exception e) {
            if (running) logger.warn("Redis subscriber kapandı: " + e.getMessage());
        }
    }
}
