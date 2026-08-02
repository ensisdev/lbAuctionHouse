package dev.ensisdev.lbauctionhouse.core.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Bir oyuncunun bakiyesi değiştiğinde tetiklenir.
 * <p>
 * Para ekleme (deposit), çekme (withdraw) ve transfer işlemlerinde fire edilir.
 * Transaction tipi {@link Type} enum'ı ile belirtilir.
 * <p>
 * Bu event async SIGNAL değildir — işlem zaten tamamlanmıştır.
 */
public class EconomyBalanceUpdateEvent extends CoreEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUUID;
    private final double oldBalance;
    private final double newBalance;
    private final double amount;
    private final Type type;

    public enum Type {
        DEPOSIT,
        WITHDRAW,
        TRANSFER_SENT,
        TRANSFER_RECEIVED
    }

    public EconomyBalanceUpdateEvent(UUID playerUUID, double oldBalance, double newBalance,
                                     double amount, Type type) {
        this.playerUUID = playerUUID;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.amount = amount;
        this.type = type;
    }

    /**
     * Bakiyesi değişen oyuncunun UUID'si.
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * İşlem öncesi bakiye.
     */
    public double getOldBalance() {
        return oldBalance;
    }

    /**
     * İşlem sonrası bakiye.
     */
    public double getNewBalance() {
        return newBalance;
    }

    /**
     * İşlem miktarı.
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Değişim tipi (DEPOSIT / WITHDRAW / TRANSFER_SENT / TRANSFER_RECEIVED).
     */
    public Type getType() {
        return type;
    }

    @Override
    public String getSystem() {
        return "economy";
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
