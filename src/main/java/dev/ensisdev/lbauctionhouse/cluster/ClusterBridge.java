package dev.ensisdev.lbauctionhouse.cluster;

import dev.ensisdev.lbauctionhouse.data.AuctionListing;

import java.util.UUID;

/**
 * Çoklu sunucu (cluster) desteği için interface.
 * <p>
 * Her sunucuda yapılan değişiklikler diğer sunuculara bildirilir.
 * LocalClusterBridge = tek sunucu (bildirim yok)
 * RedisClusterBridge = Redis üzerinden çapraz sunucu senkronizasyonu
 */
public interface ClusterBridge {

    /** Sunucu başlatılırken çağrılır. */
    void enable();

    /** Sunucu kapatılırken çağrılır. */
    void disable();

    /** İlan oluşturuldu — diğer sunuculara bildir. */
    void onListingCreated(AuctionListing listing);

    /** İlan satıldı — diğer sunuculara bildir. */
    void onListingSold(AuctionListing listing, String buyerName);

    /** İlan kaldırıldı — diğer sunuculara bildir. */
    void onListingRemoved(AuctionListing listing);

    /** Teklif verildi — diğer sunuculara bildir. */
    void onBidPlaced(AuctionListing listing, String bidderName, double amount);

    /** Bu sunucu cluster'da ana mı? (listing'leri yöneten) */
    default boolean isMaster() { return true; }

    /**
     * Çapraz sunucu listing kilidi edinmeyi dener.
     * <p>
     * Yalnızca Redis cluster modunda gerçek anlamda kilit sağlar (SETNX + TTL);
     * tek sunucu (Local) modunda her zaman başarılı döner — davranış değişmez.
     *
     * @param listingId kilitlenecek ilan
     * @return kilit başarıyla edinildiyse true
     */
    default boolean tryAcquireListingLock(UUID listingId) { return true; }

    /** Edinilmiş çapraz sunucu kilidini serbest bırakır. */
    default void releaseListingLock(UUID listingId) {}

    /**
     * Diğer sunuculardan gelen cluster event'lerinde yerel cache'i bozmak için
     * callback atar (opsiyonel). Tek sunucu modunda no-op'tur.
     *
     * @param invalidator (channel, listingId) → ListingCacheService.invalidate*
     */
    default void setCacheInvalidator(java.util.function.BiConsumer<String, UUID> invalidator) {}
}
