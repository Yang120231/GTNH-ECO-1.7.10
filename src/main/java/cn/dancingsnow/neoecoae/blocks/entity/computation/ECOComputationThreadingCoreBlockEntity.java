package cn.dancingsnow.neoecoae.blocks.entity.computation;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

public class ECOComputationThreadingCoreBlockEntity extends
    cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster, ECOComputationThreadingCoreBlockEntity>
    implements cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPUOwner {

    private static final Logger LOGGER = LogManager.getLogger(ECOComputationThreadingCoreBlockEntity.class);

    private final StorageTierDescriptor tier;

    private final ECOCraftingCPU[] cpus;
    private final CompoundTag[] deferredInit;
    private final java.util.BitSet packedExactInventories = new java.util.BitSet();

    public ECOComputationThreadingCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        StorageTierDescriptor tier) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator::new);
        this.tier = tier;
        cpus = new ECOCraftingCPU[tier.getCPUThreads()];
        deferredInit = new CompoundTag[tier.getCPUThreads()];
    }

    @Nullable
    public ECOCraftingCPU spawn(ICraftingPlan plan) {
        for (int i = 0; i < cpus.length; i++) {
            if (cpus[i] == null) {
                ECOCraftingCPU cpu = new ECOCraftingCPU(cluster, plan, this);
                cpus[i] = cpu;
                markForUpdate();
                return cpu;
            }
        }
        return null;
    }

    public boolean isWorking() {
        for (ECOCraftingCPU cpu : cpus) {
            if (cpu == null) continue;
            return true;
        }
        return false;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        for (int i = 0; i < cpus.length; i++) {
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                CompoundTag tag = new CompoundTag();
                cpu.writeToNBT(tag, registries);
                data.put("CPU" + i, tag);
            } else if (deferredInit[i] != null) {
                data.put("CPU" + i, deferredInit[i].copy());
            }
        }
    }

    @Override
    public void updateCluster(@Nullable NEComputationCluster cluster) {
        NEComputationCluster previous = this.cluster;
        if (cluster == null && previous != null) {
            HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
            if (registries != null) {
                for (int i = 0; i < cpus.length; i++) {
                    ECOCraftingCPU cpu = cpus[i];
                    if (cpu == null) {
                        continue;
                    }
                    CompoundTag tag = new CompoundTag();
                    cpu.writeToNBT(tag, registries);
                    deferredInit[i] = tag;
                    previous.deactivate(cpu.getPlan());
                    cpus[i] = null;
                }
                setChanged();
            }
        }
        super.updateCluster(cluster);
        if (cluster != null) {
            for (int i = 0; i < deferredInit.length; i++) {
                CompoundTag tag = deferredInit[i];
                if (tag != null) {
                    HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
                    if (registries == null) {
                        continue;
                    }
                    ECOCraftingCPU cpu = new ECOCraftingCPU(cluster, null, this);
                    try {
                        cpu.nativeCPU();
                        cpu.readFromNBT(tag, registries);
                        if (cpu.getPlan() != null) {
                            ECOCraftingJobLifecycle.resumePersistedJob(level.nativeWorld(), tag);
                            cpus[i] = cpu;
                            deferredInit[i] = null;
                            cluster.pickup(cpu.getPlan(), cpu);
                        } else {
                            LOGGER.error(
                                "Deferred ECO crafting CPU at {} has no valid plan; keeping it quarantined for retry",
                                worldPosition);
                        }
                    } catch (RuntimeException e) {
                        LOGGER.error(
                            "Unable to restore deferred ECO crafting CPU at {}; keeping its data for retry",
                            worldPosition,
                            e);
                    }
                }
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        Arrays.fill(cpus, null);
        Arrays.fill(deferredInit, null);
        for (int i = 0; i < cpus.length; i++) {
            if (data.contains("CPU" + i)) {
                deferredInit[i] = data.getCompound("CPU" + i);
            }
        }
    }

    /** Ends ownership without dropping inventory; callers retain or drop only the unreturned remainder. */
    public void prepareForPermanentRemoval() {
        if (level == null || level.isClientSide()) return;
        for (int i = 0; i < cpus.length; i++) {
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) cpu.getLogic()
                .cancel();
            if (deferredInit[i] != null) {
                ECOCraftingJobLifecycle.cancelPersistedJob(level.nativeWorld(), deferredInit[i]);
            }
        }
    }

    @Override
    public void breakCluster() {
        // A previously dismantled core may have deferred jobs but no cluster to notify.
        prepareForPermanentRemoval();
        super.breakCluster();
    }

    @Override
    public void addAdditionalDrops(WorldView level, BlockPos pos, List<ItemStack> drops) {
        prepareForPermanentRemoval();
        super.addAdditionalDrops(level, pos, drops);
        HolderLookup.Provider registries = level.registryAccess();
        for (int i = 0; i < cpus.length; i++) {
            if (packedExactInventories.get(i)) continue;
            KeyCounter owned = new KeyCounter();
            ECOCraftingCPU cpu = cpus[i];
            if (cpu != null) {
                cpu.getLogic()
                    .getOwnedItems(owned);
            } else if (deferredInit[i] != null) {
                collectDeferredOwnedItems(deferredInit[i], registries, owned);
            }
            addOwnedDrops(owned, drops, level, pos);
        }
    }

    @Override
    public void clearContent() {
        // AE2 calls this after collecting drops (also when wrenching). Do not serialize those items again.
        prepareForPermanentRemoval();
        for (ECOCraftingCPU cpu : cpus) {
            if (cpu != null) cpu.getLogic()
                .getInventory()
                .clear();
        }
        Arrays.fill(cpus, null);
        Arrays.fill(deferredInit, null);
        setChanged();
        super.clearContent();
    }

    /** Preserve exact-order leftovers as one portable inventory snapshot on the existing core drop. */
    public void packExactInventories(ItemStack drop) {
        if (level == null || level.isClientSide()) return;
        var recovery = new CompoundTag();
        for (int i = 0; i < cpus.length; i++) {
            var cpu = cpus[i];
            boolean exact = cpu != null ? cpu.getLogic()
                .exactInventory()
                .isEnabled()
                : deferredInit[i] != null
                    && (deferredInit[i].getBoolean("ecoExactInventory") || deferredInit[i].getCompound("job")
                        .getBoolean("exactOrder"));
            if (cpu == null && deferredInit[i] == null) continue;
            CompoundTag saved;
            if (cpu != null) {
                cpu.getLogic()
                    .cancel();
                saved = new CompoundTag();
                cpu.writeToNBT(saved, level.registryAccess());
            } else {
                saved = deferredInit[i].copy();
                ECOCraftingJobLifecycle.cancelPersistedJob(level.nativeWorld(), saved);
            }
            saved.remove("job");
            recovery.put("CPU" + i, saved);
            packedExactInventories.set(i);
        }
        if (!recovery.isEmpty()) {
            if (!drop.hasTagCompound()) drop.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
            drop.getTagCompound()
                .setTag("ecoExactCpuRecovery", recovery.nativeTag());
        }
    }

    public void restoreExactInventories(ItemStack stack) {
        var data = new CompoundTag(
            stack.hasTagCompound() ? stack.getTagCompound()
                .getCompoundTag("ecoExactCpuRecovery") : new net.minecraft.nbt.NBTTagCompound());
        for (int i = 0; i < deferredInit.length; i++) {
            if (data.contains("CPU" + i) && cpus[i] == null && deferredInit[i] == null) {
                deferredInit[i] = data.getCompound("CPU" + i)
                    .copy();
            }
        }
        setChanged();
        if (cluster != null) updateCluster(cluster);
    }

    private static void collectDeferredOwnedItems(CompoundTag cpuTag, HolderLookup.Provider registries,
        KeyCounter out) {
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        inventory.readFromNBT(cpuTag.getList("inventory", Tag.TAG_COMPOUND), registries);
        out.addAll(inventory.list);

        CompoundTag jobTag = cpuTag.getCompound("job");
        long buffered = Math.max(0L, jobTag.getLong("bufferedFinalOutput"));
        if (buffered <= 0L) {
            return;
        }
        try {
            GenericStack finalOutput = GenericStack.readTag(registries, jobTag.getCompound("finalOutput"));
            if (finalOutput != null) {
                out.add(finalOutput.what(), buffered);
            }
        } catch (RuntimeException e) {
            LOGGER.error("Unable to decode buffered output from deferred ECO crafting CPU", e);
        }
    }

    private static void addOwnedDrops(KeyCounter owned, List<ItemStack> drops, WorldView level, BlockPos pos) {
        for (Object2LongMap.Entry<AEKey> entry : owned) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                long amount = entry.getLongValue();
                while (amount > 0L) {
                    int taken = (int) Math.min(
                        amount,
                        itemKey.toStack(1)
                            .getMaxStackSize());
                    amount -= taken;
                    drops.add(itemKey.toStack(taken));
                }
            } else {
                LOGGER.warn("Non-item CPU custody is preserved by the portable core snapshot at {}", pos);
            }
        }
    }

    public void deactivate(ECOCraftingCPU cpu) {
        for (int i = 0; i < cpus.length; i++) {
            if (cpus[i] == cpu) {
                cpus[i] = null;
            }
        }
        markForUpdate();
    }

    public StorageTierDescriptor getTier() {
        return tier;
    }
}
