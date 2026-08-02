package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Event fired after a buyer has successfully claimed their purchased item from the auction house.
 * <p>
 * This event is not cancellable as the claim has already been completed.
 * The item has been delivered to the buyer's inventory.
 */
public class AuctionRemovePurchasedItemEvent extends RemoveEvent {

    /**
     * Creates a new remove purchased item event.
     *
     * @param item   the purchased item that was claimed
     * @param player the buyer who claimed the item
     */
    public AuctionRemovePurchasedItemEvent(Item item, Player player) {
        super(item, player);
    }
}
