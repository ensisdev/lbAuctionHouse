package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.util.ItemNames;
import dev.ensisdev.lbauctionhouse.util.SmallCaps;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Satın alma onay GUI'si.
 * <p>
 * Tam özelleştirme {@code gui/confirm.yml}'den okunur:
 * <ul>
 *   <li>{@code title} — başlık</li>
 *   <li>{@code rows} — satır sayısı (varsayılan: 3)</li>
 *   <li>{@code border} — çerçeve (amount, glow, hide-flags, texture, cmd destekli)</li>
 *   <li>{@code background-fill} — arka plan dolgusu</li>
 *   <li>{@code item-slot} — satın alınacak item slotu</li>
 *   <li>{@code item-lore-format} — item lore şablonu</li>
 *   <li>{@code navigation} — confirm/cancel butonları (NavItem yapısı, tam özelleştirme)</li>
 * </ul>
 * Geriye dönük uyumluluk: {@code content-slots} eski yml'lerden "11,15" biçiminde okunur
 * ve 11=confirm, 15=cancel davranışını korur.
 */
public class ConfirmBuyGUI extends BaseMenu {

    private final AuctionManager manager;
    private AuctionListing currentListing;

    private List<GUILayoutLoader.NavItem> navItems;
    private int itemSlot;
    private List<String> itemLoreFormat;
    private GUILayoutLoader.BorderConfig border;
    private GUILayoutLoader.BackgroundFillConfig backgroundFill;

    public ConfirmBuyGUI(AuctionManager manager, GUILayoutLoader loader) {
        super("auction_confirm",
                "&8&l» <gradient:#FFB74D:#FFD54F>" + SmallCaps.toSmallCaps("ONAY") + "</gradient> &8&l«",
                3);
        this.manager = manager;
        loadLayout(loader);
    }

    private void loadLayout(GUILayoutLoader loader) {
        GUILayoutLoader.GUILayout layout = loader.load("confirm.yml");
        // Loader parse ettiyse navigation/items burada → tekrar okuyarak custom bir ConfirmInfo alalım
        // Ancak standard loader bu dosyayı farklı parse edebilir: bilgi GUI'si tarzı.
        // Bu yüzden loader'ı parseConfigSection olarak kullan (özel alt-loader).
        // Burada basit yaklaşım: standard layout kullanalım — ama confirm için sadece ihtiyaçlarımız:
        border = layout.border();
        backgroundFill = layout.backgroundFill();
        navItems = layout.navItems();

        // Geriye dönük uyumluluk: eğer navItems boşsa, content-slots'tan otomatik 11=confirm, 15=cancel oluştur
        if (navItems == null || navItems.isEmpty()) {
            navItems = fallbackNavItems(layout);
        }
        itemSlot = 13;
        itemLoreFormat = layout.loreFormat();
    }

    private List<GUILayoutLoader.NavItem> fallbackNavItems(GUILayoutLoader.GUILayout layout) {
        // Eski confirm.yml: content-slots: 11,15 → confirm@11, cancel@15
        List<Integer> contentSlots = layout.contentSlots();
        if (contentSlots == null || contentSlots.isEmpty()) {
            return List.of(
                    makeNav("confirm", 11, Material.LIME_CONCRETE,
                            "&a&l✔ ONAYLA", List.of("&7Tıklayarak satın almayı onayla.")),
                    makeNav("cancel", 15, Material.RED_CONCRETE,
                            "&c&l✖ İPTAL", List.of("&7Tıklayarak iptal et."))
            );
        }
        var list = new java.util.ArrayList<GUILayoutLoader.NavItem>();
        if (contentSlots.size() >= 1) {
            list.add(makeNav("confirm", contentSlots.get(0), Material.LIME_CONCRETE,
                    "&a&l✔ ONAYLA", List.of("&7Tıklayarak satın almayı onayla.")));
        }
        if (contentSlots.size() >= 2) {
            list.add(makeNav("cancel", contentSlots.get(1), Material.RED_CONCRETE,
                    "&c&l✖ İPTAL", List.of("&7Tıklayarak iptal et.")));
        }
        return list;
    }

    private GUILayoutLoader.NavItem makeNav(String id, int slot, Material mat, String name, List<String> lore) {
        return new GUILayoutLoader.NavItem(id, slot, mat, "", name, lore, "confirm".equals(id) ? "confirm-buy" : "cancel-buy", "", 1, 0, false, false);
    }

    public void open(Player player, AuctionListing listing) {
        this.currentListing = listing;
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();

        // Border
        applyBorder(border);

        if (currentListing == null) {
            applyBackgroundFill(backgroundFill);
            return;
        }

        // Item slot (ortada)
        ItemStack display = currentListing.item() != null ? currentListing.item().clone() : new ItemStack(Material.STONE);
        var itemBuilder = MenuItem.builder(display).name("&#F5F5F5" + ItemNames.displayName(display));
        for (String line : itemLoreFormat) {
            itemBuilder.lore(replaceVars(line));
        }
        setItem(itemSlot, itemBuilder.build());

        // Navigation items
        if (navItems != null) {
            for (var nav : navItems) {
                setItem(nav.slot(), navBuilder(nav).build());
            }
        }

        // Arka plan dolgusu
        applyBackgroundFill(backgroundFill);
    }

    private String replaceVars(String template) {
        if (currentListing == null) return template;
        return template
                .replace("%seller%", currentListing.sellerName() != null ? currentListing.sellerName() : "?")
                .replace("%price%", String.format("%,.2f", currentListing.price()))
                .replace("%type%", currentListing.type() != null ? currentListing.type() : "BIN");
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        boolean right = event.isRightClick();

        if (currentListing == null) {
            event.setCancelled(true);
            return;
        }

        // Navigation item aksiyonu (config'den okur)
        var nav = findNavBySlot(slot);
        if (nav != null) {
            String action = right ? nav.rightClickAction() : nav.leftClickAction();
            if (handleNavAction(player, nav, action)) return;
        }
        event.setCancelled(true);
    }

    /** Slot'a karşılık gelen navigation item'ı döndürür. */
    private GUILayoutLoader.NavItem findNavBySlot(int slot) {
        if (navItems == null) return null;
        for (var n : navItems) if (n.slot() == slot) return n;
        return null;
    }

    /** Navigation aksiyonlarını işler (yaml-drivent layout). */
    private boolean handleNavAction(Player player, GUILayoutLoader.NavItem nav, String action) {
        if (action == null || action.isEmpty()) return false;
        String norm = action.trim().toLowerCase().replace('_', '-');
        switch (norm) {
            case "confirm-buy", "buy" -> {
                close(player);
                manager.buyItem(player, currentListing);
                return true;
            }
            case "cancel-buy", "cancel", "close", "back" -> {
                close(player);
                manager.openMainMenu(player);
                return true;
            }
            default -> { return false; }
        }
    }
}
