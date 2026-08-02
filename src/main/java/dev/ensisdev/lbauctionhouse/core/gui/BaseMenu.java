package dev.ensisdev.lbauctionhouse.core.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tüm özel menüler için abstract base sınıf.
 * <p>
 * Alt sınıflar {@link #onOpen(Player)}, {@link #onClose(Player)} ve
 * {@link #onClick(InventoryClickEvent, MenuItem)} metodlarını implemente eder.
 * Menü içindeki slot'lara {@link #setItem(int, MenuItem)} ile item yerleştirilir.
 * <p>
 * Kullanım:
 * <pre>
 * BaseMenu menu = new BaseMenu("my_menu", "&8My Menu", 3) {
 *     protected void onOpen(Player p) {
 *         setItem(13, MenuItem.builder(Material.DIAMOND).name("Hi").build());
 *     }
 * };
 * menu.open(player);
 * </pre>
 */
public abstract class BaseMenu {

    private final String id;
    private Component title;
    private final int size; // satır sayısı * 9
    private final Map<Integer, MenuItem> items;
    private final Map<UUID, Inventory> openInventories;

    private MenuManager manager;

    protected BaseMenu(String id, String title, int rows) {
        this.id = id;
        this.title = deserializeTitle(title);
        this.size = rows * 9;
        this.items = new HashMap<>();
        this.openInventories = new HashMap<>();
    }

    /**
     * GUI başlığını bir sonraki açılıştan önce değiştirir (dinamik başlıklar için).
     * {@code &} renk kodları desteklenir.
     */
    protected void setDynamicTitle(String newTitle) {
        this.title = deserializeTitle(newTitle);
    }

    private static Component deserializeTitle(String title) {
        return LegacyComponentSerializer.legacySection()
                .deserialize(title.replace('&', net.md_5.bungee.api.ChatColor.COLOR_CHAR));
    }

    // ---- Public API ----

    /**
     * Menüyü oyuncuya açar.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, size, title);
        onOpen(player);

        // Item'ları envantere yerleştir
        for (Map.Entry<Integer, MenuItem> entry : items.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue().getItem());
        }

        player.openInventory(inv);
        openInventories.put(player.getUniqueId(), inv);

        // Doğrudan LbAuctionHouse.getInstance() üzerinden MenuManager'a eriş
        // Bu yöntem classloader sorunu yaşamaz
        LbAuctionHouse core = LbAuctionHouse.getInstance();
        MenuManager mm = core != null ? core.getMenuManager() : null;
        if (mm != null) {
            mm.track(this, player.getUniqueId());
        } else if (manager != null) {
            manager.track(this, player.getUniqueId());
        }
    }

    /**
     * Menüyü kapatır.
     */
    public void close(Player player) {
        UUID uuid = player.getUniqueId();
        if (openInventories.containsKey(uuid)) {
            onClose(player);
            openInventories.remove(uuid);
            LbAuctionHouse core = LbAuctionHouse.getInstance();
            MenuManager mm = core != null ? core.getMenuManager() : null;
            if (mm != null) {
                mm.untrack(uuid);
            } else if (manager != null) {
                manager.untrack(uuid);
            }
            player.closeInventory();
        }
    }

    /**
     * Açık envanteri {@code items} haritasına göre YENİDEN doldurur.
     * <p>
     * {code setItem}/{@code clear} yalnızca iç haritayı değiştirir; oyuncunun
     * ekranda gördüğü envanteri güncellemek için bu metodun çağrılması gerekir.
     * (GUI yeniden açılmadan miktar/fiyat gibi dinamik içerikler güncellenir.)
     */
    public void refresh(Player player) {
        Inventory inv = openInventories.get(player.getUniqueId());
        if (inv == null) return;
        inv.clear();
        for (Map.Entry<Integer, MenuItem> entry : items.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue().getItem());
        }
    }

    /**
     * Slot'a item yerleştirir.
     */
    protected void setItem(int slot, MenuItem menuItem) {
        items.put(slot, menuItem);
    }

    /**
     * Slot aralığına tek bir item doldurur (boş slot'lar için).
     */
    protected void fillRange(int startInclusive, int endExclusive, MenuItem menuItem) {
        for (int i = startInclusive; i < endExclusive; i++) {
            items.putIfAbsent(i, menuItem);
        }
    }

    /**
     * Tüm boş slotları belirtilen item ile doldurur.
     */
    protected void fillEmpty(MenuItem menuItem) {
        for (int i = 0; i < size; i++) {
            items.putIfAbsent(i, menuItem);
        }
    }

    /**
     * Belirtilen slot'taki item'ı kaldırır.
     */
    protected void removeItem(int slot) {
        items.remove(slot);
    }

    /**
     * Envanteri temizler (tüm slot'lar).
     */
    protected void clear() {
        items.clear();
    }

    /**
     * Menü ID'si.
     */
    public String getId() {
        return id;
    }

    /**
     * Envanter boyutu (slot sayısı).
     */
    public int getSize() {
        return size;
    }

    // ---- Internal ----

    void setManager(MenuManager manager) {
        this.manager = manager;
    }

    boolean isOwner(UUID uuid) {
        return openInventories.containsKey(uuid);
    }

    void dispatchClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        MenuItem menuItem = items.get(slot);
        if (menuItem != null) {
            event.setCancelled(true);
            onClick(event, menuItem);
            menuItem.handleClick(event);
        } else {
            event.setCancelled(true);
            onClick(event, null);
        }
    }

    /**
     * Alt envanter (oyuncunun kendi envanteri) tıklamalarının {@link #onClick}'e
     * iletilip iletilmeyeceği.
     * <p>
     * Varsayılan {@code false} — diğer menülerin alt envanter tıklamasından
     * etkilenmemesi için. Envanterden eşya alınması gereken menüler (örn: SellGUI)
     * bunu {@code true} yapar.
     */
    protected boolean allowBottomClicks() {
        return false;
    }

    /**
     * Alt envanter tıklamasını menüye iletir. (MenuManager tarafından çağrılır;
     * yalnızca {@link #allowBottomClicks()} true olan menüler için.)
     */
    void dispatchBottomClick(InventoryClickEvent event) {
        event.setCancelled(true);
        onClick(event, null);
    }

    void dispatchClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openInventories.remove(player.getUniqueId());
            onClose(player);
            LbAuctionHouse core = LbAuctionHouse.getInstance();
            MenuManager mm = core != null ? core.getMenuManager() : null;
            if (mm != null) {
                mm.untrack(player.getUniqueId());
            } else if (manager != null) {
                manager.untrack(player.getUniqueId());
            }
        }
    }

    // ---- Abstract hooks ----

    /**
     * Menü açılmadan hemen önce çağrılır. Item'ları burada yerleştirin.
     */
    protected abstract void onOpen(Player player);

    /**
     * Menü kapatıldığında çağrılır.
     */
    protected abstract void onClose(Player player);

    /**
     * Bir slot'a tıklandığında çağrılır.
     * @param event tıklama event'i
     * @param item tıklanan slot'taki MenuItem (yoksa null)
     */
    protected abstract void onClick(InventoryClickEvent event, MenuItem item);
}
