package fr.maxlego08.zauctionhouse.buttons.shulker;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ShulkerContentButton extends PaginateButton {

    private final AuctionPlugin plugin;
    private final int emptySlot;

    public ShulkerContentButton(AuctionPlugin plugin, int emptySlot) {
        this.plugin = plugin;
        this.emptySlot = emptySlot;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        List<ItemStack> shulkerItems = cache.get(PlayerCacheKey.SHULKER_ITEMS);

        if (shulkerItems == null || shulkerItems.isEmpty()) {
            inventoryEngine.addItem(emptySlot, super.getCustomItemStack(player, false, new Placeholders()));
            return;
        }

        paginate(shulkerItems, inventoryEngine, (slot, itemStack) -> {
            if (itemStack != null && !itemStack.getType().isAir()) {
                inventoryEngine.addItem(slot, itemStack.clone());
            }
        });
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        List<ItemStack> shulkerItems = cache.get(PlayerCacheKey.SHULKER_ITEMS);
        return shulkerItems != null ? (int) shulkerItems.stream().filter(i -> i != null && !i.getType().isAir()).count() : 0;
    }
}
