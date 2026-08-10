package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.text.NumberFormat;
import java.util.List;

/**
 * Normal bir ilanın BİLGİ GUI'si (27 slot / 3 satır).
 * <p>
 * Layout {@code gui/info.yml} dosyasından tam özelleştirilebilir:
 * <ul>
 *   <li>{@code title} — başlık (&, hex, gradient destekli)</li>
 *   <li>{@code rows} — satır sayısı (varsayılan: 3)</li>
 *   <li>{@code border} — çerçeve (tam özelleştirme: amount, glow, hide-flags, texture, cmd)</li>
 *   <li>{@code background-fill} — arka plan dolgusu (boş slot'ları doldurur)</li>
 *   <li>{@code item-slot}/{@code item-lore-format} — ilan edilen eşyanın slot + lore şablonu</li>
 *   <li>{@code seller-slot}/{@code seller-lore-format} — satıcı kafası slot + lore şablonu</li>
 *   <li>{@code navigation} — buy/close/favorite butonları (NavItem yapısı, tam özelleştirme)</li>
 * </ul>
 */
public class ItemInfoGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final CollectionEntry data;
    private final AuctionEconomy economy;
    private final LbAuctionHouse addon;
    private AuctionListing currentListing;

    // Layout için tüm alanlar — info.yml'den okunur
    @SuppressWarnings("unused")
    private String layoutTitle; // sadece yeniden açılırsa dinamik başlık için okunur
    @SuppressWarnings("unused")
    private int layoutRows; // gelecek BaseMenu.rows API'si için hazır; mevcut BaseMenu fixed
    private GUILayoutLoader.BorderConfig border;
    private GUILayoutLoader.BackgroundFillConfig backgroundFill;
    private int itemSlot;
    private List<String> itemLoreFormat;
    private int sellerSlot;
    private List<String> sellerLoreFormat;
    private List<GUILayoutLoader.NavItem> navItems;

    public ItemInfoGUI(LbAuctionHouse addon, AuctionManager manager, AuctionConfig config,
                       CollectionEntry data, AuctionEconomy economy) {
        // Layout henüz yüklenmedi → sahte bir başlıkla super çağrıldı, open()'da setDynamicTitle ile değişecek
        super("auction_info", "&8&l» <gradient:#FFB74D:#FFD54F>ɪʟᴀɴ ʙɪʟɢɪꜱɪ</gradient> &8&l«", 3);
        this.addon = addon;
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        loadLayout();
    }

    /**
     * Layout'u info.yml'den yükler. Bulunamazsa plugin jar'ından varsayılanı kopyalar.
     * Bu metot constructor'da çağrıldığı için, GUI her açılışta taze layout okunmaz
     * (config değişirse plugin reload gerekir).
     */
    private void loadLayout() {
        try {
            File file = new File(addon.getDataFolder(), "gui/info.yml");
            if (!file.exists()) {
                addon.saveResource("gui/info.yml", false);
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            layoutTitle = yaml.getString("title", "&8&l» <gradient:#FFB74D:#FFD54F>ɪʟᴀɴ ʙɪʟɢɪꜱɪ</gradient> &8&l«");
            layoutRows = yaml.getInt("rows", 3);

            // Border
            ConfigurationSection bs = yaml.getConfigurationSection("border");
            if (bs != null) {
                String borderTex = bs.getString("texture", "");
                Material borderMat = (borderTex != null && !borderTex.isEmpty())
                        ? Material.PLAYER_HEAD
                        : Material.valueOf(bs.getString("material", "BLACK_STAINED_GLASS_PANE").toUpperCase());
                border = new GUILayoutLoader.BorderConfig(
                        borderMat, borderTex,
                        bs.getString("name", " "),
                        GUILayoutLoader.parseSlotListPublic(bs.getString("slots", "")),
                        bs.getInt("amount", 1),
                        bs.getInt("custom-model-data", 0),
                        bs.getBoolean("glow", false),
                        bs.getBoolean("hide-flags", false)
                );
            } else {
                border = new GUILayoutLoader.BorderConfig(Material.BLACK_STAINED_GLASS_PANE, "", " ",
                        List.of(), 1, 0, false, false);
            }

            // Background fill
            ConfigurationSection bfs = yaml.getConfigurationSection("background-fill");
            if (bfs != null) {
                String bTex = bfs.getString("texture", "");
                Material bMat = (bTex != null && !bTex.isEmpty())
                        ? Material.PLAYER_HEAD
                        : Material.valueOf(bfs.getString("material", "GRAY_STAINED_GLASS_PANE").toUpperCase());
                backgroundFill = new GUILayoutLoader.BackgroundFillConfig(
                        bMat, bTex,
                        bfs.getString("name", " "),
                        bfs.getInt("amount", 1),
                        bfs.getInt("custom-model-data", 0),
                        bfs.getBoolean("glow", false),
                        bfs.getBoolean("hide-flags", false)
                );
            }

            itemSlot = yaml.getInt("item-slot", 14);
            itemLoreFormat = yaml.getStringList("item-lore-format");
            if (itemLoreFormat.isEmpty()) itemLoreFormat = defaultItemLore();
            sellerSlot = yaml.getInt("seller-slot", 11);
            sellerLoreFormat = yaml.getStringList("seller-lore-format");
            if (sellerLoreFormat.isEmpty()) sellerLoreFormat = defaultSellerLore();

            // Navigation items (buy/close/favorite)
            navItems = new java.util.ArrayList<>();
            ConfigurationSection ns = yaml.getConfigurationSection("navigation");
            if (ns != null) {
                for (String id : ns.getKeys(false)) {
                    ConfigurationSection n = ns.getConfigurationSection(id);
                    if (n == null) continue;
                    String tex = n.getString("texture", "");
                    Material mat = (tex != null && !tex.isEmpty())
                            ? Material.PLAYER_HEAD
                            : Material.valueOf(n.getString("material", "STONE").toUpperCase());
                    navItems.add(new GUILayoutLoader.NavItem(
                            id,
                            n.getInt("slot", -1),
                            mat, tex,
                            n.getString("name", "&f" + id),
                            n.getStringList("lore"),
                            n.getString("left-click", ""),
                            n.getString("right-click", ""),
                            n.getInt("amount", 1),
                            n.getInt("custom-model-data", 0),
                            n.getBoolean("glow", false),
                            n.getBoolean("hide-flags", false)
                    ));
                }
            }
        } catch (Exception e) {
            addon.getLogger().severe("[ItemInfoGUI] gui/info.yml yüklenemedi, varsayılanlar kullanılıyor: " + e.getMessage());
            layoutTitle = "&8&l» <gradient:#FFB74D:#FFD54F>ɪʟᴀɴ ʙɪʟɢɪꜱɪ</gradient> &8&l«";
            layoutRows = 3;
            border = null;
            backgroundFill = null;
            itemSlot = 14;
            sellerSlot = 11;
            itemLoreFormat = defaultItemLore();
            sellerLoreFormat = defaultSellerLore();
            navItems = List.of();
        }
    }

    private List<String> defaultItemLore() {
        return new java.util.ArrayList<>(List.of(
                "&#8c8c8c• &#FFD54FSatıcı &#F5F5F5— %seller%",
                "&#8c8c8c• &#FFD54FFiyat &#FFAA00— %price%",
                "&#8c8c8c• &#FFD54FKalan &#FFD54F— %time_left%",
                "&#8c8c8c• &#FFD54FAdet &#F5F5F5— %amount%"
        ));
    }

    private List<String> defaultSellerLore() {
        return new java.util.ArrayList<>(List.of(
                "&#8c8c8c• &#FFB74DToplam Satılan &#F5F5F5— %sold%",
                "&#8c8c8c• &#FFB74DToplam Alınan &#F5F5F5— %bought%",
                "&#8c8c8c• &#FFB74DKazanç &#FFAA00— %earned%",
                "&#8c8c8c• &#FFB74DBakiye &#FFAA00— %balance%"
        ));
    }

    public void open(Player player, AuctionListing listing) {
        this.currentListing = listing;
        setDynamicTitle(layoutTitle);
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();

        // Border (tam özelleştirme)
        applyBorder(border);

        if (currentListing == null) {
            applyBackgroundFill(backgroundFill);
            return;
        }
        var listing = currentListing;

        // İlan slotu + lore-format (vars değişkenleriyle)
        setItem(itemSlot, buildItemDisplay(listing));

        // Satıcı kafası
        setItem(sellerSlot, buildSellerHead(listing));

        // Navigation items
        if (navItems != null) {
            for (var nav : navItems) {
                if (nav.slot() < 0) continue;
                if ("favorite".equalsIgnoreCase(nav.id())) {
                    setItem(nav.slot(), buildFavoriteItem(player, listing, nav));
                } else if ("buy".equalsIgnoreCase(nav.id())) {
                    setItem(nav.slot(), navBuilder(nav).build());
                } else if ("close".equalsIgnoreCase(nav.id())) {
                    setItem(nav.slot(), navBuilder(nav).build());
                } else {
                    setItem(nav.slot(), navBuilder(nav).build());
                }
            }
        }

        // Arka plan dolgusu
        applyBackgroundFill(backgroundFill);
    }

    private MenuItem buildItemDisplay(AuctionListing listing) {
        ItemStack display = listing.item() != null ? listing.item().clone() : new ItemStack(Material.STONE);
        var builder = MenuItem.builder(display);
        // Eşyanın kendi display name'i varsa onu koru (name(null) → colorize(null) NPE fırlatırdı);
        // yoksa standart ismi uygula.
        if (!(listing.item() != null && listing.item().hasItemMeta() && listing.item().getItemMeta().hasDisplayName())) {
            builder.name("&#F5F5F5" + displayName(listing));
        }
        for (String line : itemLoreFormat) {
            builder.lore(replaceItemVars(line, listing));
        }
        return builder.build();
    }

    private MenuItem buildSellerHead(AuctionListing listing) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(listing.sellerUUID()));
            sm.setDisplayName("§6§l" + listing.sellerName());
            var stats = manager.getListingCache().getPlayerStats(listing.sellerUUID());
            double bal = manager.getListingCache().getPlayerBalance(
                    listing.sellerUUID(),
                    uuid -> manager.getApi().getEconomyManager().getBalance(uuid));
            java.util.List<String> lore = new java.util.ArrayList<>();
            for (String line : sellerLoreFormat) {
                lore.add(replaceSellerVars(line, stats, listing.sellerName(), bal));
            }
            sm.setLore(lore);
            head.setItemMeta(sm);
        }
        return MenuItem.builder(head).build();
    }

    private MenuItem buildFavoriteItem(Player player, AuctionListing listing, GUILayoutLoader.NavItem nav) {
        boolean fav = data.isFavorite(player.getUniqueId(), listing.id());
        var b = navBuilder(nav);
        // Tıklamayla durum değişeceği için lore dinamik olmalı
        b.lore(fav ? "&#8c8c8c• &#FF5555Tıkla &#F5F5F5— favorilerden çıkar"
                  : "&#8c8c8c• &#F5F5F5Tıkla — favorilere ekle");
        return b.build();
    }

    private String replaceItemVars(String template, AuctionListing listing) {
        long hours = listing.getTimeLeft() / 3600_000;
        long minutes = (listing.getTimeLeft() % 3600_000) / 60_000;
        return template
                .replace("%seller%", listing.sellerName() != null ? listing.sellerName() : "?")
                .replace("%price%", economy.format(listing.price()))
                .replace("%time_left%", hours + "s " + minutes + "d")
                .replace("%amount%", String.valueOf(listing.item() != null ? listing.item().getAmount() : 1))
                .replace("%type%", listing.type() != null ? listing.type() : "BIN");
    }

    private String replaceSellerVars(String template,
                                     dev.ensisdev.lbauctionhouse.data.CollectionEntry.PlayerStats stats, String sellerName, double balance) {
        return template
                .replace("%seller%", sellerName != null ? sellerName : "?")
                .replace("%sold%", String.valueOf(stats.totalSold()))
                .replace("%bought%", String.valueOf(stats.totalBought()))
                .replace("%earned%", NumberFormat.getInstance().format(stats.totalEarned()))
                .replace("%balance%", NumberFormat.getInstance().format(balance))
                .replace("%spent%", NumberFormat.getInstance().format(stats.totalSpent()));
    }

    private String displayName(AuctionListing listing) {
        return dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        boolean right = event.isRightClick();

        if (currentListing == null) {
            event.setCancelled(true);
            return;
        }

        // Navigation item aksiyonu (config'den okur)
        var nav = findNavBySlot(slot);
        if (nav != null) {
            String action = right ? nav.rightClickAction() : nav.leftClickAction();
            if (handleNavAction(player, nav, action)) return;
        }

        // Aksiyon tanımlı değilse slot'u iptal et (bilgi GUI'sinde başka tıklama beklenmez)
        event.setCancelled(true);
    }

    /** Slot'a karşılık gelen navigation item'ı döndürür. */
    private GUILayoutLoader.NavItem findNavBySlot(int slot) {
        if (navItems == null) return null;
        for (var n : navItems) if (n.slot() == slot) return n;
        return null;
    }

    /** Navigation aksiyonlarını işler (yaml-drivent layout). */
    private boolean handleNavAction(Player player, GUILayoutLoader.NavItem nav, String action) {
        if (action == null || action.isEmpty()) return false;
        String norm = action.trim().toLowerCase().replace('_', '-');
        switch (norm) {
            case "close", "back" -> {
                close(player);
                manager.openMainMenu(player);
                return true;
            }
            case "buy" -> {
                if (!"buy".equalsIgnoreCase(nav.id())) return false;
                close(player);
                if (config.isConfirmOnBuy()) {
                    manager.openConfirmBuy(player, currentListing);
                } else {
                    manager.buyItem(player, currentListing);
                }
                return true;
            }
            case "toggle-favorite", "favorite" -> {
                var lang = manager.getApi().getLanguageManager();
                String itemName = displayName(currentListing);
                if (data.isFavorite(player.getUniqueId(), currentListing.id())) {
                    data.removeFavorite(player.getUniqueId(), currentListing.id());
                    player.sendMessage(lang.getPrefixed("auction.favorites.removed", "item", itemName));
                } else {
                    data.addFavorite(player.getUniqueId(), currentListing.id());
                    player.sendMessage(lang.getPrefixed("auction.favorites.added", "item", itemName));
                }
                open(player, currentListing);
                return true;
            }
            default -> { return false; }
        }
    }
}
