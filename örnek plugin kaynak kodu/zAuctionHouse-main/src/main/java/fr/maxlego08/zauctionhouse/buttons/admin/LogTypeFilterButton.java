package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public class LogTypeFilterButton extends Button {

    private final AuctionPlugin plugin;
    private final String enableText;
    private final String disableText;
    private final List<LogType> logTypes;
    private final Map<LogType, String> typeNames;
    private final String allTypesName;

    public LogTypeFilterButton(AuctionPlugin plugin, String enableText, String disableText,
                               List<LogType> logTypes, Map<LogType, String> typeNames, String allTypesName) {
        this.plugin = plugin;
        this.enableText = enableText;
        this.disableText = disableText;
        this.logTypes = logTypes;
        this.typeNames = typeNames;
        this.allTypesName = allTypesName;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        LogType currentFilter = cache.get(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER);

        // Register placeholder for ALL option
        String allStatus = (currentFilter == null ? enableText : disableText).replace("%type%", allTypesName);
        placeholders.register("ALL", allStatus);

        // Register placeholders for each log type
        for (LogType logType : logTypes) {
            String displayName = typeNames.getOrDefault(logType, logType.getDefaultDisplayName());
            String status = (logType == currentFilter ? enableText : disableText).replace("%type%", displayName);
            placeholders.register(logType.name(), status);
        }

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);
        LogType currentFilter = cache.get(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER);

        LogType nextFilter = getNextFilter(currentFilter, event.isRightClick());
        cache.set(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER, nextFilter);
        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    private LogType getNextFilter(LogType current, boolean reverse) {
        // Build full list with null (ALL) at the beginning
        int totalSize = logTypes.size() + 1; // +1 for ALL option

        int currentIndex;
        if (current == null) {
            currentIndex = 0; // ALL is at index 0
        } else {
            int typeIndex = logTypes.indexOf(current);
            currentIndex = typeIndex == -1 ? 0 : typeIndex + 1;
        }

        int direction = reverse ? -1 : 1;
        int nextIndex = (currentIndex + direction + totalSize) % totalSize;

        if (nextIndex == 0) {
            return null; // ALL
        } else {
            return logTypes.get(nextIndex - 1);
        }
    }
}
