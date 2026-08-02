package fr.maxlego08.zauctionhouse.api.event.events;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleManager;

/**
 * Event fired when item filtering rules are being loaded or reloaded.
 * <p>
 * This event allows other plugins to add custom blacklist or whitelist rules
 * to the auction house item filter.
 *
 * <pre>{@code
 * @EventHandler
 * public void onRulesLoad(RuleLoadEvent event) {
 *     event.getItemRuleManager().addBlacklistRule(myCustomRule);
 * }
 * }</pre>
 */
public class RuleLoadEvent extends AuctionEvent {

    private final ItemRuleManager itemRuleManager;

    /**
     * Creates a new rule load event.
     *
     * @param itemRuleManager the rule manager to add rules to
     */
    public RuleLoadEvent(ItemRuleManager itemRuleManager) {
        this.itemRuleManager = itemRuleManager;
    }

    /**
     * Gets the item rule manager for adding custom filtering rules.
     *
     * @return the item rule manager
     */
    public ItemRuleManager getItemRuleManager() {
        return itemRuleManager;
    }
}
