package dev.ensisdev.lbauctionhouse.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.function.Consumer;

/**
 * Onay/iptal dialog menüsü.
 * <p>
 * Statik factory metotları ile kullanılır:
 * <pre>
 * ConfirmationMenu.create("&8Delete item?")
 *     .onConfirm(p -> deleteItem())
 *     .onCancel(p -> p.sendMessage("Cancelled"))
 *     .open(player);
 * </pre>
 */
public class ConfirmationMenu extends BaseMenu {

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    private Consumer<Player> onConfirm;
    private Consumer<Player> onCancel;

    private ConfirmationMenu(String id, String title) {
        super(id, title, 3);
    }

    @Override
    protected void onOpen(Player player) {
        setItem(SLOT_CONFIRM, MenuItem.builder(Material.LIME_WOOL)
                .name("&a&l✔ Confirm")
                .lore("&7Click to confirm")
                .build());

        setItem(SLOT_CANCEL, MenuItem.builder(Material.RED_WOOL)
                .name("&c&l✖ Cancel")
                .lore("&7Click to cancel")
                .build());

        // Boşlukları doldur
        fillEmpty(MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build());
    }

    @Override
    protected void onClose(Player player) {
        // Kapatıldı — cancel değil, sadece temizlik
    }

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == SLOT_CONFIRM) {
            close(player);
            if (onConfirm != null) {
                onConfirm.accept(player);
            }
        } else if (slot == SLOT_CANCEL) {
            close(player);
            if (onCancel != null) {
                onCancel.accept(player);
            }
        }
    }

    // ---- Factory ----

    /**
     * Yeni bir onay menüsü oluşturur.
     */
    public static Builder create(String title) {
        return new Builder(title);
    }

    // ---- Builder ----

    public static class Builder {
        private final String title;
        private Consumer<Player> onConfirm;
        private Consumer<Player> onCancel;

        private Builder(String title) {
            this.title = title;
        }

        public Builder onConfirm(Consumer<Player> action) {
            this.onConfirm = action;
            return this;
        }

        public Builder onCancel(Consumer<Player> action) {
            this.onCancel = action;
            return this;
        }

        public ConfirmationMenu build() {
            ConfirmationMenu menu = new ConfirmationMenu("confirm_" + System.identityHashCode(this), title);
            menu.onConfirm = this.onConfirm;
            menu.onCancel = this.onCancel;
            return menu;
        }

        public void open(Player player) {
            build().open(player);
        }
    }
}
