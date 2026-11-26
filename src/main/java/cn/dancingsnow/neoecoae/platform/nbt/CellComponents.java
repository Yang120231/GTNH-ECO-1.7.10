package cn.dancingsnow.neoecoae.platform.nbt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

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

    /**
     * Indexed, write-through view of the existing finite-cell list. Published rows and their key tags are immutable.
     */
    public static final class Contents {

        private final Object2IntOpenHashMap<AEKey> slots = new Object2IntOpenHashMap<>();
        private ListTag entries = new ListTag();
        private NBTBase source;

        // This is still a vanilla TAG_List on disk and over the wire. The flag belongs to the list, not one
        // handler: an independent reader of the same stack also freezes the version it decoded.
        private static final class DriveList extends NBTTagList {

            private boolean shared;
        }

        public Contents(ItemStack stack) {
            this(stack, null);
        }

        private Contents(ItemStack stack, List<GenericStack> decoded) {
            source = contentsTag(stack);
            freeze();
            slots.defaultReturnValue(-1);
            for (Tag raw : storedEntries(stack)) {
                CompoundTag row = (CompoundTag) raw;
                if (!row.contains("key", Tag.TAG_COMPOUND) || !row.contains("amount", Tag.TAG_LONG))
                    throw new IllegalStateException("Incomplete finite-cell entry; preserving original item NBT");
                CompoundTag id = row.getCompound("key");
                AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, id);
                if (key == null)
                    throw new IllegalStateException("Unresolved finite-cell key; preserving original item NBT");
                long amount = row.getLong("amount");
                if (amount < 0) throw new IllegalStateException("Negative finite-cell amount");
                if (decoded != null) decoded.add(new GenericStack(key, amount));
                if (amount == 0) continue;
                int index = slots.getInt(key);
                if (index < 0) {
                    slots.put(key, entries.size());
                    entries.add(row(id.copy(), amount));
                } else {
                    CompoundTag previous = entries.getCompound(index);
                    long old = previous.getLong("amount");
                    if (amount > Long.MAX_VALUE - old) throw new IllegalStateException("Finite-cell amount overflows");
                    previous.putLong("amount", old + amount);
                }
            }
        }

        private static NBTBase contentsTag(ItemStack stack) {
            if (!stack.hasTagCompound()) return null;
            var root = stack.getTagCompound();
            NBTBase contents = root.getTag(CONTENTS);
            if (contents != null) return contents;
            return "domain_member".equals(root.getString("ECOStorageMode")) ? root.getTag("ECOStorageMode")
                : root.getTag("ECOStorage");
        }

        /** Native IO ports and alternate channel handlers can open the same stack independently. */
        public boolean isCurrent(ItemStack stack) {
            return source == contentsTag(stack);
        }

        public Object2LongMap<AEKey> amounts() {
            Object2LongMap<AEKey> amounts = new Object2LongOpenHashMap<>(slots.size());
            for (var slot : slots.object2IntEntrySet()) amounts.put(
                slot.getKey(),
                entries.getCompound(slot.getIntValue())
                    .getLong("amount"));
            return amounts;
        }

        /** A consumer may now retain the stack's NBT. The next edit forks its list before touching it. */
        public void freeze() {
            if (source instanceof DriveList list) list.shared = true;
        }

        /** Commit an absolute quantity. Only a newly admitted type needs a prepared key tag. */
        public void set(ItemStack stack, AEKey key, long amount, CompoundTag newKey, boolean owned) {
            int index = slots.getInt(key);
            CompoundTag next = amount == 0 ? null
                : row(
                    index < 0 ? newKey
                        : entries.getCompound(index)
                            .getCompound("key"),
                    amount);
            ListTag list = entries;
            boolean fork = !owned || !(source instanceof DriveList live)
                || live.shared
                || source != entries.nativeTag();
            if (fork) {
                list = owned ? new ListTag(new DriveList()) : new ListTag();
                for (int i = 0; i < entries.size(); i++) list.add(entries.getCompound(i));
            }
            if (next == null) {
                if (index < 0) return;
                if (list.size() == 1) {
                    clearStacks(stack);
                    source = contentsTag(stack);
                    entries = new ListTag();
                    slots.clear();
                    return;
                }
                list.nativeTag()
                    .removeTag(index);
            } else if (index < 0) list.add(next);
            else list.nativeTag()
                .func_150304_a(index, next.nativeTag());
            if (fork) stack.setTagCompound(withContents(stack, list).nativeTag());
            if (next == null) {
                slots.removeInt(key);
                for (var slot : slots.object2IntEntrySet())
                    if (slot.getIntValue() > index) slot.setValue(slot.getIntValue() - 1);
            } else if (index < 0) slots.put(key, list.size() - 1);
            entries = list;
            source = list.nativeTag();
        }
    }

    public static CompoundTag encodeKey(AEKey key) {
        CompoundTag id = key.toTagGeneric(RegistryAccess.EMPTY);
        if (id == null || !key.equals(AEKey.fromTagGeneric(RegistryAccess.EMPTY, id)))
            throw new IllegalArgumentException("Finite-cell key does not round-trip through its registered codec");
        return id.copy();
    }

    private static CompoundTag row(CompoundTag id, long amount) {
        CompoundTag row = new CompoundTag();
        row.put("key", id);
        row.putLong("amount", amount);
        return row;
    }

    private static ListTag storedEntries(ItemStack stack) {
        CompoundTag root = root(stack);
        Tag rawList = root.get(CONTENTS);
        if (rawList == null && root.contains("ECOStorage")) {
            if ("domain_member".equals(root.getString("ECOStorageMode"))) return new ListTag();
            if (!root.contains("ECOStorage", Tag.TAG_COMPOUND))
                throw new IllegalStateException("Invalid beta cell storage; preserving original item NBT");
            rawList = cn.dancingsnow.neoecoae.impl.storage.legacy.BetaStorageReader
                .entries(root.getCompound("ECOStorage"), true);
        }
        if (rawList != null && (!(rawList instanceof ListTag)
            || !((ListTag) rawList).isEmpty() && ((ListTag) rawList).getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalStateException("Invalid finite-cell inventory tag; preserving original item NBT");
        return rawList == null ? new ListTag() : (ListTag) rawList;
    }

    public static List<GenericStack> readStacks(ItemStack stack) {
        List<GenericStack> result = new ArrayList<>();
        new Contents(stack, result);
        return java.util.Collections.unmodifiableList(result);
    }

    public static void writeStacks(ItemStack stack, List<GenericStack> values) {
        ListTag entries = new ListTag();
        for (GenericStack value : values) entries.add(row(encodeKey(value.what()), value.amount()));
        stack.setTagCompound(withContents(stack, entries).nativeTag());
    }

    public static void clearStacks(ItemStack stack) {
        CompoundTag root = withContents(stack, null);
        stack.setTagCompound(root.isEmpty() ? null : root.nativeTag());
    }

    /** Copies every other component; the replaced contents list can be large and is never copied. */
    private static CompoundTag withContents(ItemStack stack, ListTag entries) {
        net.minecraft.nbt.NBTTagCompound fresh = new net.minecraft.nbt.NBTTagCompound();
        if (stack.hasTagCompound()) {
            net.minecraft.nbt.NBTTagCompound old = stack.getTagCompound();
            for (Object name : old.func_150296_c()) if (!CONTENTS.equals(name)) fresh.setTag(
                (String) name,
                old.getTag((String) name)
                    .copy());
        }
        // An explicitly empty inventory is authoritative over the retained beta snapshot.
        if (entries != null || fresh.hasKey("ECOStorage"))
            fresh.setTag(CONTENTS, entries == null ? new net.minecraft.nbt.NBTTagList() : entries.nativeTag());
        return new CompoundTag(fresh);
    }

    public static CustomData readCustomData(ItemStack stack) {
        CompoundTag root = root(stack);
        return root.contains(CUSTOM, Tag.TAG_COMPOUND) ? CustomData.view(root.getCompound(CUSTOM))
            : root.contains("ECOStorageMode") || root.contains("ECODiskId")
                ? CustomData.view(cn.dancingsnow.neoecoae.impl.storage.legacy.BetaStorageReader.customData(root))
                : CustomData.EMPTY;
    }

    public static CustomData readCustomDataOrNull(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root.contains(CUSTOM, Tag.TAG_COMPOUND)) return CustomData.view(root.getCompound(CUSTOM));
        CompoundTag beta = cn.dancingsnow.neoecoae.impl.storage.legacy.BetaStorageReader.customData(root);
        return beta.isEmpty() ? null : CustomData.view(beta);
    }

    public static void writeCustomData(ItemStack stack, CustomData value) {
        CompoundTag root = root(stack).copy();
        root.put(CUSTOM, value.copyTag());
        stack.setTagCompound(root.nativeTag());
    }

    public static void clearCustomData(ItemStack stack) {
        CompoundTag root = root(stack).copy();
        if (root.contains("ECOStorageMode") || root.contains("ECODiskId")) root.put(CUSTOM, new CompoundTag());
        else root.remove(CUSTOM);
        stack.setTagCompound(root.isEmpty() ? null : root.nativeTag());
    }
}
