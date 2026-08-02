package fr.maxlego08.zauctionhouse.api.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of rules that can be evaluated together.
 * <p>
 * This record implements {@link Rule} allowing it to be used as a composite rule.
 * When evaluated, it returns {@code true} if any contained rule matches (OR logic).
 *
 * @param enabled whether this rule set is active
 * @param rules   the list of rules to evaluate
 */
public record Rules(boolean enabled, List<Rule> rules) implements Rule {

    /**
     * Creates a new immutable Rules instance.
     *
     * @param enabled whether this rule set is active
     * @param rules   the list of rules (will be copied to ensure immutability)
     */
    public Rules(boolean enabled, List<Rule> rules) {
        this.enabled = enabled;
        this.rules = List.copyOf(rules);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code true} if the rule set is enabled and any contained rule matches.
     */
    @Override
    public boolean matches(ItemRuleContext context) {
        if (!enabled) return false;

        for (Rule rule : rules) {
            if (rule.matches(context)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a new Rules instance with the specified enabled state.
     *
     * @param enabled the new enabled state
     * @return a new Rules instance with the updated state
     */
    public Rules withEnabled(boolean enabled) {
        return new Rules(enabled, this.rules);
    }

    /**
     * Creates a new Rules instance with an additional rule appended.
     *
     * @param rule the rule to add
     * @return a new Rules instance containing all existing rules plus the new one
     */
    public Rules withAddedRule(Rule rule) {
        List<Rule> copy = new ArrayList<>(this.rules);
        copy.add(rule);
        return new Rules(this.enabled, copy);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code true} if this rule set contains at least one valid rule.
     */
    @Override
    public boolean isValid() {
        if (this.rules.isEmpty()) return false;
        return this.rules.stream().anyMatch(Rule::isValid);
    }

    /**
     * Creates an empty, disabled Rules instance.
     *
     * @return a new empty and disabled Rules instance
     */
    public static Rules emptyDisabled() {
        return new Rules(false, List.of());
    }
}

