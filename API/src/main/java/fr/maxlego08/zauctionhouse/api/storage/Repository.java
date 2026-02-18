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

public abstract class Repository {

    protected final AuctionPlugin plugin;
    protected final DatabaseConnection connection;
    private final String tableName;
    private final Logger logger;

    public Repository(AuctionPlugin plugin, DatabaseConnection connection, String tableName) {
        this.plugin = plugin;
        this.connection = connection;
        this.tableName = tableName;
        this.logger = JULogger.from(plugin.getLogger());
    }

    public AuctionPlugin getPlugin() {
        return this.plugin;
    }

    public DatabaseConnection getConnection() {
        return this.connection;
    }

    public String getTableName() {
        return this.tableName;
    }

    public Logger getLogger() {
        return this.logger;
    }

    protected void upsert(Consumer<Schema> consumer) {
        try {
            SchemaBuilder.upsert(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    protected void update(Consumer<Schema> consumer) {
        try {
            SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    protected void insert(Consumer<Schema> consumer) {
        insert(consumer, id -> {
        });
    }

    protected void insert(Consumer<Schema> consumer, Consumer<Integer> consumerResult) {
        try {
            consumerResult.accept(SchemaBuilder.insert(getTableName(), consumer).execute(this.connection, this.logger));
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    protected int insertSchema(Consumer<Schema> consumer) {
        try {
            return SchemaBuilder.insert(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
        return -1;
    }

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

    protected <T> List<T> selectAll(Class<T> clazz) {
        Schema schema = SchemaBuilder.select(getTableName());
        try {
            return schema.executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return new ArrayList<>();
    }

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

    protected void insert(List<Schema> schemas) {
        InsertBatchRequest insertBatchRequest = new InsertBatchRequest(schemas);
        insertBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }

    protected void upsert(List<Schema> schemas) {
        UpsertBatchRequest upsertBatchRequest = new UpsertBatchRequest(schemas);
        upsertBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }

    protected void update(List<Schema> schemas) {
        UpdateBatchRequest updateBatchRequest = new UpdateBatchRequest(schemas);
        updateBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
    }

    protected Schema createInsertSchema(Consumer<Schema> consumer) {
        return SchemaBuilder.insert(getTableName(), consumer);
    }

    protected Schema createUpdateSchema(Consumer<Schema> consumer) {
        return SchemaBuilder.update(getTableName(), consumer);
    }
}
