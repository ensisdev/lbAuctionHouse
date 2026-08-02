package fr.maxlego08.zauctionhouse.api.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;

/**
 * Interface for registering placeholder providers with PlaceholderAPI.
 */
public interface PlaceholderRegister {

    /**
     * Registers a placeholder provider with the PlaceholderAPI system.
     *
     * @param placeholder the placeholder provider to register
     * @param plugin      the auction house plugin
     */
    void register(Placeholder placeholder, AuctionPlugin plugin);

}
