package fr.maxlego08.zauctionhouse.hooks.mmoitems;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads MMOItemsRule from configuration.
 * Matches items created by MMOItems plugin.
 */
public class MMOItemsRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "mmoitems";
    }

    @Override
    public List<String> getAliases() {
        return List.of("mmo-items", "mmo_items");
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new MMOItemsRule(items);
    }
}
