package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.text.NumberFormat;

/**
 * Normal bir ilanın BİLGİ GUI'si (27 slot) — satıcı bilgileri + satın al.
 * <p>
 * Layout {@code gui/info.yml} dosyasından özelleştirilebilir:
 * <ul>
 *   <li>14 → ilan edilen eşya</li>
 *   <li>11 → satıcı bilgileri (oyuncu kafası + istatistik)</li>
 *   <li>17 → "Satın Al" butonu</li>
 *   <li>22 → geri</li>
 * </ul>
 */
public class ItemInfoGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionData data;
    private final AuctionEconomy economy;
    private final LbAuctionHouse addon;
    private AuctionListing currentListing;

    private int itemSlot = 14;
    private int sellerSlot = 11;
    private int buySlot = 17;
    private int closeSlot = 22;
    private Material buyMaterial = Material.LIME_WOOL;
    private String buyName = "&a&l✔ Satın Al";
    private java.util.List<String> buyLore = java.util.List.of("&7Tıkla — satın almayı onayla");
    private String layoutTitle = "&8&l» &6&lİLAN BİLGİSİ &8&l«";

    public ItemInfoGUI(LbAuctionHouse addon, AuctionManager manager, AuctionConfig config,
                       AuctionData data, AuctionEconomy economy) {
        super("auction_info", "&8&l» &6&lİLAN BİLGİSİ &8&l«", 3);
        this.addon = addon;
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        loadLayout();
    }

    private void loadLayout() {
        try {
            if (!new File(addon.getDataFolder(), "gui/info.yml").exists()) {
                addon.saveResource("gui/info.yml", false);
            }
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new File(addon.getDataFolder(), "gui/info.yml"));
            layoutTitle = yaml.getString("title", "&8&l» &6&lİLAN BİLGİSİ &8&l«");
            itemSlot = yaml.getInt("item-slot", 14);
            sellerSlot = yaml.getInt("seller-slot", 11);
            buySlot = yaml.getInt("buy-slot", 17);
            closeSlot = yaml.getInt("close-slot", 22);
            buyMaterial = Material.valueOf(yaml.getString("buy-material", "LIME_WOOL").toUpperCase());
            buyName = yaml.getString("buy-name", "&a&l✔ Satın Al");
            buyLore = yaml.getStringList("buy-lore");
            if (buyLore.isEmpty()) buyLore = java.util.List.of("&7Tıkla — satın almayı onayla");
        } catch (Exception e) {
            addon.getLogger().severe("[ItemInfoGUI] gui/info.yml yüklenemedi, varsayılanlar kullanılıyor: " + e.getMessage());
        }
    }

    public void open(Player player, AuctionListing listing) {
        this.currentListing = listing;
        setDynamicTitle(layoutTitle);   // gui/info.yml'deki title (& destekli)
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        fillEmpty(MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        if (currentListing == null) return;
        var listing = currentListing;

        // 14 → ilan edilen eşya
        setItem(itemSlot, MenuItem.builder(listing.item().clone())
                .name("&f" + displayName(listing))
                .lore("&7Satıcı: &f" + listing.sellerName())
                .lore("&7Fiyat: &6" + economy.format(listing.price()))
                .lore("&7Kalan: &e" + formatTimeLeft(listing.getTimeLeft()))
                .lore("&7Adet: &f" + listing.item().getAmount())
                .build());

        // 11 → satıcı kafası + istatistikler
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(listing.sellerUUID()));
            sm.setDisplayName("§6" + listing.sellerName());
            // Cache'li okuma — her GUI açılışında 2 SQL + bakiye sorgusu çekmemek için
            var stats = manager.getListingCache().getPlayerStats(listing.sellerUUID());
            double bal = manager.getListingCache().getPlayerBalance(
                    listing.sellerUUID(),
                    uuid -> manager.getApi().getEconomyManager().getBalance(uuid));
            sm.setLore(java.util.List.of(
                    "§7Satılan eşya: §f" + stats.totalSold(),
                    "§7Alınan eşya: §f" + stats.totalBought(),
                    "§7Kazanç: §6" + NumberFormat.getInstance().format(stats.totalEarned()) + "₺",
                    "§7Bakiye: §6" + NumberFormat.getInstance().format(bal) + "₺"));
            head.setItemMeta(sm);
        }
        setItem(sellerSlot, MenuItem.builder(head).build());

        // 17 → satın al
        var buyBuilder = MenuItem.builder(buyMaterial).name(buyName);
        for (String line : buyLore) buyBuilder.lore(line);
        setItem(buySlot, buyBuilder.build());

        // 22 → geri
        setItem(closeSlot, MenuItem.builder(Material.BARRIER)
                .name("&c&lGeri")
                .lore("&7Ana menüye dön")
                .build());
    }

    private String displayName(AuctionListing listing) {
        return dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());
    }

    private String formatTimeLeft(long ms) {
        long hours = ms / 3600_000;
        long minutes = (ms % 3600_000) / 60_000;
        return hours + "s " + minutes + "d";
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == buySlot && currentListing != null) {
            close(player);
            if (config.isConfirmOnBuy()) {
                manager.openConfirmBuy(player, currentListing);
            } else {
                manager.buyItem(player, currentListing);
            }
        } else if (slot == closeSlot) {
            close(player);
            manager.openMainMenu(player);
        } else {
            event.setCancelled(true);
        }
    }
}
