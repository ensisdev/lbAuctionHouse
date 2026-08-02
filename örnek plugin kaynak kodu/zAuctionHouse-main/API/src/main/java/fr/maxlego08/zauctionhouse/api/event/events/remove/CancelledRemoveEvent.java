package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.event.CancelledAuctionEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Abstract base event for cancellable item removal events.
 * <p>
 * This event is fired before an item is removed from the auction house.
 * Cancelling this event prevents the removal from completing.
 * <p>
 * Pre-removal events (e.g., {@link AuctionPreRemoveListedItemEvent}) extend
 * this class to allow plugins to intercept and cancel removal operations.
 *
 * @see RemoveEvent for post-removal events
 */
public abstract class CancelledRemoveEvent extends CancelledAuctionEvent {

    private final Item item;
    private final Player player;

    /**
     * Creates a new cancellable remove event.
     *
     * @param item   the item being removed
     * @param player the player attempting to remove the item
     */
    public CancelledRemoveEvent(Item item, Player player) {
        this.item = item;
        this.player = player;
    }

    /**
     * Gets the item being removed.
     *
     * @return the item
     */
    public Item getItem() {
        return item;
    }

    /**
     * Gets the player attempting to remove the item.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }
}
