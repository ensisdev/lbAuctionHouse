package fr.maxlego08.zauctionhouse.buttons.shulker;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentManager;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ShulkerOpenButton extends Button {

    private final AuctionPlugin plugin;

    public ShulkerOpenButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public boolean hasPermission() {
        return true;
    }

    @Override
    public boolean checkPermission(@NonNull Player player, @NonNull InventoryEngine inventory, @NonNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);

        if (!(item instanceof AuctionItem auctionItem)) {
            return false;
        }

        return this.plugin.getItemContentManager().containsContainer(auctionItem.getItemStacks());
    }

    @Override
    public void onClick(@NotNull Player player, @NotNull InventoryClickEvent event, @NotNull InventoryEngine inventory, int slot, @NotNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var contentManager = this.plugin.getItemContentManager();
        var cache = this.plugin.getAuctionManager().getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);

        if (!(item instanceof AuctionItem auctionItem)) {
            return;
        }

        List<ItemStack> containers = contentManager.getContainers(auctionItem.getItemStacks());
        if (containers.isEmpty()) {
            return;
        }

        cache.set(PlayerCacheKey.SHULKER_INDEX, 0);

        List<ItemStack> contents = contentManager.getContents(containers.getFirst());
        cache.set(PlayerCacheKey.SHULKER_ITEMS, contents);

        this.plugin.getInventoriesLoader().openInventory(player, Inventories.SHULKER_CONTENT);
    }
}
