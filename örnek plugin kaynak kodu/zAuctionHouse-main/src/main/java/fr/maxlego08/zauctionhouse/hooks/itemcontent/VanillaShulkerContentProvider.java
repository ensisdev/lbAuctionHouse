package fr.maxlego08.zauctionhouse.hooks.itemcontent;

import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentProvider;
import fr.maxlego08.zauctionhouse.utils.ShulkerHelper;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Default provider that extracts contents from vanilla Minecraft shulker boxes.
 */
public class VanillaShulkerContentProvider implements ItemContentProvider {

    @Override
    public String getName() {
        return "Vanilla";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isContainer(ItemStack itemStack) {
        return ShulkerHelper.isShulkerBox(itemStack);
    }

    @Override
    public List<ItemStack> getContents(ItemStack itemStack) {
        return ShulkerHelper.getShulkerContent(itemStack);
    }
}
