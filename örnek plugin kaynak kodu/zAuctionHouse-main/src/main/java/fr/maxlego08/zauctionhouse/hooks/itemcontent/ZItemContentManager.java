package fr.maxlego08.zauctionhouse.hooks.itemcontent;

import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentManager;
import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentProvider;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of {@link ItemContentManager}.
 * Providers are sorted by priority (lower = checked first).
 */
public class ZItemContentManager implements ItemContentManager {

    private final List<ItemContentProvider> providers = new ArrayList<>();

    @Override
    public void registerProvider(ItemContentProvider provider) {
        this.providers.add(provider);
        this.providers.sort(Comparator.comparingInt(ItemContentProvider::getPriority));
    }

    @Override
    public void unregisterProvider(String name) {
        this.providers.removeIf(provider -> provider.getName().equalsIgnoreCase(name));
    }

    @Override
    public boolean isContainer(ItemStack itemStack) {
        if (itemStack == null) return false;
        for (ItemContentProvider provider : this.providers) {
            if (provider.isContainer(itemStack)) return true;
        }
        return false;
    }

    @Override
    public List<ItemStack> getContents(ItemStack itemStack) {
        if (itemStack == null) return List.of();
        for (ItemContentProvider provider : this.providers) {
            if (provider.isContainer(itemStack)) {
                return provider.getContents(itemStack);
            }
        }
        return List.of();
    }

    @Override
    public List<ItemStack> getContainers(List<ItemStack> itemStacks) {
        if (itemStacks == null) return List.of();
        return itemStacks.stream().filter(this::isContainer).toList();
    }

    @Override
    public boolean containsContainer(List<ItemStack> itemStacks) {
        if (itemStacks == null) return false;
        return itemStacks.stream().anyMatch(this::isContainer);
    }
}
