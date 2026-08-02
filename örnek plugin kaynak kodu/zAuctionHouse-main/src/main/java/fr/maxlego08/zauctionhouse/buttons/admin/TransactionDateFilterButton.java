package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public class TransactionDateFilterButton extends Button {

    private final AuctionPlugin plugin;
    private final String enableText;
    private final String disableText;
    private final List<DateFilter> dateFilters;
    private final Map<DateFilter, String> filterNames;

    public TransactionDateFilterButton(AuctionPlugin plugin, String enableText, String disableText,
                                       List<DateFilter> dateFilters, Map<DateFilter, String> filterNames) {
        this.plugin = plugin;
        this.enableText = enableText;
        this.disableText = disableText;
        this.dateFilters = dateFilters;
        this.filterNames = filterNames;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        DateFilter currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_DATE_FILTER, DateFilter.ALL);

        for (DateFilter dateFilter : dateFilters) {
            String displayName = filterNames.getOrDefault(dateFilter, dateFilter.getDisplayName());
            String status = (dateFilter == currentFilter ? enableText : disableText).replace("%date%", displayName);
            placeholders.register(dateFilter.name(), status);
        }

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        if (dateFilters.isEmpty()) return;

        var cache = this.plugin.getAuctionManager().getCache(player);
        DateFilter currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_DATE_FILTER, DateFilter.ALL);

        int index = dateFilters.indexOf(currentFilter);
        if (index == -1) index = 0;

        int direction = event.isRightClick() ? -1 : 1;
        int size = dateFilters.size();
        int nextIndex = (index + direction + size) % size;

        DateFilter nextFilter = dateFilters.get(nextIndex);
        cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_DATE_FILTER, nextFilter);
        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }
}
