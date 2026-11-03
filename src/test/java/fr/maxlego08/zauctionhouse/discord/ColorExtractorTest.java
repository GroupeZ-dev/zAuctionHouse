package fr.maxlego08.zauctionhouse.discord;

import com.google.gson.Gson;
import fr.maxlego08.zauctionhouse.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorExtractorTest {

    private static final String DEFAULT_COLOR = "#5865F2";

    @TempDir
    File dataFolder;

    private Logger logger;

    @BeforeEach
    void setUp() {
        this.logger = Logger.getLogger("ColorExtractorTest");
    }

    private ColorExtractor newExtractor() {
        return new ColorExtractor(this.dataFolder, this.logger, DEFAULT_COLOR);
    }

    private File cacheFile() {
        return new File(new File(this.dataFolder, ".cache"), "material-colors.json");
    }

    /**
     * Appelle la methode privee {@code calculateDominantColor(BufferedImage)} : c'est le coeur
     * algorithmique, isole de tout acces reseau.
     */
    private static String dominantColor(ColorExtractor extractor, BufferedImage image) {
        return ReflectionTestUtils.invoke(extractor, "calculateDominantColor", new Class<?>[]{BufferedImage.class}, image);
    }

    private static BufferedImage filled(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    @Nested
    @DisplayName("Constructeur et cache disque")
    class CacheTests {

        @Test
        @DisplayName("le constructeur cree le dossier .cache")
        void constructor_createsCacheDirectory() {
            newExtractor();

            assertTrue(new File(dataFolder, ".cache").isDirectory());
        }

        @Test
        @DisplayName("un cache existant est relu au demarrage et sert sans acces reseau")
        void constructor_loadsExistingCache() throws Exception {
            File file = cacheFile();
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), new Gson().toJson(Map.of("DIAMOND_SWORD", "#123456")), StandardCharsets.UTF_8);

            ColorExtractor extractor = newExtractor();

            // L'URL est invalide : si le cache n'etait pas relu, on obtiendrait la couleur par defaut.
            assertEquals("#123456", extractor.getColorForMaterial("DIAMOND_SWORD", "not a url"));
        }

        @Test
        @DisplayName("un cache corrompu est ignore sans faire echouer la construction")
        void constructor_ignoresCorruptedCache() throws Exception {
            File file = cacheFile();
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), "{ this is not json", StandardCharsets.UTF_8);

            ColorExtractor extractor = newExtractor();

            assertEquals(DEFAULT_COLOR, extractor.getColorForMaterial("STONE", "not a url"));
        }

        @Test
        @DisplayName("la couleur calculee est persistee dans material-colors.json")
        void getColorForMaterial_persistsCache() throws Exception {
            ColorExtractor extractor = newExtractor();

            extractor.getColorForMaterial("STONE", "not a url");

            assertTrue(cacheFile().isFile());
            String json = Files.readString(cacheFile().toPath(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"STONE\""), json);
            assertTrue(json.contains(DEFAULT_COLOR), json);
        }

        @Test
        @DisplayName("un second extracteur relit la valeur persistee par le premier")
        void getColorForMaterial_isSharedThroughDisk() {
            newExtractor().getColorForMaterial("STONE", "not a url");

            // Avec un defaut different : si la valeur ne venait pas du cache, on lirait #000000.
            ColorExtractor second = new ColorExtractor(dataFolder, logger, "#000000");
            assertEquals(DEFAULT_COLOR, second.getColorForMaterial("STONE", "not a url"));
        }
    }

    @Nested
    @DisplayName("getColorForMaterial : gardes")
    class GuardTests {

        @Test
        @DisplayName("material null ou vide -> couleur par defaut, rien n'est mis en cache")
        void nullOrEmptyMaterial_returnsDefault() {
            ColorExtractor extractor = newExtractor();

            assertEquals(DEFAULT_COLOR, extractor.getColorForMaterial(null, "http://example.invalid/x.png"));
            assertEquals(DEFAULT_COLOR, extractor.getColorForMaterial("", "http://example.invalid/x.png"));
            assertFalse(cacheFile().exists());
        }

        @Test
        @DisplayName("URL null ou vide -> couleur par defaut (et mise en cache)")
        void nullOrEmptyUrl_returnsDefault() {
            ColorExtractor extractor = newExtractor();

            assertEquals(DEFAULT_COLOR, extractor.getColorForMaterial("STONE", null));
            assertEquals(DEFAULT_COLOR, extractor.getColorForMaterial("DIRT", ""));
            assertTrue(cacheFile().isFile());
        }

        @Test
        @DisplayName("URL syntaxiquement invalide -> exception avalee, couleur par defaut rendue")
        void invalidUrl_returnsDefault() {
            ColorExtractor extractor = newExtractor();

            assertEquals(DEFAULT_COLOR, extractor.getColorForMaterial("STONE", "::not a uri::"));
        }
    }

    @Nested
    @DisplayName("calculateDominantColor")
    class DominantColorTests {

        @Test
        @DisplayName("image unie -> sa couleur, quantifiee par pas de 8 et formatee #RRGGBB")
        void solidImage_returnsQuantizedColor() {
            // 0xFF5733 -> R=255->248, G=87->80, B=51->48
            BufferedImage image = filled(16, 16, new Color(0xFF, 0x57, 0x33).getRGB());

            assertEquals("#F85030", dominantColor(newExtractor(), image));
        }

        @Test
        @DisplayName("la couleur majoritaire gagne sur la minoritaire")
        void mixedImage_returnsMostFrequentColor() {
            BufferedImage image = filled(10, 10, Color.RED.getRGB());
            // 30 pixels bleus sur 100
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 10; x++) image.setRGB(x, y, Color.BLUE.getRGB());
            }

            assertEquals("#F80000", dominantColor(newExtractor(), image));
        }

        @Test
        @DisplayName("les pixels transparents (alpha < 128) sont ignores")
        void transparentPixels_areIgnored() {
            BufferedImage image = filled(10, 10, new Color(0, 0, 255, 0).getRGB()); // bleu totalement transparent
            // un seul pixel opaque vert
            image.setRGB(5, 5, new Color(0, 255, 0, 255).getRGB());

            assertEquals("#00F800", dominantColor(newExtractor(), image));
        }

        @Test
        @DisplayName("image entierement transparente -> couleur par defaut")
        void fullyTransparentImage_returnsDefault() {
            BufferedImage image = filled(8, 8, new Color(255, 0, 0, 10).getRGB());

            assertEquals(DEFAULT_COLOR, dominantColor(newExtractor(), image));
        }

        @Test
        @DisplayName("alpha exactement 128 est opaque, 127 est transparent")
        void alphaThreshold_isInclusiveAt128() {
            BufferedImage opaqueEnough = filled(4, 4, new Color(8, 16, 24, 128).getRGB());
            BufferedImage tooTransparent = filled(4, 4, new Color(8, 16, 24, 127).getRGB());

            assertEquals("#081018", dominantColor(newExtractor(), opaqueEnough));
            assertEquals(DEFAULT_COLOR, dominantColor(newExtractor(), tooTransparent));
        }

        @Test
        @DisplayName("grande image : l'echantillonnage par pas ne manque pas la couleur dominante")
        void largeImage_isSampledWithStep() {
            // step = min(200,200)/50 = 4 : on echantillonne 50x50 points
            BufferedImage image = filled(200, 200, Color.WHITE.getRGB());

            assertEquals("#F8F8F8", dominantColor(newExtractor(), image));
        }

        @Test
        @DisplayName("image 1x1 : un seul pixel suffit")
        void singlePixelImage_works() {
            BufferedImage image = filled(1, 1, Color.BLACK.getRGB());

            assertEquals("#000000", dominantColor(newExtractor(), image));
        }

        @Test
        @DisplayName("quantizeColor arrondit chaque composante au multiple de 8 inferieur")
        void quantizeColor_roundsDownToMultipleOf8() {
            ColorExtractor extractor = newExtractor();

            int quantized = ReflectionTestUtils.invoke(extractor, "quantizeColor", new Class<?>[]{int.class}, 0xFF0F17);
            assertEquals(0xF80810, quantized);

            int alreadyAligned = ReflectionTestUtils.invoke(extractor, "quantizeColor", new Class<?>[]{int.class}, 0x102030);
            assertEquals(0x102030, alreadyAligned);
        }
    }
}
