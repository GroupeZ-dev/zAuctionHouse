package fr.maxlego08.zauctionhouse.api.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;
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
     * Encodes an ItemStack to a Base64-compressed string.
     *
     * @param itemStack the ItemStack to encode
     * @return the Base64-encoded string, or {@code null} if encoding fails
     */
    public static String encode(ItemStack itemStack) {

        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.serializeItemStack(itemStack);
        }

        Base64.Encoder encoder = Base64.getEncoder();
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            ObjectOutputStream objectOutputStream = new BukkitObjectOutputStream(gzipOutputStream);
            objectOutputStream.writeObject(itemStack);
            objectOutputStream.close();
            return encoder.encodeToString(byteArrayOutputStream.toByteArray());
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    /**
     * Decodes a Base64-compressed string back to an ItemStack.
     *
     * @param data the Base64-encoded string
     * @return the decoded ItemStack, or {@code null} if decoding fails
     */
    public static ItemStack decode(String data) {

        if (!NmsVersion.getCurrentVersion().isAttributItemStack()) {
            return ItemStackUtils.safeDeserializeItemStack(data);
        }

        Base64.Decoder decoder = Base64.getDecoder();
        try {
            byte[] bytes = decoder.decode(data);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
            ObjectInputStream objectInputStream = new BukkitObjectInputStream(gzipInputStream);
            ItemStack item = (ItemStack) objectInputStream.readObject();
            objectInputStream.close();
            return item;
        } catch (IOException | ClassNotFoundException exception) {
            exception.printStackTrace();
            return null;
        }
    }

}