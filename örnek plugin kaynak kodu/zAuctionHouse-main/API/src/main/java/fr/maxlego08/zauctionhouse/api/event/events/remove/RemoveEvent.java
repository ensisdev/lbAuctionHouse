package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Abstract base event for item removal events.
 * <p>
 * This event is fired after an item has been successfully removed from the auction house
 * and delivered to a player. These events are not cancellable as the action has already
 * been completed.
 *
 * @see CancelledRemoveEvent for pre-removal cancellable events
 */
public abstract class RemoveEvent extends AuctionEvent {

    private final Item item;
    private final Player player;

    /**
     * Creates a new remove event.
     *
     * @param item   the item that was removed
     * @param player the player who removed the item
     */
    public RemoveEvent(Item item, Player player) {
        this.item = item;
        this.player = player;
    }

    /**
     * Gets the item that was removed.
     *
     * @return the removed item
     */
    public Item getItem() {
        return item;
    }

    /**
     * Gets the player who removed the item.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }
}
