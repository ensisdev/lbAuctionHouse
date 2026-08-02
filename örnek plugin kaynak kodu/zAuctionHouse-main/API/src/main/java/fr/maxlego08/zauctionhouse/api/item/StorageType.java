package fr.maxlego08.zauctionhouse.api.item;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;

/**
 * Represents the storage bucket where an item is located in the auction house.
 * <p>
 * Items move between storage buckets as they progress through their lifecycle:
 * listed items can become purchased or expired, and eventually be deleted.
 */
public enum StorageType {

    /**
     * Items currently listed for sale in the auction house.
     * These items are visible to all players and can be purchased.
     */
    LISTED(PlayerCacheKey.ITEMS_LISTED),

    /**
     * Items that have been purchased and are awaiting delivery to the buyer.
     * The buyer can claim these items from their purchased items inventory.
     */
    PURCHASED(PlayerCacheKey.ITEMS_PURCHASED),

    /**
     * Items that have expired without being sold.
     * The original seller can reclaim these items from their expired items inventory.
     */
    EXPIRED(PlayerCacheKey.ITEMS_EXPIRED),

    /**
     * Items that have been permanently deleted from the system.
     * This storage type has no associated cache key.
     */
    DELETED(null),

    ;

    private final PlayerCacheKey playerCacheKey;

    StorageType(PlayerCacheKey playerCacheKey) {
        this.playerCacheKey = playerCacheKey;
    }

    /**
     * Gets the player cache key associated with this storage type.
     *
     * @return the cache key, or {@code null} for DELETED storage type
     */
    public PlayerCacheKey getPlayerCacheKey() {
        return playerCacheKey;
    }
}
