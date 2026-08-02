package fr.maxlego08.zauctionhouse.api.event;

import org.bukkit.event.Cancellable;

/**
 * Base class for cancellable auction house events.
 * <p>
 * Events extending this class can be cancelled by event listeners to prevent
 * the associated action from completing. This is typically used for "pre" events
 * that fire before an action is finalized.
 *
 * @see AuctionEvent
 */
public class CancelledAuctionEvent extends AuctionEvent implements Cancellable {

    private boolean cancelled;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

}
