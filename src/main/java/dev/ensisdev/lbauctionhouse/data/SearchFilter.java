package dev.ensisdev.lbauctionhouse.data;

/**
 * Gelişmiş ilan arama filtresi.
 * <p>
 * Tüm alanlar opsiyoneldir — {@code null} / 0 / false olanlar sorguya dahil edilmez.
 *
 * @param query          eşya adı / display name araması
 * @param seller         satıcı adı araması (kısmi eşleşme)
 * @param material       tam materyal adı (enum, örn: DIAMOND)
 * @param minPrice       minimum fiyat (0 = sınırsız)
 * @param maxPrice       maksimum fiyat (0 = sınırsız)
 * @param type           ilan tipi: BIN, BID, RENT (null = tümü)
 * @param advertisedOnly sadece reklamlı ilanlar
 */
public record SearchFilter(
        String query,
        String seller,
        String material,
        double minPrice,
        double maxPrice,
        String type,
        boolean advertisedOnly
) {

    public static SearchFilter empty() {
        return new SearchFilter(null, null, null, 0, 0, null, false);
    }

    public boolean hasQuery() { return query != null && !query.isBlank(); }
    public boolean hasSeller() { return seller != null && !seller.isBlank(); }
    public boolean hasMaterial() { return material != null && !material.isBlank(); }
    public boolean hasType() { return type != null && !type.isBlank(); }
    public boolean hasMinPrice() { return minPrice > 0; }
    public boolean hasMaxPrice() { return maxPrice > 0; }
}