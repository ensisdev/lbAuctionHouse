package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.util.ItemNames;
import dev.ensisdev.lbauctionhouse.util.SmallCaps;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * Favoriler GUI'si — oyuncunun ♥ ile favorilediği hâlâ aktif ilanları gösterir.
 * <p>
 * Sol tık → satın al; sağ tık → favoriden çıkar.
 * Layout {@code gui/favorites.yml} dosyasından okunur.
 */
public class FavoritesGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final CollectionEntry data;
    private final AuctionEconomy economy;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private List<AuctionListing> listings;
    private int currentPage;

    public FavoritesGUI(AuctionManager manager, AuctionConfig config, CollectionEntry data,
                        AuctionEconomy economy, GUILayoutLoader loader) {
        super("auction_favorites",
                "&8&l» <gradient:#FFB74D:#FFD54F>ꜰᴀᴠᴏʀɪ ʟɪꜱᴛᴇꜱɪ</gradient> &8&l«", 6);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        this.layout = loader.load("favorites.yml");
        // Layout yüklendikten sonra başlığı uygula
        if (layout != null && layout.title() != null && !layout.title().isEmpty()) {
            setDynamicTitle(layout.title());
        }
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.listings = data.getFavoriteListings(player.getUniqueId());
        this.currentPage = 0;

        if (listings.isEmpty()) {
            player.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.favorites.empty"));
            manager.openMainMenu(player);
            return;
        }
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();

        // Border (tam özelleştirme — texture, amount, glow, hide-flags destekli)
        applyBorder(layout.border());

        // Navigation items — tam özelleştirme (amount, cmd, glow, hide-flags, lore)
        for (var nav : layout.navItems()) {
            setItem(nav.slot(), navBuilder(nav).build());
        }

        // Content
        var slots = layout.contentSlots();
        int start = currentPage * slots.size();
        int end = Math.min(start + slots.size(), listings.size());

        for (int i = start; i < end; i++) {
            AuctionListing listing = listings.get(i);
            int slot = slots.get(i - start);
            int listingIndex = i;

            var builder = MenuItem.builder(listing.item().clone());
            builder.name("&#F5F5F5" + ItemNames.displayName(listing.item()));
            for (String line : layout.loreFormat()) {
                builder.lore(formatLore(line, listing));
            }
            builder.onClick(e -> handleListingClick(e, listingIndex));

            setItem(slot, builder.build());
        }

        // Arka plan dolgusu (border / nav / content slot'larına dokunmaz)
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

        // Listing click delegasyonu (içerik slot tıklama) MenuItem.builder().onClick() üzerinden olur
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
                if (currentPage > 0) { currentPage--; open(currentPlayer); }
                return true;
            }
            case "next-page" -> {
                var slots = layout.contentSlots();
                int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
                if (currentPage < maxPage) { currentPage++; open(currentPlayer); }
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * Sol tık → satın al; Sağ tık → favoriden çıkar.
     */
    private void handleListingClick(InventoryClickEvent event, int index) {
        if (index < 0 || index >= listings.size()) return;
        AuctionListing listing = listings.get(index);

        if (event.isRightClick()) {
            data.removeFavorite(currentPlayer.getUniqueId(), listing.id());
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed(
                    "auction.favorites.removed", "item", ItemNames.displayName(listing.item())));
            open(currentPlayer);
            return;
        }

        // Sol tık → satın alma
        close(currentPlayer);
        if (config.isConfirmOnBuy()) {
            manager.openConfirmBuy(currentPlayer, listing);
        } else {
            manager.buyItem(currentPlayer, listing);
        }
    }

    private String formatLore(String template, AuctionListing listing) {
        long hours = listing.getTimeLeft() / 3600_000;
        long minutes = (listing.getTimeLeft() % 3600_000) / 60_000;
        return template
                .replace("%seller%", listing.sellerName())
                .replace("%price%", economy.format(listing.price()))
                .replace("%time_left%", hours + "s " + minutes + "d");
    }
}
