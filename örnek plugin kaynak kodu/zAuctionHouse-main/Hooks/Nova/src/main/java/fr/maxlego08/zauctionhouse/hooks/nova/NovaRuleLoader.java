package fr.maxlego08.zauctionhouse.hooks.nova;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads NovaRule from configuration.
 * Matches items created by Nova plugin.
 */
public class NovaRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "nova";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new NovaRule(items);
    }
}
