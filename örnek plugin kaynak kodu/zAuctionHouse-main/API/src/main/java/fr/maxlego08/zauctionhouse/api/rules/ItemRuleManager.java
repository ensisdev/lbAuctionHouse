package fr.maxlego08.zauctionhouse.api.rules;

import org.bukkit.inventory.ItemStack;

/**
 * Manages item filtering rules for the auction house.
 * <p>
 * This manager handles both blacklist and whitelist rules that determine
 * which items can be listed in the auction house. Rules are loaded from
 * configuration files and can be modified at runtime.
 * <p>
 * The filtering logic works as follows:
 * <ul>
 *     <li>If whitelist is enabled and item matches whitelist: item is allowed</li>
 *     <li>If blacklist is enabled and item matches blacklist: item is denied</li>
 *     <li>Otherwise: item is allowed</li>
 * </ul>
 */
public interface ItemRuleManager {

    /**
     * Checks if an item is blacklisted.
     *
     * @param itemStack the item to check
     * @return {@code true} if the item matches any blacklist rule
     */
    boolean isBlacklisted(ItemStack itemStack);

    /**
     * Checks if an item is whitelisted.
     *
     * @param itemStack the item to check
     * @return {@code true} if the item matches any whitelist rule
     */
    boolean isWhitelisted(ItemStack itemStack);

    /**
     * Checks if an item is allowed to be listed in the auction house.
     * <p>
     * An item is allowed if:
     * <ul>
     *     <li>Whitelist is enabled and the item matches the whitelist, OR</li>
     *     <li>Blacklist is disabled or the item does not match the blacklist</li>
     * </ul>
     *
     * @param itemStack the item to check
     * @return {@code true} if the item can be listed
     */
    boolean isAllowed(ItemStack itemStack);

    /**
     * Gets the blacklist rules configuration.
     *
     * @return the blacklist rules
     */
    Rules blacklistRules();

    /**
     * Gets the whitelist rules configuration.
     *
     * @return the whitelist rules
     */
    Rules whitelistRules();

    /**
     * Checks if the blacklist is currently enabled.
     *
     * @return {@code true} if blacklist filtering is active
     */
    boolean isBlacklistEnabled();

    /**
     * Enables or disables the blacklist.
     *
     * @param enabled {@code true} to enable blacklist filtering
     */
    void setBlacklistEnabled(boolean enabled);

    /**
     * Checks if the whitelist is currently enabled.
     *
     * @return {@code true} if whitelist filtering is active
     */
    boolean isWhitelistEnabled();

    /**
     * Enables or disables the whitelist.
     *
     * @param enabled {@code true} to enable whitelist filtering
     */
    void setWhitelistEnabled(boolean enabled);

    /**
     * Adds a rule to the blacklist.
     *
     * @param rule the rule to add
     */
    void addBlacklistRule(Rule rule);

    /**
     * Adds a rule to the whitelist.
     *
     * @param rule the rule to add
     */
    void addWhitelistRule(Rule rule);

    /**
     * Replaces all blacklist rules with the specified rules.
     *
     * @param rules the new blacklist rules
     */
    void setBlacklistRules(Rules rules);

    /**
     * Replaces all whitelist rules with the specified rules.
     *
     * @param rules the new whitelist rules
     */
    void setWhitelistRules(Rules rules);

    /**
     * Loads rules from the configuration file.
     * <p>
     * This method reads the rules.yml file and populates both
     * blacklist and whitelist rules. It also fires a {@link fr.maxlego08.zauctionhouse.api.event.events.RuleLoadEvent}
     * to allow other plugins to add custom rules.
     */
    void loadRules();
}
