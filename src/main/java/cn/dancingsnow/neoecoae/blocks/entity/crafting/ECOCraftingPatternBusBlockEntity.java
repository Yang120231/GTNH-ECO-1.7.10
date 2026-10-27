package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeSourceCraftingMedium;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.platform.inventory.InternalInventoryHost;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.sync.FieldManagedStorage;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;

public class ECOCraftingPatternBusBlockEntity extends NEBlockEntity<NECraftingCluster, ECOCraftingPatternBusBlockEntity>
    implements ManagedHolder, InternalInventoryHost, ECOJobAwareCraftingProvider, ECOFastPathDispatchProvider,
    appeng.api.networking.crafting.ICraftingProvider, NativeSourceCraftingMedium {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    @Persisted
    private final AppEngInternalInventory inventory = new AppEngInternalInventory(this, 63, 1) {

        @Override
        public boolean isItemValid(int slot, net.minecraft.item.ItemStack stack) {
            if (stack == null || !(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem))
                return false;
            if (worldObj == null) return true;
            try {
                var pattern = ((appeng.api.implementations.ICraftingPatternItem) stack.getItem())
                    .getPatternForItem(stack, worldObj);
                return pattern != null && pattern.isCraftable();
            } catch (RuntimeException invalid) {
                return false;
            }
        }
    };
    private final ECOCraftingPatternBusDispatcher dispatcher = new ECOCraftingPatternBusDispatcher(this);

    public ECOCraftingPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NECraftingClusterCalculator::new);
    }

    public FieldManagedStorage getSyncStorage() {
        return syncStorage;
    }

    public AppEngInternalInventory getInternalInventory() {
        return inventory;
    }

    public ECOCraftingSystemBlockEntity getCraftingController() {
        return cluster == null ? null : cluster.getController();
    }

    public NECraftingCluster getCraftingCluster() {
        return cluster;
    }

    public ICraftingProvider sourceCraftingProvider() {
        return this;
    }

    public List<IPatternDetails> getAvailablePatterns() {
        List<IPatternDetails> out = new ArrayList<>();
        if (worldObj == null || !formed) return out;
        List<ECOCraftingPatternBusBlockEntity> buses = cluster == null ? Collections.singletonList(this)
            : cluster.getPatternDomainBuses();
        for (var bus : buses) for (int slot = 0; slot < bus.inventory.size(); slot++) {
            var stack = bus.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem))
                continue;
            try {
                var nativePattern = ((appeng.api.implementations.ICraftingPatternItem) stack.getItem())
                    .getPatternForItem(stack, worldObj);
                if (nativePattern != null && nativePattern.isCraftable())
                    out.add(NativePatternDetails.capture(nativePattern, worldObj, Collections.emptyList()));
            } catch (RuntimeException invalid) {
                cn.dancingsnow.neoecoae.NeoECOAE.LOG
                    .warn("Invalid ECO bus pattern at {} slot {}", getBlockPos(), slot, invalid);
            }
        }
        return out;
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return pushPattern(pattern, inputs, null);
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, UUID job) {
        return dispatcher.pushPattern(pattern, inputs, job);
    }

    public boolean pushPattern(appeng.api.networking.crafting.ICraftingPatternDetails details,
        net.minecraft.inventory.InventoryCrafting table) {
        var pattern = NativePatternDetails.capture(details, worldObj, Collections.emptyList());
        KeyCounter[] inputs = new KeyCounter[pattern.getInputs().length];
        for (int i = 0; i < inputs.length; i++) inputs[i] = new KeyCounter();
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            var stack = table instanceof appeng.util.inv.MEInventoryCrafting
                ? ((appeng.util.inv.MEInventoryCrafting) table).getAEStackInSlot(slot)
                : appeng.util.item.AEItemStack.create(table.getStackInSlot(slot));
            if (stack == null) continue;
            var key = AEKey.fromNative(stack);
            for (int i = 0; i < inputs.length; i++) {
                for (var possible : pattern.getInputs()[i].getPossibleInputs()) if (possible.what()
                    .equals(key)) {
                        inputs[i].add(key, stack.getStackSize());
                        break;
                    }
            }
        }
        return pushPattern(pattern, inputs, null);
    }

    public void provideCrafting(appeng.api.networking.crafting.ICraftingProviderHelper helper) {
        for (var pattern : getAvailablePatterns())
            helper.addCraftingOption(this, ((NativePatternDetails) pattern).nativeDetails());
    }

    public boolean isBusy() {
        return dispatcher.isBusy();
    }

    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return dispatcher.prepareFastPath(context);
    }

    public ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, java.math.BigInteger requested) {
        return dispatcher.prepareExactFastPath(context, requested);
    }

    public void saveChangedInventory(AppEngInternalInventory inv) {
        setChanged();
        refreshAdvertisedPatterns();
    }

    public void refreshAdvertisedPatterns() {
        ICraftingProvider.requestUpdate(getMainNode());
    }

    @Override
    public void onReady() {
        super.onReady();
        refreshAdvertisedPatterns();
    }

    public int getPatternCount() {
        return getAvailablePatterns().size();
    }

    @com.github.bsideup.jabel.Desugar
    public record BatchFastPathOffer(ECOCraftingWorkerBlockEntity worker, ECOVerifiedFastPathRecipe recipe,
        int maxBatchSize) {}

    @com.github.bsideup.jabel.Desugar
    public record VirtualFastPathOffer(ECOCraftingWorkerBlockEntity worker, ECOVerifiedFastPathRecipe recipe) {}

    @Override
    public void addAdditionalDrops(WorldView world, BlockPos pos, List<net.minecraft.item.ItemStack> drops) {
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStackInSlot(i) != null) drops.add(
            inventory.getStackInSlot(i)
                .copy());
    }

    static int calculateBatchOfferSize(int requested, int worker, int host, long arithmetic) {
        return (int) Math
            .min(Math.min(Math.max(0, requested), Math.min(worker, host)), Math.min(Integer.MAX_VALUE, arithmetic));
    }
}
