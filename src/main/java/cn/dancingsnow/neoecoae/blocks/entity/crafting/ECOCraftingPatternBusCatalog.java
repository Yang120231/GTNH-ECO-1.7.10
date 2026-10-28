package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;

/**
 * Decoded pattern catalog of one bus. Only this bus's own slots are published to AE2: a Network Switch group
 * shares workers for dispatch, never pattern advertising, so every pattern has exactly one medium per bus that
 * physically holds it. Each slot is decoded once and kept until its stack is replaced, the slot is reported
 * changed, the world changes or recipes are reloaded.
 */
final class ECOCraftingPatternBusCatalog {

    /** Decodes one stored pattern stack; returns {@code null} for stacks that do not encode a usable pattern. */
    interface Decoder {

        @Nullable
        NativePatternDetails decode(ItemStack stack, World world);
    }

    private final AppEngInternalInventory inventory;
    private final Decoder decoder;
    private final ItemStack[] decodedFrom;
    private final NativePatternDetails[] decoded;
    private final BitSet dirty;
    @Nullable
    private World decodedWorld;
    private long decodedGeneration;
    private List<IPatternDetails> published = Collections.emptyList();
    private int decodeCount;

    ECOCraftingPatternBusCatalog(AppEngInternalInventory inventory, Decoder decoder) {
        this.inventory = inventory;
        this.decoder = decoder;
        int slots = inventory.size();
        decodedFrom = new ItemStack[slots];
        decoded = new NativePatternDetails[slots];
        dirty = new BitSet(slots);
        dirty.set(0, slots);
        decodedGeneration = PatternRuntime.reloadGeneration();
    }

    /** Marks one slot for re-decoding on the next read. */
    void invalidate(int slot) {
        if (slot >= 0 && slot < decoded.length) dirty.set(slot);
    }

    void invalidateAll() {
        dirty.set(0, decoded.length);
    }

    /** Patterns stored in this bus, in slot order. The list is immutable and reused until a slot changes. */
    List<IPatternDetails> patterns(@Nullable World world) {
        if (world == null) return Collections.emptyList();
        refresh(world);
        return published;
    }

    /**
     * Cached details for a native pattern pushed back by AE2. AE2 keys mediums by pattern equality, so the pushed
     * instance may be another bus's copy of the same pattern item; equality is accepted after identity.
     */
    @Nullable
    NativePatternDetails find(@Nullable World world, ICraftingPatternDetails nativePattern) {
        if (world == null || nativePattern == null) return null;
        refresh(world);
        for (NativePatternDetails details : decoded)
            if (details != null && details.nativeDetails() == nativePattern) return details;
        for (NativePatternDetails details : decoded) if (details != null && details.nativeDetails()
            .equals(nativePattern)) return details;
        return null;
    }

    /** Number of slot decodes performed so far; lets tests prove unchanged slots are not decoded again. */
    int decodeCount() {
        return decodeCount;
    }

    private void refresh(World world) {
        long generation = PatternRuntime.reloadGeneration();
        if (world != decodedWorld || generation != decodedGeneration) {
            decodedWorld = world;
            decodedGeneration = generation;
            invalidateAll();
        }
        boolean changed = false;
        for (int slot = 0; slot < decoded.length; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            // Slot writes report the slot; a replaced stack reference (NBT load, markDirty-only callers) is caught
            // here.
            if (!dirty.get(slot) && stack == decodedFrom[slot]) continue;
            dirty.clear(slot);
            NativePatternDetails previous = decoded[slot];
            decodedFrom[slot] = stack;
            decoded[slot] = stack == null ? null : decodeSafely(slot, stack, world);
            if (previous != null || decoded[slot] != null) changed = true;
        }
        if (changed) {
            List<IPatternDetails> next = new ArrayList<>();
            for (NativePatternDetails details : decoded) if (details != null) next.add(details);
            published = Collections.unmodifiableList(next);
        }
    }

    @Nullable
    private NativePatternDetails decodeSafely(int slot, ItemStack stack, World world) {
        decodeCount++;
        try {
            return decoder.decode(stack, world);
        } catch (RuntimeException invalid) {
            cn.dancingsnow.neoecoae.NeoECOAE.LOG.warn("Invalid ECO bus pattern in slot {}", slot, invalid);
            return null;
        }
    }
}
