package fr.maxlego08.zauctionhouse.api.filter;

import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;

import java.util.Map;

/**
 * Represents a parsed search query with an optional field and filter type.
 * <p>
 * Spaces around the operator are allowed and trimmed automatically.
 * <p>
 * Examples:
 * <ul>
 *   <li>{@code "diamond"} → field=null, type=null, value="diamond" (default search on all fields)</li>
 *   <li>{@code "name ~ diamond"} → field=NAME, type=CONTAINS, value="diamond"</li>
 *   <li>{@code "seller = Notch"} → field=SELLER, type=EQUALS, value="Notch"</li>
 *   <li>{@code "seller == notch"} → field=SELLER, type=EQUALS_IGNORE_CASE, value="notch"</li>
 *   <li>{@code "material ~= sword"} → field=MATERIAL, type=CONTAINS_IGNORE_CASE, value="sword"</li>
 * </ul>
 *
 * @param field the target field, or null for default (all fields) search
 * @param type  the filter type, or null for default (CONTAINS) matching
 * @param value the search value
 */
public record SearchQuery(SearchField field, SearchFilterType type, String value) {

    /**
     * Parses a raw query string into a {@link SearchQuery} using configured operators.
     *
     * @param raw    the raw query string
     * @param config the search filter configuration containing operator mappings
     * @return the parsed search query
     */
    public static SearchQuery parse(String raw, SearchFilterConfiguration config) {
        if (raw == null || raw.isEmpty()) {
            return new SearchQuery(null, null, "");
        }

        // Try each operator, longest first to avoid "==" being matched as "="
        for (Map.Entry<SearchFilterType, String> entry : config.orderedByLength()) {
            String operator = entry.getValue();
            int index = raw.indexOf(operator);
            if (index > 0) {
                String fieldKey = raw.substring(0, index).trim();
                SearchField field = SearchField.fromKey(fieldKey);
                if (field != null) {
                    String value = raw.substring(index + operator.length()).trim();
                    return new SearchQuery(field, entry.getKey(), value);
                }
            }
        }

        // No operator found — default search
        return new SearchQuery(null, null, raw);
    }

    /**
     * @return true if this is a default search (no specific field targeted)
     */
    public boolean isDefault() {
        return field == null;
    }
}
