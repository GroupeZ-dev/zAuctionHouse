package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.buttons.admin.TransactionStatusFilterButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransactionStatusFilterLoaderTest extends LoaderTestBase {

    private TransactionStatusFilterLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new TransactionStatusFilterLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_STATUS")
    void name() {
        assertEquals("ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_STATUS", loader.getName());
    }

    @Test
    @DisplayName("statuts, textes, nom 'tous' et noms personnalises lus")
    void fullConfiguration() {
        TransactionStatusFilterButton button = loadInline(loader, "filter", """
                enable-text: "<green>%status%"
                disable-text: "<gray>%status%"
                all-statuses-name: "Tous"
                statuses:
                  - retrieved
                  - PENDING
                status-names:
                  pending: "En attente"
                """, TransactionStatusFilterButton.class);

        assertEquals("<green>%status%", field(button, "enableText"));
        assertEquals("<gray>%status%", field(button, "disableText"));
        assertEquals("Tous", field(button, "allStatusesName"));
        assertEquals(List.of(TransactionStatus.RETRIEVED, TransactionStatus.PENDING), field(button, "statuses"));
        Map<TransactionStatus, String> names = field(button, "statusNames");
        assertEquals("En attente", names.get(TransactionStatus.PENDING));
        assertEquals("Retrieved", names.get(TransactionStatus.RETRIEVED));
        assertEquals(TransactionStatus.values().length, names.size());
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("valeurs par defaut : tous les statuts, 'All', textes &a/&7")
    void defaults() {
        TransactionStatusFilterButton button = loadInline(loader, "filter", "slot: 47", TransactionStatusFilterButton.class);

        assertEquals("&a%status%", field(button, "enableText"));
        assertEquals("&7%status%", field(button, "disableText"));
        assertEquals("All", field(button, "allStatusesName"));
        assertEquals(List.of(TransactionStatus.values()), field(button, "statuses"));
        Map<TransactionStatus, String> names = field(button, "statusNames");
        for (TransactionStatus status : TransactionStatus.values()) {
            assertEquals(status.getDefaultDisplayName(), names.get(status));
        }
    }

    @Test
    @DisplayName("statut inconnu ignore ; uniquement des inconnus -> tous les statuts")
    void invalidStatuses() {
        assertEquals(List.of(TransactionStatus.PENDING), field(loadInline(loader, "filter", "statuses: [pending, nope]", TransactionStatusFilterButton.class), "statuses"));
        assertEquals(List.of(TransactionStatus.values()), field(loadInline(loader, "filter", "statuses: [nope]", TransactionStatusFilterButton.class), "statuses"));
    }

    @Test
    @DisplayName("status-names : cle inconnue ignoree")
    void unknownNameKey_ignored() {
        TransactionStatusFilterButton button = loadInline(loader, "filter", """
                status-names:
                  nope: "x"
                """, TransactionStatusFilterButton.class);

        Map<TransactionStatus, String> names = field(button, "statusNames");
        assertEquals(TransactionStatus.values().length, names.size());
        assertEquals("Pending", names.get(TransactionStatus.PENDING));
    }
}
