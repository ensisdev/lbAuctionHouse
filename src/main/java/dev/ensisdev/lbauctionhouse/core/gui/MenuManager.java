package dev.ensisdev.lbauctionhouse.core.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Menü yöneticisi. InventoryClickEvent'i dinler ve tıklamaları
 * ilgili {@link BaseMenu} örneğine yönlendirir.
 * <p>
 * Ayrıca hangi oyuncunun hangi menüyü açtığını takip eder.


 */
public class MenuManager implements Listener {

    private static MenuManager instance;

    private final LbAuctionHouse plugin;
    private final Logger logger;
    private final Map<UUID, BaseMenu> openMenus;

    public MenuManager(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.openMenus = new ConcurrentHashMap<>();
        instance = this;
    }

    /**
     * Statik singleton — addon'ların ve BaseMenu'nün MenuManager'a erişmesi için.
     */
    public static MenuManager getInstance() {
        return instance;
    }

    /**
     * Event listener'ı kaydeder.
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("MenuManager registered.");
    }

    /**
     * Bir menüyü takibe alır.
     */
    public void track(BaseMenu menu, UUID playerUUID) {
        openMenus.put(playerUUID, menu);
    }

    /**
     * Bir oyuncunun takibini kaldırır.
     */
    public void untrack(UUID playerUUID) {
        openMenus.remove(playerUUID);
    }

    /**
     * Bir oyuncunun açık menüsü var mı?
     */
    public boolean hasOpenMenu(UUID playerUUID) {
        return openMenus.containsKey(playerUUID);
    }

    /**
     * Bir oyuncunun açık menüsünü döndürür (varsa).
     */
    public BaseMenu getOpenMenu(UUID playerUUID) {
        return openMenus.get(playerUUID);
    }

    /**
     * Tüm açık menüleri kapatır (plugin shutdown'da çağrılır).
     */
    public void closeAll() {
        for (Map.Entry<UUID, BaseMenu> entry : openMenus.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().close(player);
            }
        }
        openMenus.clear();
    }

    /**
     * Tüm açık menüleri yeniden renderlar (feature toggle değişiminde).
     * <p>
     * Oyuncu hâlâ aynı menüyü açık tutar — stok, slot ve görsel değişiklikler anında uygulanır.
     *
     * @return yenilenen menü sayısı
     */
    public int refreshAllOpen() {
        int count = 0;
        for (Map.Entry<UUID, BaseMenu> entry : openMenus.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().refresh(player);
                count++;
            }
        }
        return count;
    }

    /**
     * Tüm açık menü haritasının salt-okunur anlık görüntüsü (debug/admin için).
     */
    public Map<UUID, BaseMenu> snapshot() {
        return java.util.Collections.unmodifiableMap(openMenus);
    }

    // ---- Events ----
    // ÇİFT KATMAN: LOWEST (erken iptal) + HIGHEST (tekrar iptal)
    // Başka pluginler araya girip uncancel yapsa bile HIGHEST'te tekrar iptal ederiz.

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCancelLowest(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openMenus.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCancelHighest(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        BaseMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) return;
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        if (event.getClickedInventory() == null) return;

        if (event.getClickedInventory().equals(player.getOpenInventory().getTopInventory())) {
            // Üst (menü) envanteri tıklaması
            menu.dispatchClick(event);
        } else if (menu.allowBottomClicks()) {
            // Oyuncunun KENDİ envanterine tıklaması — yalnızca izin veren menülere iletilir.
            // (Örn: SellGUI envanterden eşya alabilmek için bunu kullanır.)
            menu.dispatchBottomClick(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDragLowest(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (openMenus.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragHighest(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (openMenus.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        BaseMenu menu = openMenus.get(player.getUniqueId());
        if (menu != null) {
            menu.dispatchClose(event);
        }
    }
}
