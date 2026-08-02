package dev.ensisdev.lbauctionhouse.event;

import dev.ensisdev.lbauctionhouse.data.AuctionListing;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bir ilan satın alınmadan HEMEN ÖNCE tetiklenir.
 * İptal edilirse satın alma gerçekleşmez.
 */
public class AuctionPrePurchaseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player buyer;
    private final AuctionListing listing;
    private boolean cancelled;

    public AuctionPrePurchaseEvent(Player buyer, AuctionListing listing) {
        this.buyer = buyer;
        this.listing = listing;
    }

    public Player getBuyer() { return buyer; }
    public AuctionListing getListing() { return listing; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
