package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for managing per-player options (preferences).
 * <p>
 * Options are cached in memory for fast access and persisted asynchronously to the database.
 * Each option is identified by a {@link PlayerOption} enum value and stored as a string value.
 * <p>
 * Typical lifecycle:
 * <ol>
 *   <li>{@link #loadPlayerOptions(UUID)} on player join — loads options from DB into cache</li>
 *   <li>{@link #getOption(UUID, PlayerOption)} / {@link #setOption(UUID, PlayerOption, boolean)} during gameplay</li>
 *   <li>{@link #clearPlayerOptions(UUID)} on player quit — frees cached data</li>
 * </ol>
 */
public interface AuctionOptionService {

    /**
     * Loads all options for the given player from the database into the in-memory cache.
     * Should be called when the player joins the server.
     *
     * @param playerUniqueId the UUID of the player
     * @return a future that completes when the options have been loaded
     */
    CompletableFuture<Void> loadPlayerOptions(UUID playerUniqueId);

    /**
     * Gets the current value of an option for a player.
     * Returns the option's default value if the player has not explicitly set it.
     *
     * @param playerUniqueId the UUID of the player
     * @param option         the option to retrieve
     * @return {@code true} if the option is enabled, {@code false} otherwise
     */
    boolean getOption(UUID playerUniqueId, PlayerOption option);

    /**
     * Convenience method that delegates to {@link #getOption(UUID, PlayerOption)} using the player's UUID.
     *
     * @param player the online player
     * @param option the option to retrieve
     * @return {@code true} if the option is enabled, {@code false} otherwise
     */
    boolean getOption(Player player, PlayerOption option);

    /**
     * Sets an option value for a player, updating both the in-memory cache and the database.
     * The database write is performed asynchronously.
     *
     * @param playerUniqueId the UUID of the player
     * @param option         the option to set
     * @param value          the new value
     */
    void setOption(UUID playerUniqueId, PlayerOption option, boolean value);

    /**
     * Sets an option value asynchronously and returns a future that completes when the database has been updated.
     *
     * @param playerUniqueId the UUID of the player
     * @param option         the option to set
     * @param value          the new value as a string
     * @return a future that completes when the option has been persisted
     */
    CompletableFuture<Void> setOptionAsync(UUID playerUniqueId, PlayerOption option, String value);

    /**
     * Returns all options currently cached for the given player.
     * The returned map may not contain entries for options the player has never changed
     * (those will use their default values via {@link #getOption}).
     *
     * @param playerUniqueId the UUID of the player
     * @return an unmodifiable map of option to value, or an empty map if no options are cached
     */
    Map<PlayerOption, String> getPlayerOptions(UUID playerUniqueId);

    /**
     * Resets all options for the given player to their default values.
     * Clears the in-memory cache and deletes all entries from the database.
     *
     * @param playerUniqueId the UUID of the player
     */
    void resetPlayerOptions(UUID playerUniqueId);

    /**
     * Removes all cached options for the given player.
     * Should be called when the player disconnects to free memory.
     *
     * @param playerUniqueId the UUID of the player
     */
    void clearPlayerOptions(UUID playerUniqueId);
}
