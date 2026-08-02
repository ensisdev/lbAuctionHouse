package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.filter.SearchFilterType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.TreeMap;

public record SearchFilterConfiguration(Map<SearchFilterType, String> operators) {

    public static SearchFilterConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        Map<SearchFilterType, String> operators = new TreeMap<>((a, b) -> Integer.compare(b.name().length(), a.name().length()));

        for (SearchFilterType filterType : SearchFilterType.values()) {
            String operator = configuration.getString("search-filters." + filterType.name(), filterType.getDefaultOperator());
            operators.put(filterType, operator);
        }

        return new SearchFilterConfiguration(operators);
    }

    /**
     * Returns the configured operator for a filter type.
     */
    public String getOperator(SearchFilterType filterType) {
        return operators.getOrDefault(filterType, filterType.getDefaultOperator());
    }

    /**
     * Returns operators ordered by length (longest first) to avoid prefix conflicts.
     */
    public Map.Entry<SearchFilterType, String>[] orderedByLength() {
        return operators.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().length(), a.getValue().length()))
                .toArray(Map.Entry[]::new);
    }
}
