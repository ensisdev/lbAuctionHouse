package fr.maxlego08.zauctionhouse.hooks.axshulkers;

import com.artillexstudios.axshulkers.AxShulkers;
import com.artillexstudios.axshulkers.cache.Shulkerbox;
import com.artillexstudios.axshulkers.cache.Shulkerboxes;
import com.artillexstudios.axshulkers.utils.ShulkerUtils;
import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentProvider;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Item content provider for the AxShulkers plugin.
 * Extracts shulker box contents managed by AxShulkers (stored externally, not in vanilla NBT).
 * Registered with a lower priority than the vanilla provider so it is checked first.
 */
public class AxShulkersContentProvider implements ItemContentProvider {

    @Override
    public String getName() {
        return "AxShulkers";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean isContainer(ItemStack itemStack) {
        return ShulkerUtils.isShulker(itemStack) && ShulkerUtils.getShulkerUUID(itemStack) != null;
    }

    @Override
    public List<ItemStack> getContents(ItemStack itemStack) {
        if (!ShulkerUtils.isShulker(itemStack)) return List.of();

        UUID uuid = ShulkerUtils.getShulkerUUID(itemStack);
        if (uuid == null) return List.of();

        ItemStack[] contents;
        Shulkerbox shulkerbox = Shulkerboxes.getShulkerMap().get(uuid);
        if (shulkerbox != null) {
            contents = shulkerbox.getShulkerInventory().getStorageContents();
        } else {
            contents = AxShulkers.getDB().getShulker(uuid);
        }

        if (contents == null) return List.of();

        List<ItemStack> result = new ArrayList<>();
        for (ItemStack content : contents) {
            if (content != null && !content.getType().isAir()) {
                result.add(content.clone());
            }
        }
        return result;
    }
}
