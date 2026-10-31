package fr.maxlego08.zauctionhouse.discord;

import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.configuration.discord.DiscordConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordPlaceholderResolverTest {

    private static final String SERVER = "Survie";
    private static final String PATTERN = "https://img.example/%item_material%.png";
    private static final String DEFAULT_COLOR = "#5865F2";
    private static final UUID SELLER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUYER_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AuctionItem auctionItem;
    private ItemStack itemStack;
    private ItemMeta meta;
    private AuctionEconomy economy;

    @BeforeEach
    void setUp() {
        this.meta = mock(ItemMeta.class);
        this.itemStack = TestItems.itemStack(Material.DIAMOND_SWORD, 3);
        lenient().when(this.itemStack.hasItemMeta()).thenReturn(true);
        lenient().when(this.itemStack.getItemMeta()).thenReturn(this.meta);

        this.economy = MockPlugins.economy("vault");
        lenient().when(this.economy.getDisplayName()).thenReturn("Dollars");

        this.auctionItem = mock(AuctionItem.class);
        lenient().when(this.auctionItem.getId()).thenReturn(42);
        lenient().when(this.auctionItem.getItemStacks()).thenReturn(List.of(this.itemStack));
        lenient().when(this.auctionItem.getItemStack()).thenReturn(this.itemStack);
        lenient().when(this.auctionItem.getAmount()).thenReturn(3);
        lenient().when(this.auctionItem.getItemDisplay()).thenReturn("<gold>Epee <bold>legendaire");
        lenient().when(this.auctionItem.getSellerName()).thenReturn("Alice");
        lenient().when(this.auctionItem.getSellerUniqueId()).thenReturn(SELLER_UUID);
        lenient().when(this.auctionItem.getPrice()).thenReturn(new BigDecimal("1500.50"));
        lenient().when(this.auctionItem.getFormattedPrice()).thenReturn("1,500.50$");
        lenient().when(this.auctionItem.getAuctionEconomy()).thenReturn(this.economy);
        lenient().when(this.auctionItem.getRemainingTime()).thenReturn("2h 10m");
        lenient().when(this.auctionItem.getFormattedExpireDate()).thenReturn("formatted-expire");
    }

    private DiscordPlaceholderResolver resolver(Player seller, Player buyer) {
        return new DiscordPlaceholderResolver(SERVER, this.auctionItem, seller, buyer, PATTERN, false, DEFAULT_COLOR, null, List.of());
    }

    private DiscordPlaceholderResolver resolver() {
        return resolver(null, null);
    }

    @Nested
    @DisplayName("resolve : comportement general")
    class ResolveTests {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("texte null ou vide -> rendu tel quel")
        void nullOrEmpty_isReturnedAsIs(String text) {
            assertEquals(text, resolver().resolve(text));
        }

        @Test
        @DisplayName("un texte sans placeholder n'est pas modifie")
        void noPlaceholder_unchanged() {
            assertEquals("Hello world", resolver().resolve("Hello world"));
        }

        @Test
        @DisplayName("plusieurs placeholders dans le meme texte sont tous remplaces")
        void multiplePlaceholders_allReplaced() {
            assertEquals("Alice vend 3 pour 1,500.50$ sur Survie",
                    resolver().resolve("%seller_name% vend %item_amount% pour %formatted_price% sur %server_name%"));
        }

        @Test
        @DisplayName("un placeholder inconnu est laisse intact")
        void unknownPlaceholder_untouched() {
            assertEquals("%unknown% 42", resolver().resolve("%unknown% %item_id%"));
        }

        @Test
        @DisplayName("une valeur null fournie par un supplier est remplacee par une chaine vide")
        void nullValue_replacedByEmptyString() {
            when(auctionItem.getSellerName()).thenReturn(null);

            assertEquals("[]", resolver().resolve("[%seller_name%]"));
        }

        @Test
        @DisplayName("les suppliers sont paresseux : un placeholder absent du texte n'est pas evalue")
        void suppliers_areLazy() {
            resolver().resolve("%item_id%");

            verify(auctionItem, never()).getRemainingTime();
            verify(auctionItem, never()).getFormattedPrice();
        }
    }

    @Nested
    @DisplayName("Placeholders item")
    class ItemTests {

        @Test
        @DisplayName("%item_id%, %item_material%, %item_amount%")
        void basicItemPlaceholders() {
            DiscordPlaceholderResolver resolver = resolver();

            assertEquals("42", resolver.resolve("%item_id%"));
            assertEquals("DIAMOND_SWORD", resolver.resolve("%item_material%"));
            assertEquals("3", resolver.resolve("%item_amount%"));
        }

        @Test
        @DisplayName("%item_display% est debarrasse de ses codes couleur MiniMessage")
        void itemDisplay_stripsColors() {
            assertEquals("Epee legendaire", resolver().resolve("%item_display%"));
        }

        @Test
        @DisplayName("%item_lore% joint les lignes par un saut de ligne")
        void itemLore_joinsLines() {
            when(meta.hasLore()).thenReturn(true);
            when(meta.lore()).thenReturn(List.<Component>of(Component.text("Ligne 1"), Component.text("Ligne 2")));

            assertEquals("Ligne 1\nLigne 2", resolver().resolve("%item_lore%"));
        }

        @Test
        @DisplayName("%item_lore% sans lore -> chaine vide")
        void itemLore_withoutLore_isEmpty() {
            when(meta.hasLore()).thenReturn(false);

            assertEquals("", resolver().resolve("%item_lore%"));
        }

        @Test
        @DisplayName("%item_enchantments% formate 'Nom Niveau' separes par des virgules")
        void enchantments_formatted() {
            Enchantment fireAspect = mock(Enchantment.class);
            when(fireAspect.getKey()).thenReturn(NamespacedKey.minecraft("fire_aspect"));
            Enchantment sharpness = mock(Enchantment.class);
            when(sharpness.getKey()).thenReturn(NamespacedKey.minecraft("sharpness"));
            Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
            enchants.put(fireAspect, 2);
            enchants.put(sharpness, 5);
            when(meta.hasEnchants()).thenReturn(true);
            when(meta.getEnchants()).thenReturn(enchants);

            assertEquals("Fire Aspect 2, Sharpness 5", resolver().resolve("%item_enchantments%"));
        }

        @Test
        @DisplayName("%item_enchantments% sans enchantement -> None")
        void enchantments_none() {
            when(meta.hasEnchants()).thenReturn(false);

            assertEquals("None", resolver().resolve("%item_enchantments%"));
        }

        @Test
        @DisplayName("%item_custom_model_data% rend la valeur ou 0")
        void customModelData() {
            when(meta.hasCustomModelData()).thenReturn(true);
            when(meta.getCustomModelData()).thenReturn(10001);
            assertEquals("10001", resolver().resolve("%item_custom_model_data%"));

            when(meta.hasCustomModelData()).thenReturn(false);
            assertEquals("0", resolver().resolve("%item_custom_model_data%"));
        }

        @Test
        @DisplayName("ItemStack sans meta -> lore vide, None, 0")
        void itemWithoutMeta_defaults() {
            when(itemStack.getItemMeta()).thenReturn(null);
            when(itemStack.hasItemMeta()).thenReturn(false);

            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("", resolver.resolve("%item_lore%"));
            assertEquals("None", resolver.resolve("%item_enchantments%"));
            assertEquals("0", resolver.resolve("%item_custom_model_data%"));
        }

        @Test
        @DisplayName("ItemStack null -> valeurs par defaut UNKNOWN / 0 / Unknown Item")
        void nullItemStack_defaults() {
            when(auctionItem.getItemStacks()).thenReturn(List.of());
            when(auctionItem.getItemStack()).thenReturn(null);

            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("UNKNOWN", resolver.resolve("%item_material%"));
            assertEquals("0", resolver.resolve("%item_amount%"));
            assertEquals("Unknown Item", resolver.resolve("%item_display%"));
            assertEquals("", resolver.resolve("%item_lore%"));
            assertEquals("None", resolver.resolve("%item_enchantments%"));
            assertEquals("0", resolver.resolve("%item_custom_model_data%"));
            assertEquals("https://img.example/UNKNOWN.png", resolver.resolve("%item_image_url%"));
        }
    }

    @Nested
    @DisplayName("Placeholders vendeur et acheteur")
    class PlayerTests {

        @Test
        @DisplayName("vendeur en ligne : nom et UUID du joueur")
        void seller_online() {
            Player seller = MockPlugins.player(SELLER_UUID, "AliceOnline");

            DiscordPlaceholderResolver resolver = resolver(seller, null);
            assertEquals("AliceOnline", resolver.resolve("%seller_name%"));
            assertEquals(SELLER_UUID.toString(), resolver.resolve("%seller_uuid%"));
        }

        @Test
        @DisplayName("vendeur absent : valeurs de l'item")
        void seller_offline_fromItem() {
            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("Alice", resolver.resolve("%seller_name%"));
            assertEquals(SELLER_UUID.toString(), resolver.resolve("%seller_uuid%"));
        }

        @Test
        @DisplayName("vendeur absent et UUID null -> chaine vide")
        void seller_offline_nullUuid() {
            when(auctionItem.getSellerUniqueId()).thenReturn(null);

            assertEquals("", resolver().resolve("%seller_uuid%"));
        }

        @Test
        @DisplayName("acheteur en ligne : nom et UUID du joueur")
        void buyer_online() {
            Player buyer = MockPlugins.player(BUYER_UUID, "Bob");

            DiscordPlaceholderResolver resolver = resolver(null, buyer);
            assertEquals("Bob", resolver.resolve("%buyer_name%"));
            assertEquals(BUYER_UUID.toString(), resolver.resolve("%buyer_uuid%"));
        }

        @Test
        @DisplayName("acheteur absent : valeurs de l'item, Unknown et vide si inconnues")
        void buyer_offline() {
            when(auctionItem.getBuyerName()).thenReturn("BobOffline");
            when(auctionItem.getBuyerUniqueId()).thenReturn(BUYER_UUID);
            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("BobOffline", resolver.resolve("%buyer_name%"));
            assertEquals(BUYER_UUID.toString(), resolver.resolve("%buyer_uuid%"));

            when(auctionItem.getBuyerName()).thenReturn(null);
            when(auctionItem.getBuyerUniqueId()).thenReturn(null);
            DiscordPlaceholderResolver noBuyer = resolver();
            assertEquals("Unknown", noBuyer.resolve("%buyer_name%"));
            assertEquals("", noBuyer.resolve("%buyer_uuid%"));
        }
    }

    @Nested
    @DisplayName("Placeholders prix, temps, serveur, categories")
    class MiscTests {

        @Test
        @DisplayName("prix et economie")
        void pricePlaceholders() {
            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("1500.50", resolver.resolve("%price%"));
            assertEquals("1,500.50$", resolver.resolve("%formatted_price%"));
            assertEquals("vault", resolver.resolve("%economy_name%"));
            assertEquals("Dollars", resolver.resolve("%economy_display_name%"));
        }

        @Test
        @DisplayName("economie null -> default / Default")
        void nullEconomy_defaults() {
            when(auctionItem.getAuctionEconomy()).thenReturn(null);

            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("default", resolver.resolve("%economy_name%"));
            assertEquals("Default", resolver.resolve("%economy_display_name%"));
        }

        @Test
        @DisplayName("%expires_at% rend un timestamp Discord en SECONDES")
        void expiresAt_discordTimestamp() {
            when(auctionItem.getExpiredAt()).thenReturn(new Date(1_700_000_000_123L));

            assertEquals("<t:1700000000:f>", resolver().resolve("%expires_at%"));
        }

        @Test
        @DisplayName("%expires_at% sans date (null ou 0) retombe sur la date formatee")
        void expiresAt_fallback() {
            when(auctionItem.getExpiredAt()).thenReturn(null);
            assertEquals("formatted-expire", resolver().resolve("%expires_at%"));

            when(auctionItem.getExpiredAt()).thenReturn(new Date(0L));
            assertEquals("formatted-expire", resolver().resolve("%expires_at%"));
        }

        @Test
        @DisplayName("%created_at% rend la date ou vide, %remaining_time% et %timestamp%")
        void timePlaceholders() {
            Date created = new Date(1_700_000_000_000L);
            when(auctionItem.getCreatedAt()).thenReturn(created);
            assertEquals(created.toString(), resolver().resolve("%created_at%"));

            when(auctionItem.getCreatedAt()).thenReturn(null);
            assertEquals("", resolver().resolve("%created_at%"));

            assertEquals("2h 10m", resolver().resolve("%remaining_time%"));
            String timestamp = resolver().resolve("%timestamp%");
            assertTrue(timestamp.endsWith("Z") && timestamp.contains("T"), timestamp);
        }

        @Test
        @DisplayName("%server_name%")
        void serverName() {
            assertEquals(SERVER, resolver().resolve("%server_name%"));
        }

        @Test
        @DisplayName("categories : noms joints par virgule et compteur")
        void categories() {
            Category weapons = mock(Category.class);
            when(weapons.getDisplayName()).thenReturn("Weapons");
            Category tools = mock(Category.class);
            when(tools.getDisplayName()).thenReturn("Tools");
            Set<Category> categories = new LinkedHashSet<>(List.of(weapons, tools));
            when(auctionItem.getCategories()).thenReturn(categories);

            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("Weapons, Tools", resolver.resolve("%category_names%"));
            assertEquals("2", resolver.resolve("%category_count%"));
        }

        @Test
        @DisplayName("categories null ou vides -> None et 0")
        void categories_noneOrNull() {
            when(auctionItem.getCategories()).thenReturn(null);
            assertEquals("None", resolver().resolve("%category_names%"));
            assertEquals("0", resolver().resolve("%category_count%"));

            when(auctionItem.getCategories()).thenReturn(Set.of());
            assertEquals("None", resolver().resolve("%category_names%"));
            assertEquals("0", resolver().resolve("%category_count%"));
        }
    }

    @Nested
    @DisplayName("Image et couleur dominante")
    class ImageTests {

        @Test
        @DisplayName("l'URL est construite depuis le pattern, en remplacant les deux casses du placeholder")
        void imageUrl_fromPattern() {
            DiscordPlaceholderResolver lower = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    "https://a/%item_material%.png", false, DEFAULT_COLOR, null, List.of());
            DiscordPlaceholderResolver upper = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    "https://a/%ITEM_MATERIAL%.png", false, DEFAULT_COLOR, null, List.of());

            assertEquals("https://a/DIAMOND_SWORD.png", lower.resolve("%item_image_url%"));
            assertEquals("https://a/DIAMOND_SWORD.png", upper.resolve("%item_image_url%"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("pattern null ou vide -> URL vide et couleur par defaut meme si l'extraction est activee")
        void emptyPattern_emptyUrl(String pattern) {
            ColorExtractor extractor = mock(ColorExtractor.class);

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    pattern, true, DEFAULT_COLOR, extractor, List.of());

            assertEquals("", resolver.resolve("%item_image_url%"));
            assertEquals(DEFAULT_COLOR, resolver.resolve("%item_dominant_color%"));
            verify(extractor, never()).getColorForMaterial(any(), any());
        }

        @Test
        @DisplayName("extraction desactivee -> couleur par defaut, extracteur jamais appele")
        void extractionDisabled_defaultColor() {
            ColorExtractor extractor = mock(ColorExtractor.class);

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, false, DEFAULT_COLOR, extractor, List.of());

            assertEquals(DEFAULT_COLOR, resolver.resolve("%item_dominant_color%"));
            verify(extractor, never()).getColorForMaterial(any(), any());
        }

        @Test
        @DisplayName("extraction activee sans extracteur -> couleur par defaut")
        void extractionEnabled_withoutExtractor_defaultColor() {
            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, true, DEFAULT_COLOR, null, List.of());

            assertEquals(DEFAULT_COLOR, resolver.resolve("%item_dominant_color%"));
        }

        @Test
        @DisplayName("extraction activee : la couleur est demandee par MATERIAL avec l'URL construite")
        void extractionEnabled_asksExtractorByMaterial() {
            ColorExtractor extractor = mock(ColorExtractor.class);
            when(extractor.getColorForMaterial("DIAMOND_SWORD", "https://img.example/DIAMOND_SWORD.png")).thenReturn("#ABCDEF");

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, true, DEFAULT_COLOR, extractor, List.of());

            assertEquals("#ABCDEF", resolver.resolve("%item_dominant_color%"));
        }

        @Test
        @DisplayName("une regle d'image personnalisee qui correspond remplace le pattern")
        void customImage_overridesPattern() {
            Rule matching = mock(Rule.class);
            when(matching.matches(any(ItemRuleContext.class))).thenReturn(true);
            List<DiscordConfiguration.CustomImageRule> rules = List.of(new DiscordConfiguration.CustomImageRule(matching, "https://custom/sword.png"));

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, false, DEFAULT_COLOR, null, rules);

            assertEquals("https://custom/sword.png", resolver.resolve("%item_image_url%"));
        }

        @Test
        @DisplayName("la PREMIERE regle qui correspond gagne")
        void customImage_firstMatchWins() {
            Rule first = mock(Rule.class);
            when(first.matches(any(ItemRuleContext.class))).thenReturn(false);
            Rule second = mock(Rule.class);
            when(second.matches(any(ItemRuleContext.class))).thenReturn(true);
            Rule third = mock(Rule.class);
            when(third.matches(any(ItemRuleContext.class))).thenReturn(true);
            List<DiscordConfiguration.CustomImageRule> rules = List.of(
                    new DiscordConfiguration.CustomImageRule(first, "https://custom/1.png"),
                    new DiscordConfiguration.CustomImageRule(second, "https://custom/2.png"),
                    new DiscordConfiguration.CustomImageRule(third, "https://custom/3.png"));

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, false, DEFAULT_COLOR, null, rules);

            assertEquals("https://custom/2.png", resolver.resolve("%item_image_url%"));
            verify(third, never()).matches(any());
        }

        @Test
        @DisplayName("aucune regle ne correspond -> retour au pattern")
        void customImage_noMatch_fallsBackToPattern() {
            Rule rule = mock(Rule.class);
            when(rule.matches(any(ItemRuleContext.class))).thenReturn(false);

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, false, DEFAULT_COLOR, null, List.of(new DiscordConfiguration.CustomImageRule(rule, "https://custom/x.png")));

            assertEquals("https://img.example/DIAMOND_SWORD.png", resolver.resolve("%item_image_url%"));
        }

        @Test
        @DisplayName("image personnalisee : la couleur est mise en cache par URL et non par material")
        void customImage_colorCachedByUrl() {
            Rule rule = mock(Rule.class);
            when(rule.matches(any(ItemRuleContext.class))).thenReturn(true);
            ColorExtractor extractor = mock(ColorExtractor.class);
            when(extractor.getColorForMaterial("https://custom/sword.png", "https://custom/sword.png")).thenReturn("#112233");

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, true, DEFAULT_COLOR, extractor, List.of(new DiscordConfiguration.CustomImageRule(rule, "https://custom/sword.png")));

            assertEquals("#112233", resolver.resolve("%item_dominant_color%"));
            verify(extractor, never()).getColorForMaterial("DIAMOND_SWORD", "https://custom/sword.png");
        }

        @Test
        @DisplayName("liste de regles null ou ItemStack null -> aucune regle evaluee")
        void customImage_nullRulesOrItem() {
            DiscordPlaceholderResolver nullRules = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, false, DEFAULT_COLOR, null, null);
            assertEquals("https://img.example/DIAMOND_SWORD.png", nullRules.resolve("%item_image_url%"));

            Rule rule = mock(Rule.class);
            when(auctionItem.getItemStacks()).thenReturn(List.of());
            when(auctionItem.getItemStack()).thenReturn(null);
            DiscordPlaceholderResolver nullItem = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, false, DEFAULT_COLOR, null, List.of(new DiscordConfiguration.CustomImageRule(rule, "https://custom/x.png")));
            assertEquals("https://img.example/UNKNOWN.png", nullItem.resolve("%item_image_url%"));
            verify(rule, never()).matches(any());
        }

        @Test
        @DisplayName("une regle qui rend une image vide est ignoree au profit du pattern")
        void customImage_emptyImage_ignored() {
            Rule rule = mock(Rule.class);
            when(rule.matches(any(ItemRuleContext.class))).thenReturn(true);

            DiscordPlaceholderResolver resolver = new DiscordPlaceholderResolver(SERVER, auctionItem, null, null,
                    PATTERN, false, DEFAULT_COLOR, null, List.of(new DiscordConfiguration.CustomImageRule(rule, "")));

            assertEquals("https://img.example/DIAMOND_SWORD.png", resolver.resolve("%item_image_url%"));
        }

        @Test
        @DisplayName("sanity : aucun placeholder image n'est null apres construction")
        void imagePlaceholders_neverNull() {
            DiscordPlaceholderResolver resolver = resolver();
            assertEquals("https://img.example/DIAMOND_SWORD.png", resolver.resolve("%item_image_url%"));
            assertEquals(DEFAULT_COLOR, resolver.resolve("%item_dominant_color%"));
        }
    }
}
