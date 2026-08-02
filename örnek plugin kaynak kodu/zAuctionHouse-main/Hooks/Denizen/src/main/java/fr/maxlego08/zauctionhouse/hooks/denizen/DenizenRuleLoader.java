package fr.maxlego08.zauctionhouse.hooks.denizen;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads DenizenRule from configuration.
 * Matches items created by Denizen plugin scripts.
 */
public class DenizenRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "denizen";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new DenizenRule(items);
    }
}
