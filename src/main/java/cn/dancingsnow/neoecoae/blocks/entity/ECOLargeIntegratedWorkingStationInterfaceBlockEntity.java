package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import appeng.api.networking.GridFlags;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeSourceCraftingMedium;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NodeListener;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Native AE2 medium for the large workstation, retaining the source batch and job ownership contracts. */
public class ECOLargeIntegratedWorkingStationInterfaceBlockEntity
    extends ECOMachineInterfaceBlockEntity<NEIntegratedWorkingStationCluster> implements
    appeng.api.networking.crafting.ICraftingProvider, NativeSourceCraftingMedium, ECOJobAwareCraftingProvider,
    ECOParallelCraftingProvider, appeng.api.util.IInterfaceViewable, appeng.api.implementations.IUpgradeableHost {

    private final LargeWorkstationPatternProvider workstationProvider = new LargeWorkstationPatternProvider(this);

    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
        NEClusterCalculator.Factory<NEIntegratedWorkingStationCluster> calculator) {
        super(type, pos, state, calculator);
        getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
            .addService(ICraftingProvider.class, this);
    }

    public LargeWorkstationPatternProvider getWorkstationProvider() {
        return workstationProvider;
    }

    public int getWorkstationPatternRows() {
        return workstationProvider.getRequiredPatternRows();
    }

    public appeng.parts.automation.UpgradeInventory getUpgrades() {
        return workstationProvider.getUpgrades();
    }

    /** AE2 upgrade cards Shift-right-clicked onto the block insert through this inventory's own slot limits. */
    @Override
    public net.minecraft.inventory.IInventory getInventoryByName(String name) {
        if ("upgrades".equals(name)) return getUpgrades();
        if ("patterns".equals(name)) return workstationProvider.getPatternInv();
        return null;
    }

    @Override
    public int getInstalledUpgrades(appeng.api.config.Upgrades upgrade) {
        return getUpgrades().getInstalledUpgrades(upgrade);
    }

    @Override
    public appeng.api.util.IConfigManager getConfigManager() {
        return workstationProvider.getConfigManager();
    }

    @Override
    public ICraftingProvider sourceCraftingProvider() {
        return this;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return workstationProvider.getAvailablePatterns();
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        for (IPatternDetails pattern : getAvailablePatterns()) if (pattern instanceof NativePatternDetails)
            helper.addCraftingOption(this, ((NativePatternDetails) pattern).nativeDetails());
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        if (worldObj == null || details == null || table == null) return false;
        IPatternDetails pattern = NativePatternDetails.capture(details, worldObj, Collections.emptyList());
        KeyCounter total = new KeyCounter();
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            IAEStack<?> stack = table instanceof appeng.util.inv.MEInventoryCrafting
                ? ((appeng.util.inv.MEInventoryCrafting) table).getAEStackInSlot(slot)
                : appeng.util.item.AEItemStack.create(table.getStackInSlot(slot));
            if (stack == null || stack.getStackSize() <= 0) continue;
            AEKey key = AEKey.fromNative(stack);
            if (key == null) return false;
            try {
                total.set(key, Math.addExact(total.get(key), stack.getStackSize()));
            } catch (ArithmeticException overflow) {
                return false;
            }
        }
        KeyCounter[] inputs = splitNativeInputs(pattern, total);
        return inputs != null && pushPattern(pattern, inputs);
    }

    /** Each condensed input owns a distinct counter, including fluid inputs; never duplicate a table entry. */
    static KeyCounter[] splitNativeInputs(IPatternDetails pattern, KeyCounter total) {
        IPatternDetails.IInput[] requirements = pattern.getInputs();
        if (requirements == null) return null;
        KeyCounter remaining = total.copy();
        KeyCounter[] result = new KeyCounter[requirements.length];
        for (int index = 0; index < requirements.length; index++) {
            result[index] = new KeyCounter();
            IPatternDetails.IInput requirement = requirements[index];
            if (requirement == null || requirement.getMultiplier() <= 0) return null;
            var choices = requirement.getPossibleInputs();
            if (choices == null) return null;
            boolean matched = false;
            for (var choice : choices) {
                if (choice == null || choice.what() == null || choice.amount() <= 0) continue;
                long count;
                try {
                    count = Math.multiplyExact(choice.amount(), requirement.getMultiplier());
                } catch (ArithmeticException overflow) {
                    return null;
                }
                if (remaining.get(choice.what()) < count) continue;
                result[index].set(choice.what(), count);
                remaining.remove(choice.what(), count);
                matched = true;
                break;
            }
            if (!matched) return null;
        }
        return remaining.isEmpty() ? result : null;
    }

    @Override
    public appeng.api.util.DimensionalCoord getLocation() {
        return new appeng.api.util.DimensionalCoord(this);
    }

    @Override
    public int rows() {
        return getWorkstationPatternRows();
    }

    @Override
    public int rowSize() {
        return LargeWorkstationPatternProvider.PATTERN_COLUMNS;
    }

    @Override
    public net.minecraft.inventory.IInventory getPatterns() {
        return workstationProvider.getPatternInv();
    }

    @Override
    public String getName() {
        return net.minecraft.util.StatCollector.translateToLocal(getRawName());
    }

    @Override
    public String getRawName() {
        return "block.neoecoae.large_integrated_working_station_interface.name";
    }

    @Override
    public net.minecraft.tileentity.TileEntity getTileEntity() {
        return this;
    }

    @Override
    public boolean shouldDisplay() {
        return workstationProvider.getConfigManager()
            .getSetting(appeng.api.config.Settings.INTERFACE_TERMINAL) == appeng.api.config.YesNo.YES;
    }

    @Override
    public ItemStack getSelfRep() {
        return getItemFromBlockEntity();
    }

    @Override
    public ItemStack getDisplayRep() {
        return getCluster() == null || getCluster().getController() == null ? getSelfRep()
            : getCluster().getController()
                .getItemFromBlockEntity();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return workstationProvider.pushPattern(pattern, inputs);
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, UUID job) {
        return workstationProvider.eco$pushPatternBatch(pattern, inputs, 1L, job);
    }

    @Override
    public int eco$getAvailableParallelSlots() {
        return workstationProvider.eco$getAvailableParallelSlots();
    }

    @Override
    public boolean eco$pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputs, long count, UUID job) {
        return workstationProvider.eco$pushPatternBatch(pattern, inputs, count, job);
    }

    @Override
    public boolean isBusy() {
        return workstationProvider.isBusy();
    }

    @Override
    public void onReady() {
        super.onReady();
        workstationProvider.updatePatterns();
        workstationProvider.updateRedstoneState();
    }

    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.TICK)
    public void tickProviderRedstone() {
        if (worldObj != null && !worldObj.isRemote) workstationProvider.updateRedstoneState();
    }

    @Override
    public void updateCluster(NEIntegratedWorkingStationCluster next) {
        super.updateCluster(next);
        workstationProvider.updatePatterns();
    }

    @Override
    public void onMainNodeStateChanged(NodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        workstationProvider.updatePatterns();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        workstationProvider.writeToNBT(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        workstationProvider.readFromNBT(data, registries);
    }

    @Override
    public void addAdditionalDrops(cn.dancingsnow.neoecoae.platform.minecraft.WorldView world, BlockPos pos,
        List<ItemStack> drops) {
        super.addAdditionalDrops(world, pos, drops);
        for (int slot = 0; slot < workstationProvider.getPatternInv()
            .getSizeInventory(); slot++) {
            ItemStack stack = workstationProvider.getPatternInv()
                .getStackInSlot(slot);
            if (stack != null) drops.add(stack.copy());
        }
        for (int slot = 0; slot < getUpgrades().getSizeInventory(); slot++) {
            ItemStack stack = getUpgrades().getStackInSlot(slot);
            if (stack != null) drops.add(stack.copy());
        }
    }
}
