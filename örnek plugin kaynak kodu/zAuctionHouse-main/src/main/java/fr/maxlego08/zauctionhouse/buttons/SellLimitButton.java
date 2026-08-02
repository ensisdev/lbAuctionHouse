package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SellLimitButton extends Button {

    private final AuctionPlugin plugin;
    private final List<ItemType> itemTypes;

    public SellLimitButton(AuctionPlugin plugin, List<ItemType> itemTypes) {
        this.plugin = plugin;
        this.itemTypes = itemTypes;
    }

    @Override
    public boolean hasSpecialRender() {
        return true;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var configuration = this.plugin.getConfiguration();
        var manager = this.plugin.getAuctionManager();

        int maxLimit = 0;
        for (ItemType itemType : this.itemTypes) {
            maxLimit += configuration.getPermission().getLimit(itemType, player);
        }

        int currentSelling = manager.getPlayerSellingItems(player).size();
        int remaining = Math.max(0, maxLimit - currentSelling);

        var slots = new ArrayList<>(getSlots());

        // Clear all slots first
        slots.forEach(slot -> {
            inventoryEngine.getSpigotInventory().setItem(slot, null);
            inventoryEngine.removeItem(slot);
        });

        // Fill slots up to remaining count
        int fillCount = Math.min(remaining, slots.size());
        for (int i = 0; i < fillCount; i++) {
            inventoryEngine.addItem(slots.get(i), this.getItemStack().build(player, false, new Placeholders()));
        }
    }
}
