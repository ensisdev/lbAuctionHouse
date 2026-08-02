package fr.maxlego08.zauctionhouse.api.event.events;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;

/**
 * Event fired when economies are being loaded or reloaded.
 * <p>
 * This event allows other plugins to register custom economy providers
 * with the auction house during the loading phase.
 *
 * <pre>{@code
 * @EventHandler
 * public void onLoadEconomies(AuctionLoadEconomyEvent event) {
 *     event.getEconomyManager().registerEconomy(myCustomEconomy);
 * }
 * }</pre>
 */
public class AuctionLoadEconomyEvent extends AuctionEvent {

    private final AuctionPlugin plugin;
    private final EconomyManager economyManager;

    /**
     * Creates a new economy load event.
     *
     * @param plugin         the auction house plugin instance
     * @param economyManager the economy manager to register economies with
     */
    public AuctionLoadEconomyEvent(AuctionPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    /**
     * Gets the auction house plugin instance.
     *
     * @return the plugin instance
     */
    public AuctionPlugin getPlugin() {
        return plugin;
    }

    /**
     * Gets the economy manager for registering custom economies.
     *
     * @return the economy manager
     */
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
