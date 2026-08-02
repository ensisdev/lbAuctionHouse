package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;

public class SellPriceButton extends Button {

    private final AuctionPlugin plugin;
    private final BigDecimal leftClickAmount;
    private final BigDecimal rightClickAmount;
    private final BigDecimal shiftLeftClickAmount;
    private final BigDecimal shiftRightClickAmount;

    public SellPriceButton(Plugin plugin, BigDecimal leftClickAmount, BigDecimal rightClickAmount, BigDecimal shiftLeftClickAmount, BigDecimal shiftRightClickAmount) {
        this.plugin = (AuctionPlugin) plugin;
        this.leftClickAmount = leftClickAmount;
        this.rightClickAmount = rightClickAmount;
        this.shiftLeftClickAmount = shiftLeftClickAmount;
        this.shiftRightClickAmount = shiftRightClickAmount;
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        BigDecimal currentPrice = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy defaultEconomy = this.plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION);
        if (defaultEconomy == null) {
            this.plugin.getAuctionManager().message(player, Message.SELL_ERROR_DEFAULT_ECONOMY);
            return;
        }
        AuctionEconomy economy = cache.get(PlayerCacheKey.SELL_ECONOMY, defaultEconomy);

        BigDecimal amount = getAmountForClick(event.getClick());
        BigDecimal newPrice = currentPrice.add(amount);

        BigDecimal minPrice = economy.getMinPrice(ItemType.AUCTION);
        if (newPrice.compareTo(minPrice) < 0) {
            newPrice = minPrice;
        }

        BigDecimal maxPrice = economy.getMaxPrice(ItemType.AUCTION);
        if (newPrice.compareTo(maxPrice) > 0) {
            newPrice = maxPrice;
        }

        cache.set(PlayerCacheKey.SELL_PRICE, newPrice);
        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    private BigDecimal getAmountForClick(ClickType clickType) {
        return switch (clickType) {
            case RIGHT -> rightClickAmount;
            case SHIFT_LEFT -> shiftLeftClickAmount;
            case SHIFT_RIGHT -> shiftRightClickAmount;
            default -> leftClickAmount;
        };
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        MenuItemStack menuItemStack = this.getItemStack();

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy defaultEconomy = this.plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION);
        if (defaultEconomy == null) return getItemStack().build(player, false, placeholders);
        AuctionEconomy economy = cache.get(PlayerCacheKey.SELL_ECONOMY, defaultEconomy);

        placeholders.register("price", this.plugin.getEconomyManager().format(economy, price));
        placeholders.register("economy", economy.getDisplayName());
        placeholders.register("left_click_amount", formatAmount(leftClickAmount));
        placeholders.register("right_click_amount", formatAmount(rightClickAmount));
        placeholders.register("shift_left_click_amount", formatAmount(shiftLeftClickAmount));
        placeholders.register("shift_right_click_amount", formatAmount(shiftRightClickAmount));

        return menuItemStack.build(player, false, placeholders);
    }

    private String formatAmount(BigDecimal amount) {
        return (amount.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + amount.stripTrailingZeros().toPlainString();
    }
}
