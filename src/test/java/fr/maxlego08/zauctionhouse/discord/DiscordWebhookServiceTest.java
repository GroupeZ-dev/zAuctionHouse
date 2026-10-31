package fr.maxlego08.zauctionhouse.discord;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.ReflectionTestUtils;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le service construit son {@link HttpClient} dans son constructeur : on le remplace par
 * reflexion par une doublure, ce qui permet d'intercepter la requete HTTP et d'en lire le JSON
 * sans jamais toucher le reseau. L'executeur du plugin mocke est synchrone, le future rendu est
 * donc deja complete au retour.
 */
class DiscordWebhookServiceTest {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123/abc";
    private static final UUID SELLER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private HttpClient httpClient;
    private AuctionItem auctionItem;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.httpClient = mock(HttpClient.class);

        ItemStack itemStack = TestItems.itemStack(Material.DIAMOND, 5);
        // Tout collaborateur mocke est construit AVANT le when(...) qui le rend : creer un mock
        // a l'interieur de thenReturn(...) declenche UnfinishedStubbingException chez Mockito.
        AuctionEconomy economy = MockPlugins.economy("vault");
        this.auctionItem = mock(AuctionItem.class);
        lenient().when(this.auctionItem.getId()).thenReturn(7);
        lenient().when(this.auctionItem.getItemStacks()).thenReturn(List.of(itemStack));
        lenient().when(this.auctionItem.getItemStack()).thenReturn(itemStack);
        lenient().when(this.auctionItem.getAmount()).thenReturn(5);
        lenient().when(this.auctionItem.getItemDisplay()).thenReturn("Diamond");
        lenient().when(this.auctionItem.getSellerName()).thenReturn("Alice");
        lenient().when(this.auctionItem.getSellerUniqueId()).thenReturn(SELLER_UUID);
        lenient().when(this.auctionItem.getPrice()).thenReturn(BigDecimal.TEN);
        lenient().when(this.auctionItem.getFormattedPrice()).thenReturn("10$");
        lenient().when(this.auctionItem.getAuctionEconomy()).thenReturn(economy);
        lenient().when(this.auctionItem.getExpiredAt()).thenReturn(new Date(1_700_000_000_000L));
    }

    private void writeDiscordYml(String content) throws Exception {
        Files.writeString(new File(this.dataFolder, "discord.yml").toPath(), content, StandardCharsets.UTF_8);
    }

    private DiscordWebhookService newService() {
        DiscordWebhookService service = new DiscordWebhookService(this.plugin);
        ReflectionTestUtils.setField(service, "httpClient", this.httpClient);
        return service;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn("body-" + status);
        return response;
    }

    private void stubResponse(int status) throws Exception {
        HttpResponse<String> response = response(status);
        when(this.httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(response);
    }

    private HttpRequest capturedRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(this.httpClient).send(captor.capture(), any());
        return captor.getValue();
    }

    /**
     * Lit le corps d'une requete HTTP : le BodyPublisher est un Flow.Publisher de ByteBuffer.
     */
    private static String bodyOf(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        CompletableFuture<String> result = new CompletableFuture<>();
        List<ByteBuffer> buffers = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                buffers.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                int size = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
                ByteBuffer all = ByteBuffer.allocate(size);
                buffers.forEach(all::put);
                result.complete(new String(all.array(), StandardCharsets.UTF_8));
            }
        });
        return result.join();
    }

    private static String enabledYaml(boolean sellEnabled, boolean purchaseEnabled, String sellUrl) {
        return """
                enabled: true
                server-name: "Survie"
                item-image-url: "https://img/%%item_material%%.png"
                extract-dominant-color: false
                default-color: "#5865F2"
                webhooks:
                  sell:
                    enabled: %s
                    url: "%s"
                    username: "zAuctionHouse"
                    avatar-url: ""
                    content: "Nouvelle vente de %%seller_name%%"
                    embed:
                      title: "New Item Listed!"
                      description: "**%%seller_name%%** sells %%item_display%%"
                      color: "#00FF00"
                      fields:
                        - name: "Item"
                          value: "%%item_display%%"
                          inline: true
                        - name: "Price"
                          value: "%%formatted_price%%"
                          inline: false
                      footer:
                        text: "Server: %%server_name%%"
                        icon-url: ""
                      author:
                        name: "%%seller_name%%"
                        url: ""
                        icon-url: "https://mc-heads.net/avatar/%%seller_uuid%%"
                      thumbnail:
                        url: "%%item_image_url%%"
                      image:
                        url: ""
                      timestamp: false
                  purchase:
                    enabled: %s
                    url: "https://discord.com/api/webhooks/999/zzz"
                    embed:
                      title: "Item Purchased!"
                      description: "**%%buyer_name%%** bought from **%%seller_name%%**"
                      color: "#FFD700"
                      timestamp: true
                """.formatted(sellEnabled, sellUrl, purchaseEnabled);
    }

    @Nested
    @DisplayName("Chargement de la configuration")
    class LoadTests {

        @Test
        @DisplayName("sans discord.yml : desactive, aucun extracteur, saveFile demande")
        void missingFile_disabled() {
            DiscordWebhookService service = newService();

            assertFalse(service.isEnabled());
            verify(plugin).saveFile("discord.yml", false);
            assertTrue(ReflectionTestUtils.<Object>field(service, "colorExtractor") == null);
        }

        @Test
        @DisplayName("enabled: false -> isEnabled() faux")
        void disabledFile_disabled() throws Exception {
            writeDiscordYml("enabled: false\n");

            assertFalse(newService().isEnabled());
        }

        @Test
        @DisplayName("enabled: true -> isEnabled() vrai")
        void enabledFile_enabled() throws Exception {
            writeDiscordYml(enabledYaml(false, false, WEBHOOK_URL));

            assertTrue(newService().isEnabled());
        }

        @Test
        @DisplayName("extract-dominant-color: true cree un ColorExtractor (et son dossier .cache)")
        void extractDominantColor_createsExtractor() throws Exception {
            writeDiscordYml("enabled: true\nextract-dominant-color: true\n");

            DiscordWebhookService service = newService();

            assertTrue(ReflectionTestUtils.<Object>field(service, "colorExtractor") instanceof ColorExtractor);
            assertTrue(new File(dataFolder, ".cache").isDirectory());
        }

        @Test
        @DisplayName("loadConfiguration() relit le fichier : passage de actif a inactif")
        void reload_picksUpChanges() throws Exception {
            writeDiscordYml("enabled: true\nextract-dominant-color: true\n");
            DiscordWebhookService service = newService();
            assertTrue(service.isEnabled());

            writeDiscordYml("enabled: true\nextract-dominant-color: false\n");
            service.loadConfiguration();

            assertTrue(service.isEnabled());
            assertTrue(ReflectionTestUtils.<Object>field(service, "colorExtractor") == null);
        }
    }

    @Nested
    @DisplayName("Gardes : aucun envoi")
    class NoSendTests {

        @Test
        @DisplayName("feature desactivee -> notifyItemSold / notifyItemPurchased ne font aucun appel HTTP")
        void disabled_noHttp() throws Exception {
            writeDiscordYml("enabled: false\n");
            DiscordWebhookService service = newService();

            CompletableFuture<Void> sold = service.notifyItemSold(null, auctionItem);
            CompletableFuture<Void> purchased = service.notifyItemPurchased(null, auctionItem);

            assertTrue(sold.isDone() && purchased.isDone());
            verify(httpClient, never()).send(any(), any());
        }

        @Test
        @DisplayName("webhook de vente desactive -> aucun appel HTTP")
        void sellWebhookDisabled_noHttp() throws Exception {
            writeDiscordYml(enabledYaml(false, false, WEBHOOK_URL));

            CompletableFuture<Void> future = newService().notifyItemSold(null, auctionItem);

            assertTrue(future.isDone());
            verify(httpClient, never()).send(any(), any());
        }

        @Test
        @DisplayName("URL qui n'est pas un webhook Discord -> aucun appel HTTP")
        void invalidUrl_noHttp() throws Exception {
            writeDiscordYml(enabledYaml(true, false, "https://example.com/hook"));

            newService().notifyItemSold(null, auctionItem);

            verify(httpClient, never()).send(any(), any());
        }

        @Test
        @DisplayName("webhook d'achat desactive -> aucun appel HTTP")
        void purchaseWebhookDisabled_noHttp() throws Exception {
            writeDiscordYml(enabledYaml(true, false, WEBHOOK_URL));

            newService().notifyItemPurchased(null, auctionItem);

            verify(httpClient, never()).send(any(), any());
        }
    }

    @Nested
    @DisplayName("Envoi : requete et JSON")
    class SendTests {

        @Test
        @DisplayName("notifyItemSold poste un JSON sur l'URL du webhook avec le bon Content-Type")
        void sold_postsJson() throws Exception {
            writeDiscordYml(enabledYaml(true, false, WEBHOOK_URL));
            stubResponse(204);

            CompletableFuture<Void> future = newService().notifyItemSold(null, auctionItem);

            assertTrue(future.isDone());
            HttpRequest request = capturedRequest();
            assertEquals(URI.create(WEBHOOK_URL), request.uri());
            assertEquals("POST", request.method());
            assertEquals(List.of("application/json"), request.headers().allValues("Content-Type"));
        }

        @Test
        @DisplayName("le JSON contient les placeholders resolus : titre, description, champs, footer, author, thumbnail")
        void sold_jsonContent() throws Exception {
            writeDiscordYml(enabledYaml(true, false, WEBHOOK_URL));
            stubResponse(200);

            newService().notifyItemSold(null, auctionItem);

            String json = bodyOf(capturedRequest());
            assertTrue(json.contains("\"username\":\"zAuctionHouse\""), json);
            assertTrue(json.contains("\"content\":\"Nouvelle vente de Alice\""), json);
            assertTrue(json.contains("\"title\":\"New Item Listed!\""), json);
            assertTrue(json.contains("\"description\":\"**Alice** sells Diamond\""), json);
            assertTrue(json.contains("\"color\":" + 0x00FF00), json);
            assertTrue(json.contains("{\"name\":\"Item\",\"value\":\"Diamond\",\"inline\":true}"), json);
            assertTrue(json.contains("{\"name\":\"Price\",\"value\":\"10$\",\"inline\":false}"), json);
            assertTrue(json.contains("\"footer\":{\"text\":\"Server: Survie\"}"), json);
            assertTrue(json.contains("\"author\":{\"name\":\"Alice\",\"icon_url\":\"https://mc-heads.net/avatar/" + SELLER_UUID + "\"}"), json);
            assertTrue(json.contains("\"thumbnail\":{\"url\":\"https://img/DIAMOND.png\"}"), json);
            assertFalse(json.contains("\"image\""), json);
            assertFalse(json.contains("\"avatar_url\""), json);
            assertFalse(json.contains("\"timestamp\""), json);
        }

        @Test
        @DisplayName("le vendeur en ligne est prefere au nom stocke dans l'item")
        void sold_onlineSellerName() throws Exception {
            writeDiscordYml(enabledYaml(true, false, WEBHOOK_URL));
            stubResponse(200);
            Player seller = MockPlugins.player(SELLER_UUID, "AliceOnline");

            newService().notifyItemSold(seller, auctionItem);

            assertTrue(bodyOf(capturedRequest()).contains("**AliceOnline** sells Diamond"));
        }

        @Test
        @DisplayName("notifyItemPurchased utilise le webhook d'achat, l'acheteur et ajoute un timestamp")
        void purchased_usesPurchaseWebhook() throws Exception {
            writeDiscordYml(enabledYaml(false, true, WEBHOOK_URL));
            stubResponse(200);
            Player buyer = MockPlugins.player(UUID.randomUUID(), "Bob");

            newService().notifyItemPurchased(buyer, auctionItem);

            HttpRequest request = capturedRequest();
            assertEquals(URI.create("https://discord.com/api/webhooks/999/zzz"), request.uri());
            String json = bodyOf(request);
            assertTrue(json.contains("\"title\":\"Item Purchased!\""), json);
            assertTrue(json.contains("**Bob** bought from **Alice**"), json);
            assertTrue(json.contains("\"color\":" + 0xFFD700), json);
            assertTrue(json.contains("\"timestamp\":\""), json);
            // Pas d'username configure sur ce webhook
            assertFalse(json.contains("\"username\""), json);
        }

        @Test
        @DisplayName("une reponse >= 400 est journalisee sans propager d'exception")
        void errorStatus_isSwallowed() throws Exception {
            writeDiscordYml(enabledYaml(true, false, WEBHOOK_URL));
            stubResponse(429);

            CompletableFuture<Void> future = newService().notifyItemSold(null, auctionItem);

            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("une exception reseau est avalee : le future se complete normalement")
        void networkException_isSwallowed() throws Exception {
            writeDiscordYml(enabledYaml(true, false, WEBHOOK_URL));
            when(httpClient.<String>send(any(HttpRequest.class), any())).thenThrow(new java.io.IOException("boom"));

            CompletableFuture<Void> future = newService().notifyItemSold(null, auctionItem);

            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("la couleur de l'embed peut etre le placeholder %item_dominant_color% (couleur par defaut sans extraction)")
        void dominantColorPlaceholder_resolvedToDefault() throws Exception {
            writeDiscordYml("""
                    enabled: true
                    default-color: "#ABCDEF"
                    extract-dominant-color: false
                    webhooks:
                      sell:
                        enabled: true
                        url: "%s"
                        embed:
                          title: "t"
                          color: "%%item_dominant_color%%"
                    """.formatted(WEBHOOK_URL));
            stubResponse(200);

            newService().notifyItemSold(null, auctionItem);

            assertTrue(bodyOf(capturedRequest()).contains("\"color\":" + 0xABCDEF));
        }
    }
}
