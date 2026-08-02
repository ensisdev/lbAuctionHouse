package fr.maxlego08.zauctionhouse.api.event.events;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.util.List;

/**
 * Event fired when items have expired and been moved to the expired storage.
 * <p>
 * This event is fired after expiration processing has completed. The items
 * have already been moved to the expired storage bucket and sellers have
 * been notified if they were online.
 * <p>
 * This event is not cancellable as the expiration has already been processed.
 */
public class AuctionExpireEvent extends AuctionEvent {

    private final List<Item> items;
    private final StorageType type;

    /**
     * Creates a new expire event.
     *
     * @param items the items that have expired
     * @param type  the original storage type of the items before expiration
     */
    public AuctionExpireEvent(List<Item> items, StorageType type) {
        this.items = items;
        this.type = type;
    }

    /**
     * Gets the list of items that have expired.
     *
     * @return the expired items
     */
    public List<Item> getItems() {
        return items;
    }

    /**
     * Gets the original storage type of the expired items.
     *
     * @return the storage type before expiration
     */
    public StorageType getType() {
        return type;
    }
}
