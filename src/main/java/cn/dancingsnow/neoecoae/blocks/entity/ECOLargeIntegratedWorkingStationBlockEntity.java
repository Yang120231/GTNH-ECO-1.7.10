package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationControllerCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IGridMultiblock;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAEMultiBlock;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cn.dancingsnow.neoecoae.platform.recipe.RecipeHolder;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import cn.dancingsnow.neoecoae.recipe.WorkstationEnergy;

public class ECOLargeIntegratedWorkingStationBlockEntity extends ECOIntegratedWorkingStationBlockEntity
    implements ManagedHolder, IGridTickable, IAEMultiBlock<NEIntegratedWorkingStationCluster> {

    public void markForUpdate() {
        markForClientUpdate();
    }

    /** A read-only view of the current cycle, using the same pause rules as the local workstation page. */
    public cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine getProcessingStatus(
        AEKey output) {
        var state = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.QUEUED;
        Component reason = Component.empty();
        PendingBatch batch = pendingBatches.peekFirst();
        boolean matches = formed && batch != null && batch.outputTotal.get(output) > 0;
        if (!formed) {
            state = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.UNAVAILABLE;
            reason = Component.translatable("gui.neoecoae.processing.unformed");
        } else if (matches) {
            if (!getMainNode().isOnline()) {
                state = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.WAITING;
                reason = Component.translatable(
                    "gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.network_unavailable");
            } else {
                int pause = pauseReasonId;
                if (!isTemperatureEnabled()
                    && (pause == PauseReason.OVERHEATED.ordinal() || pause == PauseReason.TEMPERATURE_LOW.ordinal()))
                    pause = PauseReason.NONE.ordinal();
                boolean blocked = pause != PauseReason.NONE.ordinal() || isTemperatureEnabled() && overheated;
                state = blocked
                    ? cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.WAITING
                    : cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.RUNNING;
                if (blocked) reason = getPauseReasonText();
            }
        }
        long parallel = matches
            ? batch.segmented ? Math.min(LargeWorkstationOverclock.BASE_PARALLELISM, batch.craftCount)
                : batch.craftCount
            : 0;
        int duration = matches ? batch.processingTicks : 0;
        int progress = matches ? batch.segmented ? (int) Math
            .min(duration, ((long) batch.progress * batch.craftCount + batch.progressRemainder) / Math.max(1, parallel))
            : batch.progress : 0;
        String location = (worldObj == null ? "?" : Integer.toString(worldObj.provider.dimensionId)) + " ["
            + xCoord
            + ", "
            + yCoord
            + ", "
            + zCoord
            + "]";
        return new cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine(
            Component.translatable("block.neoecoae.large_integrated_working_station"),
            location,
            matches && batch.recipeId != null ? batch.recipeId.toString() : "",
            progress,
            duration,
            parallel,
            state,
            reason);
    }

    @Override
    public void onReady() {
        super.onReady();
        rebuildMultiblock();
        if (level instanceof ServerWorldView) ((ServerWorldView) level).getServer()
            .executeIfPossible(() -> {
                if (!isRemoved()) {
                    rebuildMultiblock();
                    if (!pendingBatches.isEmpty()) getMainNode().ifPresent(
                        (grid, node) -> grid.getTickManager()
                            .wakeDevice(node));
                }
            });
    }

    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.TICK)
    public void tickLargeWorkstation() {
        if (worldObj != null && !worldObj.isRemote) {
            tickThermal(getLevel(), getBlockPos(), getBlockState());
        }
    }

    public cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition getBuildDefinition() {
        return cn.dancingsnow.neoecoae.multiblock.definition.NativeControllerDefinitions.largeWorkstation();
    }

    @Override
    public java.util.Set<cn.dancingsnow.neoecoae.platform.minecraft.Direction> getGridConnectableSides(
        cn.dancingsnow.neoecoae.platform.ae2.grid.BlockOrientation orientation) {
        var sides = java.util.EnumSet.noneOf(cn.dancingsnow.neoecoae.platform.minecraft.Direction.class);
        if (!formed) return java.util.EnumSet.allOf(cn.dancingsnow.neoecoae.platform.minecraft.Direction.class);
        if (cluster != null && level != null)
            for (var side : cn.dancingsnow.neoecoae.platform.minecraft.Direction.values())
                if (cluster.containsBlockEntity(level.getBlockEntity(worldPosition.relative(side)))) sides.add(side);
        return sides;
    }

    @Override
    public appeng.api.util.AECableType getCableConnectionType(net.minecraftforge.common.util.ForgeDirection side) {
        return formed ? appeng.api.util.AECableType.NONE : super.getCableConnectionType(side);
    }

    @Override
    public double getExternalPowerDemand(appeng.api.config.PowerUnits unit, double maximum) {
        return formed ? 0 : super.getExternalPowerDemand(unit, maximum);
    }

    @Override
    public double injectExternalPower(appeng.api.config.PowerUnits unit, double amount, Actionable mode) {
        return formed ? amount : super.injectExternalPower(unit, amount, mode);
    }

    public void updateState(boolean updateExposed) {
        if (level == null || level.isClientSide || isRemoved()) return;
        var state = getBlockState();
        nativeBlockState(
            state.setValue(cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.FORMED, formed)
                .setValue(cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines.WORKING, isWorking()));
        if (updateExposed) onGridConnectableSidesChanged();
        markForUpdate();
    }

    public void setFormed(boolean value) {
        if (formed == value) return;
        formed = value;
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(value && first != null ? first.progress : 0);
        super.onChangeTank();
        updateState(true);
        requestCommunicationProviderUpdate();
    }

    private RecipeHolder<CoolingRecipe> getCoolingRecipeForInput() {
        var input = getInputTank().getFluid();
        if (input == null) return null;
        RecipeHolder<CoolingRecipe> best = null;
        for (var entry : cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes.cooling()
            .entrySet()) {
            var recipe = entry.getValue();
            if (recipe.input()
                .ingredient()
                .test(input)
                && (best == null || recipe.maxOverclock() > best.value()
                    .maxOverclock()))
                best = new RecipeHolder<>(entry.getKey(), recipe);
        }
        return best;
    }

    private IntegratedWorkingStationRecipe resolveRecipe(PendingBatch batch) {
        if (batch.recipe != null) return batch.recipe;
        if (batch.recipeId != null)
            batch.recipe = cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes.getById(batch.recipeId);
        if (batch.recipe != null) return batch.recipe;
        KeyCounter perCraft = divideCounter(batch.inputTotal, batch.craftCount);
        var input = perCraft == null ? null : createRecipeInput(perCraft);
        for (var entry : cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes.getAll()
            .entrySet()) {
            var recipe = entry.getValue();
            if (input != null && recipe.matches(input, null)
                || batch.unlockStack != null && recipe.energy() == batch.energyPerCraft
                    && recipeOutputMatches(recipe, batch.unlockStack)) {
                batch.recipeId = entry.getKey();
                batch.recipe = recipe;
                setChanged();
                return recipe;
            }
        }
        return null;
    }

    private String pauseTranslationKey() {
        if (isTemperatureEnabled() && overheated) return PauseReason.OVERHEATED.translationKey;
        if (isTemperatureEnabled() && preheating) return "gui.neoecoae.large_integrated_working_station.preheat.on";
        return PauseReason.values()[Math
            .max(0, Math.min(pauseReasonId, PauseReason.values().length - 1))].translationKey;
    }

    public CompoundTag getUiSnapshot(int requestedOffset) {
        CompoundTag snapshot = new CompoundTag();
        int offset = Math.max(0, Math.min(requestedOffset, Math.max(0, pendingBatches.size() - 3)));
        snapshot.putInt("offset", offset);
        snapshot.putInt("taskOffset", Math.max(0, offset - 2));
        snapshot.putInt("totalTasks", pendingBatches.size());
        snapshot.putBoolean("formed", formed);
        snapshot.putBoolean("overclocked", overclocked);
        snapshot.putBoolean("activeCooling", activeCooling);
        snapshot.putBoolean("temperatureEnabled", isTemperatureEnabled());
        snapshot.putBoolean("preheating", isTemperatureEnabled() && preheating);
        snapshot.putBoolean("overheated", isTemperatureEnabled() && overheated);
        snapshot.putInt("recoveryTicks", isTemperatureEnabled() ? recoveryTicks : 0);
        snapshot.putInt("currentTier", getCurrentBatchProfile().coolingTier());
        snapshot.putInt("coolantTier", getCoolingTierId());
        snapshot.putDouble("temperature", isTemperatureEnabled() ? temperature : 0);
        var rates = isTemperatureEnabled() ? uiThermalUsage.rates(level == null ? 0 : level.getGameTime())
            : LargeWorkstationThermalUsage.Rates.ZERO;
        snapshot.putDouble("processingHeatPerSecond", rates.processing());
        snapshot.putDouble("preheatPerSecond", rates.preheat());
        snapshot.putDouble("coolingPerSecond", rates.cooling());
        snapshot.putDouble("naturalCoolingPerSecond", rates.naturalCooling());
        snapshot.putDouble("temperatureChangePerSecond", rates.net());
        snapshot.putDouble("energyUsage", uiEnergyUsage.average(level == null ? 0 : level.getGameTime()));
        snapshot.putDouble("energyStored", getAECurrentPower());
        snapshot.putDouble("energyCapacity", getAEMaxPower());
        snapshot.putDouble("energyScale", Math.max(1, getAEMaxPower()));
        PendingBatch head = pendingBatches.peekFirst();
        snapshot.putLong(
            "parallelUsed",
            head == null ? 0 : head.segmented ? Math.min(PARALLELISM, head.craftCount) : head.craftCount);
        snapshot.putLong("parallelCapacity", getMaxBatchParallelism());
        snapshot.putString("pause", pauseTranslationKey());
        ListTag entries = new ListTag();
        int index = 0;
        for (PendingBatch batch : pendingBatches) {
            if (index++ < Math.max(0, offset - 2)) continue;
            if (entries.size() >= 7) break;
            CompoundTag row = new CompoundTag();
            row.putUUID("id", batch.interactionId);
            row.putInt("progress", (int) ((long) batch.progress * 200 / batch.processingTicks));
            row.putInt("elapsedTicks", batch.progress);
            row.putInt("processingTicks", batch.processingTicks);
            row.putBoolean("returnable", batch.progress < batch.processingTicks);
            row.putBoolean("current", batch == head);
            row.putString(
                "status",
                batch != head ? "gui.neoecoae.workstation.task_queued"
                    : batch.progress >= batch.processingTicks ? "gui.neoecoae.workstation.task_output"
                        : pauseTranslationKey());
            row.putLong("craftCount", batch.craftCount);
            if (batch.unlockStack != null) row.put(
                "output",
                GenericStack
                    .writeTag(cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY, batch.unlockStack));
            entries.add(row);
        }
        snapshot.put("tasks", entries);
        return snapshot;
    }

    /** Preserve long item/fluid custody in the dropped controller item rather than narrowing into world stacks. */
    public CompoundTag preparePortableRecovery() {
        for (PendingBatch batch : pendingBatches) if (batch.craftingJobId != null && level != null)
            ECOCraftingJobLifecycle.finish(level.nativeWorld(), batch.craftingJobId, false);
        CompoundTag result = new CompoundTag();
        saveAdditional(result, cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY);
        return result;
    }

    private static final Logger LOGGER = LogManager.getLogger(ECOLargeIntegratedWorkingStationBlockEntity.class);

    private static final int MAX_PROCESSING_STEPS = 200;

    public static final int PARALLELISM = 256;

    private static final int COOLANT_PER_PROCESSING_TICK = LargeWorkstationThermal.COOLANT_PER_TICK;

    private static final int MAX_PENDING_BATCHES = 512;

    private static final long MAX_PENDING_OUTPUT_STACKS = 100_000L;

    private static final long MAX_SAVE_STACKS = 50_000L;

    private static final int MAX_POWER_STORAGE = 16_000_000;

    private final NEIntegratedWorkingStationControllerCalculator calculator;

    private final LargeWorkstationCoolingOutput coolingOutput = new LargeWorkstationCoolingOutput();

    private final Deque<PendingBatch> pendingBatches = new ArrayDeque<>();

    @Nullable
    private NEIntegratedWorkingStationCluster cluster;

    @DescSynced
    private boolean formed;

    @Persisted
    @DescSynced
    private boolean overclocked;

    @Persisted
    @DescSynced
    private boolean activeCooling;

    private int preheatTargetTier = 2;

    private double pendingThermalLoad;

    @Persisted
    @DescSynced
    private boolean preheating;

    @DescSynced
    private double temperature;

    @DescSynced
    private boolean overheated;

    @DescSynced
    private int recoveryTicks;

    @DescSynced
    private int coolingProblemId;

    @DescSynced
    private int pauseReasonId;

    @DescSynced
    private int outputBlockedReasonId;

    private double pendingPowerRefund;

    private LargeWorkstationEnergyUsage uiEnergyUsage = new LargeWorkstationEnergyUsage();

    private final LargeWorkstationEnergyUsage uiFluxUsage = new LargeWorkstationEnergyUsage();

    private long processingFeBuffer;

    private LargeWorkstationThermalUsage uiThermalUsage = new LargeWorkstationThermalUsage();

    public ECOLargeIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.calculator = new NEIntegratedWorkingStationControllerCalculator(this);
        getMainNode().setFlags(appeng.api.networking.GridFlags.MULTIBLOCK)
            .addService(IGridTickable.class, this)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);

        getMainNode().setExposedOnSides(
            getGridConnectableSides(
                new cn.dancingsnow.neoecoae.platform.ae2.grid.BlockOrientation(
                    cn.dancingsnow.neoecoae.platform.minecraft.Direction.fromNative(getForward()),
                    cn.dancingsnow.neoecoae.platform.minecraft.Direction.UP)));
    }

    @Override
    public void setWorking(boolean value) {
        super.setWorking(value);
        updateState(false);
    }

    public boolean isFormed() {
        return formed;
    }

    @Override
    public NEIntegratedWorkingStationCluster getCluster() {
        return cluster;
    }

    public void updateCluster(@Nullable NEIntegratedWorkingStationCluster next) {
        cluster = next;
        setFormed(next != null);
    }

    private void transferStoredFluid(FluidTank source, FluidTank destination) {
        if (source == destination
            || cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.isEmpty(source.getFluid())) return;
        int inserted = destination.fill(
            source.getFluid()
                .copy(),
            true);
        if (inserted > 0) source.drain(inserted, true);
    }

    @Override
    public void disconnect(boolean update) {
        if (cluster != null) {
            cluster.destroy();
            cluster = null;
            setFormed(false);
            if (update) updateState(true);
        }
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (level instanceof cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView serverLevel) {
            calculator.updateMultiblockAfterNeighborUpdate(serverLevel, worldPosition, changedPos);
        }
    }

    public void rebuildMultiblock() {
        if (level instanceof cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView serverLevel) {
            calculator.calculateMultiblock(serverLevel, worldPosition);
        }
    }

    public void breakCluster() {
        if (cluster != null) cluster.breakCluster();
    }

    @Override
    public void setRemoved() {
        disconnect(false);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        disconnect(false);
        super.onChunkUnloaded();
    }

    private java.util.Iterator<IGridNode> getMultiblockNodes() {
        if (cluster == null) return java.util.Collections.emptyIterator();
        List<IGridNode> nodes = new ArrayList<>();
        java.util.Iterator<? extends net.minecraft.tileentity.TileEntity> iterator = cluster.getBlockEntities();
        while (iterator.hasNext()) {
            net.minecraft.tileentity.TileEntity blockEntity = iterator.next();
            IGridNode node = null;
            if (blockEntity instanceof NEBlockEntity<?, ?>member) node = member.getGridNode();
            else if (blockEntity instanceof ECOLargeIntegratedWorkingStationBlockEntity member)
                node = member.getMainNode()
                    .getNode();
            if (node != null) nodes.add(node);
        }
        return nodes.iterator();
    }

    @Override
    public int getMaxProcessingTime() {
        if (!formed) return super.getMaxProcessingTime();
        var batch = pendingBatches.peekFirst();
        return batch == null ? MAX_PROCESSING_STEPS : batch.processingTicks;
    }

    public int getParallelism() {
        return formed ? PARALLELISM : 1;
    }

    @Override
    public FluidTank getInputTank() {
        return cluster == null || cluster.getInputHatch() == null ? super.getInputTank() : cluster.getInputHatch().tank;
    }

    @Override
    public FluidTank getOutputTank() {
        return cluster == null || cluster.getOutputHatch() == null ? super.getOutputTank()
            : cluster.getOutputHatch().tank;
    }

    private LargeWorkstationOverclock getCurrentBatchProfile() {
        if (!isTemperatureEnabled()) {
            RecipeHolder<CoolingRecipe> holder = getCoolingRecipeForInput();
            return LargeWorkstationOverclock.forCoolant(
                overclocked,
                activeCooling,
                holder == null ? 0
                    : holder.value()
                        .maxOverclock());
        }
        return LargeWorkstationOverclock.forTemperature(overclocked, temperature);
    }

    public boolean isTemperatureEnabled() {
        return PlanningSettings.Workstation.largeWorkstationTemperatureEnabled;
    }

    public int getMaxBatchParallelism() {
        return isTemperatureEnabled() && overheated ? 0 : getCurrentBatchProfile().maxParallelism();
    }

    public boolean isOverclocked() {
        return overclocked;
    }

    public boolean isActiveCooling() {
        return activeCooling;
    }

    public void toggleOverclocked() {
        overclocked = !overclocked;
        if (!overclocked) preheating = false;
        settingsChanged();
    }

    public void toggleActiveCooling() {
        activeCooling = !activeCooling;
        if (activeCooling) preheating = false;
        settingsChanged();
    }

    public void togglePreheating() {
        if (!isTemperatureEnabled() || overheated) return;
        preheating = !preheating;
        if (preheating) {
            preheatTargetTier = temperature < 20 ? 2 : temperature < 50 ? 6 : 9;
            overclocked = true;
            activeCooling = false;
        }
        settingsChanged();
    }

    public double getTemperature() {
        return temperature;
    }

    public Component getSelectedTierText() {
        if (!isTemperatureEnabled()) return coolantModeText();
        int tier = LargeWorkstationOverclock.forTemperature(true, temperature)
            .coolingTier();
        int displayBand = tier == 2 ? 4 : tier;
        return Component.translatable("gui.neoecoae.large_integrated_working_station.selected_tier", displayBand);
    }

    public Component getPreheatText() {
        if (!isTemperatureEnabled()) return coolantModeText();
        return Component.translatable(
            preheating ? "gui.neoecoae.large_integrated_working_station.preheat.on"
                : "gui.neoecoae.large_integrated_working_station.preheat.off");
    }

    public Component getTemperatureText() {
        if (!isTemperatureEnabled()) return coolantModeText();
        String heat = String.format(java.util.Locale.ROOT, "%.2f", temperature);
        return overheated
            ? Component.translatable(
                "gui.neoecoae.large_integrated_working_station.recovery",
                heat,
                String.format(java.util.Locale.ROOT, "%.1f", recoveryTicks / 20.0))
            : Component.translatable("gui.neoecoae.large_integrated_working_station.temperature", heat);
    }

    private void settingsChanged() {
        setChanged();
        markForUpdate();
        requestCommunicationProviderUpdate();
        getMainNode().ifPresent(
            (grid, node) -> grid.getTickManager()
                .wakeDevice(node));
    }

    public Component getOverclockSettingsText() {
        return Component.translatable(
            "gui.neoecoae.large_integrated_working_station.settings",
            Component.translatable(
                overclocked ? "gui.neoecoae.large_integrated_working_station.enabled"
                    : "gui.neoecoae.large_integrated_working_station.disabled"),
            Component.translatable(
                activeCooling ? "gui.neoecoae.large_integrated_working_station.enabled"
                    : "gui.neoecoae.large_integrated_working_station.disabled"));
    }

    public Component getCoolingTierText() {
        RecipeHolder<CoolingRecipe> holder = getCoolingRecipeForInput();
        CoolingRecipe recipe = holder == null ? null : holder.value();
        LargeWorkstationOverclock profile = recipe == null ? null
            : LargeWorkstationOverclock.forCoolingTier(recipe.maxOverclock());
        String tier = profile == null ? "none" : switch (profile.coolingTier()) {
            case 2 -> "water";
            case 6 -> "sodium";
            case 9 -> "cryotheum";
            default -> "none";
        };
        return Component.translatable(
            "gui.neoecoae.large_integrated_working_station.coolant_tier",
            Component.translatable("gui.neoecoae.large_integrated_working_station.tier." + tier));
    }

    public Component getBatchSettingsText() {
        PendingBatch batch = pendingBatches.peekFirst();
        LargeWorkstationOverclock profile = batch != null && batch.segmented ? LargeWorkstationOverclock.NORMAL
            : batch != null ? LargeWorkstationOverclock.fromPersisted(batch.coolingTier, batch.energyMultiplier)
                : getCurrentBatchProfile();
        if (profile == null) profile = LargeWorkstationOverclock.NORMAL;
        return Component.translatable(
            "gui.neoecoae.large_integrated_working_station.parallel_and_energy",
            profile.maxParallelism(),
            profile.energyMultiplier());
    }

    public Component getPauseReasonText() {
        boolean thermal = isTemperatureEnabled();
        if (thermal && overheated) return Component.translatable(PauseReason.OVERHEATED.translationKey);
        if (thermal && preheating) return getPreheatText();
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch != null && !batch.segmented && batch.progress < batch.processingTicks && batch.coolingTier > 0) {
            if (!overclocked) return Component.translatable(PauseReason.OVERCLOCK_DISABLED.translationKey);
            LargeWorkstationOverclock locked = LargeWorkstationOverclock.forCoolingTier(batch.coolingTier);
            if (thermal && locked != null && !locked.isTemperatureReady(temperature)) {
                return Component.translatable(PauseReason.TEMPERATURE_LOW.translationKey);
            }
        }
        PauseReason[] reasons = PauseReason.values();
        int status = thermal && activeCooling && coolingProblemId > 0 ? coolingProblemId : pauseReasonId;
        if (!thermal
            && (status == PauseReason.TEMPERATURE_LOW.ordinal() || status == PauseReason.OVERHEATED.ordinal())) {
            status = PauseReason.NONE.ordinal();
        }
        int index = Math.max(0, Math.min(status, reasons.length - 1));
        PauseReason reason = reasons[index];
        var result = Component.translatable(reason.translationKey);
        if (reason == PauseReason.OUTPUT_BLOCKED || reason == PauseReason.INPUT_RETURN_BLOCKED) {
            OutputBlockedReason[] details = OutputBlockedReason.values();
            int detailIndex = Math.max(0, Math.min(outputBlockedReasonId, details.length - 1));
            OutputBlockedReason detail = details[detailIndex];
            if (detail != OutputBlockedReason.NONE) {
                result.append(Component.translatable(detail.translationKey));
            }
        }
        return result;
    }

    private Component coolantModeText() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.temperature_disabled");
    }

    public List<GenericStack> getCurrentBatchInputs() {
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch == null || batch.progress >= batch.processingTicks) {
            return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
        }
        return counterEntries(batch.inputTotal);
    }

    private void setPauseReason(PauseReason reason) {
        setPauseReason(
            reason,
            reason == PauseReason.OUTPUT_BLOCKED || reason == PauseReason.INPUT_RETURN_BLOCKED
                ? OutputBlockedReason.UNKNOWN
                : OutputBlockedReason.NONE);
    }

    private void setPauseReason(PauseReason reason, OutputBlockedReason detail) {
        boolean changed = false;
        if (pauseReasonId != reason.ordinal()) {
            pauseReasonId = reason.ordinal();
            changed = true;
        }
        if (outputBlockedReasonId != detail.ordinal()) {
            outputBlockedReasonId = detail.ordinal();
            changed = true;
        }
        if (changed) {
            markForUpdate();
        }
    }

    private void setOutputBlockedReason(OutputBlockedReason detail) {
        setPauseReason(PauseReason.OUTPUT_BLOCKED, detail);
    }

    private void setInputReturnBlockedReason() {
        setPauseReason(PauseReason.INPUT_RETURN_BLOCKED, OutputBlockedReason.INPUT_RETURN);
    }

    public void onChangeTankFromHatch() {
        super.onChangeTank();

        setChanged();
        markForUpdate();
        getMainNode().ifPresent(
            (grid, node) -> grid.getTickManager()
                .wakeDevice(node));
        requestCommunicationProviderUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        super.onChangeInventory(inventory, slot);

        setChanged();
    }

    private void recordUiEnergy(double amount) {
        if (level != null && !level.isClientSide) {
            uiEnergyUsage.record(level.getGameTime(), amount);
        }
    }

    public void returnTaskInputs(UUID interactionId) {
        if (level == null || level.isClientSide || interactionId == null) return;
        PendingBatch selected = null;
        for (PendingBatch batch : pendingBatches) {
            if (batch.interactionId.equals(interactionId)) {
                selected = batch;
                break;
            }
        }
        if (selected == null || selected.progress >= selected.processingTicks) return;
        if (!deliverBatchOutputs(selected)) {
            setChanged();
            return;
        }
        if (!recoverCounterToNetwork(selected.inputTotal)) {
            setInputReturnBlockedReason();
            setChanged();
            return;
        }
        if (selected.craftingJobId != null) {
            ECOCraftingJobLifecycle.finish(level.nativeWorld(), selected.craftingJobId, false);
        }
        notifyPatternAborted(selected.unlockStack);
        pendingBatches.remove(selected);
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(first == null ? 0 : first.progress);
        setWorking(first != null);

        setPauseReason(PauseReason.NONE);

        setChanged();
        requestCommunicationProviderUpdate();
    }

    @Nullable
    private static GenericStack firstPositive(KeyCounter counter) {
        for (var entry : counter) if (entry.getLongValue() > 0) {
            return new GenericStack(entry.getKey(), entry.getLongValue());
        }
        return null;
    }

    private int getCoolingTierId() {
        RecipeHolder<CoolingRecipe> holder = getCoolingRecipeForInput();
        return holder == null ? 0
            : holder.value()
                .maxOverclock();
    }

    @Nullable
    public IntegratedWorkingStationRecipe getTask() {
        if (!formed) return super.getTask();
        PendingBatch batch = pendingBatches.peekFirst();
        return batch == null ? null : resolveRecipe(batch);
    }

    private static boolean recipeOutputMatches(IntegratedWorkingStationRecipe recipe, GenericStack expected) {
        GenericStack output = recipe.hasItemOutput() ? GenericStack.fromItemStack(recipe.itemOutput())
            : recipe.hasFluidOutput()
                ? new GenericStack(AEFluidKey.of(recipe.fluidOutput()), recipe.fluidOutput().amount)
                : null;
        return output != null && output.what()
            .equals(expected.what()) && output.amount() == expected.amount();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putLong("processingFeBuffer", processingFeBuffer);

        CompoundTag thermal = new CompoundTag();
        LargeWorkstationThermal.save(thermalState(), thermal);
        data.put("largeWorkstationThermal", thermal);
        if (pendingPowerRefund > 0.0D) data.putDouble("pendingPowerRefund", pendingPowerRefund);
        coolingOutput.save(data, registries);
        ListTag batches = new ListTag();
        for (PendingBatch batch : pendingBatches) {
            batches.add(batch.save(registries));
        }
        data.put("pendingBatches", batches);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        processingFeBuffer = Math.max(0, data.getLong("processingFeBuffer"));
        uiThermalUsage = new LargeWorkstationThermalUsage();

        var thermal = LargeWorkstationThermal.load(data.getCompound("largeWorkstationThermal"));
        temperature = thermal.temperature();
        overheated = thermal.overheated();
        recoveryTicks = thermal.recoveryTicks();
        pendingThermalLoad = 0;
        preheatTargetTier = temperature < 20 ? 2 : temperature < 50 ? 6 : 9;
        if (overheated) preheating = false;
        double restoredRefund = data.getDouble("pendingPowerRefund");
        pendingPowerRefund = Double.isFinite(restoredRefund) && restoredRefund > 0.0D ? restoredRefund : 0.0D;
        coolingOutput.load(data, registries);
        pendingBatches.clear();
        ListTag batches = data.getList("pendingBatches", Tag.TAG_COMPOUND);
        setPauseReason(PauseReason.NONE);
        long currentTick = level == null ? 0L : level.getGameTime();
        for (int index = 0; index < batches.size(); index++) {
            PendingBatch batch = PendingBatch.load(batches.getCompound(index), registries, currentTick);
            if (batch != null) {
                pendingBatches.addLast(batch);
            }
        }
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(first == null ? 0 : first.progress);
    }

    public Component getRecipeText() {
        IntegratedWorkingStationRecipe task = getTask();
        if (task == null) return Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_empty");
        long itemCount = task.inputItems()
            .stream()
            .mapToLong(it -> it.count())
            .sum();
        int fluidAmount = task.inputFluid()
            .ingredient()
            .isEmpty() ? 0
                : task.inputFluid()
                    .amount();
        return Component.translatable("gui.neoecoae.large_integrated_working_station.recipe", itemCount, fluidAmount);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return formed ? new TickingRequest(1, 20, false, false) : super.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        retryPendingPowerRefund();
        if (!formed) return super.tickingRequest(node, ticksSinceLastCall);

        transferStoredFluid(inputTank, getInputTank());
        transferStoredFluid(outputTank, getOutputTank());
        flushOutputs();
        if (!getMainNode().isOnline()) {
            setWorking(false);
            return TickRateModulation.SLOWER;
        }
        return tickPendingBatch();
    }

    public boolean canAcceptPattern() {
        return formed && (!isTemperatureEnabled() || !overheated)
            && getMainNode().isOnline()
            && pendingBatches.size() < MAX_PENDING_BATCHES
            && getPendingOutputStackCount() < MAX_PENDING_OUTPUT_STACKS;
    }

    private long getPendingOutputStackCount() {
        long count = 0L;
        for (PendingBatch batch : pendingBatches) {
            for (var entry : batch.pendingOutput) {
                if (entry.getLongValue() > 0L) {
                    if (count >= MAX_PENDING_OUTPUT_STACKS) return MAX_PENDING_OUTPUT_STACKS;
                    count++;
                }
            }
        }
        return count;
    }

    public boolean containsPendingPatternInput(Set<AEKey> patternInputs) {
        if (patternInputs == null || patternInputs.isEmpty()) {
            return false;
        }
        for (PendingBatch batch : pendingBatches) {
            for (var entry : batch.inputTotal) {
                AEKey key = entry.getKey();
                if (entry.getLongValue() > 0L && key != null && patternInputs.contains(key.dropSecondary())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean acceptPattern(IPatternDetails pattern, KeyCounter[] holders, boolean commit) {
        PendingBatch batch = createPendingBatch(
            pattern,
            holders,
            1L,
            null,
            pattern == null ? null : pattern.getPrimaryOutput());
        if (batch == null || (commit && !canAcceptPattern())) return false;
        if (commit) {
            enqueueBatch(batch, holders);
        }
        return true;
    }

    public boolean acceptPatternBatch(IPatternDetails pattern, KeyCounter[] inputTotal, long craftCount,
        @Nullable UUID craftingJobId) {
        if (!canAcceptPattern()) return false;
        PendingBatch batch = createPendingBatch(
            pattern,
            inputTotal,
            craftCount,
            craftingJobId,
            pattern == null ? null : pattern.getPrimaryOutput());
        if (batch == null) return false;
        enqueueBatch(batch, inputTotal);
        return true;
    }

    private void enqueueBatch(PendingBatch batch, @Nullable KeyCounter[] consumedInputs) {
        boolean wasEmpty = pendingBatches.isEmpty();
        pendingBatches.addLast(batch);
        if (consumedInputs != null) {
            for (KeyCounter counter : consumedInputs) {
                if (counter != null) counter.clear();
            }
        }
        if (wasEmpty) setProcessingTime(batch.progress);
        setPauseReason(PauseReason.NONE);

        setChanged();
        getMainNode().ifPresent(
            (grid, node) -> grid.getTickManager()
                .wakeDevice(node));
    }

    private TickRateModulation tickPendingBatch() {
        PendingBatch batch = pendingBatches.peekFirst();
        if (batch == null) {
            setWorking(false);
            setProcessingTime(0);
            setPauseReason(PauseReason.NONE);
            return TickRateModulation.SLOWER;
        }

        // A queued batch represents work even when its progress completed in one tick or its output
        // is temporarily waiting for a destination. Keep the controller screen on until the batch
        // is actually removed from the queue.
        setWorking(true);
        setProcessingTime(batch.progress);
        boolean terminal = batch.craftingJobId != null
            && ECOCraftingJobLifecycle.isTerminated(level.nativeWorld(), batch.craftingJobId);
        if (terminal) {
            boolean incomplete = batch.progress < batch.processingTicks;
            boolean cleared = deliverBatchOutputs(batch);
            if (cleared && incomplete && !recoverCounterToNetwork(batch.inputTotal)) {
                setInputReturnBlockedReason();
                cleared = false;
            }
            if (cleared) {
                if (incomplete) notifyPatternAborted(batch.unlockStack);
                removeFirstBatch(batch);
            }
            if (cleared) setPauseReason(PauseReason.NONE);
            setChanged();
            return cleared ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        if (!isCounterEmpty(batch.pendingOutput)) {
            boolean delivered = deliverBatchOutputs(batch);
            if (delivered && batch.progress >= batch.processingTicks) removeFirstBatch(batch);
            if (delivered) setPauseReason(PauseReason.NONE);
            setChanged();
            return delivered ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        boolean thermal = isTemperatureEnabled();
        if (thermal && overheated) {
            setPauseReason(PauseReason.OVERHEATED);
            return TickRateModulation.SLOWER;
        }
        if (batch.coolingTier > 0 && !batch.segmented) {
            if (!overclocked) {
                setPauseReason(PauseReason.OVERCLOCK_DISABLED);
                return TickRateModulation.SLOWER;
            }
            LargeWorkstationOverclock locked = LargeWorkstationOverclock.forCoolingTier(batch.coolingTier);
            if (thermal && (locked == null || !locked.isTemperatureReady(temperature))) {
                // Keep one ownership ledger and spend its already-paid work on 256-craft portions.
                batch.segmented = true;
                setChanged();
            }
        }

        boolean restoreBatch = batch.segmented && batch.coolingTier > 0
            && getCurrentBatchProfile().coolingTier() >= batch.coolingTier;
        if (!acquireExtraInputs(batch, batch.segmented && !restoreBatch)) {
            setPauseReason(PauseReason.LIGHTNING_MISSING);
            return TickRateModulation.SLOWER;
        }
        if (restoreBatch) {
            // Restore only after owning the full batch's extras, keeping interrupted saves valid.
            batch.segmented = false;
            setChanged();
        }
        refillPendingBatch(batch);

        int remainingProgress = batch.processingTicks - batch.progress;
        int advance = Math.min(batch.legacyTiming ? getParallelism() : 1, Math.max(0, remainingProgress));
        if (advance <= 0) {
            batch.progress = batch.processingTicks;
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            boolean delivered = deliverBatchOutputs(batch);
            if (delivered) removeFirstBatch(batch);
            if (delivered) setPauseReason(PauseReason.NONE);
            setChanged();
            return TickRateModulation.URGENT;
        }

        CoolingTickPlan legacyCooling = null;
        if (!thermal && batch.coolingTier > 0 && !batch.segmented) {
            legacyCooling = prepareLegacyCoolingTick(batch.coolingTier);
            if (legacyCooling == null) return TickRateModulation.SLOWER;
        }

        long completedWork = batch.progress * batch.craftCount + batch.progressRemainder;
        long portionCount = Math.min(LargeWorkstationOverclock.BASE_PARALLELISM, batch.craftCount);
        long portionWork = portionCount * batch.processingTicks;
        long work = batch.segmented
            ? Math.min(
                Math.max(0L, portionWork - completedWork),
                (batch.legacyTiming ? (long) getParallelism() : 1L) * portionCount)
            : (long) advance * batch.craftCount - batch.progressRemainder;
        double required = (double) batch.energyPerCraft * work / batch.processingTicks;
        IEnergySource source = processingEnergySource(required);

        double available = source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (required > 0.0D && available + 0.001D < required) {
            if (batch.segmented) {
                work = Math.min(
                    work,
                    Math.max(0L, (long) Math.floor(available * batch.processingTicks / batch.energyPerCraft)));
            } else {
                advance = (int) Math.min(
                    (long) advance,
                    Math.max(
                        0L,
                        (long) Math.floor(
                            (available * batch.processingTicks / batch.energyPerCraft + batch.progressRemainder)
                                / batch.craftCount)));
                work = (long) advance * batch.craftCount - batch.progressRemainder;
            }
            if (work <= 0 || !batch.segmented && advance <= 0) {
                setPauseReason(PauseReason.POWER_MISSING);
                return TickRateModulation.SLOWER;
            }
            required = (double) batch.energyPerCraft * work / batch.processingTicks;
        }

        LargeWorkstationProcessing.FluxPayment flux;
        try {
            flux = LargeWorkstationProcessing
                .fluxPayment(batch.fePerCraft, work, batch.processingTicks, batch.feRemainder);
        } catch (ArithmeticException overflow) {
            setPauseReason(PauseReason.FE_MISSING);
            return TickRateModulation.SLOWER;
        }
        if (flux.amount() > processingFeBuffer) {
            GridView grid = getMainNode().getGrid();
            if (grid != null && false) {
                MEStorage storage = grid.getStorageService()
                    .getInventory();
                var actionSource = IActionSource.ofMachine(this);
                long missing = flux.amount() - processingFeBuffer;
                long offered = 0L;
                if (offered > 0) {
                    processingFeBuffer += 0L;
                    setChanged();
                }
            }
        }
        long consumedFe = Math.min(flux.amount(), processingFeBuffer);
        long substituteFe = flux.amount() - consumedFe;
        if (substituteFe > 0) {
            try {
                required += WorkstationEnergy.aeEquivalent(substituteFe)
                    .doubleValue();
            } catch (IllegalArgumentException invalidConversion) {
                setPauseReason(PauseReason.POWER_MISSING);
                return TickRateModulation.SLOWER;
            }
            if (!Double.isFinite(required)) {
                setPauseReason(PauseReason.POWER_MISSING);
                return TickRateModulation.SLOWER;
            }
            source = processingEnergySource(required);
            if (source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.001D < required) {
                setPauseReason(PauseReason.POWER_MISSING);
                return TickRateModulation.SLOWER;
            }
        }
        IEnergySource paymentSource = source;
        CoolingTickPlan coolantPayment = legacyCooling;
        LargeWorkstationTickPayment.Result payment = LargeWorkstationTickPayment.commit(
            paymentSource,
            required,
            () -> coolantPayment == null || consumeCoolingTick(coolantPayment),
            amount -> refundProcessingPower(paymentSource, amount));
        if (payment != LargeWorkstationTickPayment.Result.PAID) {
            setPauseReason(
                payment == LargeWorkstationTickPayment.Result.POWER_MISSING ? PauseReason.POWER_MISSING
                    : PauseReason.COOLANT_OUTPUT_BLOCKED);
            return TickRateModulation.SLOWER;
        }

        processingFeBuffer -= consumedFe;
        batch.feRemainder = flux.remainder();
        if (level != null && consumedFe > 0) uiFluxUsage.record(level.getGameTime(), consumedFe);
        recordUiEnergy(required);
        preheating = false;

        if (thermal && overclocked) {
            var profile = batch.coolingTier > 0 && !batch.segmented
                ? LargeWorkstationOverclock.forCoolingTier(batch.coolingTier)
                : LargeWorkstationOverclock.NORMAL;
            double load = (double) work * batch.thermalWorkMultiplier / profile.maxParallelism();
            if (batch.legacyTiming) load /= batch.processingTicks;
            pendingThermalLoad += load;
        }
        if (batch.segmented) {
            completedWork += work;
            if (completedWork >= portionWork) {
                finishBatchPortion(batch, portionCount, completedWork - portionWork);
            } else {
                batch.progress = (int) (completedWork / batch.craftCount);
                batch.progressRemainder = completedWork % batch.craftCount;
            }
            setProcessingTime(batch.progress);
            setPauseReason(PauseReason.NONE);
            if (!isCounterEmpty(batch.pendingOutput) && deliverBatchOutputs(batch)
                && batch.progress >= batch.processingTicks) removeFirstBatch(batch);
            setChanged();
            return TickRateModulation.URGENT;
        }
        batch.progress += advance;
        batch.progressRemainder = 0;
        setProcessingTime(batch.progress);
        setPauseReason(PauseReason.NONE);
        if (batch.progress >= batch.processingTicks) {
            batch.progress = batch.processingTicks;
            // Inputs are already owned by this controller. Once the batch reaches completion, only the output
            // ledger remains recoverable and the original input total must never be replayed.
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            if (deliverBatchOutputs(batch)) removeFirstBatch(batch);
        } else {
            setWorking(true);
        }
        setChanged();
        return TickRateModulation.URGENT;
    }

    private IEnergySource processingEnergySource(double required) {
        IEnergySource source = this;
        double available = source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (required > 0 && available + 0.001D < required) {
            GridView grid = getMainNode().getGrid();
            if (grid != null && grid.getEnergyService()
                .extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG) > available) {
                source = grid.getEnergyService();
            }
        }
        return source;
    }

    private void refillPendingBatch(PendingBatch batch) {
        if (batch.segmented) return;
        LargeWorkstationOverclock profile = getCurrentBatchProfile();
        if (batch.recipeId == null || profile.coolingTier() < batch.coolingTier
            || batch.craftCount >= profile.maxParallelism()
            || batch.progress >= batch.processingTicks) return;
        KeyCounter inputsPerCraft = divideCounter(batch.inputTotal, batch.craftCount);
        KeyCounter outputsPerCraft = divideCounter(batch.outputTotal, batch.craftCount);
        if (inputsPerCraft == null || outputsPerCraft == null) return;

        var iterator = pendingBatches.iterator();
        if (!iterator.hasNext() || iterator.next() != batch) return;
        boolean changed = false;
        while (iterator.hasNext() && batch.craftCount < profile.maxParallelism()) {
            PendingBatch next = iterator.next();
            if (next.segmented || next.progress != 0
                || next.progressRemainder != 0
                || !isCounterEmpty(next.pendingOutput)
                || next.coolingTier > profile.coolingTier()
                || next.energyPerCraft != batch.energyPerCraft
                || next.energyMultiplier != batch.energyMultiplier
                || next.thermalWorkMultiplier != batch.thermalWorkMultiplier
                || next.processingTicks != batch.processingTicks
                || next.fePerCraft != batch.fePerCraft
                || next.legacyTiming != batch.legacyTiming
                || next.feRemainder != 0
                || !Objects.equals(next.recipeId, batch.recipeId)
                || !Objects.equals(next.craftingJobId, batch.craftingJobId)
                || !Objects.equals(next.unlockStack, batch.unlockStack)) break;

            long taken = Math.min(profile.maxParallelism() - batch.craftCount, next.craftCount);
            KeyCounter ownedInputs;
            KeyCounter ownedOutputs;
            KeyCounter inputPortion;
            KeyCounter outputPortion;
            try {
                KeyCounter expectedInputs = addCountersExact(next.inputTotal, next.missingExtras);
                if (!sameCounter(inputsPerCraft, divideCounter(expectedInputs, next.craftCount))
                    || !sameCounter(outputsPerCraft, divideCounter(next.outputTotal, next.craftCount))) break;
                inputPortion = scaleCounter(inputsPerCraft, taken);
                outputPortion = scaleCounter(outputsPerCraft, taken);
                ownedInputs = addCountersExact(batch.inputTotal, inputPortion);
                ownedOutputs = addCountersExact(batch.outputTotal, outputPortion);
            } catch (ArithmeticException overflow) {
                break;
            }
            // Compatibility inputs must be fully owned before any of this task joins progressed work.
            // A partial withdrawal stays on its original queued task and is saved/refunded there.
            if (!acquireExtraInputs(next)) break;

            long completedWork = batch.progress * batch.craftCount + batch.progressRemainder;
            batch.craftCount += taken;
            batch.coolingTier = profile.coolingTier();
            batch.progress = (int) (completedWork / batch.craftCount);
            batch.progressRemainder = completedWork % batch.craftCount;
            batch.inputTotal.clear();
            batch.inputTotal.addAll(ownedInputs);
            batch.outputTotal.clear();
            batch.outputTotal.addAll(ownedOutputs);
            if (taken == next.craftCount) {
                iterator.remove();
            } else {
                next.craftCount -= taken;
                for (var entry : inputPortion) next.inputTotal.remove(entry.getKey(), entry.getLongValue());
                for (var entry : outputPortion) next.outputTotal.remove(entry.getKey(), entry.getLongValue());
            }
            changed = true;
        }
        if (changed) {
            setProcessingTime(batch.progress);

            setChanged();
            requestCommunicationProviderUpdate();
        }
    }

    private void finishBatchPortion(PendingBatch batch, long count, long remainingPaidWork) {
        if (count == batch.craftCount) {
            batch.progress = batch.processingTicks;
            batch.progressRemainder = 0;
            batch.inputTotal.clear();
            batch.pendingOutput.addAll(batch.outputTotal);
            return;
        }
        KeyCounter inputs = counterPortion(
            addCountersExact(batch.inputTotal, batch.missingExtras),
            count,
            batch.craftCount);
        KeyCounter outputs = counterPortion(batch.outputTotal, count, batch.craftCount);
        for (var entry : inputs) batch.inputTotal.remove(entry.getKey(), entry.getLongValue());
        for (var entry : outputs) batch.outputTotal.remove(entry.getKey(), entry.getLongValue());
        batch.pendingOutput.addAll(outputs);
        batch.craftCount -= count;
        batch.progress = (int) (remainingPaidWork / batch.craftCount);
        batch.progressRemainder = remainingPaidWork % batch.craftCount;

    }

    private static long portionAmount(long amount, long count, long totalCount) {
        // Avoid multiplying a possibly long-sized ingredient total by the portion size.
        return amount / totalCount * count + amount % totalCount * count / totalCount;
    }

    private static KeyCounter counterPortion(KeyCounter total, long count, long totalCount) {
        KeyCounter result = new KeyCounter();
        for (var entry : total) {
            long amount = portionAmount(entry.getLongValue(), count, totalCount);
            if (amount > 0) result.add(entry.getKey(), amount);
        }
        return result;
    }

    private static KeyCounter addCountersExact(KeyCounter first, KeyCounter second) {
        KeyCounter result = new KeyCounter();
        result.addAll(first);
        for (var entry : second) {
            Math.addExact(result.get(entry.getKey()), entry.getLongValue());
            result.add(entry.getKey(), entry.getLongValue());
        }
        return result;
    }

    private static boolean sameCounter(KeyCounter expected, @Nullable KeyCounter actual) {
        if (actual == null) return false;
        for (var entry : expected) if (actual.get(entry.getKey()) != entry.getLongValue()) return false;
        for (var entry : actual) if (expected.get(entry.getKey()) != entry.getLongValue()) return false;
        return true;
    }

    private void refundProcessingPower(IEnergySource source, double amount) {
        double remaining = source instanceof IEnergyGrid network ? network.injectPower(amount, Actionable.MODULATE)
            : injectAEPower(amount, Actionable.MODULATE);
        if (remaining > 0.0D && source != this) {
            remaining = injectAEPower(remaining, Actionable.MODULATE);
        } else if (remaining > 0.0D) {
            GridView grid = getMainNode().getGrid();
            if (grid != null) remaining = grid.getEnergyService()
                .injectPower(remaining, Actionable.MODULATE);
        }
        if (remaining > 0.0D) {
            pendingPowerRefund += remaining;
            setChanged();
        }
    }

    private void retryPendingPowerRefund() {
        if (pendingPowerRefund <= 0.0D) return;
        double remaining = injectAEPower(pendingPowerRefund, Actionable.MODULATE);
        if (remaining > 0.0D) {
            GridView grid = getMainNode().getGrid();
            if (grid != null) remaining = grid.getEnergyService()
                .injectPower(remaining, Actionable.MODULATE);
        }
        if (remaining != pendingPowerRefund) {
            pendingPowerRefund = remaining;
            setChanged();
        }
    }

    private LargeWorkstationThermal.State thermalState() {
        return new LargeWorkstationThermal.State(temperature, overheated, recoveryTicks);
    }

    private void applyThermalState(LargeWorkstationThermal.State next) {
        int previousLimit = getMaxBatchParallelism();
        boolean changed = temperature != next.temperature() || overheated != next.overheated()
            || recoveryTicks != next.recoveryTicks();
        temperature = next.temperature();
        overheated = next.overheated();
        recoveryTicks = next.recoveryTicks();
        if (overheated) preheating = false;
        if (changed) setChanged();
        if (previousLimit != getMaxBatchParallelism()) {
            requestCommunicationProviderUpdate();
            getMainNode().ifPresent(
                (grid, node) -> grid.getTickManager()
                    .wakeDevice(node));
        }
    }

    public void tickThermal(WorldView tickLevel, BlockPos pos, BlockState state) {
        if (tickLevel.isClientSide) return;
        double processingLoad = pendingThermalLoad;
        pendingThermalLoad = 0;
        if (!isTemperatureEnabled()) {
            uiThermalUsage.clear();
            // Freeze saved thermal state while disabled; it applies again when temperature mode is enabled.
            coolingProblemId = 0;
            return;
        }
        if (!formed) {
            settleThermalTick(tickLevel.getGameTime(), 0, 0, 0, false);
            return;
        }
        double preheatFraction = 0;
        if (preheating && !overheated && getMainNode().isOnline()) {
            LargeWorkstationOverclock selected = LargeWorkstationOverclock.forCoolingTier(preheatTargetTier);
            double target = selected == null ? 20 : selected.minimumTemperature();
            if (temperature >= target) {
                preheating = false;
                setChanged();
            } else {
                double fraction = Math.min(
                    1,
                    (target - temperature) * LargeWorkstationThermal.TICKS_PER_SECOND
                        / LargeWorkstationThermal.preheatPerSecond(temperature));
                double required = LargeWorkstationThermal.PREHEAT_AE_PER_TICK * fraction;
                IEnergySource source = this;
                GridView grid = getMainNode().getGrid();
                if (grid != null && grid.getEnergyService()
                    .extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                    > source.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.CONFIG)) {
                    source = grid.getEnergyService();
                }
                double paid = source.extractAEPower(required, Actionable.MODULATE, PowerMultiplier.CONFIG);
                recordUiEnergy(paid);
                preheatFraction = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .clamp(paid / LargeWorkstationThermal.PREHEAT_AE_PER_TICK, 0, fraction);
            }
        }
        int coolingTier = 0;
        boolean consumed = false;
        coolingProblemId = 0;
        if (activeCooling && !preheating && temperature > 0) {
            RecipeHolder<CoolingRecipe> holder = getCoolingRecipeForInput();
            // Only the real cryotheum fluid can clear the safety latch, even with custom cooling recipes.
            boolean recoveryFluid = ResourceLocation.fromNamespaceAndPath("neoecoae", "cryotheum_solution")
                .equals(
                    getInputTank().getFluid() == null ? null
                        : cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.tryParse(
                            getInputTank().getFluid()
                                .getFluid()
                                .getName()));
            if (!overheated || recoveryFluid) {
                CoolingTickPlan plan = prepareCoolingTick(holder);
                if (plan != null && consumeCoolingTick(plan)) {
                    coolingTier = overheated ? 9
                        : holder.value()
                            .maxOverclock();
                    consumed = true;
                }
            }
        }
        settleThermalTick(tickLevel.getGameTime(), processingLoad, preheatFraction, coolingTier, consumed);
        if (preheating) {
            LargeWorkstationOverclock selected = LargeWorkstationOverclock.forCoolingTier(preheatTargetTier);
            if (selected != null && temperature >= selected.minimumTemperature()) {
                preheating = false;
                setChanged();
            }
        }
        if (tickLevel.getGameTime() % 20 == 0) markForUpdate();
    }

    private void settleThermalTick(long tick, double processingLoad, double preheatFraction, int coolingTier,
        boolean consumed) {
        var before = thermalState();
        var result = LargeWorkstationThermal.step(before, processingLoad, preheatFraction, coolingTier, consumed);
        uiThermalUsage.record(tick, before, result);
        applyThermalState(result.state());
    }

    @Nullable
    private CoolingTickPlan prepareLegacyCoolingTick(int requiredTier) {
        if (!activeCooling) {
            setPauseReason(PauseReason.COOLING_DISABLED);
            return null;
        }
        RecipeHolder<CoolingRecipe> holder = getCoolingRecipeForInput();
        if (holder != null && holder.value()
            .maxOverclock() < requiredTier) {
            setPauseReason(PauseReason.COOLANT_TIER_LOW);
            return null;
        }
        CoolingTickPlan plan = prepareCoolingTick(holder);
        if (plan == null) {
            setPauseReason(PauseReason.values()[coolingProblemId]);
        }
        return plan;
    }

    @Nullable
    private CoolingTickPlan prepareCoolingTick(@Nullable RecipeHolder<CoolingRecipe> holder) {
        if (holder == null) {
            coolingProblemId = PauseReason.COOLANT_MISSING.ordinal();
            return null;
        }
        CoolingRecipe recipe = holder.value();
        LargeWorkstationOverclock current = LargeWorkstationOverclock.forCoolingTier(recipe.maxOverclock());
        if (current == null) {
            coolingProblemId = PauseReason.COOLANT_TIER_LOW.ordinal();
            return null;
        }
        if (getInputTank().getFluidAmount() < COOLANT_PER_PROCESSING_TICK) {
            coolingProblemId = PauseReason.COOLANT_INSUFFICIENT.ordinal();
            return null;
        }

        LargeWorkstationCoolingOutput.Plan output = coolingOutput.plan(
            holder.id(),
            recipe.output(),
            recipe.inputAmount(),
            recipe.outputAmount(),
            COOLANT_PER_PROCESSING_TICK);
        if (output == null) {
            coolingProblemId = PauseReason.COOLANT_OUTPUT_BLOCKED.ordinal();
            return null;
        }
        FluidStack byproduct = output.output();
        if (!cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.isEmpty(byproduct)
            && getOutputTank().fill(byproduct, false)
                != cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.amount(byproduct)) {
            coolingProblemId = PauseReason.COOLANT_OUTPUT_BLOCKED.ordinal();
            return null;
        }
        coolingProblemId = PauseReason.NONE.ordinal();
        return new CoolingTickPlan(
            cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks
                .copy(getInputTank().getFluid(), COOLANT_PER_PROCESSING_TICK),
            output);
    }

    private boolean consumeCoolingTick(CoolingTickPlan plan) {
        FluidStack drained = getInputTank().drain(COOLANT_PER_PROCESSING_TICK, true);
        if (cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.amount(drained) != COOLANT_PER_PROCESSING_TICK
            || !cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.equal(drained, plan.input)) {
            if (!cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.isEmpty(drained))
                getInputTank().fill(drained, true);
            return false;
        }

        FluidStack byproduct = plan.output.output();
        if (!cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.isEmpty(byproduct)) {
            int filled = getOutputTank().fill(byproduct, true);
            if (filled != cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.amount(byproduct)) {
                if (filled > 0) getOutputTank().drain(filled, true);
                getInputTank().fill(drained, true);
                return false;
            }
        }
        coolingOutput.commit(plan.output);
        return true;
    }

    private void removeFirstBatch(PendingBatch batch) {
        if (pendingBatches.peekFirst() != batch) return;
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.removeFirst(pendingBatches);
        PendingBatch next = pendingBatches.peekFirst();
        setProcessingTime(next == null ? 0 : next.progress);
        if (next != null) {
            setWorking(true);
        }

        requestCommunicationProviderUpdate();
    }

    private boolean deliverBatchOutputs(PendingBatch batch) {
        if (isCounterEmpty(batch.pendingOutput)) return true;
        GridView grid = getMainNode().getGrid();
        if (grid == null) {
            setOutputBlockedReason(OutputBlockedReason.NETWORK_UNAVAILABLE);
            return false;
        }

        MEStorage storage = grid.getStorageService()
            .getInventory();
        Object craftingService = grid.getCraftingService();
        for (GenericStack stack : counterEntries(batch.pendingOutput)) {
            long requested = stack.amount();
            if (batch.craftingJobId != null
                && !ECOCraftingJobLifecycle.isTerminated(level.nativeWorld(), batch.craftingJobId)) {
                if (!(craftingService instanceof ECOCraftingOutputRouter ownerRouter)) {
                    setOutputBlockedReason(OutputBlockedReason.CPU_ROUTE);
                    return false;
                }
                long inserted = ownerRouter
                    .neoecoae$insertIntoCpuForJob(batch.craftingJobId, stack.what(), requested, Actionable.MODULATE);
                if (inserted < 0L || inserted > requested) {
                    throw new IllegalStateException(
                        "Invalid large workstation output insertion amount: " + inserted + " for " + requested);
                }
                if (inserted > 0L) batch.pendingOutput.remove(stack.what(), inserted);
                requested -= inserted;
                if (requested == 0L) continue;
                // An external CPU can finish while accepting the final output. Its remaining
                // byproducts and surplus now belong in storage, including the rest of this stack.
                if (!ECOCraftingJobLifecycle.isTerminated(level.nativeWorld(), batch.craftingJobId)) {
                    setOutputBlockedReason(OutputBlockedReason.CPU_INSERT);
                    return false;
                }
            }
            long inserted = storage.insert(stack.what(), requested, Actionable.MODULATE, IActionSource.ofMachine(this));
            if (inserted < 0L || inserted > requested) {
                throw new IllegalStateException(
                    "Invalid large workstation output insertion amount: " + inserted + " for " + requested);
            }
            if (inserted > 0L) batch.pendingOutput.remove(stack.what(), inserted);
            if (inserted < requested) {
                setOutputBlockedReason(OutputBlockedReason.NETWORK_INSERT);
                return false;
            }
        }
        boolean complete = isCounterEmpty(batch.pendingOutput);
        if (complete && batch.progress >= batch.processingTicks) notifyPatternResult(batch.unlockStack);
        return complete;
    }

    private void notifyPatternResult(@Nullable GenericStack result) {
        if (result == null || cluster == null) return;
        if (cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            communication.getWorkstationProvider()
                .onPatternResult(result);
        }
    }

    private void notifyPatternAborted(@Nullable GenericStack expectedResult) {
        if (expectedResult == null || cluster == null) return;
        if (cluster.getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            communication.getWorkstationProvider()
                .onPatternAborted(expectedResult);
        }
    }

    private void requestCommunicationProviderUpdate() {
        if (cluster != null && cluster
            .getCommunication() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity communication) {
            ICraftingProvider.requestUpdate(communication.getMainNode());
        }
    }

    private boolean acquireExtraInputs(PendingBatch batch) {
        return acquireExtraInputs(batch, batch.segmented);
    }

    private boolean acquireExtraInputs(PendingBatch batch, boolean segmented) {
        if (isCounterEmpty(batch.missingExtras)) return true;
        GridView grid = getMainNode().getGrid();
        if (grid == null) return false;
        if (segmented) {
            boolean complete = true;
            MEStorage storage = grid.getStorageService()
                .getInventory();
            for (GenericStack extra : counterEntries(batch.missingExtras)) {
                long owned = batch.inputTotal.get(extra.what());
                long target = portionAmount(
                    Math.addExact(owned, extra.amount()),
                    Math.min(LargeWorkstationOverclock.BASE_PARALLELISM, batch.craftCount),
                    batch.craftCount);
                long requested = Math.max(0L, target - owned);
                if (requested == 0) continue;
                long extracted = storage
                    .extract(extra.what(), requested, Actionable.MODULATE, IActionSource.ofMachine(this));
                if (extracted < 0 || extracted > requested)
                    throw new IllegalStateException("Invalid extra input extraction");
                if (extracted > 0) {
                    batch.inputTotal.add(extra.what(), extracted);
                    batch.missingExtras.remove(extra.what(), extracted);
                    setChanged();
                }
                if (extracted < requested) complete = false;
            }
            return complete;
        }
        return LargeWorkstationExtraInputs.acquire(
            batch.missingExtras,
            batch.inputTotal,
            grid.getStorageService()
                .getInventory(),
            IActionSource.ofMachine(this),
            this::setChanged);
    }

    private boolean recoverCounterToNetwork(KeyCounter counter) {
        if (isCounterEmpty(counter)) return true;
        GridView grid = getMainNode().getGrid();
        if (grid == null) return false;
        return LargeWorkstationExtraInputs.returnOwned(
            counter,
            grid.getStorageService()
                .getInventory(),
            IActionSource.ofMachine(this),
            this::setChanged);
    }

    @Nullable
    private PendingBatch createPendingBatch(IPatternDetails pattern, @Nullable KeyCounter[] holders, long craftCount,
        @Nullable UUID craftingJobId, @Nullable GenericStack unlockStack) {
        if (!formed || level == null || pattern == null) return null;
        LargeWorkstationOverclock profile = getCurrentBatchProfile();
        if (!profile.acceptsCraftCount(craftCount)) return null;
        // Inputs delivered by AE2 are retained in the workstation's unbounded ledger while
        // the batch runs. Recipe lookup is performed against one encoded pattern's actual input set,
        // while the owned ledger keeps the complete scaled amount for cancellation/refund.
        KeyCounter totalInputs = collectInputTotals(pattern, holders);
        if (totalInputs == null || isCounterEmpty(totalInputs)) return null;
        KeyCounter perCraftInputs = divideCounter(totalInputs, craftCount);
        if (perCraftInputs == null) return null;

        KeyCounter outputPerCraft = collectPatternOutputs(pattern);
        if (outputPerCraft == null) return null;
        var match = LargeWorkstationRecipes.findScaled(level.nativeWorld(), perCraftInputs, outputPerCraft);
        if (match == null) return null;
        var adapted = match.recipe();

        KeyCounter remainderTotal = collectRemainderTotals(pattern, holders);
        if (remainderTotal == null) return null;
        long energyPerCraft;
        long fePerCraft;
        int processingTicks;
        KeyCounter outputTotal;
        KeyCounter missingExtras = new KeyCounter();
        try {
            // craftCount counts encoded pattern executions. Each multiplied pattern must pay for
            // all of its base recipe operations, including extras absent from the encoded inputs.
            energyPerCraft = Math.multiplyExact(adapted.energy(), match.multiplier());
            fePerCraft = Math.multiplyExact(adapted.feEnergy(), match.multiplier());
            processingTicks = LargeWorkstationProcessing.duration(adapted.processingTicks(), match.multiplier());
            if (adapted.feAlternative()) {
                // Prefer AE; choose FE only at acceptance, then preserve this choice across shortages/reloads.
                GridView grid = getMainNode().getGrid();
                double firstTick = (double) energyPerCraft * craftCount / processingTicks;
                double available = extractAEPower(firstTick, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                if (grid != null) available = Math.max(
                    available,
                    grid.getEnergyService()
                        .extractAEPower(firstTick, Actionable.SIMULATE, PowerMultiplier.CONFIG));
                long firstFe = LargeWorkstationProcessing.fluxPayment(fePerCraft, craftCount, processingTicks, 0)
                    .amount();
                if (available + 0.001 < firstTick && false && grid != null && 0L >= firstFe) energyPerCraft = 0;
                else fePerCraft = 0;
            }
            outputTotal = scaleCounter(outputPerCraft, craftCount);
            for (var extra : adapted.extraInputs()) {
                if (perCraftInputs.get(extra.what()) == 0) {
                    long perPattern = Math.multiplyExact(extra.amount(), match.multiplier());
                    missingExtras.add(extra.what(), Math.multiplyExact(perPattern, craftCount));
                }
            }
        } catch (ArithmeticException overflow) {
            return null;
        }
        outputTotal.addAll(remainderTotal);
        if (isCounterEmpty(outputTotal)) return null;
        PendingBatch batch = new PendingBatch(
            craftCount,
            energyPerCraft,
            profile.coolingTier(),
            profile.energyMultiplier(),
            totalInputs,
            outputTotal,
            craftingJobId,
            adapted.id(),
            adapted.display(),
            unlockStack,
            level.getGameTime());
        batch.processingTicks = processingTicks;
        batch.fePerCraft = fePerCraft;
        batch.legacyTiming = false;
        batch.thermalWorkMultiplier = 1;
        // Compatibility-only inputs (for example AE2LT lightning) are not represented by
        // ordinary AE2 pattern holders. Keep them in the pending ledger so the actual
        // adapted recipe still charges them from the ME network before processing.
        batch.missingExtras.addAll(missingExtras);
        return batch;
    }

    @Nullable
    private static IntegratedWorkingStationRecipe.Input createRecipeInput(KeyCounter perCraftInputs) {
        List<ItemStack> items = new ArrayList<>();
        FluidStack fluid = null;
        for (var entry : perCraftInputs) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key == null || amount <= 0L) return null;
            if (key instanceof AEItemKey itemKey) {
                ItemStack one = itemKey.toStack();
                if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(one)) return null;
                int maxStackSize = Math.max(1, Math.min(64, one.getMaxStackSize()));
                while (amount > 0L) {
                    int count = (int) Math.min(amount, maxStackSize);
                    items.add(itemKey.toStack(count));
                    amount -= count;
                }
            } else if (key instanceof AEFluidKey fluidKey) {
                if (!cn.dancingsnow.neoecoae.platform.fluid.NativeFluidStacks.isEmpty(fluid)
                    || amount > Integer.MAX_VALUE) return null;
                fluid = fluidKey.toStack((int) amount);
            } else {
                return null;
            }
        }
        return new IntegratedWorkingStationRecipe.Input(items, fluid);
    }

    @Nullable
    private static KeyCounter collectInputTotals(IPatternDetails pattern, @Nullable KeyCounter[] holders) {
        KeyCounter result = new KeyCounter();
        if (holders != null) {
            for (KeyCounter holder : holders) {
                if (holder != null) result.addAll(holder);
            }
            return result;
        }
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null) return null;
        for (IPatternDetails.IInput input : inputs) {
            if (input == null || input.getMultiplier() <= 0L) return null;
            GenericStack[] possible = input.getPossibleInputs();
            if (possible == null || possible.length == 0
                || possible[0] == null
                || possible[0].what() == null
                || possible[0].amount() <= 0L) return null;
            result.add(possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier()));
        }
        return result;
    }

    @Nullable
    private static KeyCounter divideCounter(KeyCounter total, long divisor) {
        KeyCounter result = new KeyCounter();
        for (var entry : total) {
            long amount = entry.getLongValue();
            if (amount <= 0L || amount % divisor != 0L) return null;
            result.add(entry.getKey(), amount / divisor);
        }
        return result;
    }

    @Nullable
    private static KeyCounter collectPatternOutputs(IPatternDetails pattern) {
        KeyCounter result = new KeyCounter();
        List<GenericStack> outputs = pattern.getOutputs();
        if (outputs == null || outputs.isEmpty()) return null;
        for (GenericStack output : outputs) {
            if (output == null || output.what() == null || output.amount() <= 0L) return null;
            result.add(output.what(), output.amount());
        }
        return result;
    }

    @Nullable
    private static KeyCounter collectRemainderTotals(IPatternDetails pattern, @Nullable KeyCounter[] holders) {
        KeyCounter result = new KeyCounter();
        if (holders == null) return result;
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null || inputs.length != holders.length) return null;
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            KeyCounter holder = holders[slot];
            if (input == null || holder == null) return null;
            for (var entry : holder) {
                AEKey remainder = input.getRemainingKey(entry.getKey());
                if (remainder == null) continue;
                long templateAmount = templateAmount(input, entry.getKey());
                if (templateAmount <= 0L || entry.getLongValue() % templateAmount != 0L) return null;
                result.add(remainder, entry.getLongValue() / templateAmount);
            }
        }
        return result;
    }

    private static long templateAmount(IPatternDetails.IInput input, AEKey actual) {
        long exact = 0L;
        GenericStack[] possible = input.getPossibleInputs();
        if (possible == null) return 0L;
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.what() != null
                && candidate.what()
                    .equals(actual)) {
                if (exact != 0L && exact != candidate.amount()) return 0L;
                exact = candidate.amount();
            }
        }
        if (exact != 0L) return exact;
        for (GenericStack candidate : possible) {
            if (candidate != null && candidate.what() != null
                && candidate.what()
                    .getPrimaryKey()
                    .equals(actual.getPrimaryKey())) {
                if (exact != 0L && exact != candidate.amount()) return 0L;
                exact = candidate.amount();
            }
        }
        return exact;
    }

    private static KeyCounter scaleCounter(KeyCounter source, long multiplier) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) {
            result.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
        }
        return result;
    }

    private static boolean isCounterEmpty(KeyCounter counter) {
        for (var entry : counter) {
            if (entry.getLongValue() != 0L) return false;
        }
        return true;
    }

    private static List<GenericStack> counterEntries(KeyCounter counter) {
        List<GenericStack> result = new ArrayList<>();
        for (var entry : counter) {
            if (entry.getLongValue() > 0L) result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }

    private void flushOutputs() {
        if (!getMainNode().isOnline()) return;
        getMainNode().ifPresent((grid, node) -> {
            MEStorage storage = grid.getStorageService()
                .getInventory();
            GenericStack item = GenericStack.fromItemStack(outputInv.getStackInSlot(0));
            if (item != null) {
                long inserted = storage
                    .insert(item.what(), item.amount(), Actionable.MODULATE, IActionSource.ofMachine(this));
                if (inserted > 0) outputInv.extractItem(0, (int) inserted, false);
            }
        });
    }

    public void returnStoredInputs() {
        if (level == null || level.isClientSide) return;
        var batches = new ArrayList<>(pendingBatches);
        for (PendingBatch batch : batches) {
            if (batch.progress >= batch.processingTicks) continue;
            if (!deliverBatchOutputs(batch)) {
                setChanged();
                return;
            }
            if (!recoverCounterToNetwork(batch.inputTotal)) {
                setInputReturnBlockedReason();
                setChanged();
                return;
            }
            if (batch.craftingJobId != null) {
                ECOCraftingJobLifecycle.finish(level.nativeWorld(), batch.craftingJobId, false);
            }
            notifyPatternAborted(batch.unlockStack);
            pendingBatches.remove(batch);
        }
        PendingBatch first = pendingBatches.peekFirst();
        setProcessingTime(first == null ? 0 : first.progress);
        setWorking(first != null);

        setPauseReason(PauseReason.NONE);
        setChanged();
        requestCommunicationProviderUpdate();
    }

    private enum PauseReason {

        NONE("gui.neoecoae.large_integrated_working_station.pause.none"),
        OVERCLOCK_DISABLED("gui.neoecoae.large_integrated_working_station.pause.overclock_disabled"),
        COOLING_DISABLED("gui.neoecoae.large_integrated_working_station.pause.cooling_disabled"),
        COOLANT_MISSING("gui.neoecoae.large_integrated_working_station.pause.coolant_missing"),
        COOLANT_TIER_LOW("gui.neoecoae.large_integrated_working_station.pause.coolant_tier_low"),
        COOLANT_INSUFFICIENT("gui.neoecoae.large_integrated_working_station.pause.coolant_insufficient"),
        COOLANT_OUTPUT_BLOCKED("gui.neoecoae.large_integrated_working_station.pause.coolant_output_blocked"),
        POWER_MISSING("gui.neoecoae.large_integrated_working_station.pause.power_missing"),
        OUTPUT_BLOCKED("gui.neoecoae.large_integrated_working_station.pause.output_blocked"),
        INPUT_RETURN_BLOCKED("gui.neoecoae.large_integrated_working_station.pause.input_return_blocked"),
        LIGHTNING_MISSING("gui.neoecoae.large_integrated_working_station.pause.lightning_missing"),
        TEMPERATURE_LOW("gui.neoecoae.large_integrated_working_station.pause.temperature_low"),
        OVERHEATED("gui.neoecoae.large_integrated_working_station.pause.overheated"),
        APPLIED_FLUX_MISSING("gui.neoecoae.large_integrated_working_station.pause.applied_flux_missing"),
        FE_MISSING("gui.neoecoae.large_integrated_working_station.pause.fe_missing");

        private final String translationKey;

        PauseReason(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private enum OutputBlockedReason {

        NONE(null),
        UNKNOWN("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.unknown"),
        CPU_ROUTE("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.cpu_route"),
        CPU_INSERT("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.cpu_insert"),
        NETWORK_UNAVAILABLE(
            "gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.network_unavailable"),
        NETWORK_INSERT("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.network_insert"),
        INPUT_RETURN("gui.neoecoae.large_integrated_working_station.pause.output_blocked.detail.input_return");

        private final String translationKey;

        OutputBlockedReason(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record CoolingTickPlan(FluidStack input, LargeWorkstationCoolingOutput.Plan output) {}

    private static final class PendingBatch {

        /** Menu actions identify an owned batch, never its changing viewport index. */
        private final UUID interactionId = UUID.randomUUID();
        private long craftCount;
        private final long energyPerCraft;
        private int coolingTier;
        private final int energyMultiplier;
        private final KeyCounter inputTotal = new KeyCounter();
        private final KeyCounter outputTotal = new KeyCounter();
        private final KeyCounter pendingOutput = new KeyCounter();
        private final KeyCounter missingExtras = new KeyCounter();
        private final long createdTick;
        @Nullable
        private final UUID craftingJobId;
        @Nullable
        private ResourceLocation recipeId;
        @Nullable
        private IntegratedWorkingStationRecipe recipe;
        @Nullable
        private final GenericStack unlockStack;
        private int processingTicks = MAX_PROCESSING_STEPS;
        private long fePerCraft;
        private long feRemainder;
        private boolean legacyTiming = true;
        private int progress;
        /** Already-paid craft steps left over when a larger batch cannot express them as whole progress steps. */
        private long progressRemainder;
        private long thermalWorkMultiplier = 1;
        /** Temperature fallback uses 256-craft portions until this batch's overclock conditions recover. */
        private boolean segmented;

        private PendingBatch(long craftCount, long energyPerCraft, int coolingTier, int energyMultiplier,
            KeyCounter inputTotal, KeyCounter outputTotal, @Nullable UUID craftingJobId,
            @Nullable ResourceLocation recipeId, @Nullable IntegratedWorkingStationRecipe recipe,
            @Nullable GenericStack unlockStack, long createdTick) {
            this.craftCount = craftCount;
            this.energyPerCraft = energyPerCraft;
            this.coolingTier = coolingTier;
            this.energyMultiplier = energyMultiplier;
            this.inputTotal.addAll(inputTotal);
            this.outputTotal.addAll(outputTotal);
            this.craftingJobId = craftingJobId;
            this.recipeId = recipeId;
            this.recipe = recipe;
            this.unlockStack = unlockStack == null ? null : new GenericStack(unlockStack.what(), unlockStack.amount());
            this.createdTick = createdTick;
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("craftCount", craftCount);
            tag.putLong("energyPerCraft", energyPerCraft);
            tag.putInt("processingTicks", processingTicks);
            tag.putLong("fePerCraft", fePerCraft);
            tag.putLong("feRemainder", feRemainder);
            tag.putBoolean("legacyTiming", legacyTiming);
            tag.putInt("coolingTier", coolingTier);
            tag.putInt("energyMultiplier", energyMultiplier);
            tag.putInt("progress", progress);
            if (progressRemainder > 0) tag.putLong("progressRemainder", progressRemainder);
            if (segmented) tag.putBoolean("segmented", true);
            tag.putLong("thermalWorkMultiplier", thermalWorkMultiplier);
            tag.putLong("createdTick", createdTick);
            if (craftingJobId != null) tag.putUUID("craftingJobId", craftingJobId);
            if (recipeId != null) tag.putString("recipeId", recipeId.toString());
            long saveStackCount = countStackEntries(inputTotal) + countStackEntries(outputTotal)
                + countStackEntries(pendingOutput)
                + countStackEntries(missingExtras)
                + (unlockStack == null ? 0L : 1L);
            if (saveStackCount > MAX_SAVE_STACKS) {
                LOGGER.error(
                    "Skipping oversized large workstation batch save at {}: {} stack entries",
                    createdTick,
                    saveStackCount);
                tag.putBoolean("pendingStateTooLarge", true);
                return tag;
            }
            tag.put(
                "inputTotal",
                ECOFastPathStacks.writeGenericStacks(registries, ECOFastPathStacks.copyCounter(inputTotal)));
            tag.put(
                "missingExtras",
                ECOFastPathStacks.writeGenericStacks(registries, ECOFastPathStacks.copyCounter(missingExtras)));
            tag.put(
                "outputTotal",
                ECOFastPathStacks.writeGenericStacks(registries, ECOFastPathStacks.copyCounter(outputTotal)));
            tag.put(
                "pendingOutput",
                ECOFastPathStacks.writeGenericStacks(registries, ECOFastPathStacks.copyCounter(pendingOutput)));
            if (unlockStack != null) tag.put("unlockStack", GenericStack.writeTag(registries, unlockStack));
            return tag;
        }

        @Nullable
        private static PendingBatch load(CompoundTag tag, HolderLookup.Provider registries, long currentTick) {
            if (tag.getBoolean("pendingStateTooLarge")) {
                LOGGER.error("Discarding a large workstation batch whose persisted state exceeded the save limit");
                return null;
            }
            long craftCount = tag.getLong("craftCount");
            long energyPerCraft = tag.getLong("energyPerCraft");
            int processingTicks = tag.contains("processingTicks") ? tag.getInt("processingTicks")
                : MAX_PROCESSING_STEPS;
            long fePerCraft = tag.getLong("fePerCraft");
            long feRemainder = tag.getLong("feRemainder");
            boolean legacyTiming = !tag.contains("processingTicks") || tag.getBoolean("legacyTiming");
            int coolingTier = tag.contains("coolingTier", Tag.TAG_INT) ? tag.getInt("coolingTier") : 0;
            int energyMultiplier = tag.contains("energyMultiplier", Tag.TAG_INT) ? tag.getInt("energyMultiplier") : 1;
            int progress = tag.getInt("progress");
            long progressRemainder = tag.getLong("progressRemainder");
            boolean segmented = tag.getBoolean("segmented");
            LargeWorkstationOverclock profile = LargeWorkstationOverclock.fromPersisted(coolingTier, energyMultiplier);
            // Grandfather already-owned normal batches accepted under the old 1,024 limit.
            long savedLimit = coolingTier == 0 ? 1_024 : profile == null ? 0 : profile.maxParallelism();
            if (processingTicks <= 0 || fePerCraft < 0
                || feRemainder < 0
                || feRemainder >= processingTicks
                || fePerCraft == 0 && feRemainder != 0
                || progress == processingTicks && feRemainder != 0
                || profile == null
                || craftCount <= 0
                || craftCount > savedLimit
                || energyPerCraft < 0
                || progress < 0
                || progress > processingTicks
                || progressRemainder < 0
                || progressRemainder >= craftCount
                || progress == processingTicks && progressRemainder != 0) {
                return null;
            }
            KeyCounter inputTotal = readCounter(registries, tag, "inputTotal", false);
            KeyCounter outputTotal = readCounter(registries, tag, "outputTotal", true);
            KeyCounter pendingOutput = readCounter(registries, tag, "pendingOutput", false);
            KeyCounter missingExtras = readCounter(registries, tag, "missingExtras", false);
            if (inputTotal == null || outputTotal == null
                || pendingOutput == null
                || missingExtras == null
                || !segmented && (progress > 0 || progressRemainder > 0) && !missingExtras.isEmpty()
                || progress < processingTicks && inputTotal.isEmpty()
                || progress >= processingTicks && pendingOutput.isEmpty()) {
                return null;
            }
            UUID craftingJobId = tag.hasUUID("craftingJobId") ? tag.getUUID("craftingJobId") : null;
            ResourceLocation recipeId = tag.contains("recipeId", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("recipeId"))
                : null;
            GenericStack unlockStack = tag.contains("unlockStack", Tag.TAG_COMPOUND)
                ? GenericStack.readTag(registries, tag.getCompound("unlockStack"))
                : null;
            long createdTick = tag.contains("createdTick", Tag.TAG_LONG) ? tag.getLong("createdTick") : currentTick;
            PendingBatch batch = new PendingBatch(
                craftCount,
                energyPerCraft,
                profile.coolingTier(),
                profile.energyMultiplier(),
                inputTotal,
                outputTotal,
                craftingJobId,
                recipeId,
                null,
                unlockStack,
                createdTick);
            batch.pendingOutput.addAll(pendingOutput);
            batch.missingExtras.addAll(missingExtras);
            batch.processingTicks = processingTicks;
            batch.fePerCraft = fePerCraft;
            batch.feRemainder = feRemainder;
            batch.legacyTiming = legacyTiming;
            batch.progress = progress;
            batch.progressRemainder = progressRemainder;
            batch.segmented = segmented;
            batch.thermalWorkMultiplier = tag.contains("thermalWorkMultiplier", Tag.TAG_LONG)
                ? Math.max(1, tag.getLong("thermalWorkMultiplier"))
                : 1;
            return batch;
        }

        private static long countStackEntries(KeyCounter counter) {
            long count = 0L;
            for (var entry : counter) {
                if (entry.getLongValue() > 0L) count++;
            }
            return count;
        }

        @Nullable
        private static KeyCounter readCounter(HolderLookup.Provider registries, CompoundTag tag, String name,
            boolean requireNonEmpty) {
            ListTag entries = tag.getList(name, Tag.TAG_COMPOUND);
            if (entries.size() > ECOBatchCraftingHelper.MAX_BATCH_STACK_ENTRIES || requireNonEmpty && entries.isEmpty())
                return null;
            try {
                KeyCounter result = new KeyCounter();
                for (int i = 0; i < entries.size(); i++) {
                    GenericStack stack = GenericStack.readTag(registries, entries.getCompound(i));
                    if (stack == null || stack.amount() <= 0
                        || stack.amount() > ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT) return null;
                    long total = result.get(stack.what());
                    if (total > ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT - stack.amount()) return null;
                    result.add(stack.what(), stack.amount());
                }
                return result;
            } catch (RuntimeException failure) {
                return null;
            }
        }
    }
}
