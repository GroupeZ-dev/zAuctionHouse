package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.SchemaBuilder;
import fr.maxlego08.sarah.database.Schema;
import fr.maxlego08.sarah.exceptions.DatabaseException;
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

    /**
     * Maximum number of identifiers sent in a single {@code IN (...)} clause.
     * <p>
     * Les pilotes JDBC plafonnent le nombre de parametres lies (32 766 pour sqlite-jdbc,
     * 65 535 pour MySQL/MariaDB). Au-dela, l'instruction est REJETEE, et les variantes de
     * {@code select} qui avalent l'exception transformaient ce rejet en LISTE VIDE (C-001).
     */
    protected static final int IN_CLAUSE_CHUNK_SIZE = 500;

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
     * Executes an update operation and returns the number of rows actually affected.
     * <p>
     * Contrairement a {@link #update(Consumer)}, le nombre de lignes modifiees n'est PAS jete.
     * C'est le seul signal qui permette a un appelant de savoir qu'il a PERDU une course :
     * 0 ligne signifie que la ligne ne portait plus l'etat source attendu au moment de
     * l'ecriture, autrement dit qu'un autre serveur (ou un autre chemin local) est passe avant.
     *
     * @param consumer the schema configuration
     * @return the number of affected rows; {@code 0} means the compare-and-set was lost
     */
    protected int updateReturning(Consumer<Schema> consumer) {
        try {
            return SchemaBuilder.update(getTableName(), consumer).execute(this.connection, this.logger);
        } catch (SQLException exception) {
            // Filet : Sarah leve deja une DatabaseException (RuntimeException) et n'atteint
            // jamais cette branche. On relaie sans jamais avaler l'echec, sinon un echec SQL
            // serait indiscernable d'une course perdue.
            throw new DatabaseException("update", getTableName(), exception);
        }
    }

    /**
     * Executes a batch update operation and returns the total number of rows affected.
     * <p>
     * ATTENTION : certains pilotes JDBC renvoient {@code Statement.SUCCESS_NO_INFO} (-2) par
     * instruction ; la somme peut donc etre negative ou plus petite que le nombre de schemas
     * meme sans course perdue. L'appelant doit traiter tout total different du nombre de
     * schemas comme un "a verifier", jamais comme un "a echouer".
     *
     * @param schemas the list of update schemas to execute
     * @return the sum of the per-statement affected row counts
     */
    protected int updateReturning(List<Schema> schemas) {
        if (schemas.isEmpty()) return 0;
        UpdateBatchRequest updateBatchRequest = new UpdateBatchRequest(schemas);
        return updateBatchRequest.execute(this.connection, this.connection.getDatabaseConfiguration(), this.logger);
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
     * Executes a select query and maps results to the specified class, PROPAGATING any failure.
     * <p>
     * Contrairement a {@link #select(Class, Consumer)}, qui avale l'exception et rend une liste
     * vide, cette variante relance. A utiliser sur tout chemin ou "aucune ligne" et "la requete a
     * echoue" seraient indiscernables et ou le resultat vide publierait un etat casse.
     *
     * @param clazz    the class to map results to
     * @param consumer the schema configuration for WHERE clauses
     * @param <T>      the result type
     * @return list of mapped objects
     * @throws IllegalStateException if the query failed
     */
    protected <T> List<T> selectOrFail(Class<T> clazz, Consumer<Schema> consumer) {
        Schema schema = SchemaBuilder.select(getTableName());
        consumer.accept(schema);
        try {
            return schema.executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            // Le logger de Sarah n'expose que info(String) : on passe par celui du plugin.
            this.plugin.getLogger().severe("select failed on " + getTableName() + ": " + exception.getMessage());
            throw new IllegalStateException("select failed on " + getTableName(), exception);
        }
    }

    /**
     * Executes a select all query and maps results to the specified class, PROPAGATING failures.
     *
     * @param clazz the class to map results to
     * @param <T>   the result type
     * @return list of all mapped objects
     * @throws IllegalStateException if the query failed
     */
    protected <T> List<T> selectAllOrFail(Class<T> clazz) {
        try {
            return SchemaBuilder.select(getTableName()).executeSelect(clazz, this.connection, this.logger);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("selectAll failed on " + getTableName() + ": " + exception.getMessage());
            throw new IllegalStateException("selectAll failed on " + getTableName(), exception);
        }
    }

    /**
     * Runs a paginated {@code IN (...)} select, so the bound-parameter cap can never be reached.
     *
     * @param clazz      the class to map results to
     * @param columnName the column the identifiers belong to
     * @param values     the identifiers, may be empty
     * @param <T>        the result type
     * @return the concatenated results of every chunk
     * @throws IllegalStateException if any chunk failed
     */
    protected <T> List<T> selectInOrFail(Class<T> clazz, String columnName, List<String> values) {
        if (values.isEmpty()) return List.of();

        List<T> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index += IN_CLAUSE_CHUNK_SIZE) {
            // List.copyOf : subList rend une VUE, la lambda ne doit pas capturer une vue.
            List<String> chunk = List.copyOf(values.subList(index, Math.min(index + IN_CLAUSE_CHUNK_SIZE, values.size())));
            result.addAll(selectOrFail(clazz, schema -> schema.whereIn(columnName, chunk)));
        }
        return result;
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
