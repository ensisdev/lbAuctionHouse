package dev.ensisdev.lbauctionhouse.data;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Bir ihale ilanını temsil eden immutable kayıt.
 * <p>
 * İki tip vardır:
 * <ul>
 *   <li><b>BIN</b> (Buy It Now) — sabit fiyat, ilk alan alır</li>
 *   <li><b>BID</b> (Auction) — en yüksek teklifi veren kazanır</li>
 * </ul>
 */
public record AuctionListing(
        UUID id,
        UUID sellerUUID,
        String sellerName,
        ItemStack item,
        double price,          // BIN: satış fiyatı (flash sale'de indirimli), BID: güncel en yüksek teklif
        double startingBid,    // BID: başlangıç fiyatı (BIN: 0)
        String type,           // "BIN", "BID", veya "BOTH"
        long listedAt,
        long expiresAt,
        boolean sold,
        String buyerName,
        UUID buyerUUID,
        long flashSaleEndsAt,  // 0 = flash sale değil
        double originalPrice,  // flash sale öncesi orijinal fiyat (0 = flash sale değil)
        boolean expired,       // true = süresi doldu ve işlendi
        double binPrice,       // "BOTH" tipi için BIN fiyatı (0 = BIN/BID)
        boolean sealed,        // true = gizli teklif (sealed bid)
        boolean advertised     // true = reklamlı ilan (global duyurulur, fiyat +komisyon)
) {

    public boolean isBid() {
        return "BID".equals(type) || "BOTH".equals(type);
    }

    public boolean isBin() {
        return "BIN".equals(type) || "BOTH".equals(type);
    }

    public boolean isBoth() {
        return "BOTH".equals(type);
    }

    public boolean isExpired() {
        return !sold && System.currentTimeMillis() > expiresAt;
    }

    public boolean isAdvertised() {
        return advertised;
    }

    public boolean isFlashSale() {
        return flashSaleEndsAt > 0 && System.currentTimeMillis() < flashSaleEndsAt;
    }

    public boolean isFlashSaleExpired() {
        return flashSaleEndsAt > 0 && System.currentTimeMillis() >= flashSaleEndsAt;
    }

    public long getTimeLeft() {
        if (sold) return 0;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public long getFlashSaleTimeLeft() {
        if (!isFlashSale()) return 0;
        return Math.max(0, flashSaleEndsAt - System.currentTimeMillis());
    }

    public double getDiscountPercent() {
        if (!isFlashSale() || originalPrice <= 0) return 0;
        return Math.round((1 - price / originalPrice) * 100);
    }
}
