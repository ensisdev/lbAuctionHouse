package dev.ensisdev.lbauctionhouse.data;

import org.bukkit.inventory.ItemStack;

/**
 * İşlem geçmişi kaydı — her satış, satın alma, iptal, süre dolumu kaydedilir.
 */
public record AuctionLog(
        long id,
        String action,       // SELL, PURCHASE, CANCEL, EXPIRED, ADMIN_REMOVE
        String sellerUUID,
        String sellerName,
        String buyerUUID,
        String buyerName,
        ItemStack item,
        double price,
        double tax,
        long timestamp,
        String listingId
) {
    public enum Action {
        SELL, PURCHASE, CANCEL, EXPIRED, ADMIN_REMOVE
    }
}
