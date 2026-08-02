package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.util.BundleItems;
import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.core.gui.SignInputGUI;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ana ihale GUI'si — sayfalanabilir liste, sıralama, arama.
 * <p>
 * Layout tamamen {@code gui/main-menu.yml} dosyasından okunur.
 */
public class MainMenuGUI extends BaseMenu {

    private final LbAuctionHouse addon;
    private final LbAuctionHouse corePlugin;
    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionData data;
    private final AuctionEconomy economy;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private PageData pageData;
    private int currentPage;
    private String currentSearch;
    private int currentSortOption;
    private int currentCategoryIndex = -1; // -1 = all

    /** Sayfalı veri taşıyıcı — hem liste hem toplam sayıyı tutar. */
    private record PageData(List<AuctionListing> listings, int totalCount) {}


    public MainMenuGUI(LbAuctionHouse addon, LbAuctionHouse corePlugin, AuctionManager manager, AuctionConfig config,
                       AuctionData data, AuctionEconomy economy,
                       GUILayoutLoader loader) {
        super("auction_main", "&8İhale", 6);
        this.addon = addon;
        this.corePlugin = corePlugin;
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        this.layout = loader.load("main-menu.yml");
    }

    @Override
    protected void onOpen(Player player) {
        this.currentPlayer = player;
        this.currentPage = 0;
        this.pageData = fetchPage(0);
        renderPage();
    }

    @Override
    public void open(Player player) {
        // Taze açılışta arama/filtre sıfırlanır (paylaşılan GUI örneği çok oyunculu kullanılır).
        this.currentSearch = null;
        this.currentCategoryIndex = -1;
        this.currentSortOption = 0;
        applyTitle();
        super.open(player);
    }

    /**
     * Ana menüyü belirli bir arama sorgusuyla açar (örn. /ihale ara elmas).
     * Başlık: {@code "«arama»" İçin «sonuç» Sonuç Bulundu}
     */
    public void openWithSearch(Player player, String query) {
        this.currentSearch = query;
        this.currentCategoryIndex = -1;
        this.currentSortOption = 0;
        this.currentPage = 0;
        this.pageData = fetchPage(0);
        applyTitle();
        super.open(player);
    }

    /** Aramaya göre GUI başlığını belirler (& renk kodu destekli). */
    private void applyTitle() {
        if (currentSearch != null && !currentSearch.isEmpty()) {
            int count = pageData != null ? pageData.totalCount() : 0;
            setDynamicTitle("&8\"" + currentSearch + "\" &7İçin &e" + count + " &7Sonuç Bulundu");
        } else {
            setDynamicTitle(layout.title());
        }
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Navigation
        if (slot == 45) { // previous page
            if (currentPage > 0) { currentPage--; pageData = fetchPage(currentPage); renderPage(); }
            return;
        }
        if (slot == 53) { // next page
            int maxPage = getMaxPage();
            if (currentPage < maxPage) { currentPage++; pageData = fetchPage(currentPage); renderPage(); }
            return;
        }
        if (slot == 49) { // close
            close(player);
            return;
        }
        if (slot == 46) { // my listings
            close(player);
            manager.openMyListings(player);
            return;
        }
        if (slot == 52) { // collection box
            close(player);
            manager.openCollectionBox(player);
            return;
        }
        if (slot == 48 && config.isSearchEnabled()) {
            if (currentSearch != null && !currentSearch.isEmpty()) {
                // Sağ tık: aramayı temizle ve varsayılan başlıkla yeniden aç (1 tick sonra)
                close(player);
                org.bukkit.Bukkit.getScheduler().runTaskLater(corePlugin, () -> open(player), 1L);
                return;
            }
            // Sol tık: SignGUI ile ara
            close(player);
            SignInputGUI.create(corePlugin, player)
                    .lines("", "~~~~~~~~~~~", "&8Aranacak eşya", "adını yazın")
                    .onComplete((p, text) -> openWithSearch(p, text))
                    .onClose(p -> manager.openMainMenu(p))
                    .open();
            return;
        }
        if (slot == 47) { // category filter
            var cats = config.getCategories();
            currentCategoryIndex = (currentCategoryIndex + 1) % (cats.size() + 1);
            currentCategoryIndex--; // cycle: -1 (all), 0, 1, 2, ...
            if (currentCategoryIndex >= cats.size()) currentCategoryIndex = -1;
            currentPage = 0;
            pageData = fetchPage(0);
            renderPage();
            return;
        }

        if (slot == 50 && config.isSortEnabled()) { // sort
            currentSortOption = (currentSortOption + 1) % config.getSortOptions().size();
            currentPage = 0;
            pageData = fetchPage(0);
            renderPage();
            return;
        }

        // Content slot — listing click
        List<AuctionListing> currentListings = pageData != null ? pageData.listings() : List.of();
        int contentIndex = slot - layout.contentSlots().get(0);
        int realIndex = currentPage * layout.contentSlots().size() + contentIndex;
        if (realIndex >= 0 && realIndex < currentListings.size()) {
            AuctionListing listing = currentListings.get(realIndex);
            if (listing.sold()) return;
            // BID listing: teklif geçmişini görmek için sağ tık
            if (listing.isBid() && event.isRightClick()) {
                close(player);
                new BidHistoryGUI(manager, config, data, economy, listing).open(player);
                return;
            }
            // Sağ tık → fıçı İÇERİĞİ / shulker ÖNİZLEME / normal ilan BİLGİSİ
            if (event.isRightClick()) {
                close(player);
                ItemStack listingItem = listing.item();
                if (BundleItems.isBundle(listingItem)) {
                    new BundleViewGUI().open(player, listingItem);
                } else if (listingItem.getType().name().startsWith("SHULKER")) {
                    new ShulkerViewGUI(listingItem, p -> manager.openMainMenu(p)).open(player);
                } else {
                    new ItemInfoGUI(addon, manager, config, data, economy).open(player, listing);
                }
                return;
            }
            // Sol tık → satın alma onayı
            close(player);
            if (config.isConfirmOnBuy()) {
                manager.openConfirmBuy(player, listing);
            } else {
                manager.buyItem(player, listing);
            }
        }
    }

    private void renderPage() {
        clear();
        List<AuctionListing> currentListings = pageData != null ? pageData.listings() : List.of();

        // Border
        if (layout.border() != null) {
            for (int slot : layout.border().slots()) {
                setItem(slot, MenuItem.builder(layout.border().material())
                        .name(layout.border().name()).build());
            }
        }

        // Navigation items
        for (var nav : layout.navItems()) {
            var builder = MenuItem.builder(nav.material()).name(nav.name());
            for (String line : nav.lore()) builder.lore(line);
            setItem(nav.slot(), builder.build());
        }

        // Search button
        if (layout.search() != null && config.isSearchEnabled()) {
            var s = layout.search();
            setItem(s.slot(), MenuItem.builder(s.material()).name(s.name()).build());
        }

        // Sort button
        if (layout.sort() != null && config.isSortEnabled()) {
            var s = layout.sort();
            String sortName = currentSortOption < s.options().size()
                    ? s.options().get(currentSortOption) : s.name();
            setItem(s.slot(), MenuItem.builder(s.material()).name(sortName).build());
        }

        // Category button (slot 47)
        var cats = config.getCategories();
        String catName = currentCategoryIndex >= 0 && currentCategoryIndex < cats.size()
                ? cats.get(currentCategoryIndex).name() : "&7Tümü";
        setItem(47, MenuItem.builder(org.bukkit.Material.HOPPER)
                .name("&aKategori: " + catName)
                .lore("&7Tıkla — kategori değiştir")
                .build());

        // Content slots
        List<Integer> contentSlots = layout.contentSlots();
        for (int i = 0; i < currentListings.size(); i++) {
            int slot = contentSlots.get(i);
            setItem(slot, listingToMenuItem(currentListings.get(i)));
        }

        // Açık envanteri güncelle (sayfa değişimleri anında görünsün)
        if (currentPlayer != null) {
            refresh(currentPlayer);
        }
    }

    /** Toplam sayfa sayısını döndürür. */
    private int getMaxPage() {
        if (pageData == null || layout.contentSlots().isEmpty()) return 0;
        return Math.max(0, (int) Math.ceil((double) pageData.totalCount() / layout.contentSlots().size()) - 1);
    }

    /**
     * Sadece görünen sayfadaki ilanları DB'den çeker.
     * Kategori filtresi varsa önce çekip Java'da filtreler.
     */
    private PageData fetchPage(int page) {
        int pageSize = layout.contentSlots().size();
        boolean hasCategoryFilter = currentCategoryIndex >= 0
                && currentCategoryIndex < config.getCategories().size();
        String searchQuery = currentSearch;

        // Kategori filtresi: önce çek, Java'da filtrele, sayfala
        if (hasCategoryFilter) {
            var category = config.getCategories().get(currentCategoryIndex);
            var catMats = java.util.List.copyOf(category.materials());
            if (!catMats.isEmpty()) {
                var srcList = (searchQuery != null && !searchQuery.isEmpty())
                        ? data.searchListings(searchQuery)
                        : data.getActiveListings();
                var filtered = srcList.stream()
                        .filter(l -> catMats.contains(l.item().getType()))
                        .toList();
                int total = filtered.size();
                int from = page * pageSize;
                int to = Math.min(from + pageSize, filtered.size());
                if (from >= filtered.size()) return new PageData(List.of(), total);
                return new PageData(filtered.subList(from, to), total);
            }
        }

        // Kategori yok veya tümü: sayfalı SQL
        int offset = page * pageSize;
        int totalCount;
        List<AuctionListing> listings;

        if (searchQuery != null && !searchQuery.isEmpty()) {
            totalCount = data.getActiveListingsCount(searchQuery);
            listings = data.searchListingsPage(searchQuery, pageSize, offset);
        } else {
            totalCount = data.getActiveListingsCount();
            listings = data.getActiveListingsPage(pageSize, offset);
        }

        // Yeni sıralama seçenekleri için Java'da sırala (custom sort indices)
        if (currentSortOption >= 6) {
            listings = new ArrayList<>(listings);
            switch (currentSortOption) {
                case 6 -> listings.sort(Comparator.comparingLong(AuctionListing::listedAt));
                case 7 -> listings.sort(Comparator.comparing(AuctionListing::sellerName));
                case 8 -> listings.sort(Comparator.comparing(AuctionListing::sellerName).reversed());
                case 9 -> listings.sort(Comparator.comparing((AuctionListing l) -> l.item().getType().name()));
                case 10 -> listings.sort(Comparator.comparing((AuctionListing l) -> l.item().getType().name()).reversed());
            }
        }

        return new PageData(listings, totalCount);
    }

    private MenuItem listingToMenuItem(AuctionListing listing) {
        var clone = listing.item().clone();
        var builder = MenuItem.builder(clone);
        String displayName = listing.item().getItemMeta().hasDisplayName()
                ? listing.item().getItemMeta().getDisplayName()
                : "&f" + listing.item().getType().name();

        // Flash sale badge
        if (listing.isFlashSale()) {
            displayName = "&e⚡ " + displayName + " &6&l-" + (int) listing.getDiscountPercent() + "%";
            // Glow effect: add enchantment with hide flag
            var meta = clone.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                clone.setItemMeta(meta);
            }
        } else if (listing.isFlashSaleExpired()) {
            displayName = "&7⏳ " + displayName;
        }

        builder.name(displayName);

        for (String line : layout.loreFormat()) {
            builder.lore(formatLore(line, listing));
        }

        // Item meta gösterimi (enchantment, lore, hasar)
        var meta = clone.getItemMeta();
        if (meta != null) {
            // Enchantment'lar
            if (meta.hasEnchants()) {
                for (var entry : meta.getEnchants().entrySet()) {
                    String enchName = entry.getKey().getKey().getKey();
                    builder.lore("&7" + enchName + " " + toRoman(entry.getValue()));
                }
            }
            // Item lore
            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    builder.lore("&7" + line);
                }
            }
            // Hasar (durability) — sadece hasar alabilen item'lar için
            if (clone.getType().getMaxDurability() > 0 && meta instanceof org.bukkit.inventory.meta.Damageable dmg) {
                int maxDura = clone.getType().getMaxDurability();
                int currentDura = maxDura - dmg.getDamage();
                builder.lore("&7Hasar: &f" + currentDura + "/" + maxDura);
            }
        }

        return builder.build();
    }

    private String formatLore(String template, AuctionListing listing) {
        return template
                .replace("%seller%", listing.sellerName())
                .replace("%price%", economy.format(listing.price()))
                .replace("%time_left%", formatTimeLeft(listing.getTimeLeft()))
                .replace("%amount%", String.valueOf(listing.item().getAmount()));
    }

    private String formatTimeLeft(long ms) {
        long hours = ms / 3600_000;
        long minutes = (ms % 3600_000) / 60_000;
        return hours + "s " + minutes + "d";
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV";
            case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII";
            case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

}
