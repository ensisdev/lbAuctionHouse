package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * Bekleyen eşya ve paraların görüntülendiği GUI.
 * Layout {@code gui/collection-box.yml} dosyasından okunur.
 */
public class CollectionBoxGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionData data;
    private final AuctionEconomy economy;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private List<AuctionData.CollectionEntry> entries;

    public CollectionBoxGUI(AuctionManager manager, AuctionConfig config,
                            AuctionData data, AuctionEconomy economy,
                            GUILayoutLoader loader) {
        super("auction_collection", "&8&l» &6&lKOLİM &8&l«", 4);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        this.layout = loader.load("collection-box.yml");
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.entries = data.getUnclaimedCollection(player.getUniqueId());
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();

        // Border
        if (layout.border() != null) {
            for (int slot : layout.border().slots()) {
                setItem(slot, MenuItem.builder(layout.border().material())
                        .name(layout.border().name()).build());
            }
        }

        // Navigation
        for (var nav : layout.navItems()) {
            var builder = MenuItem.builder(nav.material()).name(nav.name());
            for (String line : nav.lore()) builder.lore(line);
            setItem(nav.slot(), builder.build());
        }

        // Claim All butonu (slot 35)
        if (!entries.isEmpty()) {
            setItem(35, MenuItem.builder(Material.HOPPER)
                    .name("&a&l✔ Hepsini Al")
                    .lore("&7Tüm bekleyen ödülleri al")
                    .onClick(e -> claimAll())
                    .build());
        }

        // Content
        if (entries.isEmpty()) {
            player.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.empty"));
            return;
        }

        var slots = layout.contentSlots();
        for (int i = 0; i < Math.min(slots.size(), entries.size()); i++) {
            var entry = entries.get(i);
            final int entryId = entry.id();
            final int index = i;

            var builder = MenuItem.builder(Material.CHEST)
                    .name("&6Bekleyen Ödül #" + (i + 1))
                    .onClick(e -> claim(entryId, index));

            if (entry.type().equals("ITEM") && entry.item() != null) {
                builder = MenuItem.builder(entry.item().clone())
                        .name("&e" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(entry.item()))
                        .onClick(e -> claim(entryId, index));
            } else if (entry.type().equals("MONEY")) {
                builder.lore("&7Miktar: &6" + economy.format(entry.amount()));
            }

            setItem(slots.get(i), builder.build());
        }
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        if (event.getSlot() == 31) { // close/back
            close(currentPlayer);
            manager.openMainMenu(currentPlayer);
        }
    }

    private void claim(int entryId, int index) {
        if (currentPlayer == null || entries == null || index >= entries.size()) return;

        var entry = entries.get(index);
        if (entry.type().equals("ITEM") && entry.item() != null) {
            var leftover = currentPlayer.getInventory().addItem(entry.item());
            if (leftover.isEmpty()) {
                data.markClaimed(entryId);
                currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.claimed"));
            } else {
                currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.inventory-full"));
                return;
            }
        } else if (entry.type().equals("MONEY")) {
            economy.deposit(currentPlayer.getUniqueId(), entry.amount());
            data.markClaimed(entryId);
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.claimed-money", "amount", economy.format(entry.amount())));
        }

        open(currentPlayer);
    }

    /** Tüm bekleyen ödülleri tek seferde al. */
    private void claimAll() {
        if (currentPlayer == null || entries == null) return;

        var iterator = entries.listIterator();
        boolean anyClaimed = false;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.type().equals("ITEM") && entry.item() != null) {
                var leftover = currentPlayer.getInventory().addItem(entry.item());
                if (leftover.isEmpty()) {
                    data.markClaimed(entry.id());
                    iterator.remove();
                    anyClaimed = true;
                }
            } else if (entry.type().equals("MONEY")) {
                economy.deposit(currentPlayer.getUniqueId(), entry.amount());
                data.markClaimed(entry.id());
                iterator.remove();
                anyClaimed = true;
            }
        }

        if (anyClaimed) {
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.claimed"));
        }

        open(currentPlayer);
    }
}
