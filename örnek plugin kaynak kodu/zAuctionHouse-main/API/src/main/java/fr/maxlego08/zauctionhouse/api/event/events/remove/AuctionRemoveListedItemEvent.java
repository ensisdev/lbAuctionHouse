package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Event fired after a seller has successfully removed their listed item from the auction house.
 * <p>
 * This event is not cancellable as the removal has already been completed.
 * The item has been returned to the seller's inventory.
 */
public class AuctionRemoveListedItemEvent extends RemoveEvent {

    /**
     * Creates a new remove listed item event.
     *
     * @param item   the item that was removed from listings
     * @param player the seller who removed the item
     */
    public AuctionRemoveListedItemEvent(Item item, Player player) {
        super(item, player);
    }
}
