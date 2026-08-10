package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.util.SmallCaps;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Yönetim paneli GUI'si — /ihaleadmin ile açılır.
 * <p>
 * Ana görünüm: istatistikler, KDV/vergi raporu, ilan yönetimi, yasak yönetimi.
 * Vergi raporu aynı GUI içinde ayrı bir görünüm (View.TAX) olarak açılır.
 */
public class AdminGUI extends BaseMenu {

    private enum View { HOME, TAX }

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final CollectionEntry data;
    private final AuctionEconomy economy;

    private Player currentPlayer;
    private View view = View.HOME;

    public AdminGUI(AuctionManager manager, AuctionConfig config, CollectionEntry data, AuctionEconomy economy) {
        super("admin_panel", "&8&l» <gradient:#FF5555:#FF8A80>" + SmallCaps.toSmallCaps("YÖNETİM PANELİ") + "</gradient> &8&l«", 6);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.view = View.HOME;
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        if (view == View.HOME) {
            renderHome();
        } else {
            renderTax();
        }
    }

    private void renderHome() {
        setDynamicTitle("&8&l» <gradient:#FF5555:#FF8A80>" + SmallCaps.toSmallCaps("YÖNETİM PANELİ") + "</gradient> &8&l«");
        drawBorder();

        var stats = data.getStats();
        var statsBuilder = MenuItem.builder(Material.BOOK)
                .name("&#FFD54F&l📊 ɪꜱᴛᴀᴛɪꜱᴛɪᴋʟᴇʀ")
                .lore("&#8c8c8cToplam Satış: &#F5F5F5" + stats.totalSales())
                .lore("&#8c8c8cToplam Gelir: &#FFAA00" + format(stats.totalRevenue()))
                .lore("&#8c8c8cToplam Vergi: &#FFAA00" + format(stats.totalTax()))
                .lore("&#8c8c8cToplam İlan: &#F5F5F5" + stats.totalListings())
                .lore("&#8c8c8cAktif İlan: &#F5F5F5" + stats.activeListings())
                .lore("&#8c8c8cToplam Teklif: &#F5F5F5" + stats.totalBids());
        setItem(10, statsBuilder.build());

        setItem(12, MenuItem.builder(Material.GOLD_INGOT)
                .name("&#FFAA00&l💰 ᴋᴅᴠ / ᴠᴇʀɢɪ ʀᴀᴘᴏʀᴜ")
                .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— günlük vergi dökümü")
                .build());

        setItem(14, MenuItem.builder(Material.CHEST)
                .name("&#2CCED2&l📦 ɪʟᴀɴ ʏᴏɴᴇᴛɪᴍɪ")
                .lore("&#8c8c8c• &#2CCED2Tıkla &#F5F5F5— aktif ilanları yönet")
                .lore("&#8c8c8c(Sol tık: sil)")
                .build());

        setItem(16, MenuItem.builder(Material.REDSTONE_BLOCK)
                .name("&#FF5555&l⛔ ʏᴀꜱᴀᴋ ʏᴏɴᴇᴛɪᴍɪ")
                .lore("&#8c8c8c• &#FF5555Tıkla &#F5F5F5— yasaklı oyuncuları yönet")
                .lore("&#8c8c8c(Sol tık: affet)")
                .build());

        setItem(22, MenuItem.builder(Material.BARRIER)
                .name("&#FF5555&lᴋᴀᴘᴀᴛ")
                .lore("&#8c8c8c• &#FF5555Tıkla &#F5F5F5— paneli kapat")
                .build());

        setItem(31, MenuItem.builder(Material.OAK_DOOR)
                .name("&#55FF55&lɢᴇʀɪ")
                .lore("&#8c8c8c• &#55FF55Tıkla &#F5F5F5— ana menüye dön")
                .build());
    }

    private void renderTax() {
        setDynamicTitle("&8&l» <gradient:#FFD54F:#FFB74D>" + SmallCaps.toSmallCaps("KDV / VERGİ RAPORU") + "</gradient> &8&l«");
        drawBorder();

        var stats = data.getStats();
        var totalTax = stats.totalTax();
        var list = data.getDailyTax(14);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM");

        // Toplam özet
        setItem(9, MenuItem.builder(Material.SUNFLOWER)
                .name("&#FFD54F&lᴛᴏᴘʟᴀᴍ ᴠᴇʀɢɪ: &#FFAA00" + format(totalTax))
                .lore("&#8c8c8cToplam Satış: &#F5F5F5" + stats.totalSales())
                .lore("&#8c8c8cToplam Gelir: &#FFAA00" + format(stats.totalRevenue()))
                .build());

        // Son 14 günün günlük dökümü
        int slot = 18;
        for (int i = list.size() - 1; i >= 0 && slot <= 26; i--, slot++) {
            var day = list.get(i);
            String date = sdf.format(new Date(day.dayStart()));
            var b = MenuItem.builder(day.tax() > 0 ? Material.GOLD_NUGGET : Material.GRAY_DYE)
                    .name("&#F5F5F5" + date)
                    .lore("&#8c8c8cSatış: &#F5F5F5" + day.sales())
                    .lore("&#8c8c8cVergi: &#FFAA00" + format(day.tax()));
            setItem(slot, b.build());
        }

        setItem(31, MenuItem.builder(Material.OAK_DOOR)
                .name("&#55FF55&lɢᴇʀɪ")
                .lore("&#8c8c8c• &#55FF55Tıkla &#F5F5F5— panele dön")
                .build());
    }

    private void drawBorder() {
        for (int slot : List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53)) {
            setItem(slot, MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
    }

    private String format(double v) {
        return String.format("%,.2f", v) + "₺";
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        int slot = event.getSlot();

        if (view == View.HOME) {
            if (slot == 12) { // vergi raporu
                view = View.TAX;
                open(currentPlayer);
            } else if (slot == 14) { // ilan yönetimi
                close(currentPlayer);
                new AdminListingsGUI(manager, data).open(currentPlayer);
            } else if (slot == 16) { // yasak yönetimi
                close(currentPlayer);
                new AdminBansGUI(manager, data).open(currentPlayer);
            } else if (slot == 22) { // kapat
                close(currentPlayer);
            } else if (slot == 31) { // geri → ana menü
                close(currentPlayer);
                manager.openMainMenu(currentPlayer);
            }
            return;
        }

        // TAX görünümü
        if (slot == 31) {
            view = View.HOME;
            open(currentPlayer);
        }
    }
}
