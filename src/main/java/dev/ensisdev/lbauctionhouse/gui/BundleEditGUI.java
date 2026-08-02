package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Toplu paket (fıçı) oluşturma GUI'si.
 * <p>
 * Oyuncu kendi envanterindeki eşyalara tıklayarak (veya sürükleyerek) pakete
 * ekler; "Tamam" ile eşyalar {@link dev.ensisdev.lbauctionhouse.util.BundleItems} ile
 * bir BARREL item'ına paketlenir.
 */
public class BundleEditGUI extends BaseMenu {

    private static final int GRID_END = 44;    // 0..44 → 45 slot
    private static final int DONE_SLOT = 45;
    private static final int CANCEL_SLOT = 46;
    private static final int INFO_SLOT = 53;

    private Player currentPlayer;
    private final List<ItemStack> items = new ArrayList<>();
    private Consumer<List<ItemStack>> onComplete;
    private Runnable onCancel;

    public BundleEditGUI() {
        super("auction_bundle", "&8&l» &6&l"+ dev.ensisdev.lbauctionhouse.util.SmallCaps.toSmallCaps("TOPLU PAKET")+" &8&l«", 6);
    }

    public void open(Player player, Consumer<List<ItemStack>> onComplete, Runnable onCancel) {
        this.currentPlayer = player;
        this.onComplete = onComplete;
        this.onCancel = onCancel;
        this.items.clear();
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        fillEmpty(MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        for (int i = 0; i < items.size() && i <= GRID_END; i++) {
            setItem(i, MenuItem.builder(items.get(i).clone()).build());
        }

        setItem(DONE_SLOT, MenuItem.builder(Material.LIME_WOOL)
                .name("&a&l✔ Tamam — Paketle")
                .lore("&7Eşyaları paketleyip sat")
                .build());
        setItem(CANCEL_SLOT, MenuItem.builder(Material.RED_WOOL)
                .name("&c&l✖ İptal")
                .lore("&7Satış GUI'sine dön")
                .build());
        setItem(INFO_SLOT, MenuItem.builder(Material.BOOK)
                .name("&eToplu Paket")
                .lore("&7Envanterinden eşyaları tıkla,")
                .lore("&7buraya eklensinler.", "")
                .lore("&f" + items.size() + "/" + (GRID_END + 1) + " eşya", "")
                .lore("&7Eklendi: " + items.stream().mapToInt(ItemStack::getAmount).sum() + " adet")
                .build());
    }

    @Override
    protected boolean allowBottomClicks() {
        return true;   // envanterden eşya alınabilir
    }

    @Override
    protected void onClose(Player player) {}

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        // Oyuncunun kendi envanterinden eşya ekle
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                event.setCancelled(true);
                addItem(clicked, event.isRightClick());
            }
            return;
        }

        int slot = event.getSlot();

        if (slot == DONE_SLOT) {
            close(currentPlayer);
            if (items.isEmpty()) {
                if (onCancel != null) onCancel.run();
            } else {
                if (onComplete != null) onComplete.accept(new ArrayList<>(items));
            }
            return;
        }
        if (slot == CANCEL_SLOT) {
            close(currentPlayer);
            if (onCancel != null) onCancel.run();
            return;
        }
        // Grid'deki eşyayı çıkar
        if (slot >= 0 && slot <= GRID_END && slot < items.size()) {
            items.remove(slot);
            updateDisplay();
        }
    }

    private void addItem(ItemStack clicked, boolean single) {
        if (items.size() >= GRID_END + 1) return;
        Material mat = clicked.getType();

        // DUPE KORUMASI: aynı eşyayı envanterdeki adetten fazla eklemeyi engelle.
        int playerHas = countInInventory(currentPlayer, mat);
        int alreadyAdded = items.stream()
                .filter(i -> i.getType() == mat)
                .mapToInt(ItemStack::getAmount).sum();
        int available = Math.max(0, playerHas - alreadyAdded);
        if (available <= 0) {
            currentPlayer.sendMessage("§cEnvanterinde yeterince " + mat.name() + " yok!");
            return;
        }

        ItemStack copy = clicked.clone();
        copy.setAmount(single ? 1 : Math.min(copy.getAmount(), available));
        items.add(copy);
        updateDisplay();
    }

    private int countInInventory(Player player, Material mat) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == mat) total += it.getAmount();
        }
        return total;
    }

    private void updateDisplay() {
        clear();
        onOpen(currentPlayer);
        refresh(currentPlayer);
    }
}
