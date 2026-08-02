package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code gui/*.yml} dosyalarını okuyarak menü layout'larını yükler.
 * <p>
 * Her GUI dosyası şunları içerir: title, rows, border, navigation butonları,
 * content slot aralığı, lore format şablonları.
 * <p>
 * Tüm değerler config'den okunur — hardcode yok.
 */
public class GUILayoutLoader {

    private final LbAuctionHouse plugin;
    private final AuctionConfig config;
    private final Map<String, GUILayout> cache;

    public GUILayoutLoader(LbAuctionHouse plugin, AuctionConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.cache = new HashMap<>();
    }

    /**
     * Belirtilen GUI dosyasının layout'unu yükler (cache'ler).
     */
    public GUILayout load(String fileName) {
        if (cache.containsKey(fileName)) return cache.get(fileName);

        File file = new File(plugin.getDataFolder(), "gui/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("gui/" + fileName, false);
            file = new File(plugin.getDataFolder(), "gui/" + fileName);
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        GUILayout layout = parseLayout(yaml);
        cache.put(fileName, layout);
        return layout;
    }

    /**
     * Cache'i temizler (reload için).
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * JAR içindeki {@code gui/*.yml} dosyalarının TÜMÜNÜ diske kopyalar ve cache'ler.
     * <p>
     * İlk açılışta yalnızca bir menü açıldığında dosya oluşmasın diye (lazy yükleme)
     * başlatma sırasında çağrılır. Böylece sunucu sahibi tüm GUI layout dosyalarını
     * (main-menu.yml, my-listings.yml, confirm.yml, collection-box.yml, ...)
     * ilk açılışta hazır görür.
     * <p>
     * Dosya adları hardcode edilmez — JAR'ın {@code gui/} dizini taranır, ileride
     * eklenen yeni GUI dosyaları da otomatik kapsanır.
     *
     * @return başarıyla yüklenen (ve diskte oluşturulan) GUI dosyası sayısı
     */
    public int preloadAll() {
        int count = 0;
        java.util.jar.JarFile jar = null;
        try {
            // Paper 1.20.1 API'de getJarFile() yoktur ve JavaPlugin.getFile() protected'tır.
            // LbAuctionHouse.getPluginJarFile() public köprüsü üzerinden erişilir.
            jar = new java.util.jar.JarFile(plugin.getPluginJarFile());
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("gui/") && name.endsWith(".yml") && !entry.isDirectory()) {
                    String fileName = name.substring("gui/".length());
                    try {
                        load(fileName); // diske kopyalar + parse eder + cache'ler
                        count++;
                    } catch (Exception e) {
                        plugin.getLogger().severe("[GUILayoutLoader] '" + fileName + "' ön yüklenirken hata:");
                        e.printStackTrace(); // TAM stack trace — yutulmaz
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[GUILayoutLoader] gui dosyaları ön yüklenirken hata:");
            e.printStackTrace(); // TAM stack trace — yutulmaz
        } finally {
            try {
                if (jar != null) jar.close();
            } catch (java.io.IOException ignored) {}
        }
        return count;
    }

    private GUILayout parseLayout(FileConfiguration yaml) {
        String title = yaml.getString("title", "&8Menu");
        int rows = yaml.getInt("rows", 6);

        // Border
        BorderConfig border = null;
        ConfigurationSection bs = yaml.getConfigurationSection("border");
        if (bs != null) {
            border = new BorderConfig(
                    getMaterial(bs.getString("material", "BLACK_STAINED_GLASS_PANE")),
                    bs.getString("name", " "),
                    parseSlotList(bs.getString("slots", ""))
            );
        }

        // Navigation items
        List<NavItem> navItems = new ArrayList<>();
        ConfigurationSection nav = yaml.getConfigurationSection("navigation");
        if (nav != null) {
            for (String key : nav.getKeys(false)) {
                ConfigurationSection item = nav.getConfigurationSection(key);
                if (item == null) continue;
                navItems.add(new NavItem(
                        key,
                        item.getInt("slot", -1),
                        getMaterial(item.getString("material", "STONE")),
                        item.getString("name", " "),
                        item.getStringList("lore"),
                        item.getString("left-click", ""),
                        item.getString("right-click", "")
                ));
            }
        }

        // Search
        SearchConfig search = null;
        ConfigurationSection ss = yaml.getConfigurationSection("search");
        if (ss != null && config.isSearchEnabled()) {
            search = new SearchConfig(
                    ss.getInt("slot", -1),
                    getMaterial(ss.getString("material", "OAK_SIGN")),
                    ss.getString("name", "&bAra")
            );
        }

        // Sorting
        SortConfig sort = null;
        ConfigurationSection srt = yaml.getConfigurationSection("sorting");
        if (srt != null && config.isSortEnabled()) {
            sort = new SortConfig(
                    srt.getInt("slot", -1),
                    getMaterial(srt.getString("material", "HOPPER")),
                    srt.getString("name", "&dSırala"),
                    srt.getStringList("options")
            );
        }

        // Content slots
        List<Integer> contentSlots = parseSlotList(yaml.getString("content-slots", ""));

        // Lore format
        List<String> loreFormat = yaml.getStringList("lore-format");

        return new GUILayout(title, rows, border, navItems, search, sort,
                contentSlots, loreFormat);
    }

    private Material getMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    private List<Integer> parseSlotList(String input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        List<Integer> slots = new ArrayList<>();
        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                for (int i = start; i <= end; i++) slots.add(i);
            } else {
                slots.add(Integer.parseInt(part));
            }
        }
        return slots;
    }

    // ---- Inner Records ----

    public record GUILayout(
            String title, int rows,
            BorderConfig border,
            List<NavItem> navItems,
            SearchConfig search,
            SortConfig sort,
            List<Integer> contentSlots,
            List<String> loreFormat
    ) {}

    public record BorderConfig(Material material, String name, List<Integer> slots) {}

    public record NavItem(
            String id, int slot, Material material,
            String name, List<String> lore,
            String leftClickAction, String rightClickAction
    ) {}

    public record SearchConfig(int slot, Material material, String name) {}
    public record SortConfig(int slot, Material material, String name, List<String> options) {}
}
