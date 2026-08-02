package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for performance settings including cache thresholds and cluster timeouts.
 *
 * @param parallelSortThreshold     minimum items before using parallel sort (Arrays.parallelSort)
 * @param parallelCategoryThreshold minimum items before processing categories in parallel
 * @param parallelism               number of threads for parallel processing (-1 for auto-detect)
 * @param checkAvailabilityTimeoutMs timeout in ms for checking item availability
 * @param lockItemTimeoutMs         timeout in ms for acquiring item locks
 * @param unlockItemTimeoutMs       timeout in ms for releasing item locks
 * @param notifyStatusChangeTimeoutMs timeout in ms for status change notifications
 * @param notifyItemActionTimeoutMs timeout in ms for item action notifications (purchase/remove)
 */
public record PerformanceConfiguration(
        int parallelSortThreshold,
        int parallelCategoryThreshold,
        int parallelism,
        long checkAvailabilityTimeoutMs,
        long lockItemTimeoutMs,
        long unlockItemTimeoutMs,
        long notifyStatusChangeTimeoutMs,
        long notifyItemActionTimeoutMs
) {

    /**
     * Default parallel sort threshold (10000 items).
     */
    public static final int DEFAULT_PARALLEL_SORT_THRESHOLD = 10000;

    /**
     * Default parallel category threshold (5000 items).
     */
    public static final int DEFAULT_PARALLEL_CATEGORY_THRESHOLD = 5000;

    /**
     * Default parallelism (-1 for auto-detect).
     */
    public static final int DEFAULT_PARALLELISM = -1;

    /**
     * Default timeout for cluster operations (5000ms).
     */
    public static final long DEFAULT_TIMEOUT_MS = 5000;

    public static PerformanceConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        int parallelSortThreshold = configuration.getInt("performance.cache.parallel-sort-threshold", DEFAULT_PARALLEL_SORT_THRESHOLD);
        int parallelCategoryThreshold = configuration.getInt("performance.cache.parallel-category-threshold", DEFAULT_PARALLEL_CATEGORY_THRESHOLD);
        int parallelism = configuration.getInt("performance.cache.parallelism", DEFAULT_PARALLELISM);

        long checkAvailabilityTimeoutMs = configuration.getLong("performance.cluster-timeout.check-availability-ms", DEFAULT_TIMEOUT_MS);
        long lockItemTimeoutMs = configuration.getLong("performance.cluster-timeout.lock-item-ms", DEFAULT_TIMEOUT_MS);
        long unlockItemTimeoutMs = configuration.getLong("performance.cluster-timeout.unlock-item-ms", 3000);
        long notifyStatusChangeTimeoutMs = configuration.getLong("performance.cluster-timeout.notify-status-change-ms", 3000);
        long notifyItemActionTimeoutMs = configuration.getLong("performance.cluster-timeout.notify-item-action-ms", DEFAULT_TIMEOUT_MS);

        return new PerformanceConfiguration(
                parallelSortThreshold,
                parallelCategoryThreshold,
                parallelism,
                checkAvailabilityTimeoutMs,
                lockItemTimeoutMs,
                unlockItemTimeoutMs,
                notifyStatusChangeTimeoutMs,
                notifyItemActionTimeoutMs
        );
    }

    /**
     * Calculates the actual parallelism value.
     * If parallelism is -1, returns auto-detected value based on available processors.
     *
     * @return the number of threads to use for parallel processing
     */
    public int getEffectiveParallelism() {
        if (parallelism <= 0) {
            return Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        }
        return parallelism;
    }
}
