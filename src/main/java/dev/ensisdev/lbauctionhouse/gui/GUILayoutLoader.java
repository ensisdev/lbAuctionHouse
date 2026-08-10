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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        this.cache = new ConcurrentHashMap<>();
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
     * Tüm yüklenmiş layout'ların salt-okunur kopyası — admin GUI'leri için.
     * <p>
     * Anahtar, dosya adıdır (örn. {@code "main-menu.yml"}).
     */
    public Map<String, GUILayout> getLayouts() {
        return java.util.Collections.unmodifiableMap(cache);
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
                        // TAM stack trace yutulmaz; System.err yerine logger kullanılır (Nag uyarısı yok)
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "[GUILayoutLoader] '" + fileName + "' ön yüklenirken hata:", e);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "[GUILayoutLoader] gui dosyaları ön yüklenirken hata:", e);
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
            String borderTex = bs.getString("texture", "");
            Material borderMat = (borderTex != null && !borderTex.isEmpty())
                    ? Material.PLAYER_HEAD
                    : getMaterial(bs.getString("material", "BLACK_STAINED_GLASS_PANE"));
            border = new BorderConfig(
                    borderMat,
                    borderTex,
                    bs.getString("name", " "),
                    parseSlotListPublic(bs.getString("slots", "")),
                    bs.getInt("amount", 1),
                    bs.getInt("custom-model-data", 0),
                    bs.getBoolean("glow", false),
                    bs.getBoolean("hide-flags", false)
            );
        }

        // Background fill (boş slotları doldurur — border/content/nav hariç)
        BackgroundFillConfig backgroundFill = null;
        ConfigurationSection bfs = yaml.getConfigurationSection("background-fill");
        if (bfs != null) {
            String bTex = bfs.getString("texture", "");
            Material bMat = (bTex != null && !bTex.isEmpty())
                    ? Material.PLAYER_HEAD
                    : getMaterial(bfs.getString("material", "GRAY_STAINED_GLASS_PANE"));
            backgroundFill = new BackgroundFillConfig(
                    bMat,
                    bTex,
                    bfs.getString("name", " "),
                    bfs.getInt("amount", 1),
                    bfs.getInt("custom-model-data", 0),
                    bfs.getBoolean("glow", false),
                    bfs.getBoolean("hide-flags", false)
            );
        }

        // Navigation items
        List<NavItem> navItems = new ArrayList<>();
        ConfigurationSection nav = yaml.getConfigurationSection("navigation");
        if (nav != null) {
            for (String key : nav.getKeys(false)) {
                ConfigurationSection item = nav.getConfigurationSection(key);
                if (item == null) continue;
                String texture = item.getString("texture", "");
                Material material = (texture != null && !texture.isEmpty())
                        ? Material.PLAYER_HEAD
                        : getMaterial(item.getString("material", "STONE"));
                navItems.add(new NavItem(
                        key,
                        item.getInt("slot", -1),
                        material,
                        texture,
                        item.getString("name", " "),
                        item.getStringList("lore"),
                        item.getString("left-click", ""),
                        item.getString("right-click", ""),
                        item.getInt("amount", 1),
                        item.getInt("custom-model-data", 0),
                        item.getBoolean("glow", false),
                        item.getBoolean("hide-flags", false)
                ));
            }
        }

        // Search (navigation.search kullanılıyorsa buradaki search opsiyonel)
        SearchConfig search = null;
        ConfigurationSection ss = yaml.getConfigurationSection("search");
        if (ss != null && config.isSearchEnabled()) {
            String texture = ss.getString("texture", "");
            // material boşse, ARROW varsayılır (placeholder, slot=-1 ise kullanılmaz)
            String matStr = ss.getString("material", "");
            Material material = (texture != null && !texture.isEmpty())
                    ? Material.PLAYER_HEAD
                    : (matStr == null || matStr.isEmpty() ? Material.ARROW : getMaterial(matStr));
            search = new SearchConfig(
                    ss.getInt("slot", -1),
                    material,
                    texture,
                    ss.getString("name", "&#2CCED2&lAra"),
                    ss.getStringList("lore"),
                    ss.getInt("amount", 1),
                    ss.getInt("custom-model-data", 0),
                    ss.getBoolean("glow", false),
                    ss.getBoolean("hide-flags", false)
            );
        }

        // Sorting — yml'deki anahtar "sort:"'dur (loader "sorting" okuyordu; sort butonu hiç yüklenmiyordu)
        SortConfig sort = null;
        ConfigurationSection srt = yaml.getConfigurationSection("sort");
        if (srt != null && config.isSortEnabled()) {
            String texture = srt.getString("texture", "");
            Material material = (texture != null && !texture.isEmpty())
                    ? Material.PLAYER_HEAD
                    : getMaterial(srt.getString("material", "HOPPER"));
            sort = new SortConfig(
                    srt.getInt("slot", -1),
                    material,
                    texture,
                    srt.getString("name", "&#2CCED2&lSırala"),
                    srt.getStringList("options"),
                    srt.getInt("amount", 1),
                    srt.getInt("custom-model-data", 0),
                    srt.getBoolean("glow", false),
                    srt.getBoolean("hide-flags", false)
            );
        }

        // Filtre (kategori + teklifli) — slot/material main-menu.yml'den okunur
        FilterConfig filter;
        ConfigurationSection fs = yaml.getConfigurationSection("filter");
        if (fs != null) {
            String texture = fs.getString("texture", "");
            Material material = (texture != null && !texture.isEmpty())
                    ? Material.PLAYER_HEAD
                    : getMaterial(fs.getString("material", "HOPPER"));
            filter = new FilterConfig(
                    fs.getInt("slot", 51),
                    material,
                    texture,
                    fs.getString("name", "&#2CCED2&lFiltre"),
                    fs.getStringList("lore"),
                    fs.getInt("amount", 1),
                    fs.getInt("custom-model-data", 0),
                    fs.getBoolean("glow", false),
                    fs.getBoolean("hide-flags", false)
            );
        } else {
            filter = new FilterConfig(51, getMaterial("HOPPER"), "", "&#2CCED2&lFiltre");
        }

        // Content slots
        List<Integer> contentSlots = parseSlotListPublic(yaml.getString("content-slots", ""));

        // Lore format
        List<String> loreFormat = yaml.getStringList("lore-format");

        // Expired toggle (my-listings.yml)
        ExpiredToggleConfig expiredToggle = null;
        ConfigurationSection et = yaml.getConfigurationSection("expired-toggle");
        if (et != null) {
            String texture = et.getString("texture", "");
            Material mat = (texture != null && !texture.isEmpty())
                    ? Material.PLAYER_HEAD
                    : getMaterial(et.getString("material", "CLOCK"));
            expiredToggle = new ExpiredToggleConfig(
                    et.getInt("slot", 47),
                    mat,
                    texture,
                    et.getString("name", "&#FFD54F&lSüresi Dolanlar"),
                    et.getString("on-name", "&#F5F5F5&lAktif İlanlarım"),
                    et.getStringList("lore"),
                    et.getInt("amount", 1),
                    et.getInt("custom-model-data", 0),
                    et.getBoolean("glow", false),
                    et.getBoolean("hide-flags", false)
            );
        }

        return new GUILayout(title, rows, border, backgroundFill, navItems, search, sort, filter,
                contentSlots, loreFormat, expiredToggle);
    }

    private Material getMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    /**
     * Virgülle ayrılmış slot ifadesini parse eder ("0,1,2,9-16" gibi aralıkları da destekler).
     * Hem {@link #parseSlotList(String)} (instance) hem de {@link #parseSlotListPublic(String)} (static,
     * başka GUI'lerden erişilebilir) olarak sunulur.
     */
    public static List<Integer> parseSlotListPublic(String input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        List<Integer> slots = new ArrayList<>();
        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
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
            BackgroundFillConfig backgroundFill,
            List<NavItem> navItems,
            SearchConfig search,
            SortConfig sort,
            FilterConfig filter,
            List<Integer> contentSlots,
            List<String> loreFormat,
            ExpiredToggleConfig expiredToggle
    ) {}

    /**
     * Çerçeve (border) yapılandırması — inventory'nin kenarındaki tekrarlanan item.
     * Hem {@code material} (vanilla) hem de {@code texture} (base64) destekler;
     * {@code amount} / {@code custom-model-data} / {@code glow} / {@code hide-flags}
     * ile görsel özelleştirme tamamen yapılabilir.
     */
    public record BorderConfig(
            Material material,
            String texture,
            String name,
            List<Integer> slots,
            int amount,
            int customModelData,
            boolean glow,
            boolean hideFlags
    ) {
        /** Eski 3-argümanlı çağrılarla geriye dönük uyumluluk (amount=1, cmd=0, glow/hide=false). */
        public BorderConfig(Material material, String name, List<Integer> slots) {
            this(material, "", name, slots, 1, 0, false, false);
        }
    }

    /**
     * Arka plan dolgusu — border / content / nav item olmayan tüm slotlara uygulanır.
     * Tanımlanmazsa arka plan boş kalır (mevcut davranışla uyumluluk).
     */
    public record BackgroundFillConfig(
            Material material,
            String texture,
            String name,
            int amount,
            int customModelData,
            boolean glow,
            boolean hideFlags
    ) {}

    public record NavItem(
            String id, int slot, Material material, String texture,
            String name, List<String> lore,
            String leftClickAction, String rightClickAction,
            int amount, int customModelData, boolean glow, boolean hideFlags
    ) {
        /** Eski 8-argümanlı çağrılarla geriye dönük uyumluluk (amount=1, cmd=0, glow/hide=false). */
        public NavItem(String id, int slot, Material material, String texture,
                       String name, List<String> lore,
                       String leftClickAction, String rightClickAction) {
            this(id, slot, material, texture, name, lore, leftClickAction, rightClickAction,
                    1, 0, false, false);
        }
    }

    public record SearchConfig(
            int slot,
            Material material,
            String texture,
            String name,
            List<String> lore,
            int amount,
            int customModelData,
            boolean glow,
            boolean hideFlags
    ) {
        /** Geriye dönük uyumluluk. */
        public SearchConfig(int slot, Material material, String texture, String name, List<String> lore) {
            this(slot, material, texture, name, lore, 1, 0, false, false);
        }
    }

    public record SortConfig(
            int slot,
            Material material,
            String texture,
            String name,
            List<String> options,
            int amount,
            int customModelData,
            boolean glow,
            boolean hideFlags
    ) {
        /** Geriye dönük uyumluluk. */
        public SortConfig(int slot, Material material, String texture, String name, List<String> options) {
            this(slot, material, texture, name, options, 1, 0, false, false);
        }
    }

    public record FilterConfig(
            int slot,
            Material material,
            String texture,
            String name,
            List<String> lore,
            int amount,
            int customModelData,
            boolean glow,
            boolean hideFlags
    ) {
        /** Geriye dönük uyumluluk. */
        public FilterConfig(int slot, Material material, String texture, String name) {
            this(slot, material, texture, name, List.of(), 1, 0, false, false);
        }
    }
    /**
     * "Süresi Dolanları Göster" toggle butonu — aktif ve süresi dolan ilan görünümü arasında geçiş.
     * Tam görsel özelleştirme desteklenir.
     */
    public record ExpiredToggleConfig(
            int slot,
            Material material,
            String texture,
            String name,
            String onName,
            List<String> lore,
            int amount,
            int customModelData,
            boolean glow,
            boolean hideFlags
    ) {
        /** Geriye dönük uyumluluk (lore=null, amount=1, cmd=0, glow/hide=false). */
        public ExpiredToggleConfig(int slot, Material material, String texture, String name, String onName) {
            this(slot, material, texture, name, onName, List.of(), 1, 0, false, false);
        }
    }
}