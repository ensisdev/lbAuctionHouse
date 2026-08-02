package dev.ensisdev.lbauctionhouse.core.event;

import dev.ensisdev.lbauctionhouse.core.config.ConfigManager;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Core yapılandırması yeniden yüklendiğinde tetiklenir.
 * <p>
 * {@code /lbsmpcore reload} komutu çalıştığında veya {@link ConfigManager#reload()}
 * çağrıldığında fire edilir. Addon'lar kendi konfigürasyonlarını yeniden yüklemek
 * için bu event'i dinleyebilir.
 */
public class ConfigReloadEvent extends CoreEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ConfigManager configManager;

    public ConfigReloadEvent(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * ConfigManager referansı.
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public String getSystem() {
        return "config";
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
