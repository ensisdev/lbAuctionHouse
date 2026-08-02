package fr.maxlego08.zauctionhouse.api.event.events.purchase;

import fr.maxlego08.zauctionhouse.api.event.events.remove.CancelledRemoveEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

/**
 * Event fired before a player purchases an item from the auction house.
 * <p>
 * This event is cancellable. Cancelling this event prevents the purchase
 * from completing and no economy transaction will occur.
 *
 * <pre>{@code
 * @EventHandler
 * public void onPrePurchase(AuctionPrePurchaseItemEvent event) {
 *     // Prevent purchases from specific players
 *     if (isBanned(event.getPlayer())) {
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 */
public class AuctionPrePurchaseItemEvent extends CancelledRemoveEvent {

    /**
     * Creates a new pre-purchase event.
     *
     * @param item   the item being purchased
     * @param player the player attempting to purchase the item
     */
    public AuctionPrePurchaseItemEvent(Item item, Player player) {
        super(item, player);
    }
}
