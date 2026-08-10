package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * Bekleyen eşya ve paraların görüntülendiği GUI.
 * Layout {@code gui/collection-box.yml} dosyasından okunur.
 */
public class CollectionBoxGUI extends BaseMenu {

    private final AuctionManager manager;
    private final AuctionConfig config;
    private final CollectionEntry data;
    private final AuctionEconomy economy;
    private final GUILayoutLoader.GUILayout layout;

    private Player currentPlayer;
    private List<CollectionEntry.UnclaimedEntry> entries;

    public CollectionBoxGUI(AuctionManager manager, AuctionConfig config,
                            CollectionEntry data, AuctionEconomy economy,
                            GUILayoutLoader loader) {
        super("auction_collection", "&8&l» <gradient:#FFB74D:#FFD54F>"+ dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("KOLİM")+"</gradient> &8&l«", 4);
        this.manager = manager;
        this.config = config;
        this.data = data;
        this.economy = economy;
        this.layout = loader.load("collection-box.yml");
        if (layout != null && layout.title() != null && !layout.title().isEmpty()) {
            setDynamicTitle(layout.title());
        }
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.entries = data.getUnclaimedCollection(player.getUniqueId());
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();

        // Border (tam özelleştirme)
        applyBorder(layout.border());

        // Navigation items (tam özelleştirme — yml'den amount/glow/hide-flags/cmd)
        for (var nav : layout.navItems()) {
            // claim-all aşağıda applyClaimAllButton() tarafından tek kez çizilir
            // (kutu boşsa buton gizlenir; burada çizmek zombi buton oluştururdu).
            if ("claim-all".equalsIgnoreCase(nav.id())) continue;
            setItem(nav.slot(), navBuilder(nav).build());
        }

        // Claim All butonu (slot yml'den: id=claim-all)
        applyClaimAllButton();

        // Content
        if (entries.isEmpty()) {
            player.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.empty"));
            applyBackgroundFill(layout.backgroundFill());
            return;
        }

        var slots = layout.contentSlots();
        for (int i = 0; i < Math.min(slots.size(), entries.size()); i++) {
            var entry = entries.get(i);
            final int entryId = entry.id();
            final int index = i;

            var builder = MenuItem.builder(Material.CHEST)
                    .name("&#FFD54F&lʙᴇᴋʟᴇʏᴇɴ ᴏᴅᴜʟ #" + (i + 1))
                    .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— ödülü al")
                    .onClick(e -> claim(entryId, index));

            if (entry.type().equals("ITEM") && entry.item() != null) {
                builder = MenuItem.builder(entry.item().clone())
                        .name("&#F5F5F5" + dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(entry.item()))
                        .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— ödülü al")
                        .onClick(e -> claim(entryId, index));
            } else if (entry.type().equals("MONEY")) {
                builder.lore("&#8c8c8cMiktar: &#FFAA00" + economy.format(entry.amount()));
            }

            setItem(slots.get(i), builder.build());
        }

        // Arka plan dolgusu
        applyBackgroundFill(layout.backgroundFill());
    }

    /**
     * Claim-All butonunun görseli yml'den okunur: {@code navigation.id: claim-all}.
     * Tanımlanmadıysa slot 35 + hardcoded fallback uygulanır (geriye uyumluluk).
     */
    private void applyClaimAllButton() {
        if (entries.isEmpty()) return;
        if (layout.navItems() != null) {
            for (var nav : layout.navItems()) {
                if ("claim-all".equalsIgnoreCase(nav.id())) {
                    var b = navBuilder(nav);
                    b.onClick(e -> claimAll());
                    setItem(nav.slot(), b.build());
                    return;
                }
            }
        }
        // Fallback: slot 35 hardcoded
        setItem(35, MenuItem.builder(Material.HOPPER)
                .name("&#55FF55&l✔ ʜᴇᴘꜱɪɴɪ ᴀʟ")
                .lore("&#8c8c8c• &#55FF55Tıkla &#F5F5F5— tüm bekleyen ödülleri al")
                .onClick(e -> claimAll())
                .build());
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
            case "claim-all" -> {
                claimAll();
                return true;
            }
            default -> { return false; }
        }
    }

    private void claim(int entryId, int index) {
        if (currentPlayer == null || entries == null || index >= entries.size()) return;

        var entry = entries.get(index);
        if (entry.type().equals("ITEM") && entry.item() != null) {
            var leftover = currentPlayer.getInventory().addItem(entry.item());
            if (leftover.isEmpty()) {
                data.markClaimed(entryId);
                currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.claimed"));
            } else {
                currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.inventory-full"));
                return;
            }
        } else if (entry.type().equals("MONEY")) {
            economy.deposit(currentPlayer.getUniqueId(), entry.amount());
            data.markClaimed(entryId);
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.claimed-money", "amount", economy.format(entry.amount())));
        }

        open(currentPlayer);
    }

    /** Tüm bekleyen ödülleri tek seferde al. */
    private void claimAll() {
        if (currentPlayer == null || entries == null) return;

        var iterator = entries.listIterator();
        boolean anyClaimed = false;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.type().equals("ITEM") && entry.item() != null) {
                var leftover = currentPlayer.getInventory().addItem(entry.item());
                if (leftover.isEmpty()) {
                    data.markClaimed(entry.id());
                    iterator.remove();
                    anyClaimed = true;
                }
            } else if (entry.type().equals("MONEY")) {
                economy.deposit(currentPlayer.getUniqueId(), entry.amount());
                data.markClaimed(entry.id());
                iterator.remove();
                anyClaimed = true;
            }
        }

        if (anyClaimed) {
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("auction.collection.claimed"));
        }

        open(currentPlayer);
    }
}
