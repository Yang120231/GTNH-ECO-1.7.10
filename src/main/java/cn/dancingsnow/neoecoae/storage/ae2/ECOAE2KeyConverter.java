package cn.dancingsnow.neoecoae.storage.ae2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public final class ECOAE2KeyConverter {

    private static final int KEY_CACHE_LIMIT = 8192;
    private static final Map<String, ECOStorageKey> KEY_CACHE = new LinkedHashMap<String, ECOStorageKey>(
        KEY_CACHE_LIMIT,
        0.75F,
        true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ECOStorageKey> eldest) {
            return this.size() > KEY_CACHE_LIMIT;
        }
    };
    private static final Map<Item, String> ITEM_NAME_CACHE = new LinkedHashMap<Item, String>(512, 0.75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Item, String> eldest) {
            return this.size() > 512;
        }
    };

    private ECOAE2KeyConverter() {}

    public static ECOStorageKey toKey(IAEStack stack) {
        if (stack instanceof IAEItemStack) {
            return itemKey((IAEItemStack) stack);
        }
        if (stack instanceof IAEFluidStack) {
            return fluidKey((IAEFluidStack) stack);
        }
        throw new IllegalArgumentException("Unsupported AE stack type: " + stack);
    }

    /**
     * Resolves an AE stack to an existing storage key. NBT compounds are semantically unordered, but their
     * compressed representation is not guaranteed to remain byte-identical after an AE network round trip.
     */
    public static ECOStorageKey toExistingKey(IAEStack stack, Iterable<ECOStorageKey> existingKeys) {
        ECOStorageKey requested = toKey(stack);
        if (existingKeys == null) {
            return requested;
        }
        if (existingKeys instanceof Collection && ((Collection<?>) existingKeys).contains(requested)) {
            return requested;
        }
        if (!(existingKeys instanceof Collection)) {
            for (ECOStorageKey existing : existingKeys) {
                if (requested.equals(existing)) {
                    return existing;
                }
            }
        }
        for (ECOStorageKey existing : existingKeys) {
            if (sameSemanticKey(requested, existing)) {
                return existing;
            }
        }
        return requested;
    }

    private static boolean sameSemanticKey(ECOStorageKey left, ECOStorageKey right) {
        if (left == null || right == null
            || left.getMetadata() != right.getMetadata()
            || !left.getChannel()
                .equals(right.getChannel())
            || !left.getIdentity()
                .equals(right.getIdentity())) {
            return false;
        }
        NBTTagCompound leftTag = decodeTag(left.getVariant());
        NBTTagCompound rightTag = decodeTag(right.getVariant());
        return leftTag == null ? rightTag == null : leftTag.equals(rightTag);
    }

    public static IAEItemStack toItemStack(ECOStorageKey key, long amount) {
        if (key == null || !key.isItem()) {
            return null;
        }
        Item item = (Item) Item.itemRegistry.getObject(key.getIdentity());
        if (item == null) {
            return null;
        }
        ItemStack stack = new ItemStack(item, 1, key.getMetadata());
        NBTTagCompound tag = decodeTag(key.getVariant());
        if (tag != null) {
            stack.setTagCompound(tag);
        }
        IAEItemStack aeStack = AEApi.instance()
            .storage()
            .createItemStack(stack);
        return aeStack == null ? null : aeStack.setStackSize(amount);
    }

    public static IAEFluidStack toFluidStack(ECOStorageKey key, long amount) {
        if (key == null || !key.isFluid()) {
            return null;
        }
        FluidStack stack = FluidRegistry.getFluidStack(key.getIdentity(), 1);
        if (stack == null) {
            return null;
        }
        NBTTagCompound tag = decodeTag(key.getVariant());
        if (tag != null) {
            stack.tag = tag;
        }
        IAEFluidStack aeStack = AEApi.instance()
            .storage()
            .createFluidStack(stack);
        return aeStack == null ? null : aeStack.setStackSize(amount);
    }

    private static ECOStorageKey itemKey(IAEItemStack stack) {
        ItemStack itemStack = stack.getItemStack();
        String name = itemName(itemStack.getItem());
        int metadata = itemStack.getItemDamage();
        NBTTagCompound tag = itemStack.getTagCompound();
        String cacheKey = "i|" + name + "|" + metadata + "|" + tagSignature(tag);
        ECOStorageKey cached = cachedKey(cacheKey);
        if (cached != null) {
            return cached;
        }
        return cacheKey(cacheKey, ECOStorageKey.item(name, metadata, encodeTag(tag)));
    }

    private static ECOStorageKey fluidKey(IAEFluidStack stack) {
        FluidStack fluidStack = stack.getFluidStack();
        String name = FluidRegistry.getFluidName(fluidStack);
        NBTTagCompound tag = fluidStack.tag;
        String cacheKey = "f|" + name + "|" + tagSignature(tag);
        ECOStorageKey cached = cachedKey(cacheKey);
        if (cached != null) {
            return cached;
        }
        return cacheKey(cacheKey, ECOStorageKey.fluid(name, encodeTag(tag)));
    }

    private static String itemName(Item item) {
        synchronized (ITEM_NAME_CACHE) {
            String cached = ITEM_NAME_CACHE.get(item);
            if (cached != null) {
                return cached;
            }
            UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item);
            String name = id == null ? Item.itemRegistry.getNameForObject(item) : id.modId + ":" + id.name;
            ITEM_NAME_CACHE.put(item, name);
            return name;
        }
    }

    private static ECOStorageKey cachedKey(String cacheKey) {
        synchronized (KEY_CACHE) {
            return KEY_CACHE.get(cacheKey);
        }
    }

    private static ECOStorageKey cacheKey(String cacheKey, ECOStorageKey key) {
        synchronized (KEY_CACHE) {
            KEY_CACHE.put(cacheKey, key);
        }
        return key;
    }

    private static String tagSignature(NBTTagCompound tag) {
        return tag == null || tag.hasNoTags() ? "" : tag.toString();
    }

    private static String encodeTag(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) {
            return "";
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(tag, output);
            return javax.xml.bind.DatatypeConverter.printBase64Binary(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to encode storage key tag", e);
        }
    }

    private static NBTTagCompound decodeTag(String encoded) {
        if (encoded == null || encoded.length() == 0) {
            return null;
        }
        try {
            byte[] bytes = javax.xml.bind.DatatypeConverter.parseBase64Binary(encoded);
            return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }
}
