package fr.maxlego08.zauctionhouse.hooks.executableblocks;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads ExecutableBlocksRule from configuration.
 * Matches items from ExecutableBlocks plugin.
 */
public class ExecutableBlocksRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "executableblocks";
    }

    @Override
    public List<String> getAliases() {
        return List.of("executable-blocks", "executable_blocks", "eb");
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new ExecutableBlocksRule(items);
    }
}
