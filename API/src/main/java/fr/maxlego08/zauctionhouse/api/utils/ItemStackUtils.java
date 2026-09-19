package fr.maxlego08.zauctionhouse.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

public class ItemStackUtils {

    private static final NmsVersion NMS_VERSION = NmsVersion.nmsVersion;

    /**
     * Change {@link ItemStack} to {@link String}
     *
     * @param paramItemStack the ItemStack to serialize
     * @return {@link String}, or {@code null} when the legacy NMS path is unusable
     */
    public static String serializeItemStack(ItemStack paramItemStack) {

        if (paramItemStack == null) return "null";

        try {
            Class<?> localClass = EnumReflectionItemStack.NBTTAGCOMPOUND.getClassz();
            Constructor<?> localConstructor = localClass.getConstructor();
            Object localObject1 = localConstructor.newInstance();
            Object localObject2 = EnumReflectionItemStack.CRAFTITEMSTACK.getClassz().getMethod("asNMSCopy", new Class[]{ItemStack.class}).invoke(null, paramItemStack);

            EnumReflectionItemStack.ITEMSTACK.getClassz().getMethod("b", new Class[]{localClass}).invoke(localObject2, localObject1);

            ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
            EnumReflectionItemStack.NBTCOMPRESSEDSTREAMTOOLS.getClassz().getMethod("a", new Class[]{localClass, OutputStream.class}).invoke(null, localObject1, localByteArrayOutputStream);
            return Base64.encode(localByteArrayOutputStream.toByteArray());
        } catch (Throwable throwable) {
            // Le code d'origine dereferencait `localByteArrayOutputStream` APRES son propre
            // catch : NPE garantie des que la reflexion echoue (getClassz() fait
            // nmsPackage.split(",")[3], ce qui explose sur Paper >= 1.20.6).
            // Et surtout : NE JAMAIS rendre Base64.encode(new byte[0]). Cette chaine vide
            // partait telle quelle dans auction_items.itemstack, l'INSERT REUSSISSAIT et le
            // vendeur perdait son item sans la moindre trace. On rend null : la vente doit
            // echouer bruyamment (garde a poser dans le chemin de vente, cf. C-067).
            Bukkit.getLogger().log(Level.SEVERE,
                    "[zAuctionHouse] Unable to serialize an ItemStack through the legacy NBT path", throwable);
            return null;
        }
    }


    /**
     * Deserialise une charge utile NBT historique, en retombant sur le flux Bukkit si la voie
     * NMS echoue.
     *
     * @param paramString the payload to deserialize
     * @return the decoded ItemStack, or {@code null} when the payload is unreadable
     */
    public static ItemStack safeDeserializeItemStack(String paramString) {
        try {
            return tryDeserializeItemStack(paramString);
        } catch (Throwable throwable) {
            // RECURSION MUTUELLE INFINIE corrigee (C-054) : le repli appelait
            // Base64ItemStack.decode(), qui sur un serveur < 1.20.5 rappelle cette meme
            // methode -> StackOverflowError, qu'aucun catch(Exception) du projet ne rattrape,
            // et qui avortait AuctionLoader.loadItems() (100 % synchrone, appele nu depuis
            // ZAuctionPlugin.onEnable()). On appelle desormais le decodeur Bukkit DIRECT.
            return Base64ItemStack.decodeBukkitStream(paramString);
        }
    }

    public static ItemStack tryDeserializeItemStack(String paramString) throws Exception {
        ByteArrayInputStream localByteArrayInputStream = new ByteArrayInputStream(Base64.decode(paramString));
        Class<?> localClass1 = EnumReflectionItemStack.NBTTAGCOMPOUND.getClassz();
        Class<?> localClass2 = EnumReflectionItemStack.ITEMSTACK.getClassz();
        Object localObject1;
        ItemStack localItemStack;
        Object localObject2;

        DataInputStream datainputstream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(localByteArrayInputStream)));
        localObject1 = EnumReflectionItemStack.NBTCOMPRESSEDSTREAMTOOLS.getClassz().getMethod("a", new Class[]{DataInput.class}).invoke(null, datainputstream);
        localObject2 = localClass2.getMethod("a", new Class[]{localClass1}).invoke(null, localObject1);

        localItemStack = (ItemStack) EnumReflectionItemStack.CRAFTITEMSTACK.getClassz().getMethod("asBukkitCopy", new Class[]{localClass2}).invoke(null, new Object[]{localObject2});
        return localItemStack;
    }

    public enum EnumReflectionItemStack {

        ITEMSTACK("ItemStack", "net.minecraft.world.item.ItemStack"),

        CRAFTITEMSTACK("inventory.CraftItemStack", true),

        NBTCOMPRESSEDSTREAMTOOLS("NBTCompressedStreamTools", "net.minecraft.nbt.NBTCompressedStreamTools"),

        NBTTAGCOMPOUND("NBTTagCompound", "net.minecraft.nbt.NBTTagCompound"),

        ;

        private final String oldClassName;
        private final String newClassName;
        private final boolean isBukkit;

        EnumReflectionItemStack(String oldClassName, String newClassName, boolean isBukkit) {
            this.oldClassName = oldClassName;
            this.newClassName = newClassName;
            this.isBukkit = isBukkit;
        }

        EnumReflectionItemStack(String oldClassName, String newClassName) {
            this(oldClassName, newClassName, false);
        }

        EnumReflectionItemStack(String oldClassName, boolean isBukkit) {
            this(oldClassName, null, isBukkit);
        }

        public Class<?> getClassz() {
            String nmsPackage = Bukkit.getServer().getClass().getPackage().getName();
            String nmsVersion = nmsPackage.replace(".", ",").split(",")[3];
            String var3 = NMS_VERSION.isNewNMSVersion() ? this.isBukkit ? "org.bukkit.craftbukkit." + nmsVersion + "." + this.oldClassName : this.newClassName : (this.isBukkit ? "org.bukkit.craftbukkit." : "net.minecraft.server.") + nmsVersion + "." + this.oldClassName;
            Class<?> localClass = null;
            try {
                localClass = Class.forName(var3);
            } catch (ClassNotFoundException localClassNotFoundException) {
                localClassNotFoundException.printStackTrace();
            }
            return localClass;
        }
    }
}
