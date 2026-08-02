package dev.ensisdev.lbauctionhouse.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Bir eşya ihalea konulmadan HEMEN ÖNCE tetiklenir.
 * İptal edilirse eşya ihalea konulmaz.
 */
public class AuctionPreSellEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player seller;
    private final ItemStack item;
    private double price;
    private boolean cancelled;

    public AuctionPreSellEvent(Player seller, ItemStack item, double price) {
        this.seller = seller;
        this.item = item;
        this.price = price;
    }

    public Player getSeller() { return seller; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
