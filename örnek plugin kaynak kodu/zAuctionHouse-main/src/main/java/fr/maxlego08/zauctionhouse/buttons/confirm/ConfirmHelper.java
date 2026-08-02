package fr.maxlego08.zauctionhouse.buttons.confirm;

import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;

public abstract class ConfirmHelper extends Button {

    protected final AuctionPlugin plugin;
    private final ItemStatus previous;
    private final ItemStatus next;

    public ConfirmHelper(AuctionPlugin plugin, ItemStatus previous, ItemStatus next) {
        this.plugin = plugin;
        this.previous = previous;
        this.next = next;
    }

    @Override
    public void onInventoryClose(@NonNull Player player, @NonNull InventoryEngine inventory) {
        super.onInventoryClose(player, inventory);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        // Si lors de la fermeture, le status est similaire à celui de l'ouverture, alors l'état n'a pas changé, et donc on doit mettre le prochain état de l'item.
        if (item.getStatus() == this.previous) {

            // Si l'item a expiré pendant que l'inventaire de confirmation était ouvert, on le déplace vers les items
            // expirés au lieu de le rediffuser comme disponible aux autres joueurs.
            if (processIfExpired(player, item)) return;

            item.setStatus(this.next);
            this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)
                .exceptionally(throwable -> {
                    this.plugin.getLogger().warning("Failed to notify item status change on inventory close: " + throwable.getMessage());
                    return null;
                });

            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            manager.updateListedItems(item, true, player);
        }
    }

    @Override
    public void onBackClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, @NonNull List<Inventory> oldInventories, @NonNull Inventory toInventory, int slot) {
        super.onBackClick(player, event, inventory, oldInventories, toInventory, slot);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        // Si l'item a expiré pendant que l'inventaire de confirmation était ouvert, on le déplace vers les items
        // expirés et on ne le rediffuse pas aux autres joueurs.
        if (processIfExpired(player, item)) return;

        item.setStatus(this.next);
        this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)
            .exceptionally(throwable -> {
                this.plugin.getLogger().warning("Failed to notify item status change on back click: " + throwable.getMessage());
                return null;
            });

        manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_LISTED);
        manager.updateListedItems(item, true, player);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        Item item = manager.getCache(player).get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) {
            manager.openMainAuction(player);
            return;
        }

        // R2: si l'item a expiré pendant que l'inventaire de confirmation était ouvert, on n'exécute pas l'action.
        // On déplace l'item vers les items expirés et on se contente de fermer l'inventaire (pas de réouverture de l'hôtel des ventes).
        if (processIfExpired(player, item)) {
            this.plugin.getScheduler().runAtEntity(player, w -> {
                if (player.isOnline()) player.closeInventory();
            });
            return;
        }

        onPostClick(player, event, inventory, slot, placeholders, manager, item);
    }

    /**
     * If the item's listing has expired, transition it out of the active listings using the lazy expiration path
     * ({@code LISTED -> EXPIRED}) and clear the acting player's list caches. Callers must stop their normal flow when
     * this returns {@code true}: the action must not run and the item must not be re-broadcast to other viewers.
     *
     * @param player the player interacting with the confirmation inventory
     * @param item   the item being confirmed
     * @return {@code true} if the item was expired and has been handled, {@code false} otherwise
     */
    private boolean processIfExpired(@NonNull Player player, @NonNull Item item) {
        if (!item.isExpired()) return false;

        var manager = this.plugin.getAuctionManager();
        if (item.getStatus() != ItemStatus.REMOVED && item.getStatus() != ItemStatus.DELETED) {
            manager.getExpireService().processExpiredItem(item, StorageType.LISTED);
        }
        manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
        return true;
    }

    protected abstract void onPostClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders, AuctionManager manager, Item item);
}
