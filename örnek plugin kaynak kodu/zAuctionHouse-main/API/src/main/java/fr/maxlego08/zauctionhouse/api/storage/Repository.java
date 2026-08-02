package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.SchemaBuilder;
import fr.maxlego08.sarah.database.Schema;
import fr.maxlego08.sarah.logger.JULogger;
import fr.maxlego08.sarah.logger.Logger;
import fr.maxlego08.sarah.requests.InsertBatchRequest;
import fr.maxlego08.sarah.requests.UpdateBatchRequest;
import fr.maxlego08.sarah.requests.UpsertBatchRequest;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base class for database repositories using the Sarah ORM.
 * <p>
 * Repositories provide an abstraction layer for database operations,
 * encapsulating SQL logic and providing type-safe methods for CRUD operations.
 * Each repository is associated with a specific database table.
 */
public abstract class Repository {

    protected final AuctionPlugin plugin;
    protected final DatabaseConnection connection;
    private final String tableName;
    private final Logger logger;

    /**
     * Creates a new repository instance.
     *
     * @param plugin     the auction house plugin
     * @param connection the database connection
     * @param tableName  the name of the database table this repository manages
     */
    public Repository(AuctionPlugin plugin, DatabaseConnection connection, String tableName) {
        this.plugin = plugin;
        this.connection = connection;
        this.tableName = tableName;
        this.logger = JULogger.from(plugin.getLogger());
    }

    /**
     * Gets the auction house plugin instance.
     *
     * @return the plugin
     */
    public AuctionPlugin getPlugin() {
        return this.plugin;
    }

    /**
     * Gets the database connection.
     *
     * @return the database connection
     */
    public DatabaseConnection getConnection() {
        return this.connection;
    }

    /**
     * Gets the name of the table this repository manages.
     *
     * @return the table name
     */
    public String getTableName() {
        return this.tableName;
    }

    /**
     * Gets the logger for database operations.
     *
     * @return the logger
     */
    public Logger getLogger() {
        return this.logger;
    }

    /**
     * Executes an upsert (insert or update) operation.
     *
     * @param consumer the schema configuration
     */
    protected void upsert(Consumer<Schema> consumer) {
        try {
            SchemaBuilder.upsert(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Executes an update operation.
     *
     * @param consumer the schema configuration
     */
    protected void update(Consumer<Schema> consumer) {
        try {
            SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Executes an insert operation without returning the generated ID.
     *
     * @param consumer the schema configuration
     */
    protected void insert(Consumer<Schema> consumer) {
        insert(consumer, id -> {
        });
    }

    /**
     * Executes an insert operation and passes the generated ID to a callback.
     *
     * @param consumer       the schema configuration
     * @param consumerResult callback receiving the generated ID
     */
    protected void insert(Consumer<Schema> consumer, Consumer<Integer> consumerResult) {
        try {
            consumerResult.accept(SchemaBuilder.insert(getTableName(), consumer).execute(this.connection, this.logger));
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Executes an insert operation synchronously and returns the generated ID.
     *
     * @param consumer the schema configuration
     * @return the generated ID, or -1 if the operation failed
     */
    protected int insertSync(Consumer<Schema> consumer) {
        try {
            return SchemaBuilder.insert(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
        return -1;
    }

    /**
     * Executes a count query with the given conditions.
     *
     * @param consumer the schema configuration for WHERE clauses
     * @return the count result, or 0 if the operation failed
     */
    protected long select(Consumer<Schema> consumer) {
        Schema schema = SchemaBuilder.selectCount(getTableName());
        consumer.accept(schema);
        try {
            return schema.executeSelectCount(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
        return 0L;
    }

    /**
     * Executes a select query and maps results to the specified class.
     *
     * @param clazz    the class to map results to
     * @param consumer the schema configuration for WHERE clauses
     * @param <T>      the result type
     * @return list of mapped objects, or empty list if the operation failed
     */
    protected <T> List<T> select(Class<T> clazz, Consumer<Schema> consumer) {
        Schema schema = SchemaBuilder.select(getTableName());
        consumer.accept(schema);
        try {
            return schema.executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Executes a select all query and maps results to the specified class.
     *
     * @param clazz the class to map results to
     * @param <T>   the result type
     * @return list of all mapped objects, or empty list if the operation failed
     */
    protected <T> List<T> selectAll(Class<T> clazz) {
        Schema schema = SchemaBuilder.select(getTableName());
        try {
            return schema.executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Executes a delete operation with the given conditions.
     *
     * @param consumer the schema configuration for WHERE clauses
     * @return the number of rows affected, or -1 if the operation failed
     */
    protected int delete(Consumer<Schema> consumer) {
        Schema schema = SchemaBuilder.delete(getTableName());
        consumer.accept(schema);
        try {
            return schema.execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return -1;
        }
    }

    /**
     * Executes a batch insert operation.
     *
     * @param schemas the list of insert schemas to execute
     */
    protected void insert(List<Schema> schemas) {
        InsertBatchRequest insertBatchRequest = new InsertBatchRequest(schemas);
        insertBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }

    /**
     * Executes a batch upsert operation.
     *
     * @param schemas the list of upsert schemas to execute
     */
    protected void upsert(List<Schema> schemas) {
        UpsertBatchRequest upsertBatchRequest = new UpsertBatchRequest(schemas);
        upsertBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }

    /**
     * Executes a batch update operation.
     *
     * @param schemas the list of update schemas to execute
     */
    protected void update(List<Schema> schemas) {
        UpdateBatchRequest updateBatchRequest = new UpdateBatchRequest(schemas);
        updateBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }

    /**
     * Creates an insert schema without executing it.
     *
     * @param consumer the schema configuration
     * @return the configured schema
     */
    protected Schema createInsertSchema(Consumer<Schema> consumer) {
        return SchemaBuilder.insert(getTableName(), consumer);
    }

    /**
     * Creates an update schema without executing it.
     *
     * @param consumer the schema configuration
     * @return the configured schema
     */
    protected Schema createUpdateSchema(Consumer<Schema> consumer) {
        return SchemaBuilder.update(getTableName(), consumer);
    }
}
