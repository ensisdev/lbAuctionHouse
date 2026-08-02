package dev.ensisdev.lbauctionhouse.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sayfalı liste menüleri için abstract base sınıf.
 * <p>
 * Alt sınıflar {@link #getPageItems(int)} ile her sayfa için içerik listesini
 * döndürür, {@link #slotToIndex(int)} ile slot numarasını liste index'ine çevirir.
 * Navigation butonları (önceki/sayfa/bilgi/sonraki) otomatik eklenir.
 * <p>
 * Kullanım:
 * <pre>
 * PaginatedMenu menu = new PaginatedMenu("list_menu", "&8Items", 6, 18, 44) {
 *     protected List&lt;MenuItem&gt; getPageItems(int page) { ... }
 * };
 * menu.open(player);
 * </pre>
 */
public abstract class PaginatedMenu extends BaseMenu {

    /** Varsayılan nav bar slotları (en alt satır). */
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 50;

    private final int contentStart;
    private final int contentEnd;   // exclusive
    private final int itemsPerPage;

    private int currentPage = 0;

    /**
     * @param id menü ID'si
     * @param title başlık
     * @param rows envanter satır sayısı
     * @param contentStart içeriğin başladığı slot (örn: 18)
     * @param contentEnd içeriğin bittiği slot (exclusive, örn: 45)
     */
    protected PaginatedMenu(String id, String title, int rows, int contentStart, int contentEnd) {
        super(id, title, rows);
        this.contentStart = contentStart;
        this.contentEnd = contentEnd;
        this.itemsPerPage = contentEnd - contentStart;
    }

    @Override
    protected final void onOpen(Player player) {
        currentPage = 0;
        renderPage();
    }

    @Override
    protected final void onClose(Player player) {
        // Alt sınıflar override edebilir
    }

    @Override
    protected final void onClick(InventoryClickEvent event, MenuItem item) {
        int slot = event.getSlot();

        if (slot == SLOT_PREVIOUS) {
            if (currentPage > 0) {
                currentPage--;
                renderPage();
            }
        } else if (slot == SLOT_NEXT) {
            if (hasNextPage()) {
                currentPage++;
                renderPage();
            }
        } else if (slot >= contentStart && slot < contentEnd) {
            int index = slotToIndex(slot);
            if (index >= 0) {
                onClickItem(event, index, currentPage);
            }
        } else {
            onClickOutsideContent(event, slot);
        }
    }

    /**
     * Mevcut sayfayı yeniden çizer.
     */
    private void renderPage() {
        clear();

        // Navigation bar — her zaman son satır
        setItem(SLOT_PREVIOUS, createNavigationButton(currentPage > 0, "previous"));
        setItem(SLOT_PAGE_INFO, createPageInfo(currentPage));
        setItem(SLOT_NEXT, createNavigationButton(hasNextPage(), "next"));

        // İçerik
        List<MenuItem> pageItems = getPageItems(currentPage);
        int slot = contentStart;
        for (int i = 0; i < itemsPerPage && i < pageItems.size(); i++, slot++) {
            setItem(slot, pageItems.get(i));
        }
    }

    private boolean hasNextPage() {
        return !getPageItems(currentPage + 1).isEmpty();
    }

    /**
     * Slot numarasını liste index'ine çevirir.
     * İçerik satırında satır/sütun farkı varsa override edin.
     */
    protected int slotToIndex(int slot) {
        return slot - contentStart;
    }

    // ---- Navigation item builders (override edilebilir) ----

    /**
     * Önceki/sonraki butonları.
     */
    protected MenuItem createNavigationButton(boolean enabled, String direction) {
        if (direction.equals("previous")) {
            return MenuItem.builder(enabled ? Material.ARROW : Material.BARRIER)
                    .name(enabled ? "&a&l« Previous Page" : "&cNo previous page")
                    .build();
        } else {
            return MenuItem.builder(enabled ? Material.ARROW : Material.BARRIER)
                    .name(enabled ? "&a&lNext Page »" : "&cNo next page")
                    .build();
        }
    }

    /**
     * Sayfa bilgisi item'i.
     */
    protected MenuItem createPageInfo(int page) {
        return MenuItem.builder(Material.PAPER)
                .name("&ePage &f" + (page + 1))
                .build();
    }

    // ---- Abstract ----

    /**
     * Belirtilen sayfadaki item listesini döndürür.
     * Sonraki sayfa boşsa pagination durur.
     */
    protected abstract List<MenuItem> getPageItems(int page);

    /**
     * İçerik alanındaki bir item'a tıklandığında çağrılır.
     * @param event tıklama event'i
     * @param index item'in listedeki index'i (0-based)
     * @param page içinde bulunulan sayfa numarası
     */
    protected abstract void onClickItem(InventoryClickEvent event, int index, int page);

    /**
     * İçerik alanı dışında (nav bar, border vs.) tıklanırsa çağrılır.
     */
    protected void onClickOutsideContent(InventoryClickEvent event, int slot) {
        // Varsayılan: sessizce yoksay
    }
}
