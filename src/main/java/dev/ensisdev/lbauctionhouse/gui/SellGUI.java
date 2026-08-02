package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.util.BundleItems;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.core.gui.SignInputGUI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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
    private Consumer<Boolean> onComplete;

    public SellGUI(LbAuctionHouse addon, LbAuctionHouse corePlugin, AuctionManager manager, AuctionConfig config) {
        super("auction_sell", "&8&l» &6&l" + dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("EŞYA SAT") + " &8&l«", 4);
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
        fillEmpty(MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        // Eşya koyma slotu — sürükle veya tıkla
        if (selectedItem != null) {
            ItemStack display = selectedItem.clone();
            display.setAmount(selectedQuantity);
            setItem(INPUT_SLOT, MenuItem.builder(display)
                    .name("&6&lEşya: &f" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(selectedItem))
                    .lore("&7Miktar: &f" + selectedQuantity)
                    .lore("&7Sol tık — eşyayı çıkar")
                    .build());
        } else {
            setItem(INPUT_SLOT, MenuItem.builder(Material.ENDER_CHEST)
                    .name("&6&lEşyanı Koy")
                    .lore("&7Satmak istediğin eşyayı", "&7buraya sürükle veya tıkla")
                    .build());
        }

        // Toplu paket (fıçı) butonu — birden fazla eşyayı tek pakette sat
        setItem(BUNDLE_SLOT, MenuItem.builder(Material.BARREL)
                .name("&6&lToplu Paket (Fıçı)")
                .lore("&7Birden fazla eşyayı tek pakette sat")
                .lore("&7Tıkla — eşyaları fıçıya koy")
                .build());

        // Miktar kontrolleri
        if (selectedItem != null) {
            setItem(QTY_DOWN_SLOT, MenuItem.builder(Material.RED_STAINED_GLASS_PANE)
                    .name("&c-1")
                    .lore("&7Miktarı azalt")
                    .build());
            setItem(QTY_UP_SLOT, MenuItem.builder(Material.GREEN_STAINED_GLASS_PANE)
                    .name("&a+1")
                    .lore("&7Miktarı artır")
                    .build());
            setItem(QTY_ALL_SLOT, MenuItem.builder(Material.GOLD_NUGGET)
                    .name("&6Hepsini Sat: &e" + maxQuantity)
                    .lore("&7Tıkla — maksimum miktar")
                    .build());
        }

        // Reklam toggle
        if (config.isAdvertiseEnabled()) {
            setItem(ADVERTISE_SLOT, MenuItem.builder(advertised ? Material.BLAZE_POWDER : Material.GUNPOWDER)
                    .name(advertised ? "&6&lReklam: &aAÇIK" : "&6&lReklam: &cKAPALI")
                    .lore("&7Tıkla — reklamlı ilan")
                    .lore("&7Tüm oyunculara duyurulur")
                    .lore("&6Ücret: &e" + String.format("%,.0f", config.getAdvertiseFee()) + "₺")
                    .build());
        }

        // Teklif (Pazarlık) toggle
        if (config.isNegotiationEnabled()) {
            setItem(OFFERS_SLOT, MenuItem.builder(offersEnabled ? Material.EMERALD : Material.COAL)
                    .name(offersEnabled ? "&9&lTeklif: &aAÇIK" : "&9&lTeklif: &cKAPALI")
                    .lore("&7Tıkla — pazarlık teklifi aç/kapat")
                    .lore("&7Açıkken alıcılar bu ilana fiyat teklifi gönderebilir")
                    .build());
        }

        // Fiyat
        updatePriceDisplay();

        // Onayla
        setItem(CONFIRM_SLOT, MenuItem.builder(Material.LIME_WOOL)
                .name("&a&l✔ Satışa Çıkar")
                .lore("&7Eşyayı ihaleye koy")
                .build());

        // İptal
        setItem(CANCEL_SLOT, MenuItem.builder(Material.RED_WOOL)
                .name("&c&l✖ İptal")
                .lore("&7Geri dön")
                .build());
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        // Oyuncunun KENDİ envanterine tıklanırsa → eşyayı satış slotuna koy.
        // Böylece sürüklemeye gerek kalmaz; sol/sağ/shift tık yeterli.
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                event.setCancelled(true);
                placeItemFromInventory(clicked);
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
            Bukkit.getScheduler().runTaskLater(corePlugin, this::openBundleEditor, 1L);
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
                // Oyuncunun envanterinden eşyayı düş (paketse içindeki her eşyayı)
                removeFromInventory(currentPlayer, sellItem);
                sendMsg("sell.success", "price", String.format("%,.0f", totalPrice));
                if (onComplete != null) onComplete.accept(true);
                close(currentPlayer);
            } else {
                sendMsg("sell.failed");
            }
            return;
        }

        // İptal
        if (slot == CANCEL_SLOT) {
            if (selectedItem != null) {
                ItemStack giveBack = selectedItem.clone();
                giveBack.setAmount(selectedQuantity);
                currentPlayer.getInventory().addItem(giveBack);
            }
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
        close(currentPlayer);
        new BundleEditGUI().open(currentPlayer, bundleItems -> {
            // Paket hazır → satış GUI'sine dön
            this.selectedItem = BundleItems.createBundle(bundleItems, "&6&lToplu Paket");
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
     * Varsayılan miktar tüm destedir; adet kontrolleriyle azaltılabilir.
     */
    private void placeItemFromInventory(ItemStack clicked) {
        this.selectedItem = clicked.clone();
        // Maksimum miktar = envanterdeki TOPLAM adet (başka slotlardakiler dahil).
        this.maxQuantity = countInInventory(currentPlayer, clicked.getType());
        // Varsayılan miktar = tıklanan destenin miktarı.
        this.selectedQuantity = clicked.getAmount();
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
        this.selectedItem = cursor.clone();
        this.maxQuantity = countInInventory(currentPlayer, cursor.getType());
        this.selectedQuantity = cursor.getAmount();
        event.getView().setCursor(null);
        if (selectedPrice <= 0) {
            startPriceInput();
        } else {
            updateDisplay();
        }
    }

    /**
     * Fiyat girişini tıklama event'i BİTTİKTEN sonra (1 tick sonra) açar.
     * Event içinde envanter kapatmak/açmak sorun çıkarabilir.
     */
    private void startPriceInput() {
        Bukkit.getScheduler().runTaskLater(corePlugin, this::openPriceInput, 1L);
    }

    /**
     * Tabela ile fiyat girişi. Bitince GUI state KORUNARAK yeniden açılır.
     */
    private void openPriceInput() {
        close(currentPlayer);
        SignInputGUI.create(corePlugin, currentPlayer)
                .lines("", "~~~~~~~~~~~", "&6Fiyatı yazın", "&7( sayı )")
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
                : "§7Belirlenmedi";
        MenuItem priceItem = MenuItem.builder(Material.GOLD_NUGGET)
                .name("&6Fiyat: " + priceText)
                .lore("&7Tıkla — fiyat belirle")
                .build();
        setItem(PRICE_SLOT, priceItem);

        double bal = manager.getApi().getEconomyManager().getBalance(currentPlayer.getUniqueId());
        setItem(24, MenuItem.builder(Material.SUNFLOWER)
                .name("&eBakiye: &6" + String.format("%,.0f₺", bal))
                .build());
    }
}
