package fr.maxlego08.zauctionhouse.api.filter;

/**
 * Defines the matching operators available for search queries.
 * <p>
 * Each type has a default operator that can be overridden via configuration.
 */
public enum SearchFilterType {

    CONTAINS("~"),
    EQUALS("="),
    CONTAINS_IGNORE_CASE("~="),
    EQUALS_IGNORE_CASE("==");

    private final String defaultOperator;

    SearchFilterType(String defaultOperator) {
        this.defaultOperator = defaultOperator;
    }

    public String getDefaultOperator() {
        return defaultOperator;
    }
}
