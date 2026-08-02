package fr.maxlego08.zauctionhouse.api.item;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Enumerates all placeholders available for item lore rendering.
 * <p>
 * Used to pre-compute which placeholders a lore template actually references,
 * so that {@link Item#createPlaceholders} only resolves the values that are needed.
 */
public enum ItemPlaceholder {

    ECONOMY_NAME("economy-name"),
    ECONOMY_DISPLAY_NAME("economy-display-name"),
    SELLER("seller"),
    STATUS("status"),
    PRICE("price"),
    PRICE_RAW("price-price-raw"),
    PRICE_WITH_DECIMAL_FORMAT("price-price-with-decimal-format"),
    PRICE_WITH_REDUCTION("price-price-with-reduction"),
    PRICE_WITHOUT_DECIMAL("price-price-without-decimal"),
    TIME_REMAINING("time-remaining"),
    FORMATTED_EXPIRE_DATE("formatted-expire-date"),
    ITEM_COUNT("item_count");

    private static final Set<ItemPlaceholder> ALL = Set.copyOf(EnumSet.allOf(ItemPlaceholder.class));

    private final String key;
    private final String pattern;

    ItemPlaceholder(String key) {
        this.key = key;
        this.pattern = "%" + key + "%";
    }

    /**
     * Returns an immutable set containing all placeholder values.
     */
    public static Set<ItemPlaceholder> all() {
        return ALL;
    }

    /**
     * Scans lore lines and returns the set of placeholders actually referenced.
     * Called once at configuration load time so that rendering only resolves needed values.
     *
     * @param lines the lore template lines to scan
     * @return the set of placeholders found in the lines
     */
    public static Set<ItemPlaceholder> detect(List<String> lines) {
        EnumSet<ItemPlaceholder> found = EnumSet.noneOf(ItemPlaceholder.class);
        for (String line : lines) {
            if (!line.contains("%")) continue;
            for (ItemPlaceholder placeholder : values()) {
                if (line.contains(placeholder.pattern)) {
                    found.add(placeholder);
                }
            }
        }
        return Set.copyOf(found);
    }

    public String getKey() {
        return key;
    }
}
