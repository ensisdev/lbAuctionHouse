package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Event fired after a player has successfully claimed their expired item from the auction house.
 * <p>
 * This event is not cancellable as the claim has already been completed.
 * The item has been returned to the player's inventory.
 */
public class AuctionRemoveExpiredItemEvent extends RemoveEvent {

    /**
     * Creates a new remove expired item event.
     *
     * @param item   the expired item that was claimed
     * @param player the player who claimed the item
     */
    public AuctionRemoveExpiredItemEvent(Item item, Player player) {
        super(item, player);
    }
}
