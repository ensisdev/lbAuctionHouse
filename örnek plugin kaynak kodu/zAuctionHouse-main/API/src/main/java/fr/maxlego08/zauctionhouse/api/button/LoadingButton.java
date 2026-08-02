package fr.maxlego08.zauctionhouse.api.button;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;

/**
 * Abstract base class for paginated buttons that support loading states.
 * <p>
 * Loading buttons display a loading indicator while data is being fetched
 * asynchronously, providing visual feedback to users.
 */
public abstract class LoadingButton extends PaginateButton {

    protected final AuctionPlugin plugin;
    protected final int loadingSlot;

    /**
     * Creates a new loading button.
     *
     * @param plugin      the auction house plugin
     * @param loadingSlot the inventory slot where the loading indicator is displayed
     */
    public LoadingButton(AuctionPlugin plugin, int loadingSlot) {
        this.plugin = plugin;
        this.loadingSlot = loadingSlot;
    }

    /**
     * Gets the auction house plugin instance.
     *
     * @return the plugin
     */
    public AuctionPlugin getPlugin() {
        return plugin;
    }

    /**
     * Gets the slot where the loading indicator is displayed.
     *
     * @return the loading slot
     */
    public int getLoadingSlot() {
        return loadingSlot;
    }
}
