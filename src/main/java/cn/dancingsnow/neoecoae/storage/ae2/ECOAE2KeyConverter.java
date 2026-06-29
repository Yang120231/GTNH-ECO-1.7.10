package cn.dancingsnow.neoecoae.storage.ae2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
        UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(itemStack.getItem());
        String name = id == null ? Item.itemRegistry.getNameForObject(itemStack.getItem()) : id.modId + ":" + id.name;
        return ECOStorageKey.item(name, itemStack.getItemDamage(), encodeTag(itemStack.getTagCompound()));
    }

    private static ECOStorageKey fluidKey(IAEFluidStack stack) {
        FluidStack fluidStack = stack.getFluidStack();
        String name = FluidRegistry.getFluidName(fluidStack);
        return ECOStorageKey.fluid(name, encodeTag(fluidStack.tag));
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
