package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration des taches de maintenance periodiques du plugin.
 *
 * @param expirationSweepIntervalSeconds   intervalle du balayage des annonces arrivees a terme, 0 pour desactiver
 * @param expirationSweepBatchSize         nombre maximum d'annonces traitees par passage
 * @param confirmationTimeoutSeconds       duree au-dela de laquelle un statut IS_*_CONFIRM est rearme, 0 pour desactiver
 * @param confirmationSweepIntervalSeconds intervalle du balayage des statuts de confirmation
 */
public record MaintenanceConfiguration(
        long expirationSweepIntervalSeconds,
        int expirationSweepBatchSize,
        long confirmationTimeoutSeconds,
        long confirmationSweepIntervalSeconds
) {

    /**
     * Intervalle par defaut du balayage d'expiration (60 secondes).
     */
    public static final long DEFAULT_EXPIRATION_SWEEP_INTERVAL_SECONDS = 60L;

    /**
     * Taille de lot par defaut du balayage d'expiration (50 annonces).
     */
    public static final int DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE = 50;

    /**
     * Delai par defaut de rearmement d'un statut de confirmation (60 secondes).
     */
    public static final long DEFAULT_CONFIRMATION_TIMEOUT_SECONDS = 60L;

    /**
     * Intervalle par defaut du balayage des statuts de confirmation (10 secondes).
     */
    public static final long DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS = 10L;

    /**
     * Plancher dur du delai de rearmement. En dessous, un joueur lent verrait sa confirmation
     * invalidee en pleine lecture et un second acheteur pourrait ouvrir une confirmation
     * concurrente sur le meme item.
     */
    public static final long MIN_CONFIRMATION_TIMEOUT_SECONDS = 60L;

    /**
     * Valeurs par defaut, utilisees par l'implementation {@code default} de
     * {@code Configuration#getMaintenance()} pour rester binaire-compatible avec les
     * implementations tierces existantes.
     *
     * @return la configuration de maintenance par defaut
     */
    public static MaintenanceConfiguration defaults() {
        return new MaintenanceConfiguration(
                DEFAULT_EXPIRATION_SWEEP_INTERVAL_SECONDS,
                DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE,
                DEFAULT_CONFIRMATION_TIMEOUT_SECONDS,
                DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS
        );
    }

    /**
     * Charge la section {@code maintenance} du fichier de configuration. Toutes les cles sont
     * facultatives : une cle absente retombe sur sa valeur par defaut, ce qui laisse le plugin
     * fonctionnel sur un config.yml qui ne porte pas encore la section.
     *
     * @param plugin        plugin utilise pour journaliser les valeurs refusees
     * @param configuration fichier de configuration a lire
     * @return la configuration de maintenance effective
     */
    public static MaintenanceConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        long expirationInterval = configuration.getLong("maintenance.expiration-sweep-interval-seconds", DEFAULT_EXPIRATION_SWEEP_INTERVAL_SECONDS);
        int batchSize = configuration.getInt("maintenance.expiration-sweep-batch-size", DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE);
        long confirmationTimeout = configuration.getLong("maintenance.confirmation-timeout-seconds", DEFAULT_CONFIRMATION_TIMEOUT_SECONDS);
        long confirmationInterval = configuration.getLong("maintenance.confirmation-sweep-interval-seconds", DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS);

        if (batchSize <= 0) {
            plugin.getLogger().warning("maintenance.expiration-sweep-batch-size must be > 0, falling back to " + DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE + ".");
            batchSize = DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE;
        }
        if (confirmationInterval <= 0) {
            confirmationInterval = DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS;
        }
        if (confirmationTimeout > 0 && confirmationTimeout < MIN_CONFIRMATION_TIMEOUT_SECONDS) {
            plugin.getLogger().warning("maintenance.confirmation-timeout-seconds is below the " + MIN_CONFIRMATION_TIMEOUT_SECONDS
                    + "s floor and would cancel confirmations under a player's eyes; using " + MIN_CONFIRMATION_TIMEOUT_SECONDS + "s.");
            confirmationTimeout = MIN_CONFIRMATION_TIMEOUT_SECONDS;
        }

        return new MaintenanceConfiguration(expirationInterval, batchSize, confirmationTimeout, confirmationInterval);
    }

    /**
     * @return {@code true} si le balayage d'expiration est actif
     */
    public boolean isExpirationSweepEnabled() {
        return this.expirationSweepIntervalSeconds > 0;
    }

    /**
     * @return {@code true} si le rearmement des statuts de confirmation est actif
     */
    public boolean isConfirmationSweepEnabled() {
        return this.confirmationTimeoutSeconds > 0;
    }
}
