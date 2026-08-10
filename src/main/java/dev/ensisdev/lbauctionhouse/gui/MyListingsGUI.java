package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.ConfirmationMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.core.gui.SignInputGUI;

import org.bukkit.Material;
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
    private final CollectionEntry data;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private List<AuctionListing> listings;
    private int currentPage;
    private boolean showExpired;

    public MyListingsGUI(AuctionManager manager, AuctionConfig config,
                         CollectionEntry data, GUILayoutLoader loader) {
        super("auction_mylistings", "&8&l» <gradient:#FFB74D:#FFD54F>" + dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("İLANLARIM") + "</gradient> &8&l«", 6);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.layout = loader.load("my-listings.yml");
        if (layout != null && layout.title() != null && !layout.title().isEmpty()) {
            setDynamicTitle(layout.title());
        }
    }

    public void open(Player player) {
        open(player, false);
    }

    /**
     * @param showExpired true ise "Süresi Dolanlar" görünümü açılır (onaylı yenileme).
     */
    public void open(Player player, boolean showExpired) {
        this.currentPlayer = player;
        this.showExpired = showExpired;
        this.listings = showExpired
                ? data.getExpiredListingsByPlayer(player.getUniqueId())
                : data.getActiveListingsBySeller(player.getUniqueId());
        this.currentPage = 0;
        super.open(player);
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

        // Süresi Dolanlar / Aktif geçiş butonu (onaylı yenileme) — config'den
        var et = layout.expiredToggle();
        if (et != null) {
            var etBuilder = MenuItem.Builder.of(et.material(), et.texture())
                    .name(showExpired ? et.onName() : et.name());
            if (et.amount() > 1) etBuilder.amount(et.amount());
            if (et.customModelData() != 0) etBuilder.customModelData(et.customModelData());
            if (et.glow()) etBuilder.glow(true);
            if (et.hideFlags()) etBuilder.hideFlags(true);
            setItem(et.slot(), etBuilder.build());
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
            String displayName = "&#F5F5F5" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());

            builder.name(displayName);
            if (showExpired) {
                builder.lore("&#8c8c8c•  &#2CCED2[⏰] &#F5F5F5Süresi doldu");
                builder.lore("");
                builder.lore("&#8c8c8c•  &#FFD54Fꜰɪʏᴀᴛ  &#8c8c8c— &#FFAA00" + String.format("%,.2f", listing.price()));
                builder.lore("&#8c8c8c•  &#55FF55Sol Tık  &#8c8c8c— yeniden listele");
            } else {
                for (String line : layout.loreFormat()) {
                    builder.lore(formatLore(line, listing));
                }
            }
            builder.onClick(e -> handleListingClick(e, listingIndex));

            setItem(slot, builder.build());
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

        // Expired toggle action yoksa → varsayılan davranış (sol tık → toggle)
        if (layout.expiredToggle() != null && slot == layout.expiredToggle().slot()
                && (nav == null || nav.leftClickAction() == null)) {
            open(currentPlayer, !showExpired);
            return;
        }
        // Buton tıklamalarındaki click handler MenuItem.onClick() ile çağrılıyor
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
                if (currentPage > 0) { currentPage--; open(currentPlayer, showExpired); }
                return true;
            }
            case "next-page" -> {
                var slots = layout.contentSlots();
                int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
                if (currentPage < maxPage) { currentPage++; open(currentPlayer, showExpired); }
                return true;
            }
            case "toggle-expired", "expired" -> {
                open(currentPlayer, !showExpired);
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * Sol tık → ilanı iptal et; Sağ tık → fiyatı güncelle.
     * Süresi Dolanlar görünümünde sol tık → onaylı yeniden listeleme.
     */
    private void handleListingClick(InventoryClickEvent event, int index) {
        if (index < 0 || index >= listings.size()) return;
        AuctionListing listing = listings.get(index);

        if (showExpired) {
            handleRenew(index);
            return;
        }

        if (event.isRightClick()) {
            // Fiyat güncelle — tabela ile
            close(currentPlayer);
            var plugin = (dev.ensisdev.lbauctionhouse.LbAuctionHouse) manager.getApi().getCore();
            SignInputGUI.create(plugin, currentPlayer)
                    .lines("", "~~~~~~~~~~~", "&#FFD54FYeni fiyatı yazın", "&#8c8c8c( sayı )")
                    .onComplete((p, text) -> {
                        try {
                            double np = Double.parseDouble(text.trim());
                            manager.updateListingPrice(p, listing.id(), np);
                        } catch (NumberFormatException ex) {
                            p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.listing.failed-number"));
                        }
                        open(p, showExpired);
                    })
                    .onClose(p -> open(p, showExpired))
                    .open();
            return;
        }

        handleCancel(index);
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
        open(currentPlayer, showExpired);
    }

    /**
     * Süresi dolan ilan için onaylı yeniden listeleme.
     */
    private void handleRenew(int index) {
        if (index < 0 || index >= listings.size()) return;
        AuctionListing listing = listings.get(index);

        close(currentPlayer);
        ConfirmationMenu.create("&#55FF55&lİlanı yeniden listele?")
                .onConfirm(p -> {
                    if (manager.renewListing(p, listing)) {
                        p.sendMessage(manager.getApi().getLanguageManager().getPrefixed(
                                "auction.listing.renewed",
                                "item", dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item())));
                    } else {
                        p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.listing.renew-failed"));
                    }
                    open(p, true);
                })
                .onCancel(p -> open(p, true))
                .open(currentPlayer);
    }

    private String formatLore(String template, AuctionListing listing) {
        long hours = listing.getTimeLeft() / 3600_000;
        long minutes = (listing.getTimeLeft() % 3600_000) / 60_000;
        return template
                .replace("%price%", String.format("%,.2f", listing.price()))
                .replace("%time_left%", hours + "s " + minutes + "d");
    }
}
