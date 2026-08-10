package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Başka bir oyuncunun aktif ilanlarını gösteren (salt-okunur) GUI.
 * <p>
 * /ihale gör <oyuncu> komutu ile açılır.
 * İlanlar satın alınabilir — tıklanan ilan için satın alma onayı açılır.
 * Layout {@code gui/player-listings.yml} dosyasından okunur.
 */
public class PlayerListingsGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final CollectionEntry data;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentViewer;
    private String sellerName;
    private List<AuctionListing> listings = new ArrayList<>();
    private int currentPage;

    public PlayerListingsGUI(AuctionManager manager, AuctionConfig config,
                             CollectionEntry data, GUILayoutLoader loader) {
        super("auction_playerlistings", "&8&l» <gradient:#FFB74D:#FFD54F>" + dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("OYUNCU İLANLARI") + "</gradient> &8&l«", 6);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.layout = loader.load("player-listings.yml");
        if (layout != null && layout.title() != null && !layout.title().isEmpty()) {
            setDynamicTitle(layout.title());
        }
    }

    public void open(Player viewer, String seller) {
        this.currentViewer = viewer;
        this.sellerName = seller;
        this.listings = data.searchListingsBySeller(seller);
        this.currentPage = 0;
        super.open(viewer);
    }

    public void open(Player viewer, String seller, List<AuctionListing> preloaded) {
        this.currentViewer = viewer;
        this.sellerName = seller;
        this.listings = preloaded;
        this.currentPage = 0;
        super.open(viewer);
    }

    @Override
    protected void onOpen(Player player) {
        clear();

        // Border (tam özelleştirme)
        applyBorder(layout.border());

        // Navigation items (tam özelleştirme)
        for (var nav : layout.navItems()) {
            setItem(nav.slot(), navBuilder(nav).build());
        }

        // Content — aktif ilanlar
        var slots = layout.contentSlots();
        int start = currentPage * slots.size();
        int end = Math.min(start + slots.size(), listings.size());

        for (int i = start; i < end; i++) {
            AuctionListing listing = listings.get(i);
            int slot = slots.get(i - start);

            ItemStack display = listing.item().clone();
            var builder = MenuItem.builder(display);
            String displayName = "&#F5F5F5" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());

            builder.name(displayName);
            for (String line : layout.loreFormat()) {
                builder.lore(formatLore(line, listing));
            }
            builder.onClick(e -> manager.openConfirmBuy(player, listing));

            setItem(slot, builder.build());
        }

        // Sayfa bilgisi (slot 4 — content veya border ile çakışıyorsa override etmez)
        if (slots.size() > 0 && !slots.contains(4)
                && (layout.border() == null || !layout.border().slots().contains(4))) {
            int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
            setItem(4, MenuItem.builder(Material.PAPER)
                    .name("&#F5F5F5&lꜱᴀʏꜰᴀ &#FFD54F&l" + (currentPage + 1) + " &#8c8c8c/ &#FFB74D&l" + (maxPage + 1))
                    .lore("&#8c8c8c• &#FFD54FSatıcı &#F5F5F5— " + (sellerName != null ? sellerName : "?"))
                    .lore("&#8c8c8c• &#FFD54FToplam ilan &#F5F5F5— " + listings.size())
                    .build());
        }

        // Arka plan dolgusu
        applyBackgroundFill(layout.backgroundFill());
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        boolean right = event.isRightClick();

        // Navigation item aksiyonu (config'den okur)
        var nav = findNavBySlot(slot);
        if (nav != null) {
            String action = right ? nav.rightClickAction() : nav.leftClickAction();
            if (handleNavAction(player, nav, action)) return;
        }
    }

    /** Slot'a karşılık gelen navigation item'ı döndürür. */
    private GUILayoutLoader.NavItem findNavBySlot(int slot) {
        if (layout.navItems() == null) return null;
        for (var n : layout.navItems()) if (n.slot() == slot) return n;
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
            case "previous-page" -> {
                if (currentPage > 0) { currentPage--; open(currentViewer, sellerName, listings); }
                return true;
            }
            case "next-page" -> {
                var slots = layout.contentSlots();
                int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
                if (currentPage < maxPage) { currentPage++; open(currentViewer, sellerName, listings); }
                return true;
            }
            default -> { return false; }
        }
    }

    private String formatLore(String template, AuctionListing listing) {
        long hours = listing.getTimeLeft() / 3600_000;
        long minutes = (listing.getTimeLeft() % 3600_000) / 60_000;
        String type = listing.type() != null ? listing.type() : "BIN";
        return template
                .replace("%price%", String.format("%,.2f", listing.price()))
                .replace("%time_left%", hours + "s " + minutes + "d")
                .replace("%type%", type)
                .replace("%seller%", listing.sellerName() != null ? listing.sellerName() : sellerName);
    }
}