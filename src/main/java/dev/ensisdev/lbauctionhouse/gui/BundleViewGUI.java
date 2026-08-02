package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.util.BundleItems;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * TOPLU PAKET (fıçı) içerik görüntüleyicisi (salt okunur).
 * <p>
 * Fıçı → {@link BundleItems#unpack} ile eşyalar açılır.
 * Shulker kutusu için ayrı {@link ShulkerViewGUI} kullanılır.
 */
public class BundleViewGUI extends BaseMenu {

    private static final int MAX_CONTENT_SLOTS = 45;

    private Player currentPlayer;
    private final List<ItemStack> contents = new ArrayList<>();

    public BundleViewGUI() {
        super("auction_bundle_view", "&8&l» &6&l"+ dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("PAKET İÇERİĞİ")+" &8&l«", 6);
    }

    /**
     * @param source BARREL paket item'ı
     */
    public void open(Player player, ItemStack source) {
        this.currentPlayer = player;
        this.contents.clear();
        if (BundleItems.isBundle(source)) {
            contents.addAll(BundleItems.unpack(source));
        }
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        fillEmpty(MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        int total = 0;
        for (ItemStack it : contents) total += it.getAmount();

        for (int i = 0; i < contents.size() && i < MAX_CONTENT_SLOTS; i++) {
            setItem(i, MenuItem.builder(contents.get(i).clone()).build());
        }
        setItem(53, MenuItem.builder(Material.BOOK)
                .name("&eToplu Paket")
                .lore("&7" + contents.size() + " tür, &7" + total + " adet")
                .build());
        setItem(49, MenuItem.builder(Material.BARRIER).name("&c&lKapat").build());
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        if (event.getSlot() == 49) {
            close(currentPlayer);
            return;
        }
        event.setCancelled(true); // salt okunur
    }
}
