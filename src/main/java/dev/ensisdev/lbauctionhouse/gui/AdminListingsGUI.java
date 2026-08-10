package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.ConfirmationMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.util.ItemNames;
import dev.ensisdev.lbauctionhouse.util.SmallCaps;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin ilan yönetimi GUI'si — tüm aktif ilanları listeler.
 * Sol tık → onaylı silme. Layout sabittir (admin paneline özel).
 */
public class AdminListingsGUI extends BaseMenu {

    private final AuctionManager manager;
    private final CollectionEntry data;

    private Player currentPlayer;
    private List<AuctionListing> listings;
    private int currentPage;

    public AdminListingsGUI(AuctionManager manager, CollectionEntry data) {
        super("admin_listings", "&8&l» <gradient:#2CCED2:#80DEEA>" + SmallCaps.toSmallCaps("İLAN YÖNETİMİ") + "</gradient> &8&l«", 6);
        this.manager = manager;
        this.data = data;
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.listings = new ArrayList<>(data.getActiveListings());
        this.currentPage = 0;
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        drawBorder();

        // İçerik
        var slots = contentSlots();
        int start = currentPage * slots.size();
        int end = Math.min(start + slots.size(), listings.size());

        for (int i = start; i < end; i++) {
            AuctionListing listing = listings.get(i);
            int slot = slots.get(i - start);
            int index = i;

            var builder = MenuItem.builder(listing.item().clone())
                    .name("&#F5F5F5" + ItemNames.displayName(listing.item()))
                    .lore("&#8c8c8cSatıcı: &#F5F5F5" + listing.sellerName())
                    .lore("&#8c8c8cFiyat: &#FFAA00" + String.format("%,.2f", listing.price()) + "₺")
                    .lore("&#8c8c8cKalan: &#FFD54F" + formatTimeLeft(listing.getTimeLeft()))
                    .lore("")
                    .lore("&#FF5555⚒ Sol Tık — sil (onay ister)")
                    .onClick(e -> handleClick(e, index));
            setItem(slot, builder.build());
        }
    }

    private void handleClick(InventoryClickEvent event, int index) {
        if (index < 0 || index >= listings.size()) return;
        AuctionListing listing = listings.get(index);

        close(currentPlayer);
        ConfirmationMenu.create("&#FF5555&lİlanı sil?")
                .onConfirm(p -> {
                    if (manager.removeListing(listing.id())) {
                        p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("admin.removed"));
                    } else {
                        p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("admin.not-found"));
                    }
                    open(p);
                })
                .onCancel(p -> open(p))
                .open(currentPlayer);
    }

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        int slot = event.getSlot();
        if (slot == 45) { // previous
            if (currentPage > 0) { currentPage--; open(currentPlayer); }
        } else if (slot == 53) { // next
            var slots = contentSlots();
            int maxPage = (int) Math.ceil((double) listings.size() / slots.size()) - 1;
            if (currentPage < maxPage) { currentPage++; open(currentPlayer); }
        } else if (slot == 49) { // back → admin panel
            close(currentPlayer);
            manager.openAdminGUI(currentPlayer);
        }
    }

    private List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 9; i <= 44; i++) slots.add(i);
        return slots;
    }

    private void drawBorder() {
        for (int slot : List.of(45, 46, 47, 48, 49, 50, 51, 52, 53)) {
            setItem(slot, MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
        setItem(45, MenuItem.builder(Material.ARROW)
                .name("&#FFD54F&l« &#FFB74D&lᴏɴᴄᴇᴋɪ ꜱᴀʏꜰᴀ")
                .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— önceki sayfayı aç")
                .build());
        setItem(49, MenuItem.builder(Material.BARRIER)
                .name("&#FF5555&lɢᴇʀɪ")
                .lore("&#8c8c8c• &#FF5555Tıkla &#F5F5F5— admin paneline dön")
                .build());
        setItem(53, MenuItem.builder(Material.ARROW)
                .name("&#FFB74D&lꜱᴏɴʀᴀᴋɪ ꜱᴀʏꜰᴀ &#FFD54F&l»")
                .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— sonraki sayfayı aç")
                .build());
    }

    private String formatTimeLeft(long ms) {
        long hours = ms / 3600_000;
        long minutes = (ms % 3600_000) / 60_000;
        return hours + "s " + minutes + "d";
    }

    @Override
    protected void onClose(Player player) {}
}
