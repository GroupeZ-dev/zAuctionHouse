package fr.maxlego08.zauctionhouse.api.configuration.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le JSON est assemble a la main par la classe testee : chaque test le re-parse avec Gson pour
 * verifier qu'il est bien forme ET qu'il porte les bonnes valeurs.
 */
class DiscordEmbedTest {

    private static JsonObject parse(DiscordEmbed embed) {
        return JsonParser.parseString(embed.toJson()).getAsJsonObject();
    }

    private static JsonObject firstEmbed(JsonObject root) {
        JsonArray embeds = root.getAsJsonArray("embeds");
        assertEquals(1, embeds.size());
        return embeds.get(0).getAsJsonObject();
    }

    @Nested
    @DisplayName("Structure minimale")
    class MinimalTests {

        @Test
        @DisplayName("un embed vide ne contient que la couleur (0 par defaut)")
        void emptyEmbed_onlyColor() {
            DiscordEmbed embed = DiscordEmbed.builder().build();

            JsonObject root = parse(embed);
            assertEquals(1, root.size());
            JsonObject json = firstEmbed(root);
            assertEquals(1, json.size());
            assertEquals(0, json.get("color").getAsInt());
            assertEquals("{\"embeds\":[{\"color\":0}]}", embed.toJson());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("titre et description null ou vides sont omis")
        void emptyTitleAndDescription_omitted(String value) {
            JsonObject json = firstEmbed(parse(DiscordEmbed.builder().title(value).description(value).build()));

            assertFalse(json.has("title"));
            assertFalse(json.has("description"));
        }

        @Test
        @DisplayName("titre et description presents")
        void titleAndDescription_present() {
            JsonObject json = firstEmbed(parse(DiscordEmbed.builder().title("Titre").description("Desc").build()));

            assertEquals("Titre", json.get("title").getAsString());
            assertEquals("Desc", json.get("description").getAsString());
        }
    }

    @Nested
    @DisplayName("Options racine du webhook")
    class RootOptionsTests {

        @Test
        @DisplayName("username, avatar_url et content sont au niveau racine, avant embeds")
        void rootOptions_present() {
            DiscordEmbed embed = DiscordEmbed.builder().username("Bot").avatarUrl("https://a.png").content("Hello").build();

            JsonObject root = parse(embed);
            assertEquals("Bot", root.get("username").getAsString());
            assertEquals("https://a.png", root.get("avatar_url").getAsString());
            assertEquals("Hello", root.get("content").getAsString());
            assertTrue(embed.toJson().startsWith("{\"username\":\"Bot\",\"avatar_url\":\"https://a.png\",\"content\":\"Hello\",\"embeds\":["));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("options racine null ou vides omises")
        void rootOptions_omitted(String value) {
            JsonObject root = parse(DiscordEmbed.builder().username(value).avatarUrl(value).content(value).build());

            assertFalse(root.has("username"));
            assertFalse(root.has("avatar_url"));
            assertFalse(root.has("content"));
        }
    }

    @Nested
    @DisplayName("Couleur")
    class ColorTests {

        @ParameterizedTest
        @CsvSource({
                "'#FF5733', 16734003",
                "'FF5733', 16734003",
                "'#000000', 0",
                "'#ffffff', 16777215",
                "'#5865F2', 5793266"
        })
        @DisplayName("la couleur hexadecimale (avec ou sans #) est convertie en entier")
        void hexColor_parsed(String hex, int expected) {
            assertEquals(expected, firstEmbed(parse(DiscordEmbed.builder().color(hex).build())).get("color").getAsInt());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"#GGGGGG", "not-a-color", "%item_dominant_color%"})
        @DisplayName("couleur null, vide ou invalide -> blanc (0xFFFFFF)")
        void invalidColor_white(String hex) {
            assertEquals(0xFFFFFF, firstEmbed(parse(DiscordEmbed.builder().color(hex).build())).get("color").getAsInt());
        }

        @Test
        @DisplayName("color(int) est pris tel quel")
        void intColor_asIs() {
            assertEquals(1234, firstEmbed(parse(DiscordEmbed.builder().color(1234).build())).get("color").getAsInt());
        }
    }

    @Nested
    @DisplayName("Champs")
    class FieldsTests {

        @Test
        @DisplayName("les champs sont serialises dans l'ordre d'ajout avec name/value/inline")
        void fields_inOrder() {
            DiscordEmbed embed = DiscordEmbed.builder()
                    .addField("A", "1", true)
                    .addField("B", "2", false)
                    .build();

            JsonArray fields = firstEmbed(parse(embed)).getAsJsonArray("fields");
            assertEquals(2, fields.size());
            assertEquals("A", fields.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("1", fields.get(0).getAsJsonObject().get("value").getAsString());
            assertTrue(fields.get(0).getAsJsonObject().get("inline").getAsBoolean());
            assertEquals("B", fields.get(1).getAsJsonObject().get("name").getAsString());
            assertFalse(fields.get(1).getAsJsonObject().get("inline").getAsBoolean());
        }

        @Test
        @DisplayName("sans champ, la cle fields est absente")
        void noFields_omitted() {
            assertFalse(firstEmbed(parse(DiscordEmbed.builder().build())).has("fields"));
        }

        @Test
        @DisplayName("un champ avec name null est serialise avec null (pas de NPE)")
        void nullFieldName_serializedAsNull() {
            JsonArray fields = firstEmbed(parse(DiscordEmbed.builder().addField(null, "v", false).build())).getAsJsonArray("fields");

            assertTrue(fields.get(0).getAsJsonObject().get("name").isJsonNull());
        }
    }

    @Nested
    @DisplayName("Footer, author, thumbnail, image, timestamp")
    class SectionsTests {

        @Test
        @DisplayName("footer avec et sans icone")
        void footer() {
            JsonObject withIcon = firstEmbed(parse(DiscordEmbed.builder().footer("txt", "https://i.png").build())).getAsJsonObject("footer");
            assertEquals("txt", withIcon.get("text").getAsString());
            assertEquals("https://i.png", withIcon.get("icon_url").getAsString());

            JsonObject withoutIcon = firstEmbed(parse(DiscordEmbed.builder().footer("txt", "").build())).getAsJsonObject("footer");
            assertEquals(1, withoutIcon.size());
            assertFalse(withoutIcon.has("icon_url"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("footer sans texte omis meme avec une icone")
        void footer_withoutText_omitted(String text) {
            assertFalse(firstEmbed(parse(DiscordEmbed.builder().footer(text, "https://i.png").build())).has("footer"));
        }

        @Test
        @DisplayName("author complet, partiel et omis")
        void author() {
            JsonObject full = firstEmbed(parse(DiscordEmbed.builder().author("n", "https://u", "https://i").build())).getAsJsonObject("author");
            assertEquals("n", full.get("name").getAsString());
            assertEquals("https://u", full.get("url").getAsString());
            assertEquals("https://i", full.get("icon_url").getAsString());

            JsonObject nameOnly = firstEmbed(parse(DiscordEmbed.builder().author("n", "", null).build())).getAsJsonObject("author");
            assertEquals(1, nameOnly.size());

            assertFalse(firstEmbed(parse(DiscordEmbed.builder().author("", "https://u", "https://i").build())).has("author"));
            assertFalse(firstEmbed(parse(DiscordEmbed.builder().author(null, null, null).build())).has("author"));
        }

        @Test
        @DisplayName("thumbnail et image : presents ou omis")
        void thumbnailAndImage() {
            JsonObject json = firstEmbed(parse(DiscordEmbed.builder().thumbnail("https://t").image("https://i").build()));
            assertEquals("https://t", json.getAsJsonObject("thumbnail").get("url").getAsString());
            assertEquals("https://i", json.getAsJsonObject("image").get("url").getAsString());

            JsonObject empty = firstEmbed(parse(DiscordEmbed.builder().thumbnail("").image(null).build()));
            assertFalse(empty.has("thumbnail"));
            assertFalse(empty.has("image"));
        }

        @Test
        @DisplayName("timestamp(true) ajoute un instant ISO-8601, timestamp(false) rien")
        void timestamp() {
            JsonObject with = firstEmbed(parse(DiscordEmbed.builder().timestamp(true).build()));
            String value = with.get("timestamp").getAsString();
            assertTrue(value.endsWith("Z") && value.contains("T"), value);
            // Doit etre parseable
            java.time.Instant.parse(value);

            assertFalse(firstEmbed(parse(DiscordEmbed.builder().timestamp(false).build())).has("timestamp"));
        }
    }

    @Nested
    @DisplayName("Echappement JSON")
    class EscapeTests {

        @Test
        @DisplayName("guillemets, antislash, retours a la ligne et tabulations sont echappes")
        void specialCharacters_escaped() {
            String raw = "He said \"hi\" \\ new\nline\ttab\rcr\bbs\fff";
            DiscordEmbed embed = DiscordEmbed.builder().title(raw).build();

            String json = embed.toJson();
            assertTrue(json.contains("\\\"hi\\\""), json);
            assertTrue(json.contains("\\\\"), json);
            assertTrue(json.contains("\\n"), json);
            assertTrue(json.contains("\\t"), json);
            assertTrue(json.contains("\\r"), json);
            assertTrue(json.contains("\\b"), json);
            assertTrue(json.contains("\\f"), json);
            // Round-trip : le parseur doit restituer exactement la chaine d'origine
            assertEquals(raw, firstEmbed(parse(embed)).get("title").getAsString());
        }

        @Test
        @DisplayName("les caracteres de controle < 0x20 sont echappes en \\u00XX")
        void controlCharacters_unicodeEscaped() {
            String raw = "a\u0001b\u001Fc";

            String json = DiscordEmbed.builder().title(raw).build().toJson();

            assertTrue(json.contains("\\u0001"), json);
            assertTrue(json.contains("\\u001f"), json);
            assertEquals(raw, firstEmbed(JsonParser.parseString(json).getAsJsonObject()).get("title").getAsString());
        }

        @Test
        @DisplayName("les caracteres non ASCII et le markdown Discord passent tels quels")
        void unicodeAndMarkdown_untouched() {
            String raw = "**Epee** legendaire • ✨ <t:123:f>";

            assertEquals(raw, firstEmbed(parse(DiscordEmbed.builder().description(raw).build())).get("description").getAsString());
        }
    }
}
