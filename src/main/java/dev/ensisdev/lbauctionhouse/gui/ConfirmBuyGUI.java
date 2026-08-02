package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.text.NumberFormat;

/**
 * Satın alma onay GUI'si.
 * <p>
 * Layout {@code gui/confirm.yml} dosyasından okunur.
 * Config'de {@code confirm-on-buy: false} ise direkt satın alınır (GUI atlanır).
 */
public class ConfirmBuyGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final GUILayoutLoader.GUILayout layout;
    private AuctionListing currentListing;

    public ConfirmBuyGUI(AuctionManager manager, AuctionConfig config,
                         GUILayoutLoader loader) {
        super("auction_confirm", "&8Satın Al — Onay", 3);
        this.manager = manager;
        this.config = config;
        this.layout = loader.load("confirm.yml");
    }

    @Override
    protected void onOpen(Player player) {
        clear();

        // İlan item'ını ortaya koy
        if (currentListing != null) {
            var item = MenuItem.builder(currentListing.item().clone())
                    .name("&f" + currentListing.item().getItemMeta().getDisplayName())
                    .lore("&7Satıcı: &f" + currentListing.sellerName())
                    .lore("&7Fiyat: &6" + NumberFormat.getInstance().format(currentListing.price()))
                    .build();
            setItem(13, item);

            // Onayla
            setItem(11, MenuItem.builder(Material.LIME_WOOL)
                    .name("&a&l✔ Satın Al")
                    .lore("&7Tıklayarak satın almayı onayla")
                    .build());

            // İptal
            setItem(15, MenuItem.builder(Material.RED_WOOL)
                    .name("&c&l✖ İptal")
                    .lore("&7Geri dön")
                    .build());

            // Boşluk doldur
            fillEmpty(MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 11 && currentListing != null) {
            // Satın al
            var result = manager.buyItem(player, currentListing);
            close(player);

            var lang = manager.getApi().getLanguageManager();
            switch (result) {
                case SUCCESS -> player.sendMessage(lang.getPrefixed("auction.purchase.success"));
                case INSUFFICIENT_FUNDS -> player.sendMessage(lang.getPrefixed("auction.purchase.insufficient-funds"));
                case ALREADY_SOLD -> player.sendMessage(lang.getPrefixed("auction.purchase.already-sold"));
                case CANNOT_BUY_OWN -> player.sendMessage(lang.getPrefixed("auction.purchase.cannot-buy-own"));
                default -> player.sendMessage(lang.getPrefixed("auction.purchase.error"));
            }
        } else if (slot == 15) {
            close(player);
            manager.openMainMenu(player);
        }
    }

    public void open(Player player, AuctionListing listing) {
        this.currentListing = listing;
        setDynamicTitle(layout.title());   // confirm.yml'deki title (& destekli)
        open(player);
    }
}
