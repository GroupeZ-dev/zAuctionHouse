package fr.maxlego08.zauctionhouse.economy;

import com.tcoded.folialib.enums.EntityTaskResult;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.tax.TaxAmountType;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.tax.ZTaxConfiguration;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import fr.traqueur.currencies.Currencies;
import fr.traqueur.currencies.CurrencyProvider;
import fr.traqueur.currencies.providers.ExperienceProvider;
import fr.traqueur.currencies.providers.ItemProvider;
import fr.traqueur.currencies.providers.LevelProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZAuctionEconomyTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private CurrencyProvider provider;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.provider = mock(CurrencyProvider.class);
        this.playerId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------- helpers

    private ZAuctionEconomy economy(CurrencyProvider currencyProvider, Currencies currencies) {
        EnumMap<ItemType, BigDecimal> minPrices = new EnumMap<>(ItemType.class);
        minPrices.put(ItemType.AUCTION, BigDecimal.TEN);
        EnumMap<ItemType, BigDecimal> maxPrices = new EnumMap<>(ItemType.class);
        maxPrices.put(ItemType.AUCTION, new BigDecimal("10000"));
        return new ZAuctionEconomy(this.plugin, currencyProvider, currencies, "vault", "Vault", "%price%$ coin%s%", "$",
                "zauctionhouse.economy.vault", "Sale", "Purchase", PriceFormat.PRICE_WITH_DECIMAL_FORMAT, minPrices, maxPrices,
                true, false, null);
    }

    private ZAuctionEconomy vaultEconomy() {
        return economy(this.provider, Currencies.VAULT);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "attendu " + expected + " mais obtenu " + actual);
    }

    // ---------------------------------------------------------------- getters

    @Nested
    @DisplayName("getters")
    class Getters {

        @Test
        @DisplayName("tous les attributs du constructeur sont exposes")
        void constructorValues_exposed() {
            ZAuctionEconomy economy = vaultEconomy();

            assertSame(plugin, economy.getPlugin());
            assertSame(provider, economy.getCurrencyProvider());
            assertEquals(Currencies.VAULT, economy.getCurrencies());
            assertEquals("vault", economy.getName());
            assertEquals("Vault", economy.getDisplayName());
            assertEquals("%price%$ coin%s%", economy.getFormat());
            assertEquals("$", economy.getSymbol());
            assertEquals("zauctionhouse.economy.vault", economy.getPermission());
            assertEquals("Sale", economy.getDepositReason());
            assertEquals("Purchase", economy.getWithdrawReason());
            assertEquals(PriceFormat.PRICE_WITH_DECIMAL_FORMAT, economy.getPriceFormat());
            assertTrue(economy.isAutoClaim());
            assertFalse(economy.mustBeOnline());
        }

        @Test
        @DisplayName("min/max par type d'item : present pour AUCTION, null pour les autres")
        void prices_perItemType() {
            ZAuctionEconomy economy = vaultEconomy();

            assertMoney("10", economy.getMinPrice(ItemType.AUCTION));
            assertMoney("10000", economy.getMaxPrice(ItemType.AUCTION));
            assertNull(economy.getMinPrice(ItemType.BID));
            assertNull(economy.getMaxPrice(ItemType.BID));
        }

        @Test
        @DisplayName("configuration de taxe null remplacee par une configuration desactivee")
        void nullTaxConfiguration_replacedByDisabled() {
            ZAuctionEconomy economy = vaultEconomy();

            assertFalse(economy.getTaxConfiguration().isEnabled());
            assertFalse(economy.calculateSellTax(MockPlugins.player("p"), BigDecimal.TEN, null).hasTax());
        }

        @Test
        @DisplayName("la configuration de taxe fournie est conservee")
        void taxConfiguration_kept() {
            ZTaxConfiguration tax = new ZTaxConfiguration(true, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 10, null, List.of(), false, List.of());
            ZAuctionEconomy economy = new ZAuctionEconomy(plugin, provider, Currencies.VAULT, "v", "V", "%price%", "$", null,
                    "d", "w", PriceFormat.PRICE_RAW, new EnumMap<>(ItemType.class), new EnumMap<>(ItemType.class), false, true, tax);

            assertSame(tax, economy.getTaxConfiguration());
            assertFalse(economy.isAutoClaim());
            assertTrue(economy.mustBeOnline());
            assertMoney("10.00", economy.calculatePurchaseTax(MockPlugins.player("p"), new BigDecimal("100"), null).taxAmount());
        }

        @Test
        @DisplayName("format(prix, quantite) remplace %price% et met le pluriel au-dela de 1")
        void format_defaultMethod() {
            ZAuctionEconomy economy = vaultEconomy();

            assertEquals("1,000$ coin", economy.format("1,000", 1));
            assertEquals("1,000$ coins", economy.format("1,000", 2));
            assertEquals("5$ coin", economy.format("5", 0));
        }
    }

    // ---------------------------------------------------------------- hors ligne

    @Nested
    @DisplayName("supportsOfflineDeposit")
    class OfflineDeposit {

        @ParameterizedTest(name = "{0} ne sait pas crediter un joueur hors ligne")
        @EnumSource(value = Currencies.class, names = {"ITEM", "ZMENUITEMS", "LEVEL", "EXPERIENCE"})
        void playerBackedCurrencies_doNotSupportOffline(Currencies currencies) {
            assertFalse(ZAuctionEconomy.supportsOfflineDeposit(currencies));
            assertFalse(economy(provider, currencies).supportsOfflineDeposit());
        }

        @ParameterizedTest(name = "{0} sait crediter un joueur hors ligne")
        @EnumSource(value = Currencies.class, names = {"VAULT", "PLAYERPOINTS", "COINSENGINE", "ZESSENTIALS"})
        void pluginBackedCurrencies_supportOffline(Currencies currencies) {
            assertTrue(ZAuctionEconomy.supportsOfflineDeposit(currencies));
            assertTrue(economy(provider, currencies).supportsOfflineDeposit());
        }

        @Test
        @DisplayName("type null (construction historique) : considere capable")
        void nullCurrencies_supported() {
            assertTrue(ZAuctionEconomy.supportsOfflineDeposit(null));
            assertTrue(economy(provider, null).supportsOfflineDeposit());
            assertNull(economy(provider, null).getCurrencies());
        }
    }

    // ---------------------------------------------------------------- lecture

    @Nested
    @DisplayName("get / has")
    class Balance {

        @Test
        @DisplayName("get rend le solde du provider dans un future deja complete")
        void get_returnsProviderBalance() {
            when(provider.getBalance(playerId)).thenReturn(new BigDecimal("42.5"));

            CompletableFuture<BigDecimal> future = vaultEconomy().get(playerId);

            assertTrue(future.isDone());
            assertMoney("42.5", future.join());
        }

        @Test
        @DisplayName("has : vrai si solde >= prix, y compris a l'egalite")
        void has_comparesBalance() {
            when(provider.getBalance(playerId)).thenReturn(new BigDecimal("100"));
            ZAuctionEconomy economy = vaultEconomy();

            assertTrue(economy.has(playerId, new BigDecimal("99.99")).join());
            assertTrue(economy.has(playerId, new BigDecimal("100.00")).join());
            assertFalse(economy.has(playerId, new BigDecimal("100.01")).join());
        }

        @Test
        @DisplayName("hasSync lit l'UUID du joueur")
        void hasSync_usesPlayerUniqueId() {
            Player player = MockPlugins.player(playerId, "Alice");
            when(provider.getBalance(playerId)).thenReturn(BigDecimal.TEN);

            assertTrue(vaultEconomy().hasSync(player, BigDecimal.ONE));
            assertFalse(vaultEconomy().hasSync(player, new BigDecimal("11")));
        }
    }

    // ---------------------------------------------------------------- mutations simples

    @Nested
    @DisplayName("deposit / withdraw")
    class SimpleMutations {

        @Test
        @DisplayName("deposit delegue au provider avec la raison")
        void deposit_delegates() {
            vaultEconomy().deposit(playerId, BigDecimal.TEN, "why");

            verify(provider).deposit(playerId, BigDecimal.TEN, "why");
        }

        @Test
        @DisplayName("withdraw delegue au provider avec la raison")
        void withdraw_delegates() {
            vaultEconomy().withdraw(playerId, BigDecimal.ONE, "why");

            verify(provider).withdraw(playerId, BigDecimal.ONE, "why");
        }
    }

    // ---------------------------------------------------------------- withdrawChecked

    @Nested
    @DisplayName("withdrawChecked")
    class WithdrawChecked {

        @Test
        @DisplayName("joueur null : false sans toucher au provider")
        void nullPlayer_returnsFalse() {
            assertFalse(vaultEconomy().withdrawChecked(null, BigDecimal.TEN, "r"));
            verify(provider, never()).withdraw(any(), any(), any());
        }

        @Test
        @DisplayName("montant null, nul ou negatif : true sans rien retirer")
        void nonPositiveValue_returnsTrueWithoutWithdraw() {
            ZAuctionEconomy economy = vaultEconomy();

            assertTrue(economy.withdrawChecked(playerId, null, "r"));
            assertTrue(economy.withdrawChecked(playerId, BigDecimal.ZERO, "r"));
            assertTrue(economy.withdrawChecked(playerId, new BigDecimal("-1"), "r"));
            verify(provider, never()).withdraw(any(), any(), any());
            verify(provider, never()).getBalance(any());
        }

        @Test
        @DisplayName("solde insuffisant : false et aucun retrait")
        void insufficientBalance_returnsFalse() {
            when(provider.getBalance(playerId)).thenReturn(new BigDecimal("9.99"));

            assertFalse(vaultEconomy().withdrawChecked(playerId, BigDecimal.TEN, "r"));
            verify(provider, never()).withdraw(any(), any(), any());
        }

        @Test
        @DisplayName("solde null : false et aucun retrait")
        void nullBalance_returnsFalse() {
            when(provider.getBalance(playerId)).thenReturn(null);

            assertFalse(vaultEconomy().withdrawChecked(playerId, BigDecimal.TEN, "r"));
            verify(provider, never()).withdraw(any(), any(), any());
        }

        @Test
        @DisplayName("solde exactement egal : retrait effectue et true")
        void exactBalance_withdraws() {
            when(provider.getBalance(playerId)).thenReturn(BigDecimal.TEN, BigDecimal.ZERO);

            assertTrue(vaultEconomy().withdrawChecked(playerId, BigDecimal.TEN, "r"));
            verify(provider).withdraw(playerId, BigDecimal.TEN, "r");
            verify(provider, times(2)).getBalance(playerId);
        }

        @Test
        @DisplayName("lecture du solde en echec : false et aucun retrait")
        void balanceReadFails_returnsFalse() {
            when(provider.getBalance(playerId)).thenThrow(new IllegalStateException("db down"));

            assertFalse(vaultEconomy().withdrawChecked(playerId, BigDecimal.TEN, "r"));
            verify(provider, never()).withdraw(any(), any(), any());
        }

        @Test
        @DisplayName("retrait en echec : false")
        void withdrawFails_returnsFalse() {
            when(provider.getBalance(playerId)).thenReturn(new BigDecimal("100"));
            doThrow(new IllegalStateException("boom")).when(provider).withdraw(playerId, BigDecimal.TEN, "r");

            assertFalse(vaultEconomy().withdrawChecked(playerId, BigDecimal.TEN, "r"));
        }

        @Test
        @DisplayName("post-controle informatif : un solde qui n'a pas bouge ne fait PAS echouer le retrait")
        void postCheckMismatch_stillTrue() {
            when(provider.getBalance(playerId)).thenReturn(new BigDecimal("100"), new BigDecimal("100"));

            assertTrue(vaultEconomy().withdrawChecked(playerId, BigDecimal.TEN, "r"));
            verify(provider).withdraw(playerId, BigDecimal.TEN, "r");
        }

        @Test
        @DisplayName("post-controle en echec : toujours true, l'argent a ete retire")
        void postCheckThrows_stillTrue() {
            when(provider.getBalance(playerId)).thenReturn(new BigDecimal("100")).thenThrow(new IllegalStateException("late"));

            assertTrue(vaultEconomy().withdrawChecked(playerId, BigDecimal.TEN, "r"));
        }
    }

    // ---------------------------------------------------------------- depositChecked

    @Nested
    @DisplayName("depositChecked")
    class DepositChecked {

        @Test
        @DisplayName("joueur null : false")
        void nullPlayer_returnsFalse() {
            assertFalse(vaultEconomy().depositChecked(null, BigDecimal.TEN, "r"));
            verify(provider, never()).deposit(any(), any(), any());
        }

        @Test
        @DisplayName("montant null, nul ou negatif : true sans rien deposer")
        void nonPositiveValue_returnsTrue() {
            ZAuctionEconomy economy = vaultEconomy();

            assertTrue(economy.depositChecked(playerId, null, "r"));
            assertTrue(economy.depositChecked(playerId, BigDecimal.ZERO, "r"));
            assertTrue(economy.depositChecked(playerId, new BigDecimal("-0.01"), "r"));
            verify(provider, never()).deposit(any(), any(), any());
        }

        @Test
        @DisplayName("depot reussi : true et provider appele")
        void success_returnsTrue() {
            assertTrue(vaultEconomy().depositChecked(playerId, BigDecimal.TEN, "r"));
            verify(provider).deposit(playerId, BigDecimal.TEN, "r");
        }

        @Test
        @DisplayName("depot en echec : false")
        void providerThrows_returnsFalse() {
            doThrow(new IllegalStateException("boom")).when(provider).deposit(playerId, BigDecimal.TEN, "r");

            assertFalse(vaultEconomy().depositChecked(playerId, BigDecimal.TEN, "r"));
        }

        @Test
        @DisplayName("C-032 : economie LEVEL et joueur hors ligne : refus AVANT tout appel au provider")
        void offlineIncapable_offlinePlayer_refused() {
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);

                assertFalse(economy(provider, Currencies.LEVEL).depositChecked(playerId, BigDecimal.TEN, "r"));
            }
            verify(provider, never()).deposit(any(), any(), any());
        }

        @Test
        @DisplayName("economie VAULT et joueur hors ligne : le depot passe quand meme")
        void offlineCapable_offlinePlayer_deposits() {
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);

                assertTrue(vaultEconomy().depositChecked(playerId, BigDecimal.TEN, "r"));
            }
            verify(provider).deposit(playerId, BigDecimal.TEN, "r");
        }

        @Test
        @DisplayName("economie LEVEL et joueur en ligne sur sa region : depot inline")
        void offlineIncapable_onlinePlayer_deposits() {
            Player player = MockPlugins.player(playerId, "Alice");
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertTrue(economy(provider, Currencies.LEVEL).depositChecked(playerId, BigDecimal.TEN, "r"));
            }
            verify(provider).deposit(playerId, BigDecimal.TEN, "r");
        }
    }

    // ---------------------------------------------------------------- thread proprietaire

    @Nested
    @DisplayName("confinement sur le thread proprietaire (C-084)")
    class OwnerThread {

        private Player player;
        private PlatformScheduler scheduler;

        @BeforeEach
        void setUpScheduler() {
            this.player = MockPlugins.player(playerId, "Alice");
            this.scheduler = mock(PlatformScheduler.class);
            when(this.scheduler.isOwnedByCurrentRegion(this.player)).thenReturn(false);
            when(plugin.getScheduler()).thenReturn(this.scheduler);
        }

        private void stubRunAtEntity(EntityTaskResult result, boolean executeTask) {
            when(this.scheduler.runAtEntity(eq(this.player), any())).thenAnswer(invocation -> {
                if (executeTask) {
                    Consumer<WrappedTask> task = invocation.getArgument(1);
                    task.accept(mock(WrappedTask.class));
                }
                return CompletableFuture.completedFuture(result);
            });
        }

        @Test
        @DisplayName("hors region : la mutation saute sur le thread du joueur et rend son verdict")
        void notOwned_jumpsToOwnerThread() {
            stubRunAtEntity(EntityTaskResult.SUCCESS, true);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertTrue(economy(provider, Currencies.EXPERIENCE).depositChecked(playerId, BigDecimal.TEN, "r"));
            }
            verify(scheduler).runAtEntity(eq(player), any());
            verify(provider).deposit(playerId, BigDecimal.TEN, "r");
        }

        @Test
        @DisplayName("entite retiree avant execution : false et provider jamais appele")
        void entityRetired_returnsFalse() {
            stubRunAtEntity(EntityTaskResult.ENTITY_RETIRED, false);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertFalse(economy(provider, Currencies.LEVEL).depositChecked(playerId, BigDecimal.TEN, "r"));
            }
            verify(provider, never()).deposit(any(), any(), any());
        }

        @Test
        @DisplayName("future en echec : false, le resultat est inconnu")
        void failedFuture_returnsFalse() {
            when(this.scheduler.runAtEntity(eq(this.player), any()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("scheduler down")));
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertFalse(economy(provider, Currencies.LEVEL).withdrawChecked(playerId, BigDecimal.TEN, "r"));
            }
            verify(provider, never()).withdraw(any(), any(), any());
        }

        @Test
        @DisplayName("withdrawChecked hors region : pre-controle et retrait executes dans la meme tache")
        void withdrawChecked_notOwned_runsWholeSectionOnOwnerThread() {
            AtomicBoolean insideTask = new AtomicBoolean(false);
            when(this.scheduler.runAtEntity(eq(this.player), any())).thenAnswer(invocation -> {
                Consumer<WrappedTask> task = invocation.getArgument(1);
                insideTask.set(true);
                task.accept(mock(WrappedTask.class));
                insideTask.set(false);
                return CompletableFuture.completedFuture(EntityTaskResult.SUCCESS);
            });
            when(provider.getBalance(playerId)).thenAnswer(invocation -> {
                assertTrue(insideTask.get(), "la lecture du solde doit se faire dans la tache confinee");
                return new BigDecimal("100");
            });
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertTrue(economy(provider, Currencies.LEVEL).withdrawChecked(playerId, BigDecimal.TEN, "r"));
            }
            verify(provider).withdraw(playerId, BigDecimal.TEN, "r");
        }

        @Test
        @DisplayName("economie VAULT : jamais de saut, meme hors region")
        void vault_neverJumps() {
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                vaultEconomy().deposit(playerId, BigDecimal.TEN, "r");
            }
            verify(scheduler, never()).runAtEntity(any(), any());
            verify(provider).deposit(playerId, BigDecimal.TEN, "r");
        }

        @Test
        @DisplayName("joueur hors ligne sur deposit() historique : execution inline sans saut")
        void offlinePlayer_legacyDeposit_runsInline() {
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);

                economy(provider, Currencies.LEVEL).deposit(playerId, BigDecimal.TEN, "r");
            }
            verify(scheduler, never()).runAtEntity(any(), any());
            verify(provider).deposit(playerId, BigDecimal.TEN, "r");
        }
    }

    // ---------------------------------------------------------------- providers reels

    @Nested
    @DisplayName("providers CurrenciesAPI reels adosses au joueur")
    class RealProviders {

        @Test
        @DisplayName("LEVEL : solde = niveau, depot et retrait modifient le niveau")
        void level_getDepositWithdraw() {
            Player player = MockPlugins.player(playerId, "Alice");
            when(player.getLevel()).thenReturn(5);
            ZAuctionEconomy economy = economy(new LevelProvider(), Currencies.LEVEL);

            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertMoney("5", economy.get(playerId).join());
                assertTrue(economy.has(playerId, new BigDecimal("5")).join());
                assertFalse(economy.has(playerId, new BigDecimal("6")).join());

                economy.deposit(playerId, new BigDecimal("3"), "r");
                verify(player).setLevel(8);

                economy.withdraw(playerId, new BigDecimal("2"), "r");
                verify(player).setLevel(3);
            }
        }

        @Test
        @DisplayName("LEVEL : withdrawChecked refuse au-dela du niveau et accepte en dessous")
        void level_withdrawChecked() {
            Player player = MockPlugins.player(playerId, "Alice");
            when(player.getLevel()).thenReturn(5);
            ZAuctionEconomy economy = economy(new LevelProvider(), Currencies.LEVEL);

            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertFalse(economy.withdrawChecked(playerId, new BigDecimal("10"), "r"));
                verify(player, never()).setLevel(anyInt());

                assertTrue(economy.withdrawChecked(playerId, new BigDecimal("3"), "r"));
                verify(player).setLevel(2);
            }
        }

        @Test
        @DisplayName("EXPERIENCE : joueur sans experience, depot de 7 points donne 7 points")
        void experience_deposit() {
            Player player = MockPlugins.player(playerId, "Alice");
            when(player.getLevel()).thenReturn(0);
            when(player.getExp()).thenReturn(0f);
            ZAuctionEconomy economy = economy(new ExperienceProvider(), Currencies.EXPERIENCE);

            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertMoney("0", economy.get(playerId).join());
                assertTrue(economy.depositChecked(playerId, new BigDecimal("7"), "r"));
                verify(player).giveExp(7);
            }
        }

        @Test
        @DisplayName("ITEM : solde = nombre d'items similaires dans l'inventaire")
        void item_balanceCountsSimilarItems() {
            ItemStack currency = TestItems.diamond(1);
            ItemStack stackInSlot0 = TestItems.diamond(10);
            when(stackInSlot0.isSimilar(currency)).thenReturn(true);
            ItemStack stackInSlot5 = TestItems.diamond(22);
            when(stackInSlot5.isSimilar(currency)).thenReturn(true);
            ItemStack dirt = TestItems.itemStack(Material.DIRT, 64);
            when(dirt.isSimilar(currency)).thenReturn(false);

            PlayerInventory inventory = mock(PlayerInventory.class);
            when(inventory.getItem(0)).thenReturn(stackInSlot0);
            when(inventory.getItem(3)).thenReturn(dirt);
            when(inventory.getItem(5)).thenReturn(stackInSlot5);
            Player player = MockPlugins.player(playerId, "Alice");
            when(player.getInventory()).thenReturn(inventory);

            ZAuctionEconomy economy = economy(new ItemProvider(plugin, currency), Currencies.ITEM);

            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

                assertMoney("32", economy.get(playerId).join());
                assertTrue(economy.has(playerId, new BigDecimal("32")).join());
                assertFalse(economy.has(playerId, new BigDecimal("33")).join());
                assertFalse(economy.withdrawChecked(playerId, new BigDecimal("40"), "r"));
            }
        }

        @Test
        @DisplayName("ITEM : joueur hors ligne, depositChecked refuse (C-032)")
        void item_offlineDepositRefused() {
            ZAuctionEconomy economy = economy(new ItemProvider(plugin, TestItems.diamond(1)), Currencies.ITEM);

            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(null);

                assertFalse(economy.depositChecked(playerId, BigDecimal.ONE, "r"));
            }
        }
    }
}
