package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.storage.ZStorageManager;
import org.junit.jupiter.api.AfterEach;

import static org.mockito.Mockito.lenient;

/**
 * Socle des tests d'integration qui passent par un VRAI {@link ZStorageManager} (SQLite) : c'est
 * lui qui ouvre la connexion, enregistre et execute les migrations de production, enregistre les
 * repositories et balaye les reservations orphelines, exactement comme au demarrage du plugin.
 * <p>
 * {@code plugin.getStorageManager()} rend ce manager : les services qui passent par
 * {@code plugin.getStorageManager().with(XRepository.class)} fonctionnent sans autre cablage.
 * <p>
 * Pour tester un comportement AU DEMARRAGE (par exemple la recuperation des reservations
 * orphelines), inserer les lignes voulues puis appeler {@link #restartStorageManager()} : il
 * simule un arret puis un redemarrage sur la meme base.
 */
public abstract class StorageManagerTestBase extends DatabaseTestBase {

    protected ZStorageManager storageManager;

    /**
     * Les migrations sont executees par {@code ZStorageManager.onEnable()}, pas par le socle.
     */
    @Override
    protected void registerAndRunMigrations() {
        // volontairement vide, voir la javadoc de la classe
    }

    @Override
    protected void afterDatabaseReady() throws Exception {
        startStorageManager();
    }

    @AfterEach
    void tearDownStorageManager() {
        stopStorageManager();
    }

    /**
     * Les repositories sont ceux du manager, pas ceux du socle.
     */
    @Override
    protected <T extends Repository> T repo(Class<T> type) {
        return this.storageManager.with(type);
    }

    /**
     * Demarre un manager neuf sur la base courante (migrations incluses, idempotentes).
     */
    protected void startStorageManager() {
        resetMigrationManager();
        this.storageManager = new ZStorageManager(this.plugin);
        lenient().when(this.plugin.getStorageManager()).thenReturn(this.storageManager);
        if (!this.storageManager.onEnable()) {
            throw new IllegalStateException("ZStorageManager.onEnable() returned false");
        }
    }

    protected void stopStorageManager() {
        if (this.storageManager != null) {
            this.storageManager.onDisable();
            this.storageManager = null;
        }
    }

    /**
     * Simule un arret puis un redemarrage du plugin sur la meme base.
     */
    protected void restartStorageManager() {
        stopStorageManager();
        startStorageManager();
    }
}
