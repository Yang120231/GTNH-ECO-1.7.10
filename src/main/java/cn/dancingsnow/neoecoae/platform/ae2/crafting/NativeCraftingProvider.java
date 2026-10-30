package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.storage.data.IAEStack;
import appeng.container.ContainerNull;
import appeng.util.inv.MEInventoryCrafting;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Real native medium dispatch, retaining GTNH's generic item/fluid crafting table. */
public final class NativeCraftingProvider implements ICraftingProvider, NativeProviderAdapters.BlockingState,
    cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider {

    private final ICraftingMedium medium;
    private final List<IPatternDetails> patterns;

    public static ICraftingProvider wrap(ICraftingMedium medium, List<? extends IPatternDetails> patterns) {
        if (medium instanceof NativeSourceCraftingMedium) return java.util.Objects
            .requireNonNull(((NativeSourceCraftingMedium) medium).sourceCraftingProvider(), "Native ECO provider");
        return new NativeCraftingProvider(medium, patterns);
    }

    public NativeCraftingProvider(ICraftingMedium medium, List<? extends IPatternDetails> patterns) {
        this.medium = java.util.Objects.requireNonNull(medium);
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.unboundedPatternHatch = UNBOUNDED_HATCH_TYPES.get(medium.getClass());
    }

    public ICraftingMedium nativeMedium() {
        return medium;
    }

    @Override
    public cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target neoecoae$getProcessingTarget() {
        return medium instanceof cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider
            ? ((cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider) medium)
                .neoecoae$getProcessingTarget()
            : null;
    }

    @Override
    public java.util.List<cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target> neoecoae$getProcessingTargets() {
        return medium instanceof cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider
            ? ((cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider) medium)
                .neoecoae$getProcessingTargets()
            : java.util.Collections.emptyList();
    }

    @Override
    public int getPatternPriority() {
        int priority = Integer.MIN_VALUE;
        for (var pattern : patterns) if (pattern instanceof NativePatternDetails) priority = Math.max(
            priority,
            ((NativePatternDetails) pattern).nativeDetails()
                .getPriority());
        return priority == Integer.MIN_VALUE ? 0 : priority;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patterns;
    }

    @Override
    public boolean isBusy() {
        return medium.isBusy();
    }

    @Override
    public boolean isBlocking() {
        return medium.getBlockingMode() != ICraftingMedium.BlockingMode.NONE;
    }

    public boolean supportsScaledProcessing() {
        return medium.getClass() == appeng.helpers.DualityInterface.class || isUnboundedPatternHatch();
    }

    /**
     * GT's ME crafting input bus/buffer (and subclasses): never busy, and every push is stored whole in the
     * pattern's internal slot, so one push may carry any number of copies.
     */
    public boolean isUnboundedPatternHatch() {
        return unboundedPatternHatch;
    }

    public boolean isScaledProcessingFullyInserted() {
        if (unboundedPatternHatch) return true;
        return medium.getClass() == appeng.helpers.DualityInterface.class
            && NativeProcessingAccess.isSendBufferEmpty((appeng.helpers.DualityInterface) medium);
    }

    private static final String GT_CRAFTING_INPUT_HATCH = "gregtech.common.tileentities.machines.MTEHatchCraftingInputME";
    private static final ClassValue<Boolean> UNBOUNDED_HATCH_TYPES = new ClassValue<>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass())
                if (GT_CRAFTING_INPUT_HATCH.equals(c.getName())) return true;
            return false;
        }
    };
    private final boolean unboundedPatternHatch;

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputHolder) {
        IPatternDetails physical = pattern instanceof cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern
            ? ((cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern) pattern).providerLookupPattern()
            : pattern;
        if (!(physical instanceof NativePatternDetails) || !patterns.contains(physical)) return false;
        if (physical != pattern && (!supportsScaledProcessing() || !(physical instanceof AEProcessingPattern)))
            return false;
        var nativePattern = (NativePatternDetails) physical;
        if (inputHolder.length != pattern.getInputs().length)
            throw new IllegalArgumentException("Source input dimensions differ from native pattern");
        MEInventoryCrafting table;
        if (pattern instanceof NativeCraftingPattern) {
            table = new MEInventoryCrafting(new ContainerNull(), 3, 3);
            KeyCounter[] copy = new KeyCounter[inputHolder.length];
            for (int i = 0; i < copy.length; i++) copy[i] = inputHolder[i].copy();
            ((NativeCraftingPattern) pattern).fillCraftingGrid(
                copy,
                (slot, stack) -> table.setInventorySlotContents(slot, appeng.util.item.AEItemStack.create(stack)));
            for (var counter : copy)
                if (!counter.isEmpty()) throw new IllegalArgumentException("Unexpected native crafting input surplus");
        } else {
            var stacks = new ArrayList<IAEStack<?>>();
            for (var counter : inputHolder) for (var entry : counter) {
                if (entry.getLongValue() <= 0) throw new IllegalArgumentException("Nonpositive native dispatch input");
                var stack = entry.getKey()
                    .toNative(entry.getLongValue());
                if (stack == null)
                    throw new IllegalArgumentException("Resource has no native dispatch channel: " + entry.getKey());
                stacks.add(stack);
            }
            if (stacks.isEmpty()) return false;
            table = new MEInventoryCrafting(new ContainerNull(), stacks.size(), 1);
            for (int i = 0; i < stacks.size(); i++) table.setInventorySlotContents(i, stacks.get(i));
        }
        var before = new ArrayList<IAEStack<?>>();
        var beforeItems = new ArrayList<ItemStack>();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            var stack = table.getAEStackInSlot(i);
            before.add(stack == null ? null : stack.copy());
            beforeItems.add(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copy(table.getStackInSlot(i)));
        }
        boolean accepted;
        try {
            accepted = medium.pushPattern(nativePattern.nativeDetails(), table);
        } catch (RuntimeException failure) {
            throw new ECOIndeterminateBatchException(
                "Native medium ownership is uncertain after dispatch failure",
                failure);
        }
        if (!accepted) for (int i = 0; i < table.getSizeInventory(); i++) {
            var previous = before.get(i);
            var current = table.getAEStackInSlot(i);
            boolean same = previous == null ? current == null
                : current != null && previous.getStackSize() == current.getStackSize()
                    && java.util.Objects.equals(AEKey.fromNative(previous), AEKey.fromNative(current));
            if (!same || !ItemStack.areItemStacksEqual(beforeItems.get(i), table.getStackInSlot(i)))
                throw new ECOIndeterminateBatchException("Native medium mutated rejected inputs", null);
        }
        if (accepted) for (var counter : inputHolder) counter.clear();
        return accepted;
    }
}
