package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;

public class OptionPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        placeholder.register("option_", (player, optionKey) -> {
            if (optionKey == null || optionKey.isEmpty()) return "false";
            PlayerOption option = PlayerOption.fromKey(optionKey);
            if (option == null) return "false";
            return String.valueOf(plugin.getAuctionManager().getOptionService().getOption(player, option));
        }, "Returns the value of a player option (true/false)", "<option_key>");
    }
}
