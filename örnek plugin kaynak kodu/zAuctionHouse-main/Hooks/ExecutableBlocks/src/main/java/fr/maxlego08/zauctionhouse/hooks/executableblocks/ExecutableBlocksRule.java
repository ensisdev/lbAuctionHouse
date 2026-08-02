package fr.maxlego08.zauctionhouse.hooks.executableblocks;

import com.ssomar.score.api.executableblocks.ExecutableBlocksAPI;
import com.ssomar.score.api.executableblocks.config.ExecutableBlockInterface;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Rule that matches items from ExecutableBlocks plugin.
 * Supports exact IDs and wildcard patterns (e.g., "*_block", "custom_*").
 */
public class ExecutableBlocksRule implements Rule {

    private final List<String> blockIds;
    private final List<Pattern> patterns;

    public ExecutableBlocksRule(List<String> blockIds) {
        this.blockIds = blockIds.stream().filter(id -> !id.contains("*")).map(id -> id.toLowerCase(Locale.ROOT)).toList();

        this.patterns = blockIds.stream().filter(id -> id.contains("*")).map(this::wildcardToPattern).toList();
    }

    private Pattern wildcardToPattern(String wildcard) {
        String regex = wildcard.toLowerCase(Locale.ROOT).replace(".", "\\.").replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        ItemStack itemStack = context.getItemStack();
        if (itemStack == null) return false;

        Optional<ExecutableBlockInterface> executableBlockId = ExecutableBlocksAPI.getExecutableBlocksManager().getExecutableBlock(itemStack);
        if (executableBlockId.isEmpty()) return false;

        String lowercaseId = executableBlockId.get().getId().toLowerCase(Locale.ROOT);

        // Check exact matches
        if (blockIds.contains(lowercaseId)) {
            return true;
        }

        // Check wildcard patterns
        for (Pattern pattern : patterns) {
            if (pattern.matcher(lowercaseId).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isValid() {
        return !this.blockIds.isEmpty() || !this.patterns.isEmpty();
    }
}
