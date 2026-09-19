package fr.maxlego08.zauctionhouse.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for encoding and decoding ItemStacks to/from Base64 strings.
 * <p>
 * Uses GZIP compression to minimize storage size.
 *
 * @author sya-ri
 * @see <a href="https://github.com/sya-ri/base64-itemstack/tree/master">GitHub Repository</a>
 */
public class Base64ItemStack {

    /**
     * Marqueur de format : charge utile serialisee par le flux Bukkit (serveurs &gt;= 1.20.5).
     */
    static final String MARKER_BUKKIT = "V2:";

    /**
     * Marqueur de format : charge utile serialisee par la voie NMS historique (&lt; 1.20.5).
     */
    static final String MARKER_NBT = "NBT:";

    /**
     * DEPLOIEMENT EN DEUX PHASES, OBLIGATOIRE.
     * <p>
     * Phase 1 (defaut, {@code false}) : ce jar SAIT lire les deux formats mais continue
     * d'ecrire SANS marqueur, pour qu'un noeud reste en version anterieure puisse encore lire
     * ce qu'il ecrit. Des qu'un noeud ecrit "V2:...", un noeud ancien passe cette chaine a
     * {@code Base64.getDecoder().decode()} qui leve IllegalArgumentException.
     * <p>
     * Phase 2 : basculer a {@code true} (config.yml {@code write-itemstack-format-marker}) UNE
     * FOIS que TOUS les noeuds du reseau sont passes en phase 1. Livrer les deux d'un bloc casse
     * le catalogue pendant un redemarrage tournant.
     */
    private static volatile boolean writeFormatMarker = false;

    /**
     * Enables or disables the writing of the format marker.
     *
     * @param value {@code true} to prefix every encoded payload with its format marker
     */
    public static void setWriteFormatMarker(boolean value) {
        writeFormatMarker = value;
    }

    /**
     * @return {@code true} when the format marker is written
     */
    public static boolean isWriteFormatMarker() {
        return writeFormatMarker;
    }

    /**
     * Encodes an ItemStack to a Base64-compressed string.
     * <p>
     * Le format effectivement utilise depend de la version du serveur ; quand le marqueur de
     * format est actif ({@link #setWriteFormatMarker(boolean)}), la charge utile est prefixee
     * par {@link #MARKER_BUKKIT} ou {@link #MARKER_NBT} pour que la DONNEE porte son propre
     * format, et non la version du serveur lecteur (C-029).
     *
     * @param itemStack the ItemStack to encode
     * @return the Base64-encoded string, or {@code null} if encoding fails
     */
    public static String encode(ItemStack itemStack) {

        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            String payload = ItemStackUtils.serializeItemStack(itemStack);
            if (payload == null) return null;
            return writeFormatMarker ? MARKER_NBT + payload : payload;
        }

        String payload = encodeBukkitStream(itemStack);
        if (payload == null) return null;
        return writeFormatMarker ? MARKER_BUKKIT + payload : payload;
    }

    /**
     * Decodes a Base64-compressed string back to an ItemStack.
     *
     * @param data the Base64-encoded string
     * @return the decoded ItemStack, or {@code null} if decoding fails
     */
    public static ItemStack decode(String data) {

        if (data == null) return null;

        // Le format est porte par la DONNEE. L'aiguillage d'origine se faisait sur la version
        // du serveur LECTEUR : dans un cluster 1.20.x + 1.21.x, et meme sur un serveur unique
        // mis a jour a travers le seuil 1.20.5, la meme colonne contient deux encodages
        // mutuellement illisibles et l'item devenait un BARRIER vendu au prix du vendeur (C-029).
        if (data.startsWith(MARKER_BUKKIT)) {
            return decodeBukkitStream(data.substring(MARKER_BUKKIT.length()));
        }
        if (data.startsWith(MARKER_NBT)) {
            return ItemStackUtils.safeDeserializeItemStack(data.substring(MARKER_NBT.length()));
        }

        // Lignes historiques, sans marqueur : on retombe sur l'heuristique de version.
        // Un echec rend null, ce que la quarantaine du chargement traduit en annonce NON
        // PUBLIEE au lieu d'un BARRIER achetable.
        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.safeDeserializeItemStack(data);
        }
        return decodeBukkitStream(data);
    }

    /**
     * Encodes an ItemStack through the Bukkit object stream, without any version dispatch
     * and without any format marker.
     *
     * @param itemStack the ItemStack to encode
     * @return the Base64-encoded payload, or {@code null} if encoding fails
     */
    static String encodeBukkitStream(ItemStack itemStack) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            ObjectOutputStream objectOutputStream = new BukkitObjectOutputStream(gzipOutputStream);
            objectOutputStream.writeObject(itemStack);
            objectOutputStream.close();
            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        } catch (Exception exception) {
            // Elargi de IOException a Exception : une NotSerializableException ou une
            // RuntimeException levee par la serialisation d'un meta custom ne doit jamais
            // s'echapper vers l'appelant, qui inscrirait alors une ligne sans contenu.
            Bukkit.getLogger().log(Level.SEVERE,
                    "[zAuctionHouse] Unable to encode an ItemStack", exception);
            return null;
        }
    }

    /**
     * Decodes a Bukkit object stream payload, without any version dispatch.
     * <p>
     * Portee paquet a dessein : {@code ItemStackUtils.safeDeserializeItemStack} l'appelle
     * DIRECTEMENT comme repli, pour que ce repli ne puisse jamais re-entrer dans
     * {@link #decode(String)} (recursion mutuelle infinie, C-054).
     *
     * @param data the Base64-encoded payload
     * @return the decoded ItemStack, or {@code null} if decoding fails
     */
    static ItemStack decodeBukkitStream(String data) {

        if (data == null || data.isEmpty()) return null;

        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
            ObjectInputStream objectInputStream = new BukkitObjectInputStream(gzipInputStream);
            ItemStack item = (ItemStack) objectInputStream.readObject();
            objectInputStream.close();
            return item;
        } catch (Exception exception) {
            // Elargi de (IOException | ClassNotFoundException) a Exception :
            // Base64.getDecoder().decode leve IllegalArgumentException sur une charge
            // non-Base64 et readObject leve ClassCastException ; aucune des deux n'etait
            // rattrapee et toutes deux s'echappaient du .map() de ItemLoaderUtils,
            // avortant TOUT le lot de chargement.
            // Message seul, sans trace : sur un catalogue historique illisible ce chemin est
            // emprunte une fois PAR LIGNE, la trace complete noierait la console.
            Bukkit.getLogger().log(Level.WARNING,
                    "[zAuctionHouse] Unable to decode an ItemStack payload: "
                            + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return null;
        }
    }

}
