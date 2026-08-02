package fr.maxlego08.zauctionhouse.hooks.executableitems;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads ExecutableItemsRule from configuration.
 * Matches items created by ExecutableItems plugin.
 */
public class ExecutableItemsRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "executableitems";
    }

    @Override
    public List<String> getAliases() {
        return List.of("executable-items", "executable_items", "ei");
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new ExecutableItemsRule(items);
    }
}
