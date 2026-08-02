package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Event fired before a buyer claims their purchased item from the auction house.
 * <p>
 * This event is cancellable. Cancelling this event prevents the buyer from
 * receiving their purchased item.
 */
public class AuctionPreRemovePurchasedItemEvent extends CancelledRemoveEvent {

    /**
     * Creates a new pre-remove purchased item event.
     *
     * @param item   the purchased item being claimed
     * @param player the buyer claiming their purchase
     */
    public AuctionPreRemovePurchasedItemEvent(Item item, Player player) {
        super(item, player);
    }
}
