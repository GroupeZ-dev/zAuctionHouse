package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.component.ComponentMessage;
import fr.maxlego08.zauctionhouse.api.configuration.records.BroadcastConfiguration;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.services.AuctionOptionService;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link BroadcastService} : diffusion des ventes et des achats selon {@link BroadcastConfiguration}
 * (activation, exclusion de l'acteur, option par joueur, message par categorie).
 * <p>
 * Aucune base ici : le service ne lit que la configuration, la liste des joueurs en ligne et
 * le service d'options. Les messages sont captures sur une doublure de {@code ComponentMessage}
 * substituee a {@code ComponentMessageHelper.componentMessage} avant la construction du service.
 */
class BroadcastServiceTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private Server server;
    private AuctionOptionService optionService;
    private ComponentMessage componentMessage;
    private ComponentMessage previousComponentMessage;
    private MockedStatic<PlaceholderAPI> placeholderApi;

    private Player seller;
    private Player buyer;
    private Player spectator;
    private AuctionItem auctionItem;
    private Category weapons;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.server = mock(Server.class);
        this.optionService = mock(AuctionOptionService.class);
        AuctionManager auctionManager = mock(AuctionManager.class);
        lenient().when(auctionManager.getOptionService()).thenReturn(this.optionService);
        lenient().when(this.plugin.getAuctionManager()).thenReturn(auctionManager);
        lenient().when(this.plugin.getServer()).thenReturn(this.server);

        this.placeholderApi = mockStatic(PlaceholderAPI.class);
        this.placeholderApi.when(() -> PlaceholderAPI.setPlaceholders(any(Player.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        this.previousComponentMessage = ComponentMessageHelper.componentMessage;
        this.componentMessage = mock(ComponentMessage.class);
        ComponentMessageHelper.componentMessage = this.componentMessage;

        this.seller = MockPlugins.player("Seller");
        this.buyer = MockPlugins.player("Buyer");
        this.spectator = MockPlugins.player("Spectator");
        doReturn(List.of(this.seller, this.buyer, this.spectator)).when(this.server).getOnlinePlayers();

        // Par defaut tout le monde accepte les deux diffusions.
        lenient().when(this.optionService.getOption(any(Player.class), any(PlayerOption.class))).thenReturn(true);

        this.weapons = mock(Category.class);
        lenient().when(this.weapons.getId()).thenReturn("weapons");

        this.auctionItem = mock(AuctionItem.class);
        lenient().when(this.auctionItem.getItemDisplay()).thenReturn("x1 Diamond Sword");
        lenient().when(this.auctionItem.getFormattedPrice()).thenReturn("10$");
        lenient().when(this.auctionItem.getSellerName()).thenReturn("Seller");
        Set<Category> categories = new LinkedHashSet<>();
        categories.add(this.weapons);
        lenient().when(this.auctionItem.getCategories()).thenReturn(categories);
    }

    @AfterEach
    void tearDown() {
        ComponentMessageHelper.componentMessage = this.previousComponentMessage;
        this.placeholderApi.close();
    }

    private BroadcastService service(boolean sellEnabled, boolean purchaseEnabled, boolean excludeSeller, boolean excludeBuyer,
                                     Map<String, String> sellMessages, Map<String, String> purchaseMessages) {
        BroadcastConfiguration configuration = new BroadcastConfiguration(sellEnabled, purchaseEnabled, excludeSeller, excludeBuyer, sellMessages, purchaseMessages);
        lenient().when(this.plugin.getConfiguration().getBroadcast()).thenReturn(configuration);
        return new BroadcastService(this.plugin);
    }

    private static boolean containsAll(String text, String... fragments) {
        if (text == null) return false;
        for (String fragment : fragments) {
            if (!text.contains(fragment)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("broadcastSell")
    class Sell {

        @Test
        @DisplayName("diffusion desactivee : aucun joueur n'est consulte ni notifie")
        void broadcastSell_disabled_sendsNothing() {
            BroadcastService service = service(false, true, true, true, Map.of(), Map.of());

            service.broadcastSell(seller, auctionItem);

            verifyNoInteractions(server, optionService, componentMessage);
        }

        @Test
        @DisplayName("message par defaut : tous les joueurs en ligne sauf le vendeur recoivent vendeur, items et prix")
        void broadcastSell_defaultMessage_excludesSeller() {
            BroadcastService service = service(true, false, true, true, Map.of(), Map.of());

            service.broadcastSell(seller, auctionItem);

            verify(componentMessage).sendMessage(eq(buyer), argThat((String text) -> containsAll(text, "Seller", "x1 Diamond Sword", "10$")));
            verify(componentMessage).sendMessage(eq(spectator), argThat((String text) -> containsAll(text, "Seller", "x1 Diamond Sword", "10$")));
            verify(componentMessage, never()).sendMessage(eq(seller), anyString());
        }

        @Test
        @DisplayName("exclude-seller desactive : le vendeur recoit aussi la diffusion")
        void broadcastSell_includeSeller_sendsToSellerToo() {
            BroadcastService service = service(true, false, false, true, Map.of(), Map.of());

            service.broadcastSell(seller, auctionItem);

            verify(componentMessage).sendMessage(eq(seller), argThat((String text) -> containsAll(text, "Seller", "x1 Diamond Sword")));
            verify(componentMessage).sendMessage(eq(buyer), anyString());
            verify(componentMessage).sendMessage(eq(spectator), anyString());
        }

        @Test
        @DisplayName("option BROADCAST_SELL desactivee chez un joueur : il est ignore, les autres sont notifies")
        void broadcastSell_optionDisabled_skipsPlayer() {
            when(optionService.getOption(spectator, PlayerOption.BROADCAST_SELL)).thenReturn(false);
            BroadcastService service = service(true, false, true, true, Map.of(), Map.of());

            service.broadcastSell(seller, auctionItem);

            verify(componentMessage).sendMessage(eq(buyer), anyString());
            verify(componentMessage, never()).sendMessage(eq(spectator), anyString());
            verify(optionService, never()).getOption(any(Player.class), eq(PlayerOption.BROADCAST_PURCHASE));
        }

        @Test
        @DisplayName("message personnalise de la premiere categorie : placeholders resolus, %buyer% vide, envoye brut sans prefixe")
        void broadcastSell_categoryMessage_resolvesPlaceholders() {
            Map<String, String> sellMessages = Map.of("weapons", "[%category%] %seller% vend %items% pour %price% (%buyer%)");
            BroadcastService service = service(true, false, true, true, sellMessages, Map.of());

            service.broadcastSell(seller, auctionItem);

            verify(componentMessage).sendMessage(buyer, "[weapons] Seller vend x1 Diamond Sword pour 10$ ()");
            verify(componentMessage).sendMessage(spectator, "[weapons] Seller vend x1 Diamond Sword pour 10$ ()");
            verify(componentMessage, never()).sendMessage(eq(seller), anyString());
        }

        @Test
        @DisplayName("categorie sans message dedie : retour au message par defaut")
        void broadcastSell_categoryWithoutCustomMessage_usesDefault() {
            Map<String, String> sellMessages = Map.of("blocks", "custom blocks message");
            BroadcastService service = service(true, false, true, true, sellMessages, Map.of());

            service.broadcastSell(seller, auctionItem);

            verify(componentMessage).sendMessage(eq(buyer), argThat((String text) -> containsAll(text, "Seller", "x1 Diamond Sword", "10$") && !text.contains("custom blocks message")));
        }

        @Test
        @DisplayName("annonce sans categorie (null ou vide) : message par defaut, aucune NPE")
        void broadcastSell_noCategory_usesDefault() {
            when(auctionItem.getCategories()).thenReturn(null);
            Map<String, String> sellMessages = Map.of("weapons", "custom weapons message");
            BroadcastService service = service(true, false, true, true, sellMessages, Map.of());

            service.broadcastSell(seller, auctionItem);

            verify(componentMessage).sendMessage(eq(buyer), argThat((String text) -> containsAll(text, "Seller", "x1 Diamond Sword") && !text.contains("custom weapons message")));

            when(auctionItem.getCategories()).thenReturn(Set.of());
            service.broadcastSell(seller, auctionItem);

            verify(componentMessage, never()).sendMessage(any(), eq("custom weapons message"));
        }

        @Test
        @DisplayName("aucun joueur en ligne : rien n'est envoye")
        void broadcastSell_nobodyOnline_sendsNothing() {
            doReturn(List.of()).when(server).getOnlinePlayers();
            BroadcastService service = service(true, false, true, true, Map.of(), Map.of());

            service.broadcastSell(seller, auctionItem);

            verifyNoInteractions(componentMessage);
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("broadcastPurchase")
    class Purchase {

        @Test
        @DisplayName("diffusion desactivee : aucun joueur n'est consulte ni notifie")
        void broadcastPurchase_disabled_sendsNothing() {
            BroadcastService service = service(true, false, true, true, Map.of(), Map.of());

            service.broadcastPurchase(buyer, auctionItem);

            verifyNoInteractions(server, optionService, componentMessage);
        }

        @Test
        @DisplayName("message par defaut : tous sauf l'acheteur recoivent acheteur, vendeur, items et prix")
        void broadcastPurchase_defaultMessage_excludesBuyer() {
            BroadcastService service = service(false, true, true, true, Map.of(), Map.of());

            service.broadcastPurchase(buyer, auctionItem);

            verify(componentMessage).sendMessage(eq(seller), argThat((String text) -> containsAll(text, "Buyer", "x1 Diamond Sword", "10$")));
            verify(componentMessage).sendMessage(eq(spectator), argThat((String text) -> containsAll(text, "Buyer", "x1 Diamond Sword", "10$")));
            verify(componentMessage, never()).sendMessage(eq(buyer), anyString());
        }

        @Test
        @DisplayName("exclude-buyer desactive : l'acheteur recoit aussi la diffusion")
        void broadcastPurchase_includeBuyer_sendsToBuyerToo() {
            BroadcastService service = service(false, true, true, false, Map.of(), Map.of());

            service.broadcastPurchase(buyer, auctionItem);

            verify(componentMessage).sendMessage(eq(buyer), argThat((String text) -> containsAll(text, "Buyer", "x1 Diamond Sword")));
            verify(componentMessage).sendMessage(eq(seller), anyString());
            verify(componentMessage).sendMessage(eq(spectator), anyString());
        }

        @Test
        @DisplayName("option BROADCAST_PURCHASE desactivee chez un joueur : il est ignore")
        void broadcastPurchase_optionDisabled_skipsPlayer() {
            when(optionService.getOption(seller, PlayerOption.BROADCAST_PURCHASE)).thenReturn(false);
            BroadcastService service = service(false, true, true, true, Map.of(), Map.of());

            service.broadcastPurchase(buyer, auctionItem);

            verify(componentMessage, never()).sendMessage(eq(seller), anyString());
            verify(componentMessage).sendMessage(eq(spectator), anyString());
            verify(optionService, never()).getOption(any(Player.class), eq(PlayerOption.BROADCAST_SELL));
        }

        @Test
        @DisplayName("message personnalise de la categorie : vendeur ET acheteur resolus")
        void broadcastPurchase_categoryMessage_resolvesPlaceholders() {
            Map<String, String> purchaseMessages = Map.of("weapons", "%buyer% achete %items% de %seller% pour %price% [%category%]");
            BroadcastService service = service(false, true, true, true, Map.of(), purchaseMessages);

            service.broadcastPurchase(buyer, auctionItem);

            verify(componentMessage).sendMessage(seller, "Buyer achete x1 Diamond Sword de Seller pour 10$ [weapons]");
            verify(componentMessage).sendMessage(spectator, "Buyer achete x1 Diamond Sword de Seller pour 10$ [weapons]");
            verify(componentMessage, never()).sendMessage(eq(buyer), anyString());
        }

        @Test
        @DisplayName("les messages de categorie de VENTE ne sont pas utilises pour un achat")
        void broadcastPurchase_ignoresSellCategoryMessages() {
            Map<String, String> sellMessages = Map.of("weapons", "sell only message");
            BroadcastService service = service(false, true, true, true, sellMessages, Map.of());

            service.broadcastPurchase(buyer, auctionItem);

            // Le message par defaut BROADCAST_PURCHASE ne contient pas %seller% : on verifie seulement
            // acheteur, items et prix, et surtout que le message de categorie de vente n'a pas fuite.
            verify(componentMessage, never()).sendMessage(any(), eq("sell only message"));
            verify(componentMessage).sendMessage(eq(seller), argThat((String text) -> containsAll(text, "Buyer", "x1 Diamond Sword", "10$") && !text.contains("sell only message")));
            verify(componentMessage).sendMessage(eq(spectator), argThat((String text) -> containsAll(text, "Buyer", "x1 Diamond Sword", "10$") && !text.contains("sell only message")));
            verify(componentMessage, never()).sendMessage(eq(buyer), anyString());
        }
    }
}
