package fr.maxlego08.zauctionhouse.buttons.shulker;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ShulkerInfoButton extends Button {

    private final AuctionPlugin plugin;

    public ShulkerInfoButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onInventoryOpen(@NotNull Player player, @NotNull InventoryEngine inventory, @NotNull Placeholders placeholders) {
        registerPlaceholders(player, placeholders);
        super.onInventoryOpen(player, inventory, placeholders);
    }

    @Override
    public @Nullable ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var contentManager = this.plugin.getItemContentManager();
        var cache = this.plugin.getAuctionManager().getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);

        if (!(item instanceof AuctionItem auctionItem)) {
            return super.getCustomItemStack(player, useCache, placeholders);
        }

        List<ItemStack> containers = contentManager.getContainers(auctionItem.getItemStacks());
        if (containers.isEmpty()) {
            return super.getCustomItemStack(player, useCache, placeholders);
        }

        int currentIndex = cache.get(PlayerCacheKey.SHULKER_INDEX, 0);
        if (currentIndex < 0 || currentIndex >= containers.size()) {
            currentIndex = 0;
        }

        registerPlaceholders(player, placeholders);

        return containers.get(currentIndex).clone();
    }

    private void registerPlaceholders(Player player, Placeholders placeholders) {
        var contentManager = this.plugin.getItemContentManager();
        var cache = this.plugin.getAuctionManager().getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);

        if (!(item instanceof AuctionItem auctionItem)) {
            placeholders.register("shulker-current", "0");
            placeholders.register("shulker-total", "0");
            return;
        }

        List<ItemStack> containers = contentManager.getContainers(auctionItem.getItemStacks());
        int currentIndex = cache.get(PlayerCacheKey.SHULKER_INDEX, 0);

        placeholders.register("shulker-current", String.valueOf(currentIndex + 1));
        placeholders.register("shulker-total", String.valueOf(containers.size()));
    }
}
