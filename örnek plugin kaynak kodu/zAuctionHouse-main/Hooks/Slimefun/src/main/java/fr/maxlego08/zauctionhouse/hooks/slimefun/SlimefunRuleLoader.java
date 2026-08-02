package fr.maxlego08.zauctionhouse.hooks.slimefun;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads SlimefunRule from configuration.
 * Matches items created by Slimefun plugin.
 */
public class SlimefunRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "slimefun";
    }

    @Override
    public List<String> getAliases() {
        return List.of("slime-fun", "slime_fun", "sf");
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new SlimefunRule(items);
    }
}
