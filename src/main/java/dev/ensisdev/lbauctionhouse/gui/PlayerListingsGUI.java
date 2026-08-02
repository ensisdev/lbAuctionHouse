package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
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

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int BACK_SLOT = 49;

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionData data;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentViewer;
    private String sellerName;
    private List<AuctionListing> listings = new ArrayList<>();
    private int currentPage;

    public PlayerListingsGUI(AuctionManager manager, AuctionConfig config,
                             AuctionData data, GUILayoutLoader loader) {
        super("auction_playerlistings", "&8&l» &6&lOYUNCU İLANLARI &8&l«", 6);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.layout = loader.load("player-listings.yml");
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

        // Content — aktif ilanlar
        var slots = layout.contentSlots();
        int start = currentPage * slots.size();
        int end = Math.min(start + slots.size(), listings.size());

        for (int i = start; i < end; i++) {
            AuctionListing listing = listings.get(i);
            int slot = slots.get(i - start);

            ItemStack display = listing.item().clone();
            var builder = MenuItem.builder(display);
            String displayName = "&f" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());

            builder.name(displayName);
            for (String line : layout.loreFormat()) {
                builder.lore(formatLore(line, listing));
            }
            builder.onClick(e -> manager.openConfirmBuy(player, listing));

            setItem(slot, builder.build());
        }

        // Sayfa bilgisi
        if (slots.size() > 0) {
            int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
            setItem(4, MenuItem.builder(Material.PAPER)
                    .name("&fSayfa: &e" + (currentPage + 1) + "&7/&e" + (maxPage + 1))
                    .lore("&7Satıcı: &f" + (sellerName != null ? sellerName : "?"))
                    .lore("&7Toplam ilan: &f" + listings.size())
                    .build());
        }
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        int slot = event.getSlot();

        if (slot == 45) { // previous page
            if (currentPage > 0) { currentPage--; open(currentViewer, sellerName, listings); }
            return;
        }
        if (slot == 53) { // next page
            var slots = layout.contentSlots();
            int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
            if (currentPage < maxPage) { currentPage++; open(currentViewer, sellerName, listings); }
            return;
        }
        if (slot == 49) { // back
            close(currentViewer);
            manager.openMainMenu(currentViewer);
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