package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Event fired before a player claims their expired item from the auction house.
 * <p>
 * This event is cancellable. Cancelling this event prevents the player from
 * reclaiming their expired item.
 */
public class AuctionPreRemoveExpiredItemEvent extends CancelledRemoveEvent {

    /**
     * Creates a new pre-remove expired item event.
     *
     * @param item   the expired item being claimed
     * @param player the player claiming their expired item
     */
    public AuctionPreRemoveExpiredItemEvent(Item item, Player player) {
        super(item, player);
    }
}
