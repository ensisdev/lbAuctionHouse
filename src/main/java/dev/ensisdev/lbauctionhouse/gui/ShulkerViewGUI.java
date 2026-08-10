package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.function.Consumer;

public class ShulkerViewGUI extends BaseMenu {

    private final ItemStack shulkerItem;
    private final Consumer<Player> onClose;

    public ShulkerViewGUI(ItemStack shulkerItem, Consumer<Player> onClose) {
        super("shulker_view", "&8&l» <gradient:#FFB74D:#FFD54F>"+ dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("SHULKER")+"</gradient> &8&l«", 6);
        this.shulkerItem = shulkerItem;
        this.onClose = onClose;
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        if (shulkerItem.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof ShulkerBox box) {
            int i = 0;
            for (ItemStack item : box.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    setItem(i, MenuItem.builder(item.clone()).build());
                }
                i++;
            }
        }
        fillEmpty(MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    @Override
    protected void onClose(Player player) {
        if (onClose != null) onClose.accept(player);
    }

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        event.setCancelled(true);
    }
}
