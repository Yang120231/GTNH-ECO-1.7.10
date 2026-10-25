package cn.dancingsnow.neoecoae.platform.nbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;

public final class CellComponents {

    private static final String CONTENTS = "neoecoae_inventory", CUSTOM = "neoecoae_custom_data";

    private CellComponents() {}

    public static void writeCustomName(ItemStack stack, cn.dancingsnow.neoecoae.platform.minecraft.Component name) {
        stack.setStackDisplayName(
            name.nativeText()
                .getFormattedText());
    }

    public static void writeCustomName(net.minecraftforge.fluids.FluidStack stack,
        cn.dancingsnow.neoecoae.platform.minecraft.Component name) {
        if (stack.tag == null) stack.tag = new net.minecraft.nbt.NBTTagCompound();
        stack.tag.setString("neoecoae_display_name", name.toJson());
    }

    public static void writeCustomName(cn.dancingsnow.neoecoae.platform.fluid.FluidStack stack,
        cn.dancingsnow.neoecoae.platform.minecraft.Component name) {
        var tag = stack.copyTag();
        if (tag == null) tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setString("neoecoae_display_name", name.toJson());
        stack.setTag(tag);
    }

    private static CompoundTag root(ItemStack stack) {
        return stack.hasTagCompound() ? new CompoundTag(stack.getTagCompound()) : new CompoundTag();
    }

    public static List<GenericStack> readStacks(ItemStack stack) {
        List<GenericStack> result = new ArrayList<>();
        CompoundTag root = root(stack);
        Tag rawList = root.get(CONTENTS);
        if (rawList != null && (!(rawList instanceof ListTag)
            || !((ListTag) rawList).isEmpty() && ((ListTag) rawList).getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalStateException("Invalid finite-cell inventory tag; preserving original item NBT");
        ListTag list = rawList == null ? new ListTag() : (ListTag) rawList;
        for (Tag raw : list) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.contains("key", Tag.TAG_COMPOUND) || !entry.contains("amount", Tag.TAG_LONG))
                throw new IllegalStateException("Incomplete finite-cell entry; preserving original item NBT");
            AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, entry.getCompound("key"));
            if (key == null)
                throw new IllegalStateException("Unresolved finite-cell key; preserving the original item NBT");
            long amount = entry.getLong("amount");
            if (amount < 0) throw new IllegalStateException("Negative finite-cell amount");
            result.add(new GenericStack(key, amount));
        }
        return Collections.unmodifiableList(result);
    }

    public static void writeStacks(ItemStack stack, List<GenericStack> values) {
        ListTag entries = new ListTag();
        for (GenericStack value : values) {
            CompoundTag entry = new CompoundTag();
            entry.put(
                "key",
                value.what()
                    .toTagGeneric(RegistryAccess.EMPTY));
            entry.putLong("amount", value.amount());
            entries.add(entry);
        }
        CompoundTag root = root(stack).copy();
        root.put(CONTENTS, entries);
        stack.setTagCompound(root.nativeTag());
    }

    public static void clearStacks(ItemStack stack) {
        CompoundTag root = root(stack).copy();
        root.remove(CONTENTS);
        stack.setTagCompound(root.isEmpty() ? null : root.nativeTag());
    }

    public static CustomData readCustomData(ItemStack stack) {
        CompoundTag root = root(stack);
        return root.contains(CUSTOM, Tag.TAG_COMPOUND) ? CustomData.view(root.getCompound(CUSTOM)) : CustomData.EMPTY;
    }

    public static CustomData readCustomDataOrNull(ItemStack stack) {
        CompoundTag root = root(stack);
        return root.contains(CUSTOM, Tag.TAG_COMPOUND) ? CustomData.view(root.getCompound(CUSTOM)) : null;
    }

    public static void writeCustomData(ItemStack stack, CustomData value) {
        CompoundTag root = root(stack).copy();
        root.put(CUSTOM, value.copyTag());
        stack.setTagCompound(root.nativeTag());
    }

    public static void clearCustomData(ItemStack stack) {
        CompoundTag root = root(stack).copy();
        root.remove(CUSTOM);
        stack.setTagCompound(root.isEmpty() ? null : root.nativeTag());
    }
}
