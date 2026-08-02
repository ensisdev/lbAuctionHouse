package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreatePlayerMigration extends Migration {

    @Override
    public void up() {
        create(Tables.PLAYERS, table -> {
            table.uuid("unique_id").primary().unique();
            table.string("name", 16);
            table.timestamps();
        });
    }
}
