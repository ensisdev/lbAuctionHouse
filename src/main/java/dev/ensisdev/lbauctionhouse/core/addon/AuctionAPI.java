package dev.ensisdev.lbauctionhouse.core.addon;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.core.config.ConfigManager;
import dev.ensisdev.lbauctionhouse.core.config.LanguageManager;
import dev.ensisdev.lbauctionhouse.core.data.DataManager;
import dev.ensisdev.lbauctionhouse.core.data.AsyncStorageWrapper;
import dev.ensisdev.lbauctionhouse.core.economy.EconomyManager;
import dev.ensisdev.lbauctionhouse.core.gui.MenuManager;

import java.util.logging.Logger;

/**
 * lbAuctionHouse'un iç servislerine erişim sağlayan API facade'ı.
 * <p>
 * Eski lbSmpCore addon mimarisinden bağımsızlaştırılmıştır — dış Core'a
 * bağımlılık yoktur; tüm servisler plugin'in kendi içindedir.
 */
public class AuctionAPI {

    private final LbAuctionHouse plugin;

    public AuctionAPI(LbAuctionHouse plugin) {
        this.plugin = plugin;
    }

    /** Ana plugin instance'ı. */
    public LbAuctionHouse getCore() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return plugin.getConfigManager();
    }

    public LanguageManager getLanguageManager() {
        return plugin.getLanguageManager();
    }

    public DataManager getDataManager() {
        return plugin.getDataManager();
    }

    /** Async veritabanı sorguları (main thread bloklanmaz). */
    public AsyncStorageWrapper async() {
        return plugin.getDataManager().async();
    }

    public EconomyManager getEconomyManager() {
        return plugin.getEconomyManager();
    }

    public MenuManager getMenuManager() {
        return plugin.getMenuManager();
    }

    /** Plugin logger'ı. */
    public Logger getLogger() {
        return plugin.getLogger();
    }

    /** Plugin sürümü. */
    public String getCoreVersion() {
        return plugin.getDescription().getVersion();
    }
}
