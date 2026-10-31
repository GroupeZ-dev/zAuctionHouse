package fr.maxlego08.zauctionhouse.loader;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.AuctionMessage;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.messages.MessageColor;
import fr.maxlego08.zauctionhouse.api.messages.MessageType;
import fr.maxlego08.zauctionhouse.api.messages.messages.BossBarMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.ClassicMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Message} est un enum MUTABLE partage par toute la JVM : chaque test sauvegarde puis
 * restaure les messages de toutes les constantes pour ne pas polluer les autres classes de test.
 */
class MessageLoaderTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private final Map<Message, List<AuctionMessage>> snapshot = new EnumMap<>(Message.class);

    @BeforeEach
    void setUp() {
        for (Message message : Message.values()) {
            this.snapshot.put(message, message.getMessages());
        }
        this.plugin = MockPlugins.plugin(this.dataFolder);
        // Les couleurs nommees du plugin : <primary> est remplace, <secondary> est laisse tel quel.
        when(this.plugin.getConfiguration().getMessageColors()).thenReturn(List.of(new MessageColor("<primary>", "<#24d65d>")));
    }

    @AfterEach
    void restoreMessages() {
        this.snapshot.forEach(Message::setMessages);
    }

    private void load(String yaml) throws Exception {
        Files.writeString(new File(this.dataFolder, "messages.yml").toPath(), yaml, StandardCharsets.UTF_8);
        new MessageLoader(this.plugin).load();
    }

    private static ClassicMessage classic(Message message) {
        assertEquals(1, message.getMessages().size(), "expected exactly one message for " + message);
        return assertInstanceOf(ClassicMessage.class, message.getMessages().getFirst());
    }

    @Nested
    @DisplayName("Formes simples")
    class SimpleFormsTests {

        @Test
        @DisplayName("load demande la sauvegarde/mise a jour du fichier messages.yml")
        void load_savesFile() throws Exception {
            load("search-start: \"x\"\n");

            verify(plugin).saveFile("messages.yml", true);
        }

        @Test
        @DisplayName("une chaine simple devient un ClassicMessage TCHAT")
        void plainString_becomesTchat() throws Exception {
            load("search-start: \"<gray>Tape ta recherche\"\n");

            ClassicMessage message = classic(Message.SEARCH_START);
            assertEquals(MessageType.TCHAT, message.messageType());
            assertEquals(List.of("<gray>Tape ta recherche"), message.messages());
        }

        @Test
        @DisplayName("une liste de chaines devient un ClassicMessage TCHAT multi-lignes")
        void stringList_becomesMultilineTchat() throws Exception {
            load("""
                    search-start:
                      - "ligne 1"
                      - "ligne 2"
                    """);

            assertEquals(List.of("ligne 1", "ligne 2"), classic(Message.SEARCH_START).messages());
        }

        @Test
        @DisplayName("les couleurs nommees sont remplacees dans les messages charges")
        void namedColors_replaced() throws Exception {
            load("search-start: \"<primary>Recherche <secondary>•\"\n");

            assertEquals(List.of("<#24d65d>Recherche <secondary>•"), classic(Message.SEARCH_START).messages());
        }

        @Test
        @DisplayName("la cle YAML en kebab-case est convertie en constante SNAKE_CASE")
        void kebabKey_mappedToConstant() throws Exception {
            load("inventory-not-found: \"introuvable\"\n");

            assertEquals(List.of("introuvable"), classic(Message.INVENTORY_NOT_FOUND).messages());
        }

        @Test
        @DisplayName("une cle inconnue est ignoree sans erreur")
        void unknownKey_ignored() throws Exception {
            List<AuctionMessage> before = Message.SEARCH_START.getMessages();

            load("this-does-not-exist: \"x\"\nsearch-start: \"ok\"\n");

            assertEquals(List.of("ok"), classic(Message.SEARCH_START).messages());
            assertTrue(before != Message.SEARCH_START.getMessages());
        }

        @Test
        @DisplayName("une liste vide ne peut pas etre chargee : la valeur par defaut est conservee (avec couleurs)")
        void emptyList_keepsDefault() throws Exception {
            String defaultText = classic(Message.SEARCH_START).messages().getFirst();

            load("search-start: []\n");

            assertEquals(List.of(defaultText), classic(Message.SEARCH_START).messages());
        }
    }

    @Nested
    @DisplayName("Forme map-list (type explicite)")
    class MapListTests {

        @Test
        @DisplayName("type ACTION et type absent (TCHAT) sur deux entrees")
        void actionAndDefaultType() throws Exception {
            load("""
                    search-start:
                      - type: ACTION
                        message: "<primary>barre"
                      - messages:
                          - "a"
                          - "b"
                    """);

            List<AuctionMessage> messages = Message.SEARCH_START.getMessages();
            assertEquals(2, messages.size());
            ClassicMessage action = assertInstanceOf(ClassicMessage.class, messages.get(0));
            assertEquals(MessageType.ACTION, action.messageType());
            assertEquals(List.of("<#24d65d>barre"), action.messages());
            ClassicMessage tchat = assertInstanceOf(ClassicMessage.class, messages.get(1));
            assertEquals(MessageType.TCHAT, tchat.messageType());
            assertEquals(List.of("a", "b"), tchat.messages());
        }

        @Test
        @DisplayName("type inconnu -> TCHAT par defaut")
        void unknownType_fallsBackToTchat() throws Exception {
            load("""
                    search-start:
                      - type: NOPE
                        message: "x"
                    """);

            assertEquals(MessageType.TCHAT, classic(Message.SEARCH_START).messageType());
        }

        @Test
        @DisplayName("type TITLE : titre, sous-titre et durees (entiers YAML convertis en long)")
        void title_parsed() throws Exception {
            load("""
                    search-start:
                      - type: TITLE
                        title: "<primary>Titre"
                        subtitle: "Sous"
                        start: 10
                        time: 20
                        end: 30
                    """);

            TitleMessage title = assertInstanceOf(TitleMessage.class, Message.SEARCH_START.getMessages().getFirst());
            assertEquals(new TitleMessage("<#24d65d>Titre", "Sous", 10L, 20L, 30L), title);
        }

        @Test
        @DisplayName("type TITLE sans durees -> valeurs par defaut 100/2800/100 et textes par defaut")
        void title_defaults() throws Exception {
            load("""
                    search-start:
                      - type: TITLE
                    """);

            TitleMessage title = assertInstanceOf(TitleMessage.class, Message.SEARCH_START.getMessages().getFirst());
            assertEquals(new TitleMessage("Default title", "Default subtitle", 100L, 2800L, 100L), title);
        }

        @Test
        @DisplayName("type BOSSBAR valide : toutes les options sont lues")
        void bossBar_parsed() throws Exception {
            load("""
                    search-start:
                      - type: BOSSBAR
                        text: "<primary>Boss"
                        color: RED
                        overlay: NOTCHED_6
                        flags: [DARKEN_SCREEN]
                        duration: 100
                        static: true
                    """);

            BossBarMessage bossBar = assertInstanceOf(BossBarMessage.class, Message.SEARCH_START.getMessages().getFirst());
            assertEquals("<#24d65d>Boss", bossBar.text());
            assertEquals("RED", bossBar.color());
            assertEquals("NOTCHED_6", bossBar.overlay());
            assertEquals(List.of("DARKEN_SCREEN"), bossBar.flags());
            assertEquals(100L, bossBar.duration());
            assertTrue(bossBar.isStatic());
        }

        @Test
        @DisplayName("type BOSSBAR avec couleur invalide -> message rejete (liste vide)")
        void bossBar_invalidColor_rejected() throws Exception {
            load("""
                    search-start:
                      - type: BOSSBAR
                        text: "Boss"
                        color: TURQUOISE
                    """);

            assertTrue(Message.SEARCH_START.getMessages().isEmpty());
        }

        @Test
        @DisplayName("type BOSSBAR : valeur de type incorrect (duration en chaine) -> valeur par defaut 60")
        void bossBar_typeMismatch_default() throws Exception {
            load("""
                    search-start:
                      - type: BOSSBAR
                        text: "Boss"
                        duration: "abc"
                    """);

            BossBarMessage bossBar = assertInstanceOf(BossBarMessage.class, Message.SEARCH_START.getMessages().getFirst());
            assertEquals(60L, bossBar.duration());
            assertEquals("WHITE", bossBar.color());
            assertEquals("PROGRESS", bossBar.overlay());
        }

        @Test
        @DisplayName("entree TCHAT sans message -> ignoree, les autres entrees restent")
        void emptyTchatEntry_skipped() throws Exception {
            load("""
                    search-start:
                      - type: TCHAT
                      - message: "ok"
                    """);

            assertEquals(List.of("ok"), classic(Message.SEARCH_START).messages());
        }

        @Test
        @DisplayName("'message' et 'messages' sont fusionnes dans l'ordre messages puis message")
        void messageAndMessages_merged() throws Exception {
            load("""
                    search-start:
                      - message: "seul"
                        messages: ["a", "b"]
                    """);

            assertEquals(List.of("a", "b", "seul"), classic(Message.SEARCH_START).messages());
        }
    }

    @Nested
    @DisplayName("Forme section avec .type")
    class SectionTests {

        @Test
        @DisplayName("section TITLE")
        void sectionTitle() throws Exception {
            load("""
                    search-start:
                      type: TITLE
                      title: "<primary>T"
                      subtitle: "S"
                      start: 1
                      time: 2
                      end: 3
                    """);

            assertEquals(new TitleMessage("<#24d65d>T", "S", 1L, 2L, 3L), Message.SEARCH_START.getMessages().getFirst());
        }

        @Test
        @DisplayName("section TCHAT avec 'messages' puis avec 'message'")
        void sectionTchat() throws Exception {
            load("""
                    search-start:
                      type: CENTER
                      messages: ["<primary>a", "b"]
                    inventory-not-found:
                      type: WITHOUT_PREFIX
                      message: "<primary>seul"
                    """);

            ClassicMessage center = classic(Message.SEARCH_START);
            assertEquals(MessageType.CENTER, center.messageType());
            assertEquals(List.of("<#24d65d>a", "b"), center.messages());
            ClassicMessage single = classic(Message.INVENTORY_NOT_FOUND);
            assertEquals(MessageType.WITHOUT_PREFIX, single.messageType());
            assertEquals(List.of("<#24d65d>seul"), single.messages());
        }

        @Test
        @DisplayName("section BOSSBAR : texte lu, options absentes -> defauts")
        void sectionBossBar() throws Exception {
            load("""
                    search-start:
                      type: BOSSBAR
                      text: "<primary>B"
                    """);

            BossBarMessage bossBar = assertInstanceOf(BossBarMessage.class, Message.SEARCH_START.getMessages().getFirst());
            assertEquals("<#24d65d>B", bossBar.text());
            assertEquals("WHITE", bossBar.color());
            assertEquals(60L, bossBar.duration());
        }

        @Test
        @DisplayName("section avec type inconnu -> TCHAT")
        void sectionUnknownType_tchat() throws Exception {
            load("""
                    search-start:
                      type: WHATEVER
                      message: "x"
                    """);

            assertEquals(MessageType.TCHAT, classic(Message.SEARCH_START).messageType());
        }
    }

    @Nested
    @DisplayName("Messages manquants")
    class MissingTests {

        @Test
        @DisplayName("les constantes absentes du fichier gardent leur valeur par defaut, couleurs appliquees")
        void missing_defaultsWithColors() throws Exception {
            load("search-start: \"x\"\n");

            ClassicMessage prefix = classic(Message.PREFIX);
            assertEquals(List.of("<#24d65d>zAuctionHouse <secondary>• "), prefix.messages());
            assertSame(plugin, fieldPlugin(Message.PREFIX));
        }

        @Test
        @DisplayName("le plugin est attache aux messages charges")
        void loaded_pluginAttached() throws Exception {
            load("search-start: \"x\"\n");

            assertSame(plugin, fieldPlugin(Message.SEARCH_START));
        }

        private static AuctionPlugin fieldPlugin(Message message) {
            return fr.maxlego08.zauctionhouse.testutil.ReflectionTestUtils.field(message, "plugin");
        }
    }
}
