package dev.ensisdev.lbauctionhouse.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eşya adlarını oyuncu dostu gösterir — {@code ENCHANTING_TABLE} yerine
 * "Enchanting Table" / "Büyü Masası" gibi.
 * <p>
 * Öncelik sırası:
 * <ol>
 *   <li>Eşyanın özel (custom) display name'i</li>
 *   <li>{@code material-names} config override'ı (sunucu sahibi Türkçe ad girebilir)</li>
 *   <li>Paper {@code Material#translationKey()} → Adventure GlobalTranslator (oyuncu/sunucu dili)</li>
 *   <li>Enum adının okunabilir hali (humanize fallback)</li>
 * </ol>
 */
public final class ItemNames {

    private static final Map<Material, String> OVERRIDES = new ConcurrentHashMap<>();

    private ItemNames() {}

    /** Config'ten okunan material adı override'ı (boşsa kaldırır). */
    public static void setOverride(Material material, String name) {
        if (material == null) return;
        if (name == null || name.trim().isEmpty()) OVERRIDES.remove(material);
        else OVERRIDES.put(material, name.trim());
    }

    public static void clearOverrides() {
        OVERRIDES.clear();
    }

    /**
     * Eşyanın gösterilecek adını döndürür.
     */
    public static String displayName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "§7?";
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        String override = OVERRIDES.get(item.getType());
        if (override != null) return override;
        return translationName(item);
    }

    /**
     * Paper {@code Material#translationKey()} + Adventure GlobalTranslator ile
     * yerelleştirilmiş adı çözer; başarısızsa humanize fallback kullanır.
     */
    private static String translationName(ItemStack item) {
        try {
            String key = item.getType().translationKey(); // Paper API
            Component translated = GlobalTranslator.get().translate(
                    Component.translatable(key), Locale.getDefault());
            String out = LegacyComponentSerializer.legacySection().serialize(translated);
            if (out != null && !out.isEmpty() && !out.equals(key)) return out;
        } catch (Throwable ignored) {
            // transcriptionKey/ GlobalTranslator bulunamazsa fallback
        }
        return humanize(item.getType().name());
    }

    /** "ENCHANTING_TABLE" → "Enchanting Table" */
    public static String humanize(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}