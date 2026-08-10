package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.util.BundleItems;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.core.gui.SignInputGUI;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class SellGUI extends BaseMenu {

    private static final int INPUT_SLOT = 22;
    private static final int CONFIRM_SLOT = 16;
    private static final int CANCEL_SLOT = 25;
    private static final int QTY_DOWN_SLOT = 19;
    private static final int QTY_UP_SLOT = 21;
    private static final int QTY_ALL_SLOT = 23;
    private static final int PRICE_SLOT = 13;
    private static final int ADVERTISE_SLOT = 15;
    private static final int BUNDLE_SLOT = 9;   // Toplu paket (fıçı)
    private static final int OFFERS_SLOT = 14;  // Teklif (pazarlık) toggle

    private final LbAuctionHouse addon;
    private final LbAuctionHouse corePlugin;
    private final AuctionManager manager;
    private final AuctionConfig config;

    private Player currentPlayer;
    private ItemStack selectedItem;
    private int selectedQuantity = 1;
    private int maxQuantity = 0;
    private double selectedPrice = -1;
    private boolean advertised = false;
    private boolean offersEnabled = false;   // pazarlık/teklif açık mı
    private boolean suppressingReturn = false; // programatik kapanışta eşya iadesini bastır
    private Consumer<Boolean> onComplete;

    public SellGUI(LbAuctionHouse addon, LbAuctionHouse corePlugin, AuctionManager manager, AuctionConfig config) {
        super("auction_sell", "&8&l» <gradient:#FFB74D:#FFD54F>" + dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("EŞYA SAT") + "</gradient> &8&l«", 4);
        this.addon = addon;
        this.corePlugin = corePlugin;
        this.manager = manager;
        this.config = config;
    }

    public void open(Player player) {
        this.currentPlayer = player;
        resetState();   // yalnızca İLK açılışta sıfırla
        super.open(player);
    }

    /**
     * Fiyat tabelası vb. sonrası GUI'yi AÇIK KALAN state ile yeniden açar.
     * (open() state'i sıfırlar — burada seçilen eşya/miktar/fiyat korunur.)
     */
    private void reopen(Player player) {
        this.currentPlayer = player;
        super.open(player);
    }

    private void resetState() {
        this.selectedItem = null;
        this.selectedQuantity = 1;
        this.selectedPrice = -1;
        this.advertised = false;
        this.offersEnabled = false;
    }

    public void open(Player player, Consumer<Boolean> callback) {
        this.onComplete = callback;
        open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        fillEmpty(MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

        // Eşya koyma slotu — sürükle veya tıkla
        if (selectedItem != null) {
            ItemStack display = selectedItem.clone();
            display.setAmount(selectedQuantity);
            setItem(INPUT_SLOT, MenuItem.builder(display)
                    .name("&#FFD54F&lᴇꜱyᴀ: &#F5F5F5" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(selectedItem))
                    .lore("&#8c8c8c• &#FFD54FMiktar &#F5F5F5— " + selectedQuantity)
                    .lore("&#8c8c8c• &#FF5555Sol Tık &#F5F5F5— eşyayı çıkar")
                    .build());
        } else {
            setItem(INPUT_SLOT, MenuItem.builder(Material.ENDER_CHEST)
                    .name("&#FFD54F&lᴇꜱyᴀɴɪ ᴋᴏʏ")
                    .lore("&#8c8c8c• &#F5F5F5Satmak istediğin eşyayı")
                    .lore("&#8c8c8c  buraya sürükle veya tıkla")
                    .build());
        }

        // Toplu paket (fıçı) butonu — birden fazla eşyayı tek pakette sat
        setItem(BUNDLE_SLOT, MenuItem.builder(Material.BARREL)
                .name("&#FFB74D&lᴛᴏᴘʟᴜ ᴘᴀᴋᴇᴛ (ꜰɪᴄɪ)")
                .lore("&#8c8c8c• &#F5F5F5Birden fazla eşyayı tek pakette sat")
                .lore("&#8c8c8c• &#FFB74DTıkla &#F5F5F5— eşyaları fıçıya koy")
                .build());

        // Miktar kontrolleri
        if (selectedItem != null) {
            setItem(QTY_DOWN_SLOT, MenuItem.builder(Material.RED_STAINED_GLASS_PANE)
                    .name("&#FF5555&l-1")
                    .lore("&#8c8c8c• &#FF5555Tıkla &#F5F5F5— miktarı azalt")
                    .build());
            setItem(QTY_UP_SLOT, MenuItem.builder(Material.GREEN_STAINED_GLASS_PANE)
                    .name("&#55FF55&l+1")
                    .lore("&#8c8c8c• &#55FF55Tıkla &#F5F5F5— miktarı artır")
                    .build());
            setItem(QTY_ALL_SLOT, MenuItem.builder(Material.GOLD_NUGGET)
                    .name("&#FFD54F&lʜᴇᴘꜱɪɴɪ ꜱᴀᴛ: &#FFAA00" + maxQuantity)
                    .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— maksimum miktar")
                    .build());
        }

        // Reklam toggle
        if (config.isAdvertiseEnabled()) {
            setItem(ADVERTISE_SLOT, MenuItem.builder(advertised ? Material.BLAZE_POWDER : Material.GUNPOWDER)
                    .name(advertised ? "&#FFD54F&lʀᴇᴋʟᴀᴍ: &#55FF55&lᴀᴄɪᴋ" : "&#FFD54F&lʀᴇᴋʟᴀᴍ: &#FF5555&lᴋᴀᴘᴀʟɪ")
                    .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— reklamlı ilan")
                    .lore("&#8c8c8c• &#F5F5F5Tüm oyunculara duyurulur")
                    .lore("&#8c8c8cÜcret: &#FFAA00" + String.format("%,.0f", config.getAdvertiseFee()) + "₺")
                    .build());
        }

        // Teklif (Pazarlık) toggle
        if (config.isNegotiationEnabled()) {
            setItem(OFFERS_SLOT, MenuItem.builder(offersEnabled ? Material.EMERALD : Material.COAL)
                    .name(offersEnabled ? "&#2CCED2&lᴛᴇᴋʟɪꜰ: &#55FF55&lᴀᴄɪᴋ" : "&#2CCED2&lᴛᴇᴋʟɪꜰ: &#FF5555&lᴋᴀᴘᴀʟɪ")
                    .lore("&#8c8c8c• &#2CCED2Tıkla &#F5F5F5— pazarlık teklifi aç/kapat")
                    .lore("&#8c8c8c• &#F5F5F5Açıkken alıcılar bu ilana fiyat teklifi gönderebilir")
                    .build());
        }

        // Fiyat
        updatePriceDisplay();

        // Onayla
        setItem(CONFIRM_SLOT, MenuItem.builder(Material.LIME_WOOL)
                .name("&#55FF55&l✔ ꜱᴀᴛɪꜱᴀ ᴄɪᴋᴀʀ")
                .lore("&#8c8c8c• &#55FF55Tıkla &#F5F5F5— eşyayı ihaleye koy")
                .build());

        // İptal
        setItem(CANCEL_SLOT, MenuItem.builder(Material.RED_WOOL)
                .name("&#FF5555&l✖ ɪᴘᴛᴀʟ")
                .lore("&#8c8c8c• &#FF5555Tıkla &#F5F5F5— geri dön")
                .build());
    }

    @Override
    protected void onClose(Player player) {
        // Oyuncu GUI'yi ESC ile kapatırsa seçilen eşyayı envantere geri ver
        // (normal item için; bundle içindeki eşyalar zaten envanterde durur)
        if (!suppressingReturn) {
            returnItemToInventory();
        }
    }

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        // Oyuncunun KENDİ envanterine tıklanırsa → eşyayı satış slotuna koy.
        // Böylece sürüklemeye gerek kalmaz; sol/sağ/shift tık yeterli.
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                event.setCancelled(true);
                int bottomSlot = event.getRawSlot() - event.getView().getTopInventory().getSize();
                placeItemFromInventory(clicked, bottomSlot);
            }
            return;
        }

        int slot = event.getSlot();

        // INPUT_SLOT: eşyayı koy / çıkar
        if (slot == INPUT_SLOT) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                // İmleçte eşya var → koy (sürükleme yöntemi)
                placeItemFromCursor(cursor, event);
            } else if (selectedItem != null) {
                // Eşyayı geri al
                ItemStack giveBack = selectedItem.clone();
                giveBack.setAmount(selectedQuantity);
                event.getView().setCursor(giveBack);
                selectedItem = null;
                selectedQuantity = 1;
                updateDisplay();
            }
            return;
        }

        // Toplu paket (fıçı) — eşya seçimi yapılmışsa fıçıya geç
        if (slot == BUNDLE_SLOT) {
            // Tıklama event'i bitmeden envanter değiştirme → 1 tick ertele
            // (Folia'da bu işlem oyuncunun kendi region'ında koşmalıdır)
            corePlugin.getScheduler().runTaskLaterForPlayer(currentPlayer, this::openBundleEditor, 1L);
            return;
        }

        // Miktar azalt
        if (slot == QTY_DOWN_SLOT && selectedItem != null) {
            if (event.isRightClick() && selectedQuantity > 1) {
                selectedQuantity = Math.max(1, selectedQuantity - 10);
            } else {
                selectedQuantity = Math.max(1, selectedQuantity - 1);
            }
            updateDisplay();
            return;
        }

        // Miktar artır
        if (slot == QTY_UP_SLOT && selectedItem != null) {
            if (event.isRightClick() && selectedQuantity < maxQuantity) {
                selectedQuantity = Math.min(maxQuantity, selectedQuantity + 10);
            } else {
                selectedQuantity = Math.min(maxQuantity, selectedQuantity + 1);
            }
            updateDisplay();
            return;
        }

        // Hepsini sat
        if (slot == QTY_ALL_SLOT && selectedItem != null) {
            selectedQuantity = maxQuantity;
            updateDisplay();
            return;
        }

        // Fiyat belirle
        if (slot == PRICE_SLOT) {
            startPriceInput();
            return;
        }

        // Reklam toggle
        if (slot == ADVERTISE_SLOT) {
            advertised = !advertised;
            updateDisplay();
            return;
        }

        // Teklif (Pazarlık) toggle
        if (slot == OFFERS_SLOT && config.isNegotiationEnabled()) {
            offersEnabled = !offersEnabled;
            updateDisplay();
            return;
        }

        // Onayla
        if (slot == CONFIRM_SLOT) {
            if (selectedItem == null) {
                sendMsg("sell.no-item");
                return;
            }
            if (selectedPrice <= 0) {
                startPriceInput();
                return;
            }
            if (config.isBlacklisted(selectedItem.getType())) {
                sendMsg("sell.blacklisted");
                return;
            }
            // Miktarı ayarla (paketse adet 1'dir — eşyalar paketin içinde)
            ItemStack sellItem = selectedItem.clone();
            if (!BundleItems.isBundle(sellItem)) {
                sellItem.setAmount(selectedQuantity);
            }
            // TEK fiyat — adet başı değil; fiyat, ilanın tamamı içindir.
            double totalPrice = selectedPrice;

            boolean ok = manager.listItem(currentPlayer, sellItem, totalPrice, config.getExpireHours(), advertised, offersEnabled);
            if (ok) {
                // Normal item tıklama anında envanterden düşülmüştü.
                // Sadece bundle (fıçı) için içindeki eşyaları envanterden düş.
                if (BundleItems.isBundle(sellItem)) {
                    removeFromInventory(currentPlayer, sellItem);
                }
                this.selectedItem = null;
                sendMsg("sell.success", "price", String.format("%,.0f", totalPrice));
                if (onComplete != null) onComplete.accept(true);
                close(currentPlayer);
            } else {
                // İlan oluşturulamadı → eşyayı envantere geri ver
                returnItemToInventory();
                sendMsg("sell.failed");
                updateDisplay();
            }
            return;
        }

        // İptal
        if (slot == CANCEL_SLOT) {
            returnItemToInventory();
            if (onComplete != null) onComplete.accept(false);
            close(currentPlayer);
            manager.openMainMenu(currentPlayer);
        }
    }

    /**
     * SellGUI, oyuncunun kendi envanterine tıklayarak eşya alabilir
     * (sürüklemeye gerek yok). MenuManager alt envanter tıklamalarını
     * bu menüye iletir.
     */
    @Override
    protected boolean allowBottomClicks() {
        return true;
    }

    /**
     * Oyuncunun envanterinden satılacak eşyayı düşer.
     * Paket (fıçı) ise içindeki HER eşya ayrı ayrı düşülür.
     */
    private void removeFromInventory(Player player, ItemStack toRemove) {
        if (BundleItems.isBundle(toRemove)) {
            for (ItemStack it : BundleItems.unpack(toRemove)) {
                removeByType(player, it.getType(), it.getAmount());
            }
        } else {
            removeByType(player, toRemove.getType(), toRemove.getAmount());
        }
    }

    private void removeByType(Player player, Material type, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack invItem = contents[i];
            if (invItem != null && invItem.getType() == type) {
                int take = Math.min(invItem.getAmount(), remaining);
                invItem.setAmount(invItem.getAmount() - take);
                remaining -= take;
            }
        }
        player.getInventory().setContents(contents);
    }

    /** Oyuncunun envanterindeki belirli bir eşyadan TOPLAM adet. */
    private int countInInventory(Player player, Material type) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == type) total += it.getAmount();
        }
        return total;
    }

    /** Fıçı (toplu paket) düzenleyicisini açar. */
    private void openBundleEditor() {
        suppressingReturn = true;
        close(currentPlayer);
        suppressingReturn = false;
        new BundleEditGUI().open(currentPlayer, bundleItems -> {
            // Paket hazır → satış GUI'sine dön
            this.selectedItem = BundleItems.createBundle(bundleItems, "&#FFB74D&lToplu Paket");
            this.maxQuantity = 1;
            this.selectedQuantity = 1;
            this.selectedPrice = -1;
            this.advertised = false;
            reopen(currentPlayer);
            if (selectedPrice <= 0) {
                startPriceInput();
            }
        }, () -> reopen(currentPlayer));
    }

    /**
     * Eşyayı oyuncunun envanterinden satış slotuna koyar (tıklayınca).
     * Dupe önleme: eşya tıklama anında envanterden DÜŞÜLÜR ve
     * iptal/ESC/listItem hatasında geri verilir.
     *
     * @param clicked    tıklanan eşya
     * @param bottomSlot tıklanan eşyanın alt envanterdeki slotu (0-based)
     */
    private void placeItemFromInventory(ItemStack clicked, int bottomSlot) {
        // Önceki seçim varsa onu geri ver
        returnItemToInventory();
        this.selectedItem = clicked.clone();
        // Maksimum miktar = tıklanan destenin miktarı (güvenli: yalnızca düşülen eşyalar)
        this.maxQuantity = clicked.getAmount();
        this.selectedQuantity = clicked.getAmount();
        // Dupe önleme: eşyayı envanterden düş
        currentPlayer.getInventory().setItem(bottomSlot, null);
        if (selectedPrice <= 0) {
            startPriceInput();
        } else {
            updateDisplay();
        }
    }

    /**
     * İmleçteki eşyayı satış slotuna koyar (sürükleme yöntemi).
     */
    private void placeItemFromCursor(ItemStack cursor, InventoryClickEvent event) {
        // Önceki seçim varsa onu geri ver
        returnItemToInventory();
        this.selectedItem = cursor.clone();
        this.maxQuantity = cursor.getAmount();
        this.selectedQuantity = cursor.getAmount();
        event.getView().setCursor(null);
        if (selectedPrice <= 0) {
            startPriceInput();
        } else {
            updateDisplay();
        }
    }

    /**
     * Seçilen eşyayı (normal item) envantere geri verir.
     * Bundle (fıçı) için no-op — paketin içindeki eşyalar envanterde durur.
     */
    private void returnItemToInventory() {
        if (selectedItem == null) return;
        if (!BundleItems.isBundle(selectedItem)) {
            ItemStack giveBack = selectedItem.clone();
            giveBack.setAmount(selectedQuantity);
            currentPlayer.getInventory().addItem(giveBack);
        }
        selectedItem = null;
        selectedQuantity = 1;
    }

    /**
     * Fiyat girişini tıklama event'i BİTTİKTEN sonra (1 tick sonra) açar.
     * Event içinde envanter kapatmak/açmak sorun çıkarabilir.
     */
    private void startPriceInput() {
        corePlugin.getScheduler().runTaskLaterForPlayer(currentPlayer, this::openPriceInput, 1L);
    }

    /**
     * Tabela ile fiyat girişi. Bitince GUI state KORUNARAK yeniden açılır.
     */
    private void openPriceInput() {
        suppressingReturn = true;
        close(currentPlayer);
        suppressingReturn = false;
        SignInputGUI.create(corePlugin, currentPlayer)
                .lines("", "~~~~~~~~~~~", "&#FFD54FFiyatı yazın", "&#8c8c8c( sayı )")
                .onComplete((p, text) -> {
                    try {
                        selectedPrice = Double.parseDouble(text.trim());
                        if (selectedPrice < config.getMinPrice()) {
                            p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.sell.min-price", "price", String.valueOf(config.getMinPrice())));
                            selectedPrice = config.getMinPrice();
                        }
                        if (selectedPrice > config.getMaxPrice()) {
                            p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.sell.max-price", "price", String.valueOf(config.getMaxPrice())));
                            selectedPrice = config.getMaxPrice();
                        }
                    } catch (NumberFormatException e) {
                        p.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.sell.invalid-number"));
                        selectedPrice = -1;
                    }
                    reopen(p);   // state'i koruyarak geri dön
                })
                .onClose(p -> reopen(p))
                .open();
    }

    private void updateDisplay() {
        clear();
        onOpen(currentPlayer);
        refresh(currentPlayer);   // açık envanterdeki item'ları gerçekten güncelle
    }

    private void sendMsg(String key, String... placeholders) {
        currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction." + key, placeholders));
    }

    private void updatePriceDisplay() {
        // TEK fiyat — adet başı değil. Fiyat, ilanın tamamı içindir.
        String priceText = selectedPrice > 0
                ? String.format("%,.0f₺", selectedPrice)
                : "&#FF5555&lʙᴇʟɪʀʟᴇɴᴍᴇᴅɪ";
        MenuItem priceItem = MenuItem.builder(Material.GOLD_NUGGET)
                .name("&#FFD54F&lꜰɪʏᴀᴛ: &#FFAA00" + priceText)
                .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— fiyat belirle")
                .build();
        setItem(PRICE_SLOT, priceItem);

        double bal = manager.getApi().getEconomyManager().getBalance(currentPlayer.getUniqueId());
        setItem(24, MenuItem.builder(Material.SUNFLOWER)
                .name("&#F5F5F5&lʙᴀᴋɪʏᴇ: &#FFAA00" + String.format("%,.0f₺", bal))
                .build());
    }
}
