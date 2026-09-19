package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateItemMigration extends Migration {

    @Override
    public void up() {
        // createOrAlter et non create : positionne isAlter() a true, ce qui autorise
        // MigrationManager a AJOUTER les colonnes manquantes sur les bases DEJA migrees.
        // Avec create(), MigrationManager sort immediatement (MigrationManager:100-102) et
        // pending_publish n'existerait JAMAIS sur une installation existante : chaque INSERT
        // d'annonce (ItemRepository.create) echouerait alors a l'execution.
        createOrAlter(Tables.ITEMS, table -> {
            table.autoIncrement("id");
            table.string("item_type", 255);
            table.string("seller_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("buyer_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);
            table.decimal("price", 65, 2);
            table.string("economy_name", 255);
            table.enumType("storage_type", StorageType.class);
            table.string("server_name", 255);
            table.timestamp("expired_at");
            // ----------------------------------------------------------------------------
            // pending_publish : JALON DE VENTE. C'est cette colonne, et elle seule, qui dit
            // si le VENDEUR a encore son lot en main. Deux abandons de vente sont possibles
            // et ils appellent des traitements OPPOSES : les confondre duplique le lot.
            //
            //   1  = RESERVEE. La ligne et ses contenus existent, mais les items sont ENCORE
            //        dans l'inventaire du vendeur. Une ligne 1 orpheline doit etre SUPPRIMEE
            //        (ItemRepository.recoverOrphanReservations) : la rendre reclamable
            //        donnerait au vendeur un SECOND exemplaire de son lot.
            //   2  = ENGAGEE. removeItemsFromSlots est passe, le vendeur a PERDU son lot et
            //        la publication n'est pas encore confirmee. Une ligne 2 orpheline doit
            //        etre rendue RECLAMABLE (EXPIRED) : c'est la seule facon de ne pas
            //        detruire le lot.
            //   0  = cycle de vie normal (publiee, expiree, achetee, supprimee).
            //   NULL = ligne anterieure a cette version, a ne JAMAIS considerer comme une
            //        reservation.
            //
            // NULLABLE et SANS valeur par defaut obligatoire : les INSERT de la migration V3
            // (V3MigrationService.createItem / createSentinelItem) n'ecrivent PAS cette
            // colonne, et toutes les operations de reservation exigent explicitement 1 ou 2,
            // donc les lignes migrees (NULL) ne sont ni ressuscitees ni supprimees par erreur.
            // ----------------------------------------------------------------------------
            table.integer("pending_publish").nullable();
            table.timestamps();
        });
    }
}
