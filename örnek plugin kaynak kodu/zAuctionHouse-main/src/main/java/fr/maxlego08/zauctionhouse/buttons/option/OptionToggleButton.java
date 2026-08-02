package fr.maxlego08.zauctionhouse.buttons.option;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class OptionToggleButton extends Button {

    private final AuctionPlugin plugin;
    private final PlayerOption option;
    private final String enableText;
    private final String disableText;

    public OptionToggleButton(AuctionPlugin plugin, PlayerOption option, String enableText, String disableText) {
        this.plugin = plugin;
        this.option = option;
        this.enableText = enableText;
        this.disableText = disableText;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        boolean value = this.plugin.getAuctionManager().getOptionService().getOption(player, this.option);
        placeholders.register("option_status", value ? this.enableText : this.disableText);
        placeholders.register("option_value", String.valueOf(value));
        placeholders.register("option_name", this.option.getKey());
        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        boolean currentValue = this.plugin.getAuctionManager().getOptionService().getOption(player, this.option);
        boolean newValue = !currentValue;

        this.plugin.getAuctionManager().getOptionService().setOption(player.getUniqueId(), this.option, newValue);
        this.plugin.sendMessage(player, newValue ? this.option.getEnabledMessage() : this.option.getDisabledMessage());

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }
}
