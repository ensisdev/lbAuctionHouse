package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * Oyuncunun kendi aktif ilanlarını gösteren GUI.
 * Tıklayarak ilanı iptal edebilir.
 * Layout {@code gui/my-listings.yml} dosyasından okunur.
 */
public class MyListingsGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionData data;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private List<AuctionListing> listings;
    private int currentPage;

    public MyListingsGUI(AuctionManager manager, AuctionConfig config,
                         AuctionData data, GUILayoutLoader loader) {
        super("auction_mylistings", "&8İlanlarım", 6);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.layout = loader.load("my-listings.yml");
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.listings = data.getActiveListingsBySeller(player.getUniqueId());
        this.currentPage = 0;
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

        // Content
        var slots = layout.contentSlots();
        int start = currentPage * slots.size();
        int end = Math.min(start + slots.size(), listings.size());

        for (int i = start; i < end; i++) {
            AuctionListing listing = listings.get(i);
            int slot = slots.get(i - start);
            int listingIndex = i;

            var builder = MenuItem.builder(listing.item().clone());
            String displayName = listing.item().getItemMeta().hasDisplayName()
                    ? listing.item().getItemMeta().getDisplayName()
                    : "&f" + listing.item().getType().name();

            builder.name(displayName);
            for (String line : layout.loreFormat()) {
                builder.lore(formatLore(line, listing));
            }
            builder.onClick(e -> handleCancel(listingIndex));

            setItem(slot, builder.build());
        }
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        int slot = event.getSlot();

        if (slot == 45) { // previous page
            if (currentPage > 0) { currentPage--; open(currentPlayer); }
            return;
        }
        if (slot == 53) { // next page
            var slots = layout.contentSlots();
            int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
            if (currentPage < maxPage) { currentPage++; open(currentPlayer); }
            return;
        }
        if (slot == 49) { // back
            close(currentPlayer);
            manager.openMainMenu(currentPlayer);
        }
    }

    private void handleCancel(int index) {
        if (index < 0 || index >= listings.size()) return;
        AuctionListing listing = listings.get(index);

        if (manager.cancelListing(currentPlayer, listing)) {
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.cancel.success"));
        } else {
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.cancel.failed"));
        }

        // Listeyi yenile
        open(currentPlayer);
    }

    private String formatLore(String template, AuctionListing listing) {
        long hours = listing.getTimeLeft() / 3600_000;
        long minutes = (listing.getTimeLeft() % 3600_000) / 60_000;
        return template
                .replace("%price%", String.format("%,.2f", listing.price()))
                .replace("%time_left%", hours + "s " + minutes + "d");
    }
}
