package dev.ensisdev.lbauctionhouse.cluster;

import dev.ensisdev.lbauctionhouse.data.AuctionListing;

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
}
