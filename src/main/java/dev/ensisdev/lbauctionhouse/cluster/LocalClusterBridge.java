package dev.ensisdev.lbauctionhouse.cluster;

import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

/**
 * Tek sunucu cluster bridge — hiçbir şey yapmaz.
 * Çoklu sunucu kurulumu olmayanlar için varsayılan davranış.
 */
public class LocalClusterBridge implements ClusterBridge {

    private final AddonLogger logger;

    public LocalClusterBridge(AddonLogger logger) {
        this.logger = logger;
    }

    @Override public void enable() { logger.info("Cluster: tek sunucu modu."); }
    @Override public void disable() {}
    @Override public void onListingCreated(AuctionListing listing) {}
    @Override public void onListingSold(AuctionListing listing, String buyerName) {}
    @Override public void onListingRemoved(AuctionListing listing) {}
    @Override public void onBidPlaced(AuctionListing listing, String bidderName, double amount) {}
    @Override public boolean isMaster() { return true; }
}
