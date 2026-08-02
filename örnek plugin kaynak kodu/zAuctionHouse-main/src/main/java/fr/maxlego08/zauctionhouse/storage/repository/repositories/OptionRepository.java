package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.OptionDTO;

import java.util.List;
import java.util.UUID;

public class OptionRepository extends Repository {

    public OptionRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.OPTIONS);
    }

    public void upsertOption(UUID playerUniqueId, String optionName, String optionValue) {
        upsert(schema -> {
            schema.uuid("player_unique_id", playerUniqueId).primary();
            schema.string("option_name", optionName).primary();
            schema.string("option_value", optionValue);
        });
    }

    public List<OptionDTO> selectAll(UUID playerUniqueId) {
        return select(OptionDTO.class, schema -> schema.where("player_unique_id", playerUniqueId.toString()));
    }

    public void deleteOption(UUID playerUniqueId, String optionName) {
        delete(schema -> schema.where("player_unique_id", playerUniqueId.toString()).where("option_name", optionName));
    }

    public void deleteAll(UUID playerUniqueId) {
        delete(schema -> schema.where("player_unique_id", playerUniqueId.toString()));
    }
}
