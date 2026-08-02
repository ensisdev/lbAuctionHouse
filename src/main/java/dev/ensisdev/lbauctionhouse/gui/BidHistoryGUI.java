package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionBid;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Teklif geçmişi GUI'si — bir BID ilanının tüm tekliflerini kronolojik gösterir.
 */
public class BidHistoryGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionData data;
    private final AuctionEconomy economy;
    private final AuctionListing listing;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");

    public BidHistoryGUI(AuctionManager manager, AuctionConfig config,
                         AuctionData data, AuctionEconomy economy,
                         AuctionListing listing) {
        super("bid_history_" + listing.id().toString().substring(0, 8), "&8Teklif Geçmişi", 4);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        this.listing = listing;
    }

    @Override
    protected void onOpen(Player player) {
        render();
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        if (event.getSlot() == 31) {
            close(player);
            manager.openMainMenu(player);
        }
    }

    private void render() {
        clear();

        // Bilgi paneli
        String itemName = dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());
        setItem(4, MenuItem.builder(listing.item().clone())
                .name("&6" + itemName)
                .lore("&7Fiyat: &e" + economy.format(listing.price()))
                .lore("&7Durum: " + (listing.sold() ? "&cSatıldı" : "&aAktif"))
                .build());

        // Back button
        setItem(31, MenuItem.builder(Material.ARROW)
                .name("&7← Geri")
                .build());

        // Border
        for (int i = 0; i < 9; i++) {
            setItem(i, MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name("&7").build());
        }
        for (int i = 27; i < 36; i++) {
            setItem(i, MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name("&7").build());
        }

        // Teklif listesi (en yeniden eskiye)
        List<AuctionBid> bids = data.getBids(listing.id());
        int slot = 9;
        if (bids.isEmpty()) {
            setItem(13, MenuItem.builder(Material.BARRIER)
                    .name("&cHenüz teklif yok")
                    .build());
            return;
        }

        // Ters sırala (en yeni üstte)
        for (int i = bids.size() - 1; i >= 0 && slot < 27; i--) {
            AuctionBid bid = bids.get(i);
            String date = sdf.format(new Date(bid.timestamp()));
            boolean isTop = (i == bids.size() - 1);
            String prefix = isTop ? "&a⬆ " : "&7  ";

            setItem(slot++, MenuItem.builder(Material.GOLD_NUGGET)
                    .name(prefix + "&f" + bid.bidderName())
                    .lore("&7Teklif: &e" + economy.format(bid.amount()))
                    .lore("&7Tarih: &7" + date)
                    .build());
        }
    }
}
