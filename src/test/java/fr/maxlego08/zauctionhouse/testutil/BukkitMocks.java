package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.utils.tags.TagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.plugin.PluginManager;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

/**
 * Doublure statique de {@link Bukkit} pour le code qui en a besoin sans serveur :
 * <ul>
 *   <li>{@code Bukkit.getBukkitVersion()} (lu par {@code MinecraftVersion.getCurrentVersion()}
 *   de zMenu, puis mis en cache statique pour toute la JVM) ;</li>
 *   <li>{@code Bukkit.getPluginManager()} (lu par {@code Event.callEvent()}) ;</li>
 *   <li>{@code Bukkit.getTag(...)} (lu par l'initialisation statique de {@link Tag}).</li>
 * </ul>
 * <p>
 * Les constantes de {@link Tag} ne sont initialisees qu'UNE fois par JVM : la premiere lecture
 * doit se faire sous un Bukkit mocke, sinon la classe reste cassee ({@code NoClassDefFoundError})
 * pour tous les tests suivants. {@link #ensureTagsInitialized()} s'en charge et remplace chaque
 * constante par un {@link FakeTag} dont l'appartenance est pilotee par {@link #defineTag}.
 * <p>
 * {@code BlocksTag} (tag custom "blocks" de production) repose sur {@code Material.isBlock()},
 * qui interroge le registre serveur : il est systematiquement remplace par un faux tag dans les
 * tests via {@link #overrideBlocksTag}.
 */
public final class BukkitMocks {

    public static final String BUKKIT_VERSION = "1.21.10-R0.1-SNAPSHOT";

    private static final Map<String, Set<Material>> TAG_MEMBERS = new ConcurrentHashMap<>();
    private static boolean tagsInitialized;

    private BukkitMocks() {
    }

    /**
     * Ouvre un mock statique de Bukkit (a fermer dans un try-with-resources). Garantit au
     * prealable que {@link Tag} est initialise sous mock.
     */
    public static MockedStatic<Bukkit> mockBukkit() {
        ensureTagsInitialized();
        return openBukkit();
    }

    private static MockedStatic<Bukkit> openBukkit() {
        // Collaborateurs crees AVANT tout when(...)
        PluginManager pluginManager = mock(PluginManager.class);
        MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getBukkitVersion).thenReturn(BUKKIT_VERSION);
        bukkit.when(Bukkit::getMinecraftVersion).thenReturn("1.21.10");
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(() -> Bukkit.getTag(anyString(), any(NamespacedKey.class), eq(Material.class)))
                .thenAnswer(invocation -> new FakeTag(invocation.getArgument(1)));
        return bukkit;
    }

    /**
     * Force l'initialisation de {@link Tag} et de {@link TagRegistry} sous Bukkit mocke.
     * Idempotent. Ne doit PAS etre appele alors qu'un mock statique de Bukkit est deja ouvert
     * sur le thread courant (utiliser {@link #mockBukkit()} qui l'appelle en premier).
     */
    public static synchronized void ensureTagsInitialized() {
        if (tagsInitialized) return;
        try (MockedStatic<Bukkit> ignored = openBukkit()) {
            Objects.requireNonNull(Tag.WOOL, "Tag.WOOL doit etre un FakeTag, pas null");
            // Declenche l'init statique du registre (parcourt tous les champs de Tag)
            TagRegistry.getTag("blocks");
        }
        tagsInitialized = true;
    }

    /**
     * Definit l'appartenance d'un tag (par sa cle, p.ex. "wool" pour {@code Tag.WOOL}).
     */
    public static void defineTag(String key, Material... members) {
        TAG_MEMBERS.put(key, Set.of(members));
    }

    /**
     * Enregistre dans {@link TagRegistry} un faux tag custom sous la cle exacte donnee.
     */
    public static Tag<Material> registerCustomTag(String key, Material... members) {
        ensureTagsInitialized();
        FakeTag tag = new FakeTag(new NamespacedKey("zauctionhouse", key));
        defineTag(key, members);
        TagRegistry.register(key, tag);
        return tag;
    }

    /**
     * Remplace le tag "blocks" de production (qui exige un serveur) par un faux tag.
     */
    public static Tag<Material> overrideBlocksTag(Material... members) {
        return registerCustomTag("blocks", members);
    }

    public static boolean isMember(String key, Material material) {
        return TAG_MEMBERS.getOrDefault(key, Set.of()).contains(material);
    }

    /**
     * Tag dont l'appartenance est lue dynamiquement dans {@link #TAG_MEMBERS}.
     */
    public record FakeTag(NamespacedKey namespacedKey) implements Tag<Material> {

        @Override
        public boolean isTagged(Material material) {
            return isMember(this.namespacedKey.getKey(), material);
        }

        @Override
        public Set<Material> getValues() {
            return TAG_MEMBERS.getOrDefault(this.namespacedKey.getKey(), Set.of());
        }

        @Override
        public NamespacedKey getKey() {
            return this.namespacedKey;
        }
    }
}
