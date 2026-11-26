package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IInterfaceViewable;
import appeng.container.ContainerNull;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.parts.AEBasePart;
import appeng.parts.p2p.PartP2PInterface;
import appeng.util.inv.MEInventoryCrafting;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern;
import cn.dancingsnow.neoecoae.compat.proghatches.ProgrammableHatchCrafting;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Real native medium dispatch, retaining GTNH's generic item/fluid crafting table. */
public final class NativeCraftingProvider implements ICraftingProvider, NativeProviderAdapters.BlockingState,
    cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider {

    private final ICraftingMedium medium;
    private final List<IPatternDetails> patterns;
    private final @Nullable NativePatternDetails publishedPattern;
    private final boolean usesAssemblerBatchSize;
    private final boolean preserveInputSlots;
    private final boolean scalesProcessingInputs;
    private boolean assemblerBatchRejected;

    private static final String ASSEMBLER_MATRIX = "com.science.gtnl.common.machine.multiblock.AssemblerMatrix";
    private static final String ME_CHISEL = "com.science.gtnl.common.block.blocks.tile.TileEntityMEChisel";
    private static final String SUPER_CRAFTING_INPUT_HATCH = "com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME";
    private static final String GT_CRAFTING_INPUT_HATCH = "gregtech.common.tileentities.machines.MTEHatchCraftingInputME";
    /**
     * TenkeiTech's super and auto-template input hatches keep every pushed stack whole in long-backed
     * per-pattern / shared stores, exactly like GT's crafting input hatch. The bender and wiremill
     * variants extend the auto-template hatch, so the superclass chain is what has to match.
     */
    private static final String TENKEI_AUTO_TEMPLATE_INPUT_HATCH = "com.tenkeitech.machine.MTEHatchAutoTemplateInputME";
    private static final String TENKEI_SUPER_CRAFTING_INPUT_HATCH = "com.tenkeitech.machine.MTESuperCraftingInputME";
    private static final ClassValue<Boolean> TENKEI_PATTERN_HATCHES = new ClassValue<>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                String name = c.getName();
                if (TENKEI_AUTO_TEMPLATE_INPUT_HATCH.equals(name) || TENKEI_SUPER_CRAFTING_INPUT_HATCH.equals(name))
                    return true;
            }
            return false;
        }
    };
    private static final String GT_INPUT_BUS = "gregtech.api.metatileentity.implementations.MTEHatchInputBus";
    private static final String GT_INPUT_HATCH = "gregtech.api.metatileentity.implementations.MTEHatchInput";
    private static final String GTSWN_ITEM_INPUT = "com.miaokatze.gtswn.common.assembly.MatrixItemInputBus";
    private static final String GTSWN_FLUID_INPUT = "com.miaokatze.gtswn.common.assembly.MatrixFluidInputHatch";
    private static final ClassValue<AssemblerBatchAccess> ASSEMBLER_BATCH_ACCESS = new ClassValue<>() {

        @Override
        protected AssemblerBatchAccess computeValue(Class<?> type) {
            return new AssemblerBatchAccess(
                findBatchMethod(type, "setAssemblerSize", void.class, long.class),
                findBatchMethod(type, "getAssemblerSize", long.class));
        }
    };

    @Nullable
    private static Method findBatchMethod(Class<?> type, String name, Class<?> result, Class<?>... parameters) {
        try {
            Method method = type.getMethod(name, parameters);
            return method.getReturnType() == result ? method : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    public static ICraftingProvider wrap(ICraftingMedium medium, List<? extends IPatternDetails> patterns) {
        if (medium instanceof NativeSourceCraftingMedium source)
            return java.util.Objects.requireNonNull(source.sourceCraftingProvider(), "Native ECO provider");
        return new NativeCraftingProvider(medium, patterns);
    }

    static ICraftingProvider wrap(ICraftingMedium medium, NativePatternDetails requested,
        NativePatternDetails published) {
        if (medium instanceof NativeSourceCraftingMedium || requested == published)
            return wrap(medium, Collections.singletonList(requested));
        return new NativeCraftingProvider(medium, Collections.singletonList(requested), published);
    }

    public NativeCraftingProvider(ICraftingMedium medium, List<? extends IPatternDetails> patterns) {
        this(medium, patterns, null);
    }

    private NativeCraftingProvider(ICraftingMedium medium, List<? extends IPatternDetails> patterns,
        @Nullable NativePatternDetails publishedPattern) {
        this.medium = java.util.Objects.requireNonNull(medium);
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.publishedPattern = publishedPattern;
        boolean matrix = false;
        for (Class<?> type = medium.getClass(); type != null; type = type.getSuperclass())
            if (ASSEMBLER_MATRIX.equals(type.getName())) {
                matrix = true;
                break;
            }
        // Both GTSWN endpoints are final. Match the optional medium once without loading either class.
        Class<?> mediumClass = medium.getClass();
        String mediumType = mediumClass.getName();
        boolean orderedMatrix = GTSWN_ITEM_INPUT.equals(mediumType) || GTSWN_FLUID_INPUT.equals(mediumType);
        this.usesAssemblerBatchSize = matrix || ME_CHISEL.equals(mediumType);
        this.preserveInputSlots = matrix || orderedMatrix;
        this.scalesProcessingInputs = mediumClass == DualityInterface.class
            || medium instanceof DualityInterface && mediumClass.getEnclosingClass() == PartP2PInterface.class
            || GT_CRAFTING_INPUT_HATCH.equals(mediumType)
            || ASSEMBLER_MATRIX.equals(mediumType)
            || ME_CHISEL.equals(mediumType)
            || SUPER_CRAFTING_INPUT_HATCH.equals(mediumType)
            || orderedMatrix
            || TENKEI_PATTERN_HATCHES.get(mediumClass);
    }

    public ICraftingMedium nativeMedium() {
        return medium;
    }

    /** Physical pattern holder, including the face of a cable-mounted interface. Never probes a push target. */
    @Nullable
    public static Target patternProviderTarget(Object provider) {
        if (provider instanceof NativeCraftingProvider nativeProvider) provider = nativeProvider.nativeMedium();
        if (provider instanceof LargeWorkstationPatternProvider workstation)
            provider = workstation.getWorkstationInterface();
        // DualityInterface#getTile is null for parts; its host owns the actual cable tile and part face.
        if (provider instanceof DualityInterface duality) provider = duality.getHost();
        ForgeDirection side = provider instanceof AEBasePart part ? part.getSide() : null;
        TileEntity tile = provider instanceof IInterfaceViewable viewable ? viewable.getTileEntity()
            : provider instanceof TileEntity direct ? direct : NativeGregTech.hostTile(provider);
        if (tile == null || tile.isInvalid() || tile.getWorldObj() == null) return null;
        return new Target(
            tile.getWorldObj().provider.dimensionId,
            new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord),
            side);
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
        return processingTargets(medium);
    }

    /** Destinations of the last accepted push of a raw native medium; same rules as a wrapped provider. */
    public static java.util.List<cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target> processingTargets(
        ICraftingMedium medium) {
        if (medium instanceof cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider)
            return ((cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider) medium)
                .neoecoae$getProcessingTargets();
        if (medium instanceof IInterfaceHost host) return processingTargets(host.getInterfaceDuality());
        if (medium instanceof NativeSourceCraftingMedium) {
            var source = ((NativeSourceCraftingMedium) medium).sourceCraftingProvider();
            return source instanceof cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider
                ? ((cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider) source)
                    .neoecoae$getProcessingTargets()
                : ownPosition(medium);
        }
        return ownPosition(medium);
    }

    /**
     * A medium that is itself the destination (GT's ME crafting input bus/buffer, other pattern hatches) records no
     * push targets; its own block is where the inputs went, and the status scan resolves its controllers from there.
     */
    private static java.util.List<cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target> ownPosition(
        ICraftingMedium medium) {
        Target target = patternProviderTarget(medium);
        return target == null ? Collections.emptyList()
            : Collections.singletonList(new Target(target.dimension(), target.position()));
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

    /**
     * Adaptive probing requires a medium that can accept the entire scaled input as one batch.
     * Interface settings can still require single-copy pushes for a compatible medium.
     */
    public boolean supportsScaledProcessing() {
        return singleCopyReason() == null;
    }

    @Nullable
    public String singleCopyReason() {
        if (!scalesProcessingInputs) return "single_copy";
        if (usesAssemblerBatchSize) {
            var access = ASSEMBLER_BATCH_ACCESS.get(MEInventoryCrafting.class);
            if (assemblerBatchRejected || access.setter() == null || access.getter() == null)
                return "batch_size_unavailable";
        }
        if (!(medium instanceof DualityInterface duality)) return null;
        if (hasCraftingLock(duality)) return "crafting_lock";
        if (duality.getConfigManager()
            .getSetting(appeng.api.config.Settings.PATTERN_OPTIMIZATION) != appeng.api.config.YesNo.YES)
            return "optimization_disabled";
        return singleCopyTargetReason(duality);
    }

    @Nullable
    private static String singleCopyTargetReason(DualityInterface duality) {
        boolean programmable = cpw.mods.fml.common.Loader.isModLoaded("programmablehatches");
        if (!(duality.getHost() instanceof IInterfaceHost host)) return null;
        TileEntity origin = host.getTileEntity();
        if (origin == null || origin.getWorldObj() == null) return null;
        for (ForgeDirection direction : host.getTargets()) {
            int x = origin.xCoord + direction.offsetX;
            int y = origin.yCoord + direction.offsetY;
            int z = origin.zCoord + direction.offsetZ;
            if (y < 0 || y >= origin.getWorldObj()
                .getHeight()) continue;
            if (!origin.getWorldObj()
                .blockExists(x, y, z)) {
                if (programmable) return "single_copy";
                continue;
            }
            TileEntity target = origin.getWorldObj()
                .getTileEntity(x, y, z);
            if (programmable && ProgrammableHatchCrafting.requiresSingleCopy(target, direction.getOpposite()))
                return "single_copy";
            Object meta = NativeGregTech.metaTile(target);
            if (meta != null) {
                String type = meta.getClass()
                    .getName();
                if (GT_INPUT_BUS.equals(type) || GT_INPUT_HATCH.equals(type)) return "limited_gt_input";
            }
        }
        return null;
    }

    public boolean supportsCountedBatch(IPatternDetails pattern) {
        if (!(pattern instanceof NativePatternDetails)) return false;
        var nativePattern = nativePatternFor(pattern);
        return ProgrammableHatchCrafting.supportsCountedBatch(
            medium,
            nativePattern.nativeDetails(),
            nativePattern.supportsPushInputsToExternalInventory());
    }

    public boolean canDeliverCircuitInstantly(IPatternDetails pattern) {
        return pattern instanceof NativePatternDetails
            && ProgrammableHatchCrafting.canDeliverCircuitInstantly(medium, nativePatternFor(pattern).nativeDetails());
    }

    public int pushCountedBatch(IPatternDetails pattern, KeyCounter[] inputs, int offered) {
        var nativePattern = nativePatternFor(pattern);
        var table = processingTable(nativePattern, inputs);
        return table == null ? 0
            : ProgrammableHatchCrafting.pushCountedBatch(medium, nativePattern.nativeDetails(), table, offered);
    }

    public NativePatternDetails nativePatternFor(IPatternDetails pattern) {
        return publishedPattern != null ? publishedPattern : (NativePatternDetails) pattern;
    }

    static boolean hasCraftingLock(appeng.helpers.DualityInterface medium) {
        return isCraftingLocked(medium.getConfigManager());
    }

    /** Any configured LOCK_CRAFTING_MODE other than NONE, whether or not it is currently holding the interface. */
    public static boolean isCraftingLocked(appeng.api.util.IConfigManager config) {
        if (config == null) return false;
        var mode = config.getSetting(appeng.api.config.Settings.LOCK_CRAFTING_MODE);
        return mode != null && mode != appeng.api.config.LockCraftingMode.NONE;
    }

    /**
     * The medium's own pushPattern decides whether the target is empty; ECO only sizes the single push.
     */
    public boolean allowsScaledWhileBlocking() {
        return supportsScaledProcessing();
    }

    /** Plain blocking accepts one push per empty target; smart blocking keeps accepting the same pattern. */
    public boolean isSinglePushPerVisit() {
        return medium.getBlockingMode() == ICraftingMedium.BlockingMode.BLOCKING;
    }

    /**
     * Whether the last rejected push was refused because the target was occupied or locked. Such a rejection says
     * nothing about how many copies fit, so it must not shrink the learned batch size.
     */
    public boolean wasRejectedByBlocking() {
        var reason = medium.getScheduledReason();
        return reason == appeng.util.ScheduledReason.BLOCKING_MODE || reason == appeng.util.ScheduledReason.LOCK_MODE;
    }

    /** Mirrors GTNH CraftingCPUCluster: only a DualityInterface medium carrying the fake crafting card qualifies. */
    public boolean isFakeCraftingMode() {
        return medium instanceof appeng.helpers.DualityInterface
            && ((appeng.helpers.DualityInterface) medium).isFakeCraftingMode();
    }

    /** Unknown media may read int-sized item/fluid views, so bound aggregate amounts before probing. */
    public long scaledProcessingCopyLimit(KeyCounter[] perCraftInputs) {
        var totals = new java.util.HashMap<AEKey, Long>();
        try {
            for (var counter : perCraftInputs) for (var entry : counter) {
                if (entry.getLongValue() > 0) totals.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
        } catch (ArithmeticException overflow) {
            return 1L;
        }
        long limit = Long.MAX_VALUE;
        for (long amount : totals.values()) limit = Math.min(limit, Integer.MAX_VALUE / amount);
        return Math.max(1L, limit);
    }

    /** Successful native pushes drive the probe; AE2 additionally exposes unfinished outgoing transfers. */
    public boolean isScaledProcessingFullyInserted() {
        return !(medium instanceof DualityInterface)
            || NativeProcessingAccess.isSendBufferEmpty((DualityInterface) medium);
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputHolder) {
        IPatternDetails physical = pattern instanceof ECOProcessingExecutionPattern execution
            ? execution.providerLookupPattern()
            : pattern;
        if (!(physical instanceof NativePatternDetails) || !patterns.contains(physical)) return false;
        var nativePattern = nativePatternFor(physical);
        if (physical != pattern
            && (!supportsScaledProcessing() || !nativePattern.supportsPushInputsToExternalInventory())) return false;
        if (inputHolder.length != pattern.getInputs().length)
            throw new IllegalArgumentException("Source input dimensions differ from native pattern");
        long copies = pattern instanceof ECOProcessingExecutionPattern execution ? execution.copies() : 1L;
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
        } else if (preserveInputSlots) {
            table = orderedProcessingTable(nativePattern, inputHolder, copies);
            if (table == null) return false;
        } else {
            table = processingTable(nativePattern, inputHolder);
            if (table == null) return false;
        }
        if (usesAssemblerBatchSize && !applyAssemblerBatchSize(table, copies)) {
            if (copies > 1L) assemblerBatchRejected = true;
            return false;
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

    @Nullable
    private static MEInventoryCrafting processingTable(NativePatternDetails pattern, KeyCounter[] inputs) {
        var stacks = new ArrayList<IAEStack<?>>();
        for (var counter : inputs) for (var entry : counter) {
            if (entry.getLongValue() <= 0) throw new IllegalArgumentException("Nonpositive native dispatch input");
            var stack = entry.getKey()
                .toNative(entry.getLongValue());
            if (stack == null)
                throw new IllegalArgumentException("Resource has no native dispatch channel: " + entry.getKey());
            stacks.add(stack);
        }
        if (stacks.isEmpty()) {
            return pattern.getInputs().length == 0 && NativePatternDetails.mightHaveEmptyInputs(pattern.nativeDetails())
                ? new MEInventoryCrafting(new ContainerNull(), 3, 3)
                : null;
        }
        var table = new MEInventoryCrafting(new ContainerNull(), stacks.size(), 1);
        for (int slot = 0; slot < stacks.size(); slot++) table.setInventorySlotContents(slot, stacks.get(slot));
        return table;
    }

    /** Restore the physical slots for GTNL container returns and GTSWN ordered processing. */
    @Nullable
    private static MEInventoryCrafting orderedProcessingTable(NativePatternDetails pattern, KeyCounter[] inputHolder,
        long copies) {
        var remaining = new KeyCounter();
        for (var counter : inputHolder) for (var entry : counter) {
            if (entry.getLongValue() <= 0) throw new IllegalArgumentException("Nonpositive native dispatch input");
            remaining.set(entry.getKey(), Math.addExact(remaining.get(entry.getKey()), entry.getLongValue()));
        }
        GenericStack[] sparse = pattern.getSparseInputs();
        var table = new MEInventoryCrafting(new ContainerNull(), Math.max(1, sparse.length), 1);
        for (int slot = 0; slot < sparse.length; slot++) {
            GenericStack input = sparse[slot];
            if (input == null) continue;
            long amount = Math.multiplyExact(input.amount(), copies);
            if (remaining.get(input.what()) < amount) return null;
            var stack = input.what()
                .toNative(amount);
            if (stack == null) return null;
            table.setInventorySlotContents(slot, stack);
            remaining.remove(input.what(), amount);
        }
        return remaining.isEmpty() ? table : null;
    }

    /** Refuse an unverified multiplier before the medium can claim any of the extracted inputs. */
    private static boolean applyAssemblerBatchSize(InventoryCrafting table, long copies) {
        AssemblerBatchAccess access = ASSEMBLER_BATCH_ACCESS.get(table.getClass());
        // Older integrations without long metadata can still use the native one-copy path.
        if (access.getter() == null) return copies == 1L;
        try {
            if (copies == 1L && Long.valueOf(1L)
                .equals(
                    access.getter()
                        .invoke(table)))
                return true;
            if (access.setter() == null) return false;
            access.setter()
                .invoke(table, copies);
            return Long.valueOf(copies)
                .equals(
                    access.getter()
                        .invoke(table));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record AssemblerBatchAccess(Method setter, Method getter) {}
}
