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

    /** Flash sale indirimli fiyat: {@code price * (100 - discountPercent) / 100}. */
    public static double flashSalePrice(double price, int discountPercent) {
        if (discountPercent <= 0) return price;
        if (discountPercent >= 100) return 0;
        return price * (100.0 - discountPercent) / 100.0;
    }

    /** İlanın toplam listeleme maliyeti: süre ücreti + sabit ön ücret. */
    public static double listingCost(double durationFee, double listingFee) {
        return Math.max(0, durationFee) + Math.max(0, listingFee);
    }

    /** Kuruş (2 ondalık) hassasiyetine yuvarlar. */
    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}