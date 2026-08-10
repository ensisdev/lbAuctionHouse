package dev.ensisdev.lbauctionhouse.util;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Toplu paket (fıçı) yardımcıları.
 * <p>
 * Birden fazla eşya, tek bir {@code BARREL} ItemStack'inin kalıcı veri alanına
 * (PDC) kodlanır. Böylece tek eşya taşıyan mevcut ilan/veritabanı yapısıyla
 * birlikte çalışır — BARREL item'ı normal bir eşya gibi depolanır, DB'de
 * ek sütun gerekmez. Alıcı satın alınca eşyalar paketten açılır.
 */
public final class BundleItems {

    private static NamespacedKey key;
    private static int maxBundleItems = 45;

    private BundleItems() {}

    /** Plugin başlangıcında çağrılmalı (LbAuctionHouse.onAddonEnable). */
    public static void init(LbAuctionHouse plugin) {
        if (key == null) {
            key = new NamespacedKey(plugin, "lbsmp_bundle");
        }
        maxBundleItems = Math.max(1, plugin.getAuctionConfig().getMaxBundleItems());
    }

    public static boolean isBundle(ItemStack item) {
        return item != null && item.getType() == Material.BARREL && hasKey(item);
    }

    private static boolean hasKey(ItemStack item) {
        if (key == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key);
    }

    /**
     * Eşyaları tek bir BARREL item'ına paketler.
     */
    public static ItemStack createBundle(List<ItemStack> items, String displayName) {
        ItemStack barrel = new ItemStack(Material.BARREL);
        ItemMeta meta = barrel.getItemMeta();
        if (meta == null || key == null) return barrel;

        List<String> lore = new ArrayList<>();
        List<ItemStack> packed = new ArrayList<>();
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            packed.add(it.clone());
            String n = it.getItemMeta().hasDisplayName()
                    ? it.getItemMeta().getDisplayName() : it.getType().name();
            lore.add("§7" + it.getAmount() + "x §f" + n);
            if (packed.size() >= maxBundleItems) break;
        }

        meta.setDisplayName(displayName != null ? displayName : "§6§lToplu Paket");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, encode(packed));
        barrel.setItemMeta(meta);
        return barrel;
    }

    /**
     * Paketteki eşyaları açar.
     */
    public static List<ItemStack> unpack(ItemStack bundle) {
        if (key == null) return List.of();
        ItemMeta meta = bundle.getItemMeta();
        if (meta == null) return List.of();
        String data = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return decode(data);
    }

    private static String encode(List<ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack it : items) {
            try {
                byte[] bytes = it.serializeAsBytes();
                if (sb.length() > 0) sb.append("\n");
                sb.append(Base64.getEncoder().encodeToString(bytes));
            } catch (Exception ignored) {
                // tek eşya atlanır, diğerleri paketlenir
            }
        }
        return sb.toString();
    }

    private static List<ItemStack> decode(String data) {
        List<ItemStack> result = new ArrayList<>();
        if (data == null || data.isEmpty()) return result;
        for (String part : data.split("\n")) {
            if (part.isEmpty()) continue;
            try {
                ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(part));
                if (it != null && it.getType() != Material.AIR) result.add(it);
            } catch (Exception ignored) {
                // bozuk giriş atlanır
            }
        }
        return result;
    }
}
