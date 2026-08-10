package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionBid;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
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
    private final CollectionEntry data;
    private final AuctionEconomy economy;
    private final AuctionListing listing;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");

    public BidHistoryGUI(AuctionManager manager, AuctionConfig config,
                         CollectionEntry data, AuctionEconomy economy,
                         AuctionListing listing) {
        super("bid_history_" + listing.id().toString().substring(0, 8), "&8&l» <gradient:#FFB74D:#FFD54F>"+ dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("TEKLİF GEÇMİŞİ")+"</gradient> &8&l«", 4);
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
                .name("&#F5F5F5" + itemName)
                .lore("&#8c8c8cFiyat: &#FFAA00" + economy.format(listing.price()))
                .lore("&#8c8c8cDurum: " + (listing.sold() ? "&#FF5555Satıldı" : "&#55FF55Aktif"))
                .build());

        // Back button
        setItem(31, MenuItem.builder(Material.ARROW)
                .name("&#FFD54F&l« &#F5F5F5&lɢᴇʀɪ")
                .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— ana menüye dön")
                .build());

        // Border
        for (int i = 0; i < 9; i++) {
            setItem(i, MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
        for (int i = 27; i < 36; i++) {
            setItem(i, MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        // Teklif listesi (en yeniden eskiye)
        List<AuctionBid> bids = data.getBids(listing.id());
        int slot = 9;
        if (bids.isEmpty()) {
            setItem(13, MenuItem.builder(Material.BARRIER)
                    .name("&#FF5555&lʜᴇɴᴜᴢ ᴛᴇᴋʟɪꜰ ʏᴏᴋ")
                    .build());
            return;
        }

        // Ters sırala (en yeni üstte)
        for (int i = bids.size() - 1; i >= 0 && slot < 27; i--) {
            AuctionBid bid = bids.get(i);
            String date = sdf.format(new Date(bid.timestamp()));
            boolean isTop = (i == bids.size() - 1);
            String prefix = isTop ? "&#55FF55⬆ " : "&#8c8c8c  ";

            setItem(slot++, MenuItem.builder(Material.GOLD_NUGGET)
                    .name(prefix + "&#F5F5F5" + bid.bidderName())
                    .lore("&#8c8c8cTeklif: &#FFAA00" + economy.format(bid.amount()))
                    .lore("&#8c8c8cTarih: &#F5F5F5" + date)
                    .build());
        }
    }
}
