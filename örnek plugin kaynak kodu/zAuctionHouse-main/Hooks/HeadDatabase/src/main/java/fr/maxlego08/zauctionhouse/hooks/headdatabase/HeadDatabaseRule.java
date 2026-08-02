package fr.maxlego08.zauctionhouse.hooks.headdatabase;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule that matches heads from HeadDatabase plugin.
 * Supports exact IDs and wildcard patterns (e.g., "123*", "*456").
 */
public class HeadDatabaseRule implements Rule {

    private final HeadDatabaseAPI api;
    private final List<String> headIds;
    private final List<Pattern> patterns;

    public HeadDatabaseRule(List<String> headIds) {
        this.api = new HeadDatabaseAPI();
        this.headIds = headIds.stream().filter(id -> !id.contains("*")).toList();

        this.patterns = headIds.stream().filter(id -> id.contains("*")).map(this::wildcardToPattern).toList();
    }

    private Pattern wildcardToPattern(String wildcard) {
        String regex = wildcard.replace(".", "\\.").replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        ItemStack itemStack = context.getItemStack();
        if (itemStack == null) return false;

        String headId = api.getItemID(itemStack);
        if (headId == null || headId.isEmpty()) return false;

        // Check exact matches
        if (headIds.contains(headId)) {
            return true;
        }

        // Check wildcard patterns
        for (Pattern pattern : patterns) {
            if (pattern.matcher(headId).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isValid() {
        return !this.headIds.isEmpty() || !this.patterns.isEmpty();
    }
}
