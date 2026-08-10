package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.util.BundleItems;
import dev.ensisdev.lbauctionhouse.util.ItemNames;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.config.FeatureRegistry;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.core.gui.SignInputGUI;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final CollectionEntry data;
    private final AuctionEconomy economy;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private PageData pageData;
    private int currentPage;
    private String currentSearch;
    /**
     * Tek huni (sıralama) durumu: 0..N-1 = sıralama seçenekleri, N = Favoriler, N+1 = Teklifli.
     * N = config'deki sıralama seçeneği sayısı.
     */
    private int filterMode = 0;
    /** Async sayfa çekişlerinde eski isteklerin render edilmemesi için sayaç. */
    private volatile long pageToken = 0;

    /** Sayfalı veri taşıyıcı — hem liste hem toplam sayıyı tutar. */
    private record PageData(List<AuctionListing> listings, int totalCount) {}


    public MainMenuGUI(LbAuctionHouse addon, LbAuctionHouse corePlugin, AuctionManager manager, AuctionConfig config,
                       CollectionEntry data, AuctionEconomy economy,
                       GUILayoutLoader loader) {
        super("auction_main", "&8&l» <gradient:#FFB74D:#FFD54F>ɪʜᴀʟᴇ</gradient> &8&l«", 6);
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
        // Önce şu anki durumla (veya boş) çiz, ardından veriyi ASYNC çekip yeniden çiz.
        renderPage();
        schedulePage(currentPage);
    }

    @Override
    public void open(Player player) {
        // Taze açılışta arama/filtre sıfırlanır (paylaşılan GUI örneği çok oyunculu kullanılır).
        this.currentSearch = null;
        this.filterMode = 0;
        this.currentPage = 0;
        this.pageData = null;
        applyTitle();
        super.open(player);
    }

    /**
     * Ana menüyü belirli bir arama sorgusuyla açar (örn. /ihale ara elmas).
     * Başlık: {@code "«arama»" İçin «sonuç» Sonuç Bulundu}
     */
    public void openWithSearch(Player player, String query) {
        this.currentSearch = query;
        this.filterMode = 0;
        this.currentPage = 0;
        this.pageData = null;
        super.open(player);
    }

    /**
     * Deşarj: Veriyi ASYNC çekip, tamamlanınca main thread'de çizer.
     * Eski istekler (hızlı sayfa değişiminde) yok sayılır.
     */
    // ---- Tek huni yönetimi (sıralama + favoriler + teklifli) ----

    /** Config'deki sıralama seçeneği sayısı (N). */
    private int sortCount() {
        return config.getSortOptions().size();
    }

    /** Toplam huni modu sayısı: N sıralama + (favori özelliği açıksa) Favoriler + (pazarlık açıksa) Teklifli. */
    private int modeCount() {
        int n = sortCount();
        int extra = 0;
        if (config.isFeatureEnabled(FeatureRegistry.Keys.FAVORITES)) extra++;
        if (config.isNegotiationEnabled()) extra++;
        return n + extra;
    }

    /** Geçerli modun gösterim adı. */
    private String modeLabel() {
        int N = sortCount();
        if (filterMode < N) return config.getSortOptions().get(filterMode);
        int offset = 0;
        if (config.isFeatureEnabled(FeatureRegistry.Keys.FAVORITES)) {
            if (filterMode == N + offset) return dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("Favorilerim");
            offset++;
        }
        return dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("Teklifli");
    }

    /** Sıralama/filtre modlarını sırayla değiştirir (tek huniyle döner). */
    private void cycleMode() {
        int total = modeCount();
        if (total <= 0) return;
        filterMode = (filterMode + 1) % total;
    }

    /** Tek huni butonunu çizer: ad + lore'da tüm modlar, aktif olan vurgulu. */
    private void buildSortButton() {
        if (layout.sort() == null || !config.isSortEnabled()) return;
        var s = layout.sort();
        String name = modeLabel();
        var builder = MenuItem.Builder.of(s.material(), s.texture()).name("&#2CCED2&l" + name + " &#8c8c8c( Tıkla )");
        // Yapılandırılabilir görsel alanlar
        if (s.amount() > 1) builder.amount(s.amount());
        if (s.customModelData() != 0) builder.customModelData(s.customModelData());
        if (s.glow()) builder.glow(true);
        if (s.hideFlags()) builder.hideFlags(true);
        int N = sortCount();
        for (int i = 0; i < N; i++) {
            boolean active = filterMode == i;
            builder.lore((active ? "&#2CCED2&l• " : "&#8c8c8c• ") + config.getSortOptions().get(i));
        }
        int offset = 0;
        if (config.isFeatureEnabled(FeatureRegistry.Keys.FAVORITES)) {
            boolean favActive = filterMode == N + offset;
            builder.lore((favActive ? "&#FF5555&l• " : "&#8c8c8c• ") + (favActive ? "&#FF5555&l" : "&#F5F5F5") + dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("Favorilerim"));
            offset++;
        }
        if (config.isNegotiationEnabled()) {
            boolean offActive = filterMode == N + offset;
            builder.lore((offActive ? "&#2CCED2&l• " : "&#8c8c8c• ") + (offActive ? "&#2CCED2&l" : "&#F5F5F5") + "💬 " + dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("Teklifli"));
        }
        builder.lore("&#8c8c8c— Tıkla — sırala/filtrele");
        setItem(s.slot(), builder.build());
    }

    private void schedulePage(int page) {
        final long token = ++pageToken;
        final Player viewer = currentPlayer;
        corePlugin.getScheduler().runTaskAsynchronously(() -> {
            PageData pd = fetchPage(page);
            // GUI güncellemeleri oyuncuya aittir — Folia'da oyuncunun region'ında koşar.
            if (viewer != null && viewer.isOnline()) {
                corePlugin.getScheduler().runTaskForPlayer(viewer, () -> {
                    if (token != pageToken) return; // eski istek — atla
                    this.currentPage = page;
                    this.pageData = pd;
                    applyTitle();
                    updateOpenTitle();
                    renderPage();
                });
            }
        });
    }

    /** Aramaya göre GUI başlığı metnini döndürür (& kodu destekli). */
    private String resolveTitle() {
        if (currentSearch != null && !currentSearch.isEmpty()) {
            int count = pageData != null ? pageData.totalCount() : 0;
            return "&8\"" + currentSearch + "\" &#8c8c8cİçin &#FFD54F&l" + count + " &#8c8c8cSonuç Bulundu";
        }
        return layout.title();
    }

    /** Açık envanterin başlığını da güncelle (arama sonucu canlı kalsın). */
    private void updateOpenTitle() {
        if (currentPlayer == null) return;
        try {
            currentPlayer.getOpenInventory().setTitle(
                resolveTitle().replace('&', net.md_5.bungee.api.ChatColor.COLOR_CHAR));
        } catch (Exception ignored) {}
    }

    /** Aramaya göre GUI başlığını belirler (& kodu destekli). */
    private void applyTitle() {
        setDynamicTitle(resolveTitle());
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
            if (handleNavAction(player, nav, action, right)) return;
        }

        // Tek huni: sıralama + Favorilerim + Teklifli filtreleri
        if (layout.sort() != null && slot == layout.sort().slot() && config.isSortEnabled()) {
            cycleMode();
            currentPage = 0;
            schedulePage(0);
            return;
        }

        // Content slot — listing click
        List<AuctionListing> currentListings = pageData != null ? pageData.listings() : List.of();
        int contentIndex = slot - layout.contentSlots().get(0);
        int realIndex = currentPage * layout.contentSlots().size() + contentIndex;
        if (realIndex >= 0 && realIndex < currentListings.size()) {
            AuctionListing listing = currentListings.get(realIndex);
            if (listing.sold()) return;
            // Shift + Sağ Tık → favoriye ekle/çıkar
            if (event.isShiftClick() && event.isRightClick()) {
                toggleFavorite(player, listing);
                return;
            }
            // BID listing: teklif geçmişini görmek için sağ tık
            if (listing.isBid() && event.isRightClick()) {
                close(player);
                new BidHistoryGUI(manager, config, data, economy, listing).open(player);
                return;
            }
            // Shift + Sol Tık → pazarlık teklifi (yalnızca teklif açık ilanlar)
            if (event.isShiftClick() && !event.isRightClick()
                    && listing.offersEnabled() && config.isNegotiationEnabled()) {
                openOfferPrompt(player, listing);
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

    /** Slot'a karşılık gelen navigation item'ı döndürür. */
    private GUILayoutLoader.NavItem findNavBySlot(int slot) {
        if (layout.navItems() == null) return null;
        for (var n : layout.navItems()) {
            if (n.slot() == slot) return n;
        }
        return null;
    }

    /**
     * Navigation item aksiyonunu işler. {@code null} veya tanınmayan aksiyonlarda
     * false döner (alt menü aksiyonlarına dokunulmamalı).
     */
    private boolean handleNavAction(Player player, GUILayoutLoader.NavItem nav, String action, boolean right) {
        if (action == null || action.isEmpty()) return false;
        // Hem tire hem alt çizgi kabul eder (yaml-stili "previous_page" ve menü-stili "previous-page")
        String norm = action.trim().toLowerCase().replace('_', '-');
        switch (norm) {
            case "close", "back" -> {
                close(player);
                return true;
            }
            case "previous-page" -> {
                if (currentPage > 0) { currentPage--; schedulePage(currentPage); }
                return true;
            }
            case "next-page" -> {
                int maxPage = getMaxPage();
                if (currentPage < maxPage) { currentPage++; schedulePage(currentPage); }
                return true;
            }
            case "open-my-listings", "my-listings" -> {
                close(player);
                manager.openMyListings(player);
                return true;
            }
            case "open-collection-box", "collection-box" -> {
                close(player);
                manager.openCollectionBox(player);
                return true;
            }
            case "open-favorites", "favorites" -> {
                if (!config.isFeatureEnabled(FeatureRegistry.Keys.FAVORITES)) return true;
                close(player);
                manager.openFavorites(player);
                return true;
            }
            case "refresh-listings", "refresh" -> {
                if (config.isFeatureEnabled(FeatureRegistry.Keys.REFRESH_LISTINGS)) {
                    schedulePage(currentPage);
                }
                return true;
            }
            case "open-search", "search" -> {
                if (!config.isSearchEnabled()) return true;
                openSearchPrompt(player);
                return true;
            }
            case "clear-search" -> {
                if (!right) return false;
                close(player);
                corePlugin.getScheduler().runTaskLaterForPlayer(player, () -> open(player), 1L);
                return true;
            }
            default -> { return false; }
        }
    }

    /** Search butonu aksiyonunu bağımsız yapar — slot-only özel toggle için. */
    private void openSearchPrompt(Player player) {
        if (currentSearch != null && !currentSearch.isEmpty()) {
            // Arama zaten açık: sağ tıkla aramayı temizle, sol tıkla arama panelini aç
            close(player);
            corePlugin.getScheduler().runTaskLaterForPlayer(player, () -> open(player), 1L);
            return;
        }
        close(player);
        SignInputGUI.create(corePlugin, player)
                .lines("", "~~~~~~~~~~~", "&#F5F5F5Aranacak eşya", "&#8c8c8cadını yazın")
                .onComplete((p, text) -> openWithSearch(p, text))
                .onClose(p -> manager.openMainMenu(p))
                .open();
    }

    private void renderPage() {
        clear();
        List<AuctionListing> currentListings = pageData != null ? pageData.listings() : List.of();

        // Border (tam özelleştirme + background-fill önceliklendirilir)
        applyBorder(layout.border());

        // Navigation items — feature gate'li olanlar yok sayılır (renderlanmaz → zombi buton yok)
        for (var nav : layout.navItems()) {
            // Navigation kimliği → ilişkili feature anahtarı eşlemesi
            String fid = nav.id();
            if (fid == null) fid = "";
            // "refresh" navigation anahtarı features.yml → refresh-listings ile kapılı
            if ("refresh".equalsIgnoreCase(fid)
                    && !config.isFeatureEnabled(FeatureRegistry.Keys.REFRESH_LISTINGS)) {
                continue;
            }
            // "search" → search feature ile kapılı
            if ("search".equalsIgnoreCase(fid)
                    && !config.isSearchEnabled()) {
                continue;
            }
            // "my-listings" → my-listings feature ile kapılı
            if ("my-listings".equalsIgnoreCase(fid) && !config.isFeatureEnabled(FeatureRegistry.Keys.MY_LISTINGS)) {
                continue;
            }
            // "collection-box" → collection-box feature ile kapılı
            if ("collection-box".equalsIgnoreCase(fid) && !config.isFeatureEnabled(FeatureRegistry.Keys.COLLECTION_BOX)) {
                continue;
            }
            // "favorites" → favorites feature ile kapılı
            if ("favorites".equalsIgnoreCase(fid) && !config.isFeatureEnabled(FeatureRegistry.Keys.FAVORITES)) {
                continue;
            }
            // sort ve filter NavItem üzerinde değil, ayrı alanlar — onlar aşağıda ayrıca gate'lendi

            // Builder'a TÜM alanları uygula (amount/custom-model-data/glow/hide-flags/lore)
            var builder = navBuilder(nav);
            setItem(nav.slot(), builder.build());
        }

        // Search button (eğer ayrı bir SearchConfig tanımlıysa)
        if (layout.search() != null && config.isSearchEnabled() && layout.search().slot() >= 0) {
            var s = layout.search();
            var sb = MenuItem.Builder.of(s.material(), s.texture()).name(s.name());
            if (s.amount() > 1) sb.amount(s.amount());
            if (s.customModelData() != 0) sb.customModelData(s.customModelData());
            if (s.glow()) sb.glow(true);
            if (s.hideFlags()) sb.hideFlags(true);
            setItem(s.slot(), sb.build());
        }

        // Tek huni (sıralama + Favorilerim + Teklifli)
        buildSortButton();

        // Content slots
        List<Integer> contentSlots = layout.contentSlots();
        for (int i = 0; i < currentListings.size(); i++) {
            int slot = contentSlots.get(i);
            setItem(slot, listingToMenuItem(currentListings.get(i)));
        }

        // Arka plan dolgusu (border / nav / content slot'larına dokunmaz)
        applyBackgroundFill(layout.backgroundFill());

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
     * Geçerli moda göre ilanları çeker: sıralama modları Java'da sıralar,
     * Favorilerim / Teklifli filtreleri Java'da filtreler ve sayfalar.
     */
    private PageData fetchPage(int page) {
        int pageSize = layout.contentSlots().size();
        int N = sortCount();
        // Mod indexleri özellik kapalıyken kayar: Favoriler kapalıysa Teklifli N'de olur.
        int favoritesMode = config.isFeatureEnabled(FeatureRegistry.Keys.FAVORITES) ? N : -1;
        int offersMode = config.isNegotiationEnabled() ? (favoritesMode >= 0 ? N + 1 : N) : -1;
        boolean favoritesOnly = filterMode == favoritesMode;
        boolean offersOnly = filterMode == offersMode;
        int sortIndex = filterMode < N ? filterMode : -1;
        String searchQuery = currentSearch;

        List<AuctionListing> src = (searchQuery != null && !searchQuery.isEmpty())
                ? data.searchListings(searchQuery)
                : data.getActiveListings();

        List<AuctionListing> filtered = new ArrayList<>(src);

        if (favoritesOnly) {
            Set<UUID> favIds;
            if (currentPlayer != null) {
                favIds = new HashSet<>(data.getFavoriteListingIds(currentPlayer.getUniqueId()));
            } else {
                favIds = Set.of();
            }
            filtered.removeIf(l -> !favIds.contains(l.id()));
        } else if (offersOnly) {
            filtered.removeIf(l -> !l.offersEnabled());
        }

        applySort(filtered, sortIndex);

        int total = filtered.size();
        int from = page * pageSize;
        int to = Math.min(from + pageSize, filtered.size());
        if (from >= filtered.size()) return new PageData(List.of(), total);
        return new PageData(filtered.subList(from, to), total);
    }

    /** Sıralama modunu ilan listesine uygular (genellikle aramayla, hepsi değil). */
    private void applySort(List<AuctionListing> list, int idx) {
        if (idx < 0) return;
        switch (idx) {
            case 0 -> list.sort(Comparator.comparingDouble(AuctionListing::price));
            case 1 -> list.sort(Comparator.comparingDouble(AuctionListing::price).reversed());
            case 2 -> list.sort(Comparator.comparingLong(AuctionListing::getTimeLeft));
            case 3 -> list.sort(Comparator.comparingLong(AuctionListing::getTimeLeft).reversed());
            case 4 -> list.sort(Comparator.comparingLong(AuctionListing::listedAt).reversed());
            case 5 -> list.sort(Comparator.comparingLong(AuctionListing::listedAt));
            case 6 -> list.sort(Comparator.comparing(AuctionListing::sellerName, String.CASE_INSENSITIVE_ORDER));
            case 7 -> list.sort(Comparator.comparing(AuctionListing::sellerName, String.CASE_INSENSITIVE_ORDER).reversed());
            case 8 -> list.sort(Comparator.comparing((AuctionListing l) -> ItemNames.displayName(l.item()), String.CASE_INSENSITIVE_ORDER));
            case 9 -> list.sort(Comparator.comparing((AuctionListing l) -> ItemNames.displayName(l.item()), String.CASE_INSENSITIVE_ORDER).reversed());
            default -> { /* bilinmeyen/ekstra sıralama — dokunma */ }
        }
    }

    private MenuItem listingToMenuItem(AuctionListing listing) {
        var clone = listing.item().clone();
        var builder = MenuItem.builder(clone);
        String displayName = "&#F5F5F5" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());

        // Flash sale badge
        if (listing.isFlashSale()) {
            displayName = "&#FFAA00⚡ " + displayName + " &#FFD54F&l-" + (int) listing.getDiscountPercent() + "%";
            // Glow effect: add enchantment with hide flag
            var meta = clone.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                clone.setItemMeta(meta);
            }
        } else if (listing.isFlashSaleExpired()) {
            displayName = "&#8c8c8c⏳ " + displayName;
        }

        // Pazarlık rozeti — teklif açık ilanlar
        if (listing.offersEnabled() && config.isNegotiationEnabled()) {
            displayName = "&#2CCED2💬 " + displayName;
        }

        builder.name(displayName);

        for (String line : layout.loreFormat()) {
            builder.lore(formatLore(line, listing));
        }

        // Pazarlık açık ilan ipucu
        if (listing.offersEnabled() && config.isNegotiationEnabled()) {
            builder.lore("&#8c8c8c•  &#2CCED2[Pazarlık] &#F5F5F5Teklif açık &#8c8c8c— Shift+Sol Tıkla");
        }

        // Item meta gösterimi (enchantment, lore, hasar)
        var meta = clone.getItemMeta();
        if (meta != null) {
            // Enchantment'lar
            if (meta.hasEnchants()) {
                for (var entry : meta.getEnchants().entrySet()) {
                    String enchName = entry.getKey().getKey().getKey();
                    builder.lore("&#8c8c8c" + enchName + " " + toRoman(entry.getValue()));
                }
            }
            // Item lore
            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    builder.lore("&#8c8c8c" + line);
                }
            }
            // Hasar (durability) — sadece hasar alabilen item'lar için
            if (clone.getType().getMaxDurability() > 0 && meta instanceof org.bukkit.inventory.meta.Damageable dmg) {
                int maxDura = clone.getType().getMaxDurability();
                int currentDura = maxDura - dmg.getDamage();
                builder.lore("&#8c8c8c•  &#FFD54Fʜᴀꜱᴀʀ  &#8c8c8c— &#F5F5F5" + currentDura + "/" + maxDura);
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

    /** Shift+sol tık: teklif fiyatı girmek için tabela açar. */
    private void openOfferPrompt(Player player, AuctionListing listing) {
        close(player);
        var plugin = (dev.ensisdev.lbauctionhouse.LbAuctionHouse) manager.getApi().getCore();
        SignInputGUI.create(plugin, player)
                .lines("", "~~~~~~~~~~~", "&#2CCED2Teklif fiyatını yazın", "&#8c8c8c( sayı )")
                .onComplete((p, text) -> {
                    try {
                        double price = Double.parseDouble(text.trim());
                        var result = manager.getNegotiation().sendOffer(p, listing, price);
                        p.sendMessage(offerMsg(result));
                    } catch (NumberFormatException e) {
                        p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("pazarlik.err.number"));
                    }
                    manager.openMainMenu(p);
                })
                .onClose(p -> manager.openMainMenu(p))
                .open();
    }

    /** SendResult → sohbet mesajı. */
    private net.kyori.adventure.text.Component offerMsg(dev.ensisdev.lbauctionhouse.service.NegotiationService.SendResult r) {
        var lang = manager.getApi().getLanguageManager();
        return switch (r) {
            // OK durumunda metni NegotiationService zaten gönderir (offer-sent)
            case OK -> net.kyori.adventure.text.Component.empty();
            case OFFERS_DISABLED -> lang.getPrefixed("pazarlik.err.disabled");
            case SOLD -> lang.getPrefixed("pazarlik.err.sold");
            case SELF -> lang.getPrefixed("pazarlik.err.self");
            case BLOCKED -> lang.getPrefixed("pazarlik.err.blocked");
            case PRICE_NOT_ALLOWED -> lang.getPrefixed("pazarlik.err.price");
            case ACTIVE_LIMIT -> lang.getPrefixed("pazarlik.err.limit");
            case ALREADY_OPEN -> lang.getPrefixed("pazarlik.err.open");
            case RATE_LIMITED -> lang.getPrefixed("pazarlik.err.ratelimited");
        };
    }

    private void toggleFavorite(Player player, AuctionListing listing) {
        var lang = manager.getApi().getLanguageManager();
        String itemName = dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());
        if (data.isFavorite(player.getUniqueId(), listing.id())) {
            data.removeFavorite(player.getUniqueId(), listing.id());
            player.sendMessage(lang.getPrefixed("auction.favorites.removed", "item", itemName));
        } else {
            data.addFavorite(player.getUniqueId(), listing.id());
            player.sendMessage(lang.getPrefixed("auction.favorites.added", "item", itemName));
        }
        schedulePage(currentPage); // sayfayı yeniden çiz
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
