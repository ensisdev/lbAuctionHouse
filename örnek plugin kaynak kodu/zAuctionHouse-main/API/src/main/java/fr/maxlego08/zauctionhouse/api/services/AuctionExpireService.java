package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.util.List;

/**
 * Service responsible for processing expired items in the auction house.
 * <p>
 * This service handles the transition of items from their current storage bucket
 * to the expired storage, notifying sellers and updating the database accordingly.
 */
public interface AuctionExpireService {

    /**
     * Processes a single expired item, moving it to the expired storage bucket.
     * <p>
     * This method updates the item status, notifies the seller if online,
     * and persists the change to the database.
     *
     * @param item        the expired item to process
     * @param storageType the original storage type of the item
     */
    void processExpiredItem(Item item, StorageType storageType);

    /**
     * Processes multiple expired items in batch, executing a single SQL query per StorageType.
     *
     * @param items       list of expired items to process
     * @param storageType the original storage type of the items
     */
    void processExpiredItems(List<Item> items, StorageType storageType);

}
