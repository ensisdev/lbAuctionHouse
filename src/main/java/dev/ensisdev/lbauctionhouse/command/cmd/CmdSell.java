package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction sell <fiyat> — eldeki eşyayı ihalea koyar.
 * Fiyat aralığı, vergi ve blacklist config'den kontrol edilir.
 */
public class CmdSell extends AuctionCmd {

    public CmdSell() {
        super("sell", "lbauctionhouse.sell", false);
        setAliases("sat", "list");
        setUsage("<fiyat>");
        setDescription("Eşya sat");
    }

    @Override
    protected void execute() {
        if (player == null) return;

        if (!hasArg(0)) {
            new dev.ensisdev.lbauctionhouse.gui.SellGUI(
                plugin,
                dev.ensisdev.lbauctionhouse.LbAuctionHouse.getInstance(),
                manager, config
            ).open(player);
            return;
        }

        double price = argDouble(0);
        if (Double.isNaN(price) || price <= 0) {
            msg("listing.failed-number");
            return;
        }

        var item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            msg("listing.failed-hand");
            return;
        }

        if (price < config.getMinPrice() || price > config.getMaxPrice()) {
            msg("listing.failed-price");
            return;
        }

        if (config.isBlacklisted(item.getType())) {
            msg("listing.failed-blacklist");
            return;
        }

        boolean ok = manager.listItem(player, item.clone(), price);
        if (ok) {
            msg("listing.success", "price", String.format("%,.0f", price));
            item.setAmount(0);
        } else {
            msg("listing.failed-limit");
        }
    }
}
