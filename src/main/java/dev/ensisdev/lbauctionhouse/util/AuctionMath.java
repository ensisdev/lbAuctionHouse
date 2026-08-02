package dev.ensisdev.lbauctionhouse.util;

/**
 * İhale fiyat/vergi hesaplamaları için saf (pure) yardımcılar.
 * <p>
 * Bukkit'e bağımlı değildir — birim testleriyle doğrulanabilir.
 */
public final class AuctionMath {

    private AuctionMath() {}

    /** Vergi düşülmüş net tutar: {@code gross * (1 - taxPercent/100)}. */
    public static double calculateNet(double gross, double taxPercent) {
        return gross * (1.0 - taxPercent / 100.0);
    }

    /** Fiyatı [min, max] aralığına sıkıştırır. */
    public static double clampPrice(double price, double min, double max) {
        return Math.max(min, Math.min(max, price));
    }

    /** Fiyat geçerli [min, max] aralığında mı? */
    public static boolean withinPrice(double price, double min, double max) {
        return price >= min && price <= max;
    }
}