package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import org.bukkit.inventory.ItemStack;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Doublure statique de {@link Base64ItemStack}, qui exige un serveur Bukkit pour fonctionner.
 * <p>
 * Convention des charges utiles :
 * <ul>
 *   <li>{@code "payload-N"} se decode en {@code TestItems.diamond(N)} et un ItemStack de N
 *   unites s'encode en {@code "payload-N"} (aller-retour coherent avec
 *   {@link TestItems#encodedPayload(int)}) ;</li>
 *   <li>{@link #BROKEN} se decode en {@code null} (charge illisible, chemin "quarantaine") ;</li>
 *   <li>{@link #EXPLODING} fait LEVER une {@link Error} au decodage (voie NMS historique sur
 *   un serveur remappe), chemin "ceinture et bretelles" de {@code ItemLoaderUtils} ;</li>
 *   <li>toute autre chaine se decode en {@code null}.</li>
 * </ul>
 * A fermer (try-with-resources ou @AfterEach) : un mock statique non ferme fuit dans les tests
 * suivants du meme thread.
 */
public final class Base64Stubs {

    public static final String BROKEN = "broken";
    public static final String EXPLODING = "explodes";
    private static final String PREFIX = "payload-";

    private Base64Stubs() {
    }

    public static MockedStatic<Base64ItemStack> open() {
        MockedStatic<Base64ItemStack> mocked = Mockito.mockStatic(Base64ItemStack.class);
        mocked.when(() -> Base64ItemStack.decode(anyString())).thenAnswer(invocation -> decode(invocation.getArgument(0)));
        mocked.when(() -> Base64ItemStack.encode(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack itemStack = invocation.getArgument(0);
            return PREFIX + itemStack.getAmount();
        });
        return mocked;
    }

    private static ItemStack decode(String payload) {
        if (payload == null || !payload.startsWith(PREFIX)) {
            if (EXPLODING.equals(payload)) throw new NoClassDefFoundError("net.minecraft.world.item.ItemStack");
            return null;
        }
        try {
            return TestItems.diamond(Integer.parseInt(payload.substring(PREFIX.length())));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
