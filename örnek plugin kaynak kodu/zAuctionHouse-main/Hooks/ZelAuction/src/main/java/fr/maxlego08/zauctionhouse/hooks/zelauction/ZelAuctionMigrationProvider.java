package fr.maxlego08.zauctionhouse.hooks.zelauction;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.migration.MigrationCallback;
import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import fr.maxlego08.zauctionhouse.api.migration.MigrationResult;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Migration provider for ZelAuction.
 * <p>
 * This provider handles migration from the ZelAuction plugin to zAuctionHouse V4.
 * It reads data directly from the ZelAuction database (SQLite or MySQL) using
 * the database.yml configuration from the ZelAuction plugin folder.
 * </p>
 */
public class ZelAuctionMigrationProvider implements MigrationProvider {

    @Override
    public String getId() {
        return "zelauction";
    }

    @Override
    public String getDisplayName() {
        return "ZelAuction";
    }

    @Override
    public String getDescription() {
        return "ZelAuction auction plugin (reads from plugins/ZelAuction/)";
    }

    @Override
    public List<String> getAliases() {
        return List.of("zel", "zelauctions");
    }

    @Override
    public String getConfigSection() {
        return null;
    }

    @Override
    public String getDefaultSqlitePath() {
        return null;
    }

    @Override
    public String getDefaultJsonFolder() {
        return null;
    }

    @Override
    public String getDefaultTablePrefix() {
        return null;
    }

    @Override
    public String validateConfig(ConfigurationSection config) {
        File zelAuctionFolder = new File("plugins/ZelAuction");
        if (!zelAuctionFolder.exists() || !zelAuctionFolder.isDirectory()) {
            return "ZelAuction plugin folder not found at plugins/ZelAuction/";
        }

        File databaseYml = new File(zelAuctionFolder, "database.yml");
        if (!databaseYml.exists()) {
            return "ZelAuction database.yml not found at plugins/ZelAuction/database.yml";
        }

        return null;
    }

    @Override
    public CompletableFuture<MigrationResult> migrate(AuctionPlugin plugin, ConfigurationSection config, MigrationCallback callback) {
        var service = new ZelAuctionMigrationService(plugin);
        service.onProgress(callback::onProgress);
        return service.migrate().thenApply(result -> {
            if (!result.isSuccess()) {
                return MigrationResult.failure(result.getErrorMessage());
            }
            return MigrationResult.success(
                    result.getPlayersImported(),
                    result.getItemsImported(),
                    result.getTransactionsImported(),
                    result.getErrors(),
                    result.getDurationMs()
            );
        }).exceptionally(ex -> MigrationResult.failure(ex.getMessage()));
    }
}
