package dev.ensisdev.lbauctionhouse.core.config;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.core.event.ConfigReloadEvent;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * Yapılandırma yöneticisi — config.yml yükleme, varsayılan değer atama ve kaydetme.
 * <p>



 */
public class ConfigManager {

    private final LbAuctionHouse plugin;
    private final Logger logger;
    private FileConfiguration config;

    public ConfigManager(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Config.yml dosyasını yükler. Dosya yoksa varsayılan değerlerle oluşturur.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig(); // resources/config.yml → plugin klasörüne kopyala (yoksa)
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Varsayılan değerleri kontrol et
        config.addDefault("settings.debug", false);
        config.addDefault("settings.check-addon-updates", true);
        config.addDefault("settings.warn-incompatible-addons", true);
        config.options().copyDefaults(true);
        plugin.saveConfig();

        logger.info("Config.yml yüklendi.");
    }

    /**
     * Config'i yeniden yükler.
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        logger.info("Config.yml yeniden yüklendi.");
        Bukkit.getPluginManager().callEvent(new ConfigReloadEvent(this));
    }

    /**
     * Config'i diske kaydeder.
     */
    public void saveConfig() {
        plugin.saveConfig();
    }

    /**
     * Debug modu aktif mi?
     */
    public boolean isDebug() {
        return config != null && config.getBoolean("settings.debug", false);
    }

    /**
     * Addon güncelleme kontrolü aktif mi?
     */
    public boolean isCheckAddonUpdates() {
        return config == null || config.getBoolean("settings.check-addon-updates", true);
    }

    /**
     * Uyumsuz addon uyarısı gösterilsin mi?
     */
    public boolean isWarnIncompatibleAddons() {
        return config == null || config.getBoolean("settings.warn-incompatible-addons", true);
    }

    /**
     * Ham Bukkit FileConfiguration nesnesini döndürür.
     * Addon'lar bu metot üzerinden kendi config değerlerine erişebilir.
     */
    public FileConfiguration getBukkitConfig() {
        return config;
    }
}
