package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Configuration for performance debug filtering.
 * Allows filtering which operations are logged during performance debugging.
 *
 * @param mode       The filter mode (WHITELIST or BLACKLIST)
 * @param operations The list of operation names to filter
 */
public record PerformanceDebugConfiguration(FilterMode mode, List<String> operations) {

    /**
     * Creates a PerformanceDebugConfiguration from the plugin configuration.
     *
     * @param plugin        the auction plugin
     * @param configuration the file configuration
     * @return the performance debug configuration
     */
    public static PerformanceDebugConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        String modeStr = configuration.getString("performance-debug.filter.mode", "DISABLED");
        FilterMode mode;
        try {
            mode = FilterMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid performance-debug filter mode: " + modeStr + ". Using DISABLED.");
            mode = FilterMode.DISABLED;
        }

        List<String> operations = configuration.getStringList("performance-debug.filter.operations");

        return new PerformanceDebugConfiguration(mode, operations);
    }

    /**
     * Checks if the given operation name should be logged based on the filter configuration.
     *
     * @param operationName the name of the operation
     * @return true if the operation should be logged, false otherwise
     */
    public boolean shouldLog(String operationName) {
        return switch (mode) {
            case DISABLED -> true;
            case WHITELIST -> matchesAny(operationName);
            case BLACKLIST -> !matchesAny(operationName);
        };
    }

    /**
     * Checks if the operation name matches any pattern in the operations list.
     * Supports prefix matching with wildcard (*) at the end.
     *
     * @param operationName the operation name to check
     * @return true if it matches any pattern
     */
    private boolean matchesAny(String operationName) {
        for (String pattern : operations) {
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (operationName.startsWith(prefix)) {
                    return true;
                }
            } else if (operationName.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The filter mode for performance debug operations.
     */
    public enum FilterMode {
        /**
         * Only log operations that are in the list
         */
        WHITELIST,
        /**
         * Log all operations except those in the list
         */
        BLACKLIST,
        /**
         * Log all operations (no filtering)
         */
        DISABLED
    }
}
