package dev.ensisdev.lbauctionhouse.core.gui;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menü içindeki bir slot'u temsil eder.
 * İçinde bir {@link ItemStack} ve tıklandığında çalışacak bir handler barındırır.
 * <p>
 * Builder pattern ile oluşturulur:
 * <pre>
 * MenuItem.builder(Material.DIAMOND)
 *     .name("&bPremium")
 *     .lore("&7Click to activate")
 *     .onClick(e -> player.sendMessage("Activated!"))
 *     .build();
 * </pre>
 */
public class MenuItem {

    private final ItemStack item;
    private final Consumer<InventoryClickEvent> handler;

    private MenuItem(ItemStack item, Consumer<InventoryClickEvent> handler) {
        this.item = item;
        this.handler = handler;
    }

    /**
     * Bu item'in kopyasını döndürür.
     */
    public ItemStack getItem() {
        return item.clone();
    }

    /**
     * Tıklama handler'ını çalıştırır.
     */
    public void handleClick(InventoryClickEvent event) {
        if (handler != null) {
            handler.accept(event);
        }
    }

    /**
     * Builder oluşturur.
     */
    public static Builder builder(Material material) {
        return new Builder(new ItemStack(material));
    }

    /**
     * Builder oluşturur (var olan ItemStack'ten).
     */
    public static Builder builder(ItemStack item) {
        return new Builder(item.clone());
    }

    // ----------------------------------------------------------------

    public static class Builder {
        private final ItemStack item;
        private Consumer<InventoryClickEvent> handler;

        private Builder(ItemStack item) {
            this.item = item;
        }

        public Builder name(String displayName) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(displayName));
                item.setItemMeta(meta);
            }
            return this;
        }

        public Builder lore(String line) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(color(line));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return this;
        }

        public Builder lore(String... lines) {
            for (String line : lines) lore(line);
            return this;
        }

        public Builder amount(int amount) {
            item.setAmount(amount);
            return this;
        }

        public Builder onClick(Consumer<InventoryClickEvent> handler) {
            this.handler = handler;
            return this;
        }

        public MenuItem build() {
            return new MenuItem(item, handler);
        }

        private String color(String text) {
            return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
        }
    }
}
