package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateOptionsMigration extends Migration {

    @Override
    public void up() {
        create(Tables.OPTIONS, table -> {
            table.string("player_unique_id", 36).primary().foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("option_name", 64).primary();
            table.longText("option_value");
            table.timestamps();
        });
    }
}
