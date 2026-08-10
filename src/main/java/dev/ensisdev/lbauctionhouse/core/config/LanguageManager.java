package dev.ensisdev.lbauctionhouse.core.config;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dil/Mesaj yönetim sistemi.
 * <p>
 * {@code messages.yml} dosyasından mesajları yükler, MiniMessage formatında
 * parse eder ve placeholders desteği sunar. Addon'lar kendi mesaj dosyalarını
 * kaydederek Core'un mesaj sistemini genişletebilir.
 * <p>
 * Kullanım:
 * <pre>
 * api.getLanguageManager().get("core.reload.success")
 * api.getLanguageManager().getPrefixed("economy.balance", "balance", econ.format(bal))
 * </pre>
 */
public class LanguageManager {

    private final LbAuctionHouse plugin;
    private final Logger logger;

    private FileConfiguration messages;
    private final Map<String, String> defaults;
    private final Map<String, FileConfiguration> addonMessages;

    /** Prefix bileşeni — tüm mesajların başına eklenebilir. */
    private Component prefix;

    public static final String MESSAGES_FILE = "messages.yml";

    public LanguageManager(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.defaults = new HashMap<>();
        this.addonMessages = new HashMap<>();
        registerDefaults();
    }

    // ----------------------------------------------------------------
    // Yükleme
    // ----------------------------------------------------------------

    /**
     * messages.yml dosyasını yükler (yoksa varsayılanı kopyalar).
     */
    public void load() {
        // resources/messages.yml → plugin klasörü
        plugin.saveResource(MESSAGES_FILE, false);

        File file = new File(plugin.getDataFolder(), MESSAGES_FILE);
        if (file.exists()) {
            this.messages = YamlConfiguration.loadConfiguration(file);
        } else {
            this.messages = new YamlConfiguration();
            logger.warning("messages.yml bulunamadı, varsayılan değerler kullanılacak.");
        }

        // Prefix'i parse et
        String prefixRaw = getRaw("prefix");
        if (prefixRaw != null) {
            this.prefix = deserialize(prefixRaw);
        } else {
            this.prefix = Component.text("[lbAuctionHouse]");
        }

        logger.info("LanguageManager yüklendi (" + messages.getKeys(true).size() + " keys).");
    }

    /**
     * Addon'un kendi messages.yml'ini kaydeder.
     * Addon tarafından {@link dev.ensisdev.lbauctionhouse.core.addon.LbSmpAddon#onAddonEnable} sırasında çağrılır.
     * <p>
     * Çakışan key'lerde addon mesajları Core mesajlarını override eder.
     *
     * @param addonId addon ID'si (ör: "Order")
     * @param resource addon'un resources/messages.yml'sinden açılmış InputStream
     */
    public void registerAddonMessages(String addonId, InputStream resource) {
        if (resource == null) {
            logger.warning("Addon '" + addonId + "' mesaj dosyası sağlamadı (null).");
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            FileConfiguration addonConfig = YamlConfiguration.loadConfiguration(reader);
            addonMessages.put(addonId, addonConfig);

            // Addon mesajlarını logla
            int count = addonConfig.getKeys(true).size();
            logger.info("Addon mesajları kaydedildi: " + addonId + " (" + count + " keys).");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Addon mesajları yüklenirken hata: " + addonId, e);
        }
    }

    /**
     * Addon'un kendi messages.yml'ini <b>diskteki gerçek dosyadan</b> kaydeder.
     * <p>
     * Addon önce {@code saveResource("messages.yml", false)} ile JAR'daki varsayılanı
     * data klasörüne kopyalar, sonra bu metodu {@code new File(getDataFolder(), "messages.yml")}
     * ile çağırır — böylece sunucu sahibinin messages.yml üzerinde yaptığı düzenlemeler
     * her reload'da disketen okunur, JAR'daki gömülü kopya ezilmez.
     *
     * @param addonId addon ID'si (ör: "Auction")
     * @param file    disketeki messages.yml dosyası (getDataFolder() içinde)
     */
    public void registerAddonMessages(String addonId, File file) {
        registerAddonMessages(addonId, file, false);
    }

    /**
     * Addon mesajlarını diskten kaydeder. {@code mergeExisting=true} ise aynı
     * addonId için daha önce kaydedilmiş mesajların üzerine EKLENİR (sadece bu
     * dosyada bulunan anahtarlar override edilir).
     * <p>
     * Bu, addon'ların şöyle çalışmasını sağlar: önce varsayılan messages.yml
     * kaydedilir, ardından seçili dil dosyası (lang/&lt;code&gt;.yml) birleştirilir —
     * dil dosyasında olmayan anahtarlar varsayılana düşer, olanlar dili gösterir.
     *
     * @param addonId      addon ID'si (ör: "Auction")
     * @param file         disketeki mesaj dosyası
     * @param mergeExisting true → mevcut addon mesajlarıyla birleştir, false → değiştir
     */
    public void registerAddonMessages(String addonId, File file, boolean mergeExisting) {
        if (file == null || !file.exists()) {
            logger.warning("Addon '" + addonId + "' mesaj dosyası diskte bulunamadı: "
                    + (file == null ? "null" : file.getAbsolutePath()));
            return;
        }

        try {
            FileConfiguration addonConfig = YamlConfiguration.loadConfiguration(file);

            if (mergeExisting && addonMessages.containsKey(addonId)) {
                FileConfiguration existing = addonMessages.get(addonId);
                for (String key : addonConfig.getKeys(true)) {
                    Object value = addonConfig.get(key);
                    // Yalnızca yaprak (leaf) değerleri birleştir — ConfigurationSection
                    // ara düğümlerini set etmek nested yapıyı bozabilir.
                    if (value instanceof org.bukkit.configuration.ConfigurationSection) continue;
                    existing.set(key, value);
                }
                addonMessages.put(addonId, existing);
                logger.info("Addon mesajları dil dosyasıyla BİRLEŞTİRİLDİ: " + addonId
                        + " ← " + file.getName());
            } else {
                addonMessages.put(addonId, addonConfig);
                int count = addonConfig.getKeys(true).size();
                logger.info("Addon mesajları diskten kaydedildi: " + addonId
                        + " (" + count + " keys) ← " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Addon mesajları diskten yüklenirken hata: " + addonId, e);
        }
    }

    // ----------------------------------------------------------------
    // Sorgulama
    // ----------------------------------------------------------------

    /**
     * Ham mesaj metnini döndürür (MiniMessage formatında, placeholder'lar değiştirilmemiş).
     */
    public String getRaw(String key) {
        // 1) Addon mesajlarında ara
        for (var entry : addonMessages.entrySet()) {
            String value = entry.getValue().getString(key);
            if (value != null) return value;
        }

        // 2) messages.yml'de ara
        if (messages != null) {
            String value = messages.getString(key);
            if (value != null) return value;
        }

        // 3) Varsayılan değer
        return defaults.get(key);
    }

    /**
     * Mesajı Component olarak döndürür (placeholder'lar dahil).
     * <p>
     * Placeholder'lar tek/çift sayılı argümanlar olarak verilir:
     * {@code get("economy.balance", "amount", "100.50")}
     * → "{amount}" → "100.50"
     *
     * @param key mesaj anahtarı
     * @param placeholders key1, value1, key2, value2, ...
     */
    public Component get(String key, String... placeholders) {
        String raw = getRaw(key);
        if (raw == null) {
            return deserialize("<red>Missing message: " + key + "</red>");
        }

        raw = applyPlaceholders(raw, placeholders);
        return deserialize(raw);
    }

    /**
     * Mesajı prefix ile birlikte döndürür.
     */
    public Component getPrefixed(String key, String... placeholders) {
        return prefix.append(Component.space()).append(get(key, placeholders));
    }

    /**
     * Mevcut prefix Component'ini döndürür.
     */
    public Component getPrefix() {
        return prefix;
    }

    /**
     * Prefix'i günceller (addon'ların özel prefix kullanması için).
     */
    public void setPrefix(Component newPrefix) {
        this.prefix = newPrefix;
    }

    // ----------------------------------------------------------------
    // Yardımcılar
    // ----------------------------------------------------------------

    /**
     * Placeholder'ları metne uygular.
     * {key} formatındaki her placeholder karşılık gelen değerle değiştirilir.
     */
    private String applyPlaceholders(String text, String... placeholders) {
        if (placeholders == null || placeholders.length == 0) return text;

        String result = text;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String key = "{" + placeholders[i] + "}";
            String pctKey = "%" + placeholders[i] + "%";  // legacy %seller% formatı da desteklenir
            String value = placeholders[i + 1] != null ? placeholders[i + 1] : "";
            result = result.replace(key, value).replace(pctKey, value);
        }
        return result;
    }

    /**
     * Bir metni Component'e çevirir.
     * Önce MiniMessage dener, legacy {@code &} kodlarını da destekler.
     */
    public Component deserialize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // Eğer MiniMessage formatı içeriyorsa MiniMessage ile parse et
        if (text.contains("<") && text.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(text);
            } catch (Exception ignored) {
                // MiniMessage başarısız → legacy'e düş
            }
        }

        // Legacy & kodlarını parse et (&a, &l, &7, ...)
        return LegacyComponentSerializer.legacySection()
                .deserialize(text.replace('&', net.md_5.bungee.api.ChatColor.COLOR_CHAR));
    }

    // ----------------------------------------------------------------
    // Varsayılan değerler
    // ----------------------------------------------------------------

    private void registerDefaults() {
        defaults.put("prefix", "<dark_gray><bold>[</bold></dark_gray><gradient:#7B2FBE:#C084FC><bold>ʟʙᴀᴜᴄᴛɪᴏɴʜᴏᴜꜱᴇ</bold></gradient><dark_gray><bold>]</bold></dark_gray>");
        defaults.put("core.reload.success", "<green>Configuration and addons reloaded.</green>");
        defaults.put("core.reload.no-permission", "<red>You don't have permission!</red>");
        defaults.put("core.version.info", "<aqua>lbAuctionHouse v{version}</aqua>");
        defaults.put("core.version.authors", "<gray>Authors: <white>{authors}</white></gray>");
        defaults.put("core.addons.none", "<yellow>No addons installed.</yellow>");
        defaults.put("core.addons.header", "<gold>Installed addons ({count}):</gold>");
        defaults.put("core.addons.entry", "<dark_gray>  - </dark_gray><white>{name}</white> <gray>v{version}</gray>");
        defaults.put("core.no-permission", "<red>You don't have permission!</red>");

        defaults.put("economy.balance", "<gold>Balance: <white>{balance}</white></gold>");
        defaults.put("economy.no-funds", "<red>Insufficient funds!</red>");
        defaults.put("economy.transfer.sent", "<green>Sent <white>{amount}</white> to <white>{target}</white></green>");
        defaults.put("economy.transfer.received", "<green>Received <white>{amount}</white> from <white>{sender}</white></green>");
    }
}
