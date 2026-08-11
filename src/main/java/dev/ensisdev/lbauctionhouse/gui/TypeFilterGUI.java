package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Eşya türü (type) filtre menüsü — sort butonuna sağ tıklayınca açılır.
 * <p>
 * type.yml'deki tüm türleri ikon + ad + lore ile listeler; bir tür seçilince
 * ana menü o türe göre filtrelenerek yeniden açılır. Ek olarak "Tümü"
 * (filtre sıfırla) ve "Diğer" (hiçbir türe uymayan eşyalar) seçenekleri sunar.
 */
public class TypeFilterGUI extends BaseMenu {

    /** Tür seçim geri çağrısı — {@code typeId} boş ise "Diğer", {@code null} ise "Tümü". */
    public interface TypeSelector {
        void onSelect(Player player, String typeId);
    }

    /** İçerik slotları — tür ikonlarının yerleştirileceği 7 sütunlu grid. */
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final AuctionConfig config;
    private final TypeSelector selector;
    private Player viewer;

    public TypeFilterGUI(AuctionConfig config, TypeSelector selector) {
        super("auction_type_filter", "&8&l» <gradient:#FFB74D:#FFD54F>ᴛüʀ ꜰɪʟᴛʀᴇꜱɪ</gradient> &8&l«", 6);
        this.config = config;
        this.selector = selector;
    }

    @Override
    protected void onOpen(Player player) {
        this.viewer = player;
        clear();

        // Çerçeve
        for (int slot : new int[]{
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17,
                18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53
        }) {
            setItem(slot, MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        // Başlık / geri
        setItem(4, MenuItem.builder(Material.PAPER)
                .name("&#F5F5F5&lᴛüʀ ꜰɪʟᴛʀᴇꜱɪ")
                .lore("&#8c8c8c•  &#FFD54FTür &#8c8c8c— &#F5F5F5görüntülenecek eşyaları filtrele")
                .lore("&#8c8c8c•  &#55FF55Sol Tık &#8c8c8c— türü seç / uygula")
                .build());

        // Geri butonu
        setItem(45, MenuItem.builder(Material.ARROW)
                .name("&#F5F5F5&l◀ &#8c8c8cGeri")
                .lore("&#8c8c8c•  &#55FF55Sol Tık &#8c8c8c— ana menüye dön")
                .onClick(e -> {
                    close(player);
                    if (selector != null) selector.onSelect(player, null);
                })
                .build());

        // Tümü / filtreyi sıfırla
        setItem(49, MenuItem.builder(Material.BARRIER)
                .name("&#FF5555&lTümü")
                .lore("&#8c8c8c•  &#55FF55Sol Tık &#8c8c8c— tür filtresini kaldır")
                .onClick(e -> {
                    close(player);
                    if (selector != null) selector.onSelect(player, null);
                })
                .build());

        // Tür listesi
        java.util.List<AuctionConfig.ItemType> types = config.getTypes();
        int idx = 0;
        for (AuctionConfig.ItemType t : types) {
            if (idx >= CONTENT_SLOTS.length) break;
            int slot = CONTENT_SLOTS[idx++];
            var b = MenuItem.Builder.of(t.icon(), t.texture())
                    .name(t.name())
                    .lore("&#8c8c8c•  &#FFD54Fİçerik &#8c8c8c— &#F5F5F5" + t.materials().size() + " eşya türü")
                    .lore("")
                    .lore("&#8c8c8c•  &#55FF55Sol Tık &#8c8c8c— bu türü filtrele");
            if (t.glow()) b.glow(true);
            if (t.hideFlags()) b.hideFlags(true);
            String id = t.id();
            b.onClick(e -> {
                close(player);
                if (selector != null) selector.onSelect(player, id);
            });
            setItem(slot, b.build());
        }

        // Diğer (hiçbir türe uymayan eşyalar) — yalnızca yeterli slot varsa ekle
        if (idx < CONTENT_SLOTS.length) {
            int slot = CONTENT_SLOTS[idx];
            setItem(slot, MenuItem.builder(Material.PAPER)
                    .name("&7Diğer")
                    .lore("&#8c8c8c•  &#FFD54FTür &#8c8c8c— &#F5F5F5hiçbir türe uymayan eşyalar")
                    .lore("")
                    .lore("&#8c8c8c•  &#55FF55Sol Tık &#8c8c8c— bu türü filtrele")
                    .onClick(e -> {
                        close(player);
                        if (selector != null) selector.onSelect(player, "");
                    })
                    .build());
        }
    }

    @Override
    protected void onClose(Player player) {
        this.viewer = null;
    }

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        // Item'lar kendi tıklama handler'larını MenuItem.Builder.onClick ile çözer.
        // Boş/çerçeve slot tıklamaları otomatik iptal edilir (BaseMenu.dispatchClick).
    }
}
