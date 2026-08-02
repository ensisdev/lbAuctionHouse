package fr.maxlego08.zauctionhouse.hooks.headdatabase;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads HeadDatabaseRule from configuration.
 * Matches heads from HeadDatabase plugin.
 */
public class HeadDatabaseRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "headdatabase";
    }

    @Override
    public List<String> getAliases() {
        return List.of("head-database", "head_database", "hdb");
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new HeadDatabaseRule(items);
    }
}
