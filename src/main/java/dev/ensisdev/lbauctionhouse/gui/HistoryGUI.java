package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionLog;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.util.ItemNames;
import dev.ensisdev.lbauctionhouse.util.SmallCaps;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Kişisel işlem geçmişi GUI'si — oyuncunun satış ve satın alımlarını gösterir.
 * <p>
 * Filtrenin slot değeri {@code history.yml → navigation.filter} içinden okunur.
 * Sol/sağ tık ayrımı navigation item'ın {@code left-click} ve {@code right-click}
 * değerlerinden gelir (varsayılan: sol tık → filtre geçişi).
 */
public class HistoryGUI extends BaseMenu {

    private enum Filter { ALL, SELL, PURCHASE }

    /** Filter butonunun slot değerini yml'den okur; yoksa 47 varsayılır. */
    private int filterSlot() {
        if (layout.navItems() != null) {
            for (var n : layout.navItems()) {
                if ("filter".equalsIgnoreCase(n.id())) return n.slot();
            }
        }
        return 47;
    }

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final CollectionEntry data;
    private final AuctionEconomy economy;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private List<AuctionLog> allLogs;
    private Filter filter = Filter.ALL;
    private int currentPage;

    public HistoryGUI(AuctionManager manager, AuctionConfig config, CollectionEntry data,
                      AuctionEconomy economy, GUILayoutLoader loader) {
        super("auction_history", "&8&l» <gradient:#FFB74D:#FFD54F>" + SmallCaps.toSmallCaps("İŞLEM GEÇMİŞİ") + "</gradient> &8&l«", 6);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        this.layout = loader.load("history.yml");
        if (layout != null && layout.title() != null && !layout.title().isEmpty()) {
            setDynamicTitle(layout.title());
        }
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.allLogs = data.getLogsByPlayer(player.getUniqueId().toString(), Math.max(1, config.getMaxHistoryLogs()));
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

        // Filter button (slot yml'den) — filter id'li navigation kullanılır, lore dinamik eklenir
        applyFilterButton();

        // Content
        List<AuctionLog> visible = filterLogs();
        if (visible.isEmpty()) {
            player.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.history.empty"));
            close(player);
            manager.openMainMenu(player);
            return;
        }

        var slots = layout.contentSlots();
        int start = currentPage * slots.size();
        int end = Math.min(start + slots.size(), visible.size());

        for (int i = start; i < end; i++) {
            AuctionLog log = visible.get(i);
            setItem(slots.get(i - start), buildLogItem(log));
        }

        // Arka plan dolgusu
        applyBackgroundFill(layout.backgroundFill());
    }

    /** Filter navigation item'ını dinamik lore ile birlikte uygular. */
    private void applyFilterButton() {
        if (layout.navItems() == null) return;
        for (var nav : layout.navItems()) {
            if (!"filter".equalsIgnoreCase(nav.id())) continue;
            var b = navBuilder(nav);
            for (String line : filterLoreLines()) b.lore(line);
            setItem(nav.slot(), b.build());
        }
    }

    private List<String> filterLoreLines() {
        String chosen = switch (filter) {
            case ALL -> "&#F5F5F5&lHepsi";
            case SELL -> "&#FFD54F&lSatışlarım";
            case PURCHASE -> "&#2CCED2&lAlımlarım";
        };
        List<String> lore = new ArrayList<>();
        lore.add("&#8c8c8c•  &#FFD54Fꜰɪʟᴛʀᴇ  &#8c8c8c— " + chosen);
        lore.add("");
        lore.add("&#8c8c8c•  &#2CCED2Tıkla  &#8c8c8c— sonraki filtreye geç");
        return lore;
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

        // Filter slot'u üzerinde hiçbir action yoksa (varsayılan: sol tık filtre geçişi)
        if (slot == filterSlot() && (nav == null || (nav.leftClickAction() == null && nav.rightClickAction() == null))) {
            cycleFilter();
            currentPage = 0;
            open(currentPlayer);
        }
    }

    private void cycleFilter() {
        filter = switch (filter) {
            case ALL -> Filter.SELL;
            case SELL -> Filter.PURCHASE;
            case PURCHASE -> Filter.ALL;
        };
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
                int maxPage = (int) Math.ceil((double) filterLogs().size() / slots.size()) - 1;
                if (currentPage < maxPage) { currentPage++; open(currentPlayer); }
                return true;
            }
            case "cycle-filter", "filter" -> {
                cycleFilter();
                currentPage = 0;
                open(currentPlayer);
                return true;
            }
            default -> { return false; }
        }
    }

    private List<AuctionLog> filterLogs() {
        List<AuctionLog> result = new ArrayList<>();
        for (AuctionLog log : allLogs) {
            boolean matches = switch (filter) {
                case ALL -> true;
                case SELL -> log.action().equals(AuctionLog.Action.SELL.name());
                case PURCHASE -> log.action().equals(AuctionLog.Action.PURCHASE.name());
            };
            if (matches) result.add(log);
        }
        return result;
    }

    private MenuItem buildLogItem(AuctionLog log) {
        ItemStack display = log.item() != null ? log.item().clone() : new ItemStack(Material.STONE);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        var builder = MenuItem.builder(display)
                .name("&#F5F5F5" + ItemNames.displayName(display))
                .lore(actionLabel(log.action()))
                .lore("&#8c8c8c•  &#FFD54Fꜰɪʏᴀᴛ  &#8c8c8c— &#FFAA00" + economy.format(log.price()))
                .lore("&#8c8c8c•  &#FFD54Fᴠᴇʀɢɪ  &#8c8c8c— &#FFAA00" + (log.tax() > 0 ? economy.format(log.tax()) : "0"))
                .lore("&#8c8c8c•  &#FFD54Fᴛᴀʀɪʜ  &#8c8c8c— &#F5F5F5" + sdf.format(new Date(log.timestamp())));

        if (log.action().equals(AuctionLog.Action.SELL.name())) {
            builder.lore("&#8c8c8c•  &#FFD54Fᴀʟɪᴄɪ  &#8c8c8c— &#F5F5F5" + (log.buyerName() != null ? log.buyerName() : "—"));
        } else if (log.action().equals(AuctionLog.Action.PURCHASE.name())) {
            builder.lore("&#8c8c8c•  &#FFD54Fꜱᴀᴛɪᴄɪ  &#8c8c8c— &#F5F5F5" + (log.sellerName() != null ? log.sellerName() : "—"));
        }
        return builder.build();
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "SELL" -> "&#55FF55&l✔ ꜱᴀᴛɪꜱ";
            case "PURCHASE" -> "&#FFD54F&l🛒 ꜱᴀᴛɪɴ ᴀʟᴍᴀ";
            case "CANCEL" -> "&#FF5555&l✖ ɪᴘᴛᴀʟ";
            case "EXPIRED" -> "&#F5F5F5&l⏰ ꜱᴜʀᴇꜱɪ ᴅᴏʟᴅᴜ";
            case "ADMIN_REMOVE" -> "&#FF5555&l⚒ ᴀᴅᴍɪɴ ᴋᴀʟᴅɪʀᴅɪ";
            default -> "&#8c8c8c" + action;
        };
    }
}
