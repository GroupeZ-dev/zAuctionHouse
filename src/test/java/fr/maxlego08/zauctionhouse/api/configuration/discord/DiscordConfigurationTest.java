package fr.maxlego08.zauctionhouse.api.configuration.discord;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordConfigurationTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
    }

    private void writeDiscordYml(String content) throws Exception {
        Files.writeString(new File(this.dataFolder, "discord.yml").toPath(), content, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("of(plugin) : fichier et valeurs par defaut")
    class OfTests {

        @Test
        @DisplayName("fichier absent : saveFile est demande et les valeurs par defaut s'appliquent")
        void missingFile_defaults() {
            DiscordConfiguration configuration = DiscordConfiguration.of(plugin);

            verify(plugin).saveFile("discord.yml", false);
            assertFalse(configuration.enabled());
            assertEquals("My Server", configuration.serverName());
            assertEquals("https://img.groupez.dev/minecraft/%item_material%.png", configuration.itemImageUrl());
            assertTrue(configuration.extractDominantColor());
            assertEquals("#5865F2", configuration.defaultColor());
            assertTrue(configuration.customImages().isEmpty());
            assertFalse(configuration.sellWebhook().enabled());
            assertFalse(configuration.purchaseWebhook().enabled());
            assertEquals("", configuration.sellWebhook().url());
            assertEquals("#FFFFFF", configuration.sellWebhook().embed().color());
        }

        @Test
        @DisplayName("fichier present : saveFile n'est pas appele et les valeurs sont lues")
        void existingFile_isRead() throws Exception {
            writeDiscordYml("""
                    enabled: true
                    server-name: "Survie"
                    item-image-url: "https://x/%item_material%.png"
                    extract-dominant-color: false
                    default-color: "#123456"
                    """);

            DiscordConfiguration configuration = DiscordConfiguration.of(plugin);

            verify(plugin, never()).saveFile(any(), anyBoolean());
            assertTrue(configuration.enabled());
            assertEquals("Survie", configuration.serverName());
            assertEquals("https://x/%item_material%.png", configuration.itemImageUrl());
            assertFalse(configuration.extractDominantColor());
            assertEquals("#123456", configuration.defaultColor());
        }

        @Test
        @DisplayName("le discord.yml livre dans les ressources se charge avec webhooks desactives et 4 champs")
        void shippedResource_loads() throws Exception {
            YamlConfiguration shipped = TestResources.yaml("discord.yml");
            writeDiscordYml(shipped.saveToString());

            DiscordConfiguration configuration = DiscordConfiguration.of(plugin);

            assertFalse(configuration.enabled());
            assertFalse(configuration.extractDominantColor());
            assertFalse(configuration.sellWebhook().enabled());
            assertFalse(configuration.sellWebhook().isValid());
            assertEquals("zAuctionHouse", configuration.sellWebhook().username());
            assertEquals(4, configuration.sellWebhook().embed().fields().size());
            assertEquals("Item", configuration.sellWebhook().embed().fields().get(0).name());
            assertEquals("%item_display%", configuration.sellWebhook().embed().fields().get(0).value());
            assertTrue(configuration.sellWebhook().embed().fields().get(0).inline());
            assertTrue(configuration.sellWebhook().embed().timestamp());
            assertEquals("%item_image_url%", configuration.sellWebhook().embed().thumbnail().url());
            assertEquals("https://mc-heads.net/avatar/%buyer_uuid%", configuration.purchaseWebhook().embed().author().iconUrl());
            assertTrue(configuration.customImages().isEmpty());
        }
    }

    @Nested
    @DisplayName("custom-images")
    class CustomImagesTests {

        private RuleLoaderRegistry registry;

        @BeforeEach
        void setUpRegistry() {
            this.registry = mock(RuleLoaderRegistry.class);
            lenient().when(plugin.getRuleLoaderRegistry()).thenReturn(this.registry);
        }

        @Test
        @DisplayName("sans registre de regles -> liste vide, meme si des entrees existent")
        void noRegistry_empty() throws Exception {
            when(plugin.getRuleLoaderRegistry()).thenReturn(null);
            writeDiscordYml("""
                    custom-images:
                      - rule:
                          type: material
                        image: "https://a.png"
                    """);

            assertTrue(DiscordConfiguration.of(plugin).customImages().isEmpty());
        }

        @Test
        @DisplayName("une entree valide produit une CustomImageRule avec la regle chargee et l'image")
        void validEntry_loaded() throws Exception {
            Rule rule = mock(Rule.class);
            when(rule.isValid()).thenReturn(true);
            when(registry.loadRule(anyMap())).thenReturn(rule);
            writeDiscordYml("""
                    custom-images:
                      - rule:
                          type: material
                          materials: [DIAMOND_SWORD]
                        image: "https://a.png"
                    """);

            DiscordConfiguration configuration = DiscordConfiguration.of(plugin);

            assertEquals(1, configuration.customImages().size());
            assertSame(rule, configuration.customImages().get(0).rule());
            assertEquals("https://a.png", configuration.customImages().get(0).image());
            verify(registry).loadRule(Map.of("type", "material", "materials", java.util.List.of("DIAMOND_SWORD")));
        }

        @Test
        @DisplayName("l'ordre de declaration est conserve")
        void order_isPreserved() throws Exception {
            Rule rule = mock(Rule.class);
            when(rule.isValid()).thenReturn(true);
            when(registry.loadRule(anyMap())).thenReturn(rule);
            writeDiscordYml("""
                    custom-images:
                      - rule: {type: material}
                        image: "https://1.png"
                      - rule: {type: material}
                        image: "https://2.png"
                      - rule: {type: material}
                        image: "https://3.png"
                    """);

            var images = DiscordConfiguration.of(plugin).customImages();

            assertEquals(java.util.List.of("https://1.png", "https://2.png", "https://3.png"), images.stream().map(DiscordConfiguration.CustomImageRule::image).toList());
        }

        @Test
        @DisplayName("entree sans rule, rule non-map, sans image ou image vide -> ignoree")
        void malformedEntries_skipped() throws Exception {
            Rule rule = mock(Rule.class);
            lenient().when(rule.isValid()).thenReturn(true);
            lenient().when(registry.loadRule(anyMap())).thenReturn(rule);
            writeDiscordYml("""
                    custom-images:
                      - image: "https://no-rule.png"
                      - rule: "material"
                        image: "https://rule-is-string.png"
                      - rule: {type: material}
                      - rule: {type: material}
                        image: ""
                    """);

            assertTrue(DiscordConfiguration.of(plugin).customImages().isEmpty());
            verify(registry, never()).loadRule(anyMap());
        }

        @Test
        @DisplayName("regle null ou invalide rendue par le registre -> entree ignoree")
        void invalidRule_skipped() throws Exception {
            Rule invalid = mock(Rule.class);
            when(invalid.isValid()).thenReturn(false);
            when(registry.loadRule(anyMap())).thenReturn(invalid, (Rule) null);
            writeDiscordYml("""
                    custom-images:
                      - rule: {type: material}
                        image: "https://invalid.png"
                      - rule: {type: material}
                        image: "https://null.png"
                    """);

            assertTrue(DiscordConfiguration.of(plugin).customImages().isEmpty());
        }

        @Test
        @DisplayName("une image numerique est convertie en chaine")
        void numericImage_isStringified() throws Exception {
            Rule rule = mock(Rule.class);
            when(rule.isValid()).thenReturn(true);
            when(registry.loadRule(anyMap())).thenReturn(rule);
            writeDiscordYml("""
                    custom-images:
                      - rule: {type: material}
                        image: 12345
                    """);

            assertEquals("12345", DiscordConfiguration.of(plugin).customImages().get(0).image());
        }
    }

    @Nested
    @DisplayName("WebhookConfiguration")
    class WebhookTests {

        private DiscordConfiguration.WebhookConfiguration webhook(boolean enabled, String url) {
            return new DiscordConfiguration.WebhookConfiguration(enabled, url, "", "", "", DiscordConfiguration.EmbedConfiguration.of(new YamlConfiguration(), "x"));
        }

        @Test
        @DisplayName("isValid exige enabled ET une URL Discord")
        void isValid_requiresEnabledAndDiscordUrl() {
            assertTrue(webhook(true, "https://discord.com/api/webhooks/1/a").isValid());
            assertFalse(webhook(false, "https://discord.com/api/webhooks/1/a").isValid());
            assertFalse(webhook(true, "").isValid());
            assertFalse(webhook(true, null).isValid());
            assertFalse(webhook(true, "https://discordapp.com/api/webhooks/1/a").isValid());
            assertFalse(webhook(true, "http://discord.com/api/webhooks/1/a").isValid());
            assertFalse(webhook(true, "https://example.com/discord.com/api/webhooks/1/a").isValid());
        }

        @Test
        @DisplayName("hasUsername / hasAvatarUrl / hasContent : vrai seulement pour une valeur non vide")
        void hasXxx_flags() {
            var embed = DiscordConfiguration.EmbedConfiguration.of(new YamlConfiguration(), "x");
            var full = new DiscordConfiguration.WebhookConfiguration(true, "u", "name", "avatar", "content", embed);
            var empty = new DiscordConfiguration.WebhookConfiguration(true, "u", "", "", "", embed);
            var nulls = new DiscordConfiguration.WebhookConfiguration(true, "u", null, null, null, embed);

            assertTrue(full.hasUsername() && full.hasAvatarUrl() && full.hasContent());
            assertFalse(empty.hasUsername() || empty.hasAvatarUrl() || empty.hasContent());
            assertFalse(nulls.hasUsername() || nulls.hasAvatarUrl() || nulls.hasContent());
        }

        @Test
        @DisplayName("of(config, path) lit chaque cle sous le chemin donne")
        void of_readsPath() {
            YamlConfiguration config = TestResources.yamlOf("""
                    webhooks:
                      sell:
                        enabled: true
                        url: "https://discord.com/api/webhooks/1/a"
                        username: "Bot"
                        avatar-url: "https://avatar.png"
                        content: "hello"
                        embed:
                          title: "T"
                    """);

            var webhook = DiscordConfiguration.WebhookConfiguration.of(config, "webhooks.sell");

            assertTrue(webhook.enabled());
            assertEquals("https://discord.com/api/webhooks/1/a", webhook.url());
            assertEquals("Bot", webhook.username());
            assertEquals("https://avatar.png", webhook.avatarUrl());
            assertEquals("hello", webhook.content());
            assertEquals("T", webhook.embed().title());
            assertTrue(webhook.isValid());
        }
    }

    @Nested
    @DisplayName("EmbedConfiguration et sous-sections")
    class EmbedTests {

        @Test
        @DisplayName("valeurs par defaut sur une section absente")
        void defaults() {
            var embed = DiscordConfiguration.EmbedConfiguration.of(new YamlConfiguration(), "missing");

            assertEquals("", embed.title());
            assertEquals("", embed.description());
            assertEquals("#FFFFFF", embed.color());
            assertTrue(embed.fields().isEmpty());
            assertFalse(embed.footer().hasContent());
            assertFalse(embed.author().hasContent());
            assertFalse(embed.thumbnail().hasContent());
            assertFalse(embed.image().hasContent());
            assertFalse(embed.timestamp());
        }

        @Test
        @DisplayName("fields : name/value manquants -> vides, inline manquant -> false, inline en chaine accepte")
        void fields_parsing() {
            YamlConfiguration config = TestResources.yamlOf("""
                    e:
                      fields:
                        - name: "A"
                          value: "1"
                          inline: true
                        - name: "B"
                        - value: "3"
                          inline: "true"
                        - inline: "yes"
                    """);

            var fields = DiscordConfiguration.EmbedConfiguration.of(config, "e").fields();

            assertEquals(4, fields.size());
            assertEquals(new DiscordConfiguration.FieldConfiguration("A", "1", true), fields.get(0));
            assertEquals(new DiscordConfiguration.FieldConfiguration("B", "", false), fields.get(1));
            assertEquals(new DiscordConfiguration.FieldConfiguration("", "3", true), fields.get(2));
            // Boolean.parseBoolean("yes") == false
            assertEquals(new DiscordConfiguration.FieldConfiguration("", "", false), fields.get(3));
        }

        @Test
        @DisplayName("footer, author, thumbnail, image : lecture et hasContent")
        void subSections() {
            YamlConfiguration config = TestResources.yamlOf("""
                    e:
                      footer:
                        text: "foot"
                        icon-url: "https://f.png"
                      author:
                        name: "auth"
                        url: "https://a"
                        icon-url: "https://a.png"
                      thumbnail:
                        url: "https://t.png"
                      image:
                        url: "https://i.png"
                      timestamp: true
                    """);

            var embed = DiscordConfiguration.EmbedConfiguration.of(config, "e");

            assertEquals(new DiscordConfiguration.FooterConfiguration("foot", "https://f.png"), embed.footer());
            assertEquals(new DiscordConfiguration.AuthorConfiguration("auth", "https://a", "https://a.png"), embed.author());
            assertEquals(new DiscordConfiguration.ThumbnailConfiguration("https://t.png"), embed.thumbnail());
            assertEquals(new DiscordConfiguration.ImageConfiguration("https://i.png"), embed.image());
            assertTrue(embed.footer().hasContent() && embed.author().hasContent() && embed.thumbnail().hasContent() && embed.image().hasContent());
            assertTrue(embed.timestamp());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " "})
        @DisplayName("hasContent du footer depend du texte seulement ; une icone seule ne suffit pas")
        void footer_hasContent_dependsOnTextOnly(String text) {
            var withIconOnly = new DiscordConfiguration.FooterConfiguration("", "https://icon.png");
            assertFalse(withIconOnly.hasContent());
            assertFalse(new DiscordConfiguration.FooterConfiguration(null, null).hasContent());
            // un espace est un contenu (la classe ne trim pas)
            assertEquals(!text.isEmpty(), new DiscordConfiguration.FooterConfiguration(text, "").hasContent());
        }

        @Test
        @DisplayName("hasContent de l'author depend du nom seulement")
        void author_hasContent_dependsOnNameOnly() {
            assertFalse(new DiscordConfiguration.AuthorConfiguration("", "https://u", "https://i").hasContent());
            assertFalse(new DiscordConfiguration.AuthorConfiguration(null, null, null).hasContent());
            assertTrue(new DiscordConfiguration.AuthorConfiguration("n", null, null).hasContent());
        }

        @Test
        @DisplayName("thumbnail / image : null ou vide -> pas de contenu")
        void thumbnailImage_hasContent() {
            assertFalse(new DiscordConfiguration.ThumbnailConfiguration(null).hasContent());
            assertFalse(new DiscordConfiguration.ThumbnailConfiguration("").hasContent());
            assertTrue(new DiscordConfiguration.ThumbnailConfiguration("x").hasContent());
            assertFalse(new DiscordConfiguration.ImageConfiguration(null).hasContent());
            assertFalse(new DiscordConfiguration.ImageConfiguration("").hasContent());
            assertTrue(new DiscordConfiguration.ImageConfiguration("x").hasContent());
        }
    }
}
