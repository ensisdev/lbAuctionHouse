package fr.maxlego08.zauctionhouse.hooks.denizen;

import com.denizenscript.denizen.objects.ItemTag;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rule that matches items created by Denizen plugin.
 * Supports exact script names and wildcard patterns (e.g., "*_weapon", "custom_*").
 */
public class DenizenRule implements Rule {

    private final List<String> scriptNames;
    private final List<Pattern> patterns;

    public DenizenRule(List<String> scriptNames) {
        this.scriptNames = scriptNames.stream().filter(id -> !id.contains("*")).map(id -> id.toLowerCase(Locale.ROOT)).toList();

        this.patterns = scriptNames.stream().filter(id -> id.contains("*")).map(this::wildcardToPattern).toList();
    }

    private Pattern wildcardToPattern(String wildcard) {
        String regex = wildcard.toLowerCase(Locale.ROOT).replace(".", "\\.").replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        ItemStack itemStack = context.getItemStack();
        if (itemStack == null) return false;

        ItemTag itemTag = new ItemTag(itemStack);
        String scriptName = itemTag.getScriptName();
        if (scriptName == null || scriptName.isEmpty()) return false;

        String lowercaseScript = scriptName.toLowerCase(Locale.ROOT);

        // Check exact matches
        if (scriptNames.contains(lowercaseScript)) {
            return true;
        }

        // Check wildcard patterns
        for (Pattern pattern : patterns) {
            if (pattern.matcher(lowercaseScript).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isValid() {
        return !this.scriptNames.isEmpty() || !this.patterns.isEmpty();
    }
}
