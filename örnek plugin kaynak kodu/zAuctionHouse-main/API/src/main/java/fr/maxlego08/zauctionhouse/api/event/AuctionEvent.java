package fr.maxlego08.zauctionhouse.api.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base event class for all auction house events.
 * <p>
 * All auction-related events extend this class, providing a common foundation
 * for event handling. Events can be fired synchronously or asynchronously
 * depending on the context.
 *
 * @see CancelledAuctionEvent for cancellable events
 */
public class AuctionEvent extends Event {

    private final static HandlerList handlers = new HandlerList();

    /**
     * Creates a new synchronous auction event.
     */
    public AuctionEvent() {
        super();
    }

    /**
     * Creates a new auction event with the specified async mode.
     *
     * @param isAsync true if the event should be fired asynchronously
     */
    public AuctionEvent(boolean isAsync) {
        super(isAsync);
    }

    /**
     * Gets the handler list for this event type.
     *
     * @return the static handler list
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * {@inheritDoc}
     */
    public HandlerList getHandlers() {
        return handlers;
    }

}
