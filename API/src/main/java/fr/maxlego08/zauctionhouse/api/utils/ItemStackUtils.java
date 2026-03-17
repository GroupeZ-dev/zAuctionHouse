package fr.maxlego08.zauctionhouse.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.zip.GZIPInputStream;

public class ItemStackUtils {

    private static final NmsVersion NMS_VERSION = NmsVersion.nmsVersion;

    /**
     * Change {@link ItemStack} to {@link String}
     *
     * @return {@link String}
     */
    public static String serializeItemStack(ItemStack paramItemStack) {

        if (paramItemStack == null) return "null";

        ByteArrayOutputStream localByteArrayOutputStream = null;
        try {
            Class<?> localClass = EnumReflectionItemStack.NBTTAGCOMPOUND.getClassz();
            Constructor<?> localConstructor = localClass.getConstructor();
            Object localObject1 = localConstructor.newInstance();
            Object localObject2 = EnumReflectionItemStack.CRAFTITEMSTACK.getClassz().getMethod("asNMSCopy", new Class[]{ItemStack.class}).invoke(null, paramItemStack);

            EnumReflectionItemStack.ITEMSTACK.getClassz().getMethod("b", new Class[]{localClass}).invoke(localObject2, localObject1);

            localByteArrayOutputStream = new ByteArrayOutputStream();
            EnumReflectionItemStack.NBTCOMPRESSEDSTREAMTOOLS.getClassz().getMethod("a", new Class[]{localClass, OutputStream.class}).invoke(null, localObject1, localByteArrayOutputStream);
        } catch (Exception localException) {
            localException.printStackTrace();
        }
        return Base64.encode(localByteArrayOutputStream.toByteArray());
    }


    public static ItemStack safeDeserializeItemStack(String paramString) {
        try {
            return tryDeserializeItemStack(paramString);
        } catch (Exception exception) {
            return Base64ItemStack.decode(paramString);
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
