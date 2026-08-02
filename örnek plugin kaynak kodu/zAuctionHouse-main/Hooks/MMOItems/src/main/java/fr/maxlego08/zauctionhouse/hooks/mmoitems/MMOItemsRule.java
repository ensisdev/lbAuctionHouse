package fr.maxlego08.zauctionhouse.hooks.mmoitems;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rule that matches items created by MMOItems plugin.
 * Supports exact IDs and wildcard patterns (e.g., "SWORD:*", "*:CUTLASS").
 * Format: TYPE:ID or just ID (matches any type)
 */
public class MMOItemsRule implements Rule {

    private final List<String> itemIds;
    private final List<Pattern> patterns;

    public MMOItemsRule(List<String> itemIds) {
        this.itemIds = itemIds.stream().filter(id -> !id.contains("*")).map(id -> id.toUpperCase(Locale.ROOT)).toList();

        this.patterns = itemIds.stream().filter(id -> id.contains("*")).map(this::wildcardToPattern).toList();
    }

    private Pattern wildcardToPattern(String wildcard) {
        String regex = wildcard.toUpperCase(Locale.ROOT).replace(".", "\\.").replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        ItemStack itemStack = context.getItemStack();
        if (itemStack == null) return false;

        NBTItem nbtItem = NBTItem.get(itemStack);
        if (!nbtItem.hasType()) return false;

        String type = nbtItem.getType();
        String id = nbtItem.getString("MMOITEMS_ITEM_ID");
        if (type == null || id == null) return false;

        String fullId = type.toUpperCase(Locale.ROOT) + ":" + id.toUpperCase(Locale.ROOT);
        String idOnly = id.toUpperCase(Locale.ROOT);

        // Check exact matches (full format TYPE:ID or just ID)
        if (itemIds.contains(fullId) || itemIds.contains(idOnly)) {
            return true;
        }

        // Check wildcard patterns
        for (Pattern pattern : patterns) {
            if (pattern.matcher(fullId).matches() || pattern.matcher(idOnly).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isValid() {
        return !this.itemIds.isEmpty() || !this.patterns.isEmpty();
    }
}
