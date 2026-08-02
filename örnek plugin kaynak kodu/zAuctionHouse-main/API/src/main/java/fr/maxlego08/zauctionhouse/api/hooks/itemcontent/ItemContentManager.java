package fr.maxlego08.zauctionhouse.api.hooks.itemcontent;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Manages {@link ItemContentProvider} instances and provides a unified API
 * to detect and extract the contents of container items.
 */
public interface ItemContentManager {

    /**
     * Registers a new item content provider.
     *
     * @param provider the provider to register
     */
    void registerProvider(ItemContentProvider provider);

    /**
     * Unregisters a provider by name.
     *
     * @param name the provider name
     */
    void unregisterProvider(String name);

    /**
     * Checks whether the given item is a container recognized by any registered provider.
     *
     * @param itemStack the item to check
     * @return true if any provider can handle this item
     */
    boolean isContainer(ItemStack itemStack);

    /**
     * Extracts the contents of the given container item using the first matching provider.
     *
     * @param itemStack the container item
     * @return the contents, or an empty list if no provider matches
     */
    List<ItemStack> getContents(ItemStack itemStack);

    /**
     * Returns all containers from the given list of items.
     *
     * @param itemStacks the items to search
     * @return a list of items that are recognized as containers
     */
    List<ItemStack> getContainers(List<ItemStack> itemStacks);

    /**
     * Checks if the given list contains any container items.
     *
     * @param itemStacks the items to check
     * @return true if at least one item is a container
     */
    boolean containsContainer(List<ItemStack> itemStacks);
}
