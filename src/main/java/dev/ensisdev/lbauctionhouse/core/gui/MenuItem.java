package dev.ensisdev.lbauctionhouse.core.gui;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    /**
     * Base64 texture'lı oyuncu kafasından builder oluşturur.
     * <p>
     * Kullanım: {@code MenuItem.builder("eyJ0ZXh0dXJlcyI6...")}
     * <p>
     * Texture Boş ise düz {@link Material#PLAYER_HEAD} döner.
     *
     * @param base64Texture Minecraft skin texture'ının base64 değeri
     */
    public static Builder builder(String base64Texture) {
        Builder b = new Builder(new ItemStack(Material.PLAYER_HEAD));
        if (base64Texture != null && !base64Texture.isEmpty()) {
            b.texture(base64Texture);
        }
        return b;
    }

    // ----------------------------------------------------------------

    public static class Builder {
        private final ItemStack item;
        private Consumer<InventoryClickEvent> handler;
        /** Parıltı için kullanılan enchantment, glow(false) ile kaldırırken kullanılır. */
        private Enchantment glowEnchant;

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

        /**
         * {@code CustomModelData} değeri (Resource pack destekli özel modeller için).
         * Paper/Spigot 1.14+ gereklidir; daha eski sürümlerde sessizce yok sayılır.
         */
        public Builder customModelData(int data) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                try {
                    meta.setCustomModelData(data);
                    item.setItemMeta(meta);
                } catch (Throwable ignored) {
                    // < 1.14
                }
            }
            return this;
        }

        /**
         * Enchantment parıltısı ekler (glow). Açıksa UNBREAKING + ITEM_FLAGS HIDE_ENCHANTS uygular;
         * kapalıysa UNBREAKING kaldırılır (flag her zaman kalır, görünmez).
         */
        public Builder glow(boolean glow) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                try {
                    if (glow) {
                        Enchantment unbreaking = glowEnchant;
                        if (unbreaking == null) {
                            try {
                                unbreaking = Enchantment.getByKey(
                                        org.bukkit.NamespacedKey.minecraft("unbreaking"));
                                if (unbreaking == null) {
                                    //noinspection deprecation
                                    unbreaking = org.bukkit.enchantments.Enchantment.DURABILITY;
                                }
                            } catch (Throwable t) {
                                //noinspection deprecation
                                unbreaking = org.bukkit.enchantments.Enchantment.DURABILITY;
                            }
                            glowEnchant = unbreaking;
                        }
                        if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    } else if (glowEnchant != null) {
                        meta.removeEnchant(glowEnchant);
                    }
                    item.setItemMeta(meta);
                } catch (Throwable ignored) {
                    // Eski API'ler — sessizce geç
                }
            }
            return this;
        }

        /**
         * Tüm item flag'lerini (attributes, enchants, unbreakable, dye, ... ) gizler.
         */
        public Builder hideFlags(boolean all) {
            if (!all) return this;
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                try {
                    for (ItemFlag flag : ItemFlag.values()) meta.addItemFlags(flag);
                    item.setItemMeta(meta);
                } catch (Throwable ignored) {
                    // Eski API — sessizce geç
                }
            }
            return this;
        }

        /**
         * Bu item'i base64 texture'lı oyuncu kafasına dönüştürür.
         * <p>
         * Paper API {@code PlayerProfile} kullanır — desteklenmeyen
         * sunucularda sessizce yutulur, item PLAYER_HEAD olarak kalır.
         *
         * @param base64Texture Minecraft skin texture'ının base64 değeri
         */
        public Builder texture(String base64Texture) {
            if (base64Texture == null || base64Texture.isEmpty()) return this;
            item.setType(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
            if (meta != null) {
                try {
                    UUID id = UUID.nameUUIDFromBytes(base64Texture.getBytes());
                    com.destroystokyo.paper.profile.PlayerProfile profile =
                            org.bukkit.Bukkit.createProfile(id, null);
                    profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64Texture));
                    meta.setPlayerProfile(profile);
                    item.setItemMeta(meta);
                } catch (Exception ignored) {
                    // Paper olmayan sunucu — sessizce yut
                }
            }
            return this;
        }

        /**
         * {@code nav.material()} + opsiyonel {@code nav.texture()} ile builder başlatır.
         * <p>
         * Navigation item'ları için kolay helper: texture boş değilse otomatik
         * {@link Material#PLAYER_HEAD} + base64 texture uygular; boşsa material kalır.
         *
         * @param material fallback material (texture varsa otomatik PLAYER_HEAD olur)
         * @param texture  base64 texture değeri (boş/null olabilir)
         */
        public static Builder of(Material material, String texture) {
            Builder b;
            if (texture != null && !texture.isEmpty()) {
                b = new Builder(new ItemStack(Material.PLAYER_HEAD));
                b.texture(texture);
            } else {
                b = new Builder(new ItemStack(material));
            }
            return b;
        }

        public Builder onClick(Consumer<InventoryClickEvent> handler) {
            this.handler = handler;
            return this;
        }

        public MenuItem build() {
            return new MenuItem(item, handler);
        }

        private String color(String text) {
            return dev.ensisdev.lbauctionhouse.util.ColorUtil.colorize(text);
        }
    }
}
