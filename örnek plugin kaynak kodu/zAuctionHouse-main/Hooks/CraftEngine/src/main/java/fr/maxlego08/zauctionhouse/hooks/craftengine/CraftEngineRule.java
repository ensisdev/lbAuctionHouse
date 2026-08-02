package fr.maxlego08.zauctionhouse.hooks.craftengine;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rule that matches items from CraftEngine plugin.
 * Supports exact IDs and wildcard patterns (e.g., "namespace:*", "*:item_id").
 */
public class CraftEngineRule implements Rule {

    private final List<String> itemIds;
    private final List<Pattern> patterns;

    public CraftEngineRule(List<String> itemIds) {
        this.itemIds = itemIds.stream().filter(id -> !id.contains("*")).map(id -> id.toLowerCase(Locale.ROOT)).toList();

        this.patterns = itemIds.stream().filter(id -> id.contains("*")).map(this::wildcardToPattern).toList();
    }

    private Pattern wildcardToPattern(String wildcard) {
        String regex = wildcard.toLowerCase(Locale.ROOT).replace(".", "\\.").replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        ItemStack itemStack = context.getItemStack();
        if (itemStack == null) return false;

        Key itemKey = CraftEngineItems.getCustomItemId(itemStack);
        if (itemKey == null) return false;

        String lowercaseId = itemKey.toString().toLowerCase(Locale.ROOT);

        // Check exact matches
        if (itemIds.contains(lowercaseId)) {
            return true;
        }

        // Check wildcard patterns
        for (Pattern pattern : patterns) {
            if (pattern.matcher(lowercaseId).matches()) {
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
