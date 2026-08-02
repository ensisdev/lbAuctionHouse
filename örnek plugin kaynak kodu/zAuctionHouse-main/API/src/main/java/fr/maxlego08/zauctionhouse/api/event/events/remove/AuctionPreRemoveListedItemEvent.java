package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Event fired before a player removes their own listed item from the auction house.
 * <p>
 * This event is cancellable. Cancelling this event prevents the seller from
 * reclaiming their listed item.
 */
public class AuctionPreRemoveListedItemEvent extends CancelledRemoveEvent {

    /**
     * Creates a new pre-remove listed item event.
     *
     * @param item   the listed item being removed
     * @param player the seller removing their listing
     */
    public AuctionPreRemoveListedItemEvent(Item item, Player player) {
        super(item, player);
    }
}
