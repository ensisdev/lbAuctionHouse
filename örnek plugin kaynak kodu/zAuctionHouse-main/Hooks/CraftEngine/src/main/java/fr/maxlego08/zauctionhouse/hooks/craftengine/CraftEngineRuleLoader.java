package fr.maxlego08.zauctionhouse.hooks.craftengine;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads CraftEngineRule from configuration.
 * Matches items from CraftEngine plugin.
 */
public class CraftEngineRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "craftengine";
    }

    @Override
    public List<String> getAliases() {
        return List.of("craft-engine", "craft_engine", "ce");
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new CraftEngineRule(items);
    }
}
