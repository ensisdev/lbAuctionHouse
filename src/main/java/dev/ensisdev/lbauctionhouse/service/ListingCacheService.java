package dev.ensisdev.lbauctionhouse.service;

import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Listing verileri için in-memory cache servisi.
 * <p>
 * Sık çağrılan DB sorgularını (aktif ilanlar, oyuncu ilan sayısı, tek ilan)
 * önbelleğe alır. Cache bozma (invalidation) stratejisi:
 * <ul>
 *   <li><b>Zaman bazlı (TTL):</b> Her giriş belirli süre sonra otomatik düşer</li>
 *   <li><b>Olay bazlı:</b> İlan eklendi/satıldı/silindiğinde ilgili cache temizlenir</li>
 *   <li><b>Oyuncu bazlı:</b> Bir oyuncunun işlemi onun cache'lerini geçersiz kılar</li>
 * </ul>
 * <p>
 * Önemli: {@link AntiDupeService#getFreshListing} gibi <b>kritik işlem öncesi
 * doğrulamalar asla cache'den okunmaz</b> — her zaman taze DB sorgusu yapılır.
 * Cache yalnızca görüntüleme/sayı sorguları içindir.
 */
public class ListingCacheService {

    // ----------------------------------------------------------------
    // Cache Yapıları
    // ----------------------------------------------------------------

    /** Aktif ilan listesi (tümü) — TTL 5s */
    private volatile CacheEntry<List<AuctionListing>> activeListingsCache;

    /** Oyuncu bazlı aktif ilan sayısı — TTL 5s */
    private final Map<UUID, CacheEntry<Integer>> playerCountCache = new ConcurrentHashMap<>();

    /** Listing ID bazlı taze-olmayan veri cache'i — TTL 3s */
    private final Map<UUID, CacheEntry<AuctionListing>> listingCache = new ConcurrentHashMap<>();

    /** Unclaimed koleksiyon sayısı (oyuncu bazlı) — TTL 5s */
    private final Map<UUID, CacheEntry<Integer>> unclaimedCountCache = new ConcurrentHashMap<>();

    /** Aktif ilan TOPLAM sayısı — TTL 5s (ana menüde sayfalama için) */
    private volatile CacheEntry<Integer> activeCountCache;

    /** Satıcı istatistikleri (oyuncu bazlı) — TTL 15s (ItemInfoGUI) */
    private final Map<UUID, CacheEntry<CollectionEntry.PlayerStats>> playerStatsCache = new ConcurrentHashMap<>();

    /** Oyuncu bakiyesi — TTL 3s (ItemInfoGUI) */
    private final Map<UUID, CacheEntry<Double>> balanceCache = new ConcurrentHashMap<>();

    private final CollectionEntry data;
    private final AddonLogger logger;
    private final long activeListingsTtlMillis;
    private final long playerCountTtlMillis;
    private final long listingTtlMillis;
    private final long unclaimedTtlMillis;

    public ListingCacheService(CollectionEntry data, AddonLogger logger) {
        this(data, logger, 5000L, 5000L, 3000L, 5000L);
    }

    public ListingCacheService(CollectionEntry data, AddonLogger logger,
                               long activeListingsTtlMillis,
                               long playerCountTtlMillis,
                               long listingTtlMillis,
                               long unclaimedTtlMillis) {
        this.data = data;
        this.logger = logger;
        this.activeListingsTtlMillis = activeListingsTtlMillis;
        this.playerCountTtlMillis = playerCountTtlMillis;
        this.listingTtlMillis = listingTtlMillis;
        this.unclaimedTtlMillis = unclaimedTtlMillis;
    }

    // ----------------------------------------------------------------
    // Cache'li Okuma
    // ----------------------------------------------------------------

    /**
     * Tüm aktif ilanları cache'den okur. Cache boşsa DB'den yükler.
     */
    public List<AuctionListing> getActiveListings() {
        CacheEntry<List<AuctionListing>> cached = activeListingsCache;
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        List<AuctionListing> fresh = data.getActiveListings();
        activeListingsCache = new CacheEntry<>(fresh, System.currentTimeMillis() + activeListingsTtlMillis);
        return fresh;
    }

    /**
     * Oyuncunun aktif ilan sayısını cache'den okur.
     */
    public int getActiveCountBySeller(UUID playerUuid) {
        CacheEntry<Integer> cached = playerCountCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        int fresh = data.getActiveCountBySeller(playerUuid);
        playerCountCache.put(playerUuid, new CacheEntry<>(fresh, System.currentTimeMillis() + playerCountTtlMillis));
        return fresh;
    }

    /**
     * Tek ilanı cache'den okur. DB birebir (fresh-read) içindir — satın alma
     * gibi kritik işlemler öncesi {@link AntiDupeService#getFreshListing} kullanılır.
     */
    public AuctionListing getListing(UUID listingId) {
        CacheEntry<AuctionListing> cached = listingCache.get(listingId);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        AuctionListing fresh = data.getListing(listingId);
        if (fresh != null) {
            listingCache.put(listingId, new CacheEntry<>(fresh, System.currentTimeMillis() + listingTtlMillis));
        }
        return fresh;
    }

    /**
     * Oyuncunun toplanmayı bekleyen koleksiyon sayısını cache'den okur.
     */
    public int getUnclaimedCount(UUID playerUuid) {
        CacheEntry<Integer> cached = unclaimedCountCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        int fresh = data.getUnclaimedCount(playerUuid);
        unclaimedCountCache.put(playerUuid, new CacheEntry<>(fresh, System.currentTimeMillis() + unclaimedTtlMillis));
        return fresh;
    }

    /**
     * Aktif ilan TOPLAM sayısını cache'den okur (ana menü sayfalama).
     */
    public int getActiveListingsCount() {
        CacheEntry<Integer> cached = activeCountCache;
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        int fresh = data.getActiveListingsCount();
        activeCountCache = new CacheEntry<>(fresh, System.currentTimeMillis() + activeListingsTtlMillis);
        return fresh;
    }

    /**
     * Satıcı istatistiklerini cache'den okur (TTL 15s) — ItemInfoGUI açılışlarında
     * her seferinde 2 SQL sorgusu çekmemek için.
     */
    public CollectionEntry.PlayerStats getPlayerStats(UUID playerUuid) {
        CacheEntry<CollectionEntry.PlayerStats> cached = playerStatsCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        CollectionEntry.PlayerStats fresh = data.getPlayerStats(playerUuid);
        playerStatsCache.put(playerUuid, new CacheEntry<>(fresh, System.currentTimeMillis() + 15_000L));
        return fresh;
    }

    /**
     * Oyuncu bakiyesini cache'den okur (TTL 3s). Loader, bakiye kaynağıdır (ör: economy manager).
     */
    public double getPlayerBalance(UUID playerUuid, Function<UUID, Double> loader) {
        CacheEntry<Double> cached = balanceCache.get(playerUuid);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        double fresh;
        try {
            Double v = loader.apply(playerUuid);
            fresh = v == null ? 0 : v;
        } catch (Exception e) {
            fresh = 0;
        }
        balanceCache.put(playerUuid, new CacheEntry<>(fresh, System.currentTimeMillis() + 3_000L));
        return fresh;
    }

    // ----------------------------------------------------------------
    // Cache Bozma (Invalidation)
    // ----------------------------------------------------------------

    /**
     * Tüm cache'leri temizler — bir ilan eklendi, satıldı veya silindiğinde çağrılır.
     */
    public void invalidateAll() {
        activeListingsCache = null;
        activeCountCache = null;
        playerCountCache.clear();
        listingCache.clear();
        unclaimedCountCache.clear();
    }

    /**
     * Tek ilanla ilgili cache'leri temizler.
     */
    public void invalidateListing(UUID listingId) {
        activeListingsCache = null;
        activeCountCache = null;
        listingCache.remove(listingId);
        playerCountCache.clear();
    }

    /**
     * Oyuncuyla ilgili cache'leri temizler.
     */
    public void invalidatePlayer(UUID playerUuid) {
        playerCountCache.remove(playerUuid);
        unclaimedCountCache.remove(playerUuid);
        playerStatsCache.remove(playerUuid);
        balanceCache.remove(playerUuid);
    }

    /**
     * Belirli bir işlemi cache üzerinden çalıştırır ve sonucu cache'ler.
     * Fonksiyon DB'den okuma yapmalıdır.
     */
    public <T> T getOrLoad(UUID key, Function<UUID, T> loader, Map<UUID, CacheEntry<T>> cache, long ttlMillis) {
        CacheEntry<T> cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        T value = loader.apply(key);
        if (value != null) {
            cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
        }
        return value;
    }

    // ----------------------------------------------------------------
    // Bakım
    // ----------------------------------------------------------------

    /**
     * Süresi dolmuş cache girişlerini temizler.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        if (activeListingsCache != null && activeListingsCache.isExpired()) {
            activeListingsCache = null;
        }
        if (activeCountCache != null && activeCountCache.isExpired()) {
            activeCountCache = null;
        }
        playerCountCache.entrySet().removeIf(e -> e.getValue().isExpired());
        listingCache.entrySet().removeIf(e -> e.getValue().isExpired());
        unclaimedCountCache.entrySet().removeIf(e -> e.getValue().isExpired());
        playerStatsCache.entrySet().removeIf(e -> e.getValue().isExpired());
        balanceCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * Tüm cache'leri ve içeriklerini temizler.
     */
    public void shutdown() {
        activeListingsCache = null;
        activeCountCache = null;
        playerCountCache.clear();
        listingCache.clear();
        unclaimedCountCache.clear();
        playerStatsCache.clear();
        balanceCache.clear();
        logger.info("ListingCacheService kapatıldı.");
    }

    // ----------------------------------------------------------------
    // Veri Kaydı
    // ----------------------------------------------------------------

    private record CacheEntry<T>(T value, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}