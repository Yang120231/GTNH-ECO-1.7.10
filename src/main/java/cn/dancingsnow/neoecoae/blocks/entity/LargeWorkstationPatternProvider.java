package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.util.IConfigManager;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/**
 * Source pattern-capacity, compatibility, atomic parallel dispatch and result/pulse locks over native AE2 inventories.
 */
public final class LargeWorkstationPatternProvider
    implements ICraftingProvider, ECOParallelCraftingProvider, IAEAppEngInventory, IConfigManagerHost,
    cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider {

    public static final int PATTERN_COLUMNS = 9, BASE_PATTERN_ROWS = 4, MAX_CAPACITY_CARDS = 4, UPGRADE_SLOTS = 5,
        MAX_PATTERN_SLOTS = 72;
    private final ECOLargeIntegratedWorkingStationInterfaceBlockEntity host;
    private final appeng.tile.inventory.AppEngInternalInventory patterns = new appeng.tile.inventory.AppEngInternalInventory(
        this,
        MAX_PATTERN_SLOTS) {

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return canInsertPattern(slot) && !ItemStacks.isEmpty(stack)
                && stack.getItem() instanceof ICraftingPatternItem;
        }
    };
    private final UpgradeInventory upgrades = new UpgradeInventory(this, UPGRADE_SLOTS) {

        @Override
        public int getMaxInstalled(Upgrades upgrade) {
            return upgrade == Upgrades.CAPACITY ? 4
                : upgrade == Upgrades.REDSTONE || upgrade == Upgrades.LOCK_CRAFTING ? 1 : 0;
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = getStackInSlot(slot);
            if (!ItemStacks.isEmpty(stack) && stack.getItem() instanceof appeng.api.implementations.items.IUpgradeModule
                && ((appeng.api.implementations.items.IUpgradeModule) stack.getItem()).getType(stack)
                    == Upgrades.CAPACITY
                && !canRemoveCapacityCard()) return null;
            return super.decrStackSize(slot, amount);
        }
    };
    private final ConfigManager config = new ConfigManager(this);
    private List<IPatternDetails> compatiblePatterns = Collections.emptyList();
    private Object lastController, lastRecipes;
    private boolean dirty = true, waitingForPulse, redstone;
    private GenericStack unlockStack;
    private int revision;
    private cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target processingTarget;

    @Override
    public cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target neoecoae$getProcessingTarget() {
        return processingTarget;
    }

    public LargeWorkstationPatternProvider(ECOLargeIntegratedWorkingStationInterfaceBlockEntity host) {
        this.host = host;
        config.registerSetting(Settings.BLOCK, YesNo.NO);
        config.registerSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
        config.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
    }

    public IInventory getPatternInv() {
        return patterns;
    }

    public IInventory getMenuPatternInventory() {
        return patterns;
    }

    public UpgradeInventory getCapacityUpgrades() {
        return upgrades;
    }

    public UpgradeInventory getUpgrades() {
        return upgrades;
    }

    public IConfigManager getConfigManager() {
        return config;
    }

    public int getUnlockedPatternRows() {
        return BASE_PATTERN_ROWS
            + Math.max(0, Math.min(MAX_CAPACITY_CARDS, upgrades.getInstalledUpgrades(Upgrades.CAPACITY)));
    }

    public boolean canInsertPattern(int slot) {
        return slot >= 0 && slot < getUnlockedPatternRows() * PATTERN_COLUMNS;
    }

    public boolean canRemoveCapacityCard() {
        int remaining = Math.max(BASE_PATTERN_ROWS, getUnlockedPatternRows() - 1) * PATTERN_COLUMNS;
        for (int slot = remaining; slot < MAX_PATTERN_SLOTS; slot++)
            if (!ItemStacks.isEmpty(patterns.getStackInSlot(slot))) return false;
        return true;
    }

    public int getRequiredPatternRows() {
        for (int slot = MAX_PATTERN_SLOTS - 1; slot >= 0; slot--)
            if (!ItemStacks.isEmpty(patterns.getStackInSlot(slot)))
                return Math.max(getUnlockedPatternRows(), slot / PATTERN_COLUMNS + 1);
        return getUnlockedPatternRows();
    }

    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity getWorkstationInterface() {
        return host;
    }

    private ECOLargeIntegratedWorkingStationBlockEntity controller() {
        return host.getCluster() == null ? null
            : host.getCluster()
                .getController();
    }

    public int getAdvertisementRevision() {
        getAvailablePatterns();
        return revision;
    }

    public boolean advertises(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        for (var pattern : getAvailablePatterns()) if (pattern.getDefinition()
            .equals(key)) return true;
        return false;
    }

    public boolean accepts(ItemStack stack) {
        var c = controller();
        if (c == null || host.getWorldObj() == null) return false;
        var p = PatternDetailsHelper.decodePattern(AEItemKey.of(stack), host.getWorldObj());
        return p != null && c.acceptPattern(p, null, false);
    }

    public void updatePatterns() {
        dirty = true;
        revision++;
        requestProviderUpdate();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var controller = controller();
        if (host.getWorldObj() == null || controller == null) {
            compatiblePatterns = Collections.emptyList();
            lastController = null;
            return compatiblePatterns;
        }
        Object recipes = cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes.getAll(host.getWorldObj());
        if (!dirty && lastController == controller && lastRecipes == recipes) return compatiblePatterns;
        List<IPatternDetails> result = new ArrayList<>();
        for (int slot = 0; slot < getRequiredPatternRows() * PATTERN_COLUMNS; slot++) {
            var pattern = PatternDetailsHelper
                .decodePattern(AEItemKey.of(patterns.getStackInSlot(slot)), host.getWorldObj());
            if (pattern != null && controller.acceptPattern(pattern, null, false)) result.add(pattern);
        }
        compatiblePatterns = Collections.unmodifiableList(result);
        lastController = controller;
        lastRecipes = recipes;
        dirty = false;
        revision++;
        return compatiblePatterns;
    }

    private boolean canDispatch(IPatternDetails pattern) {
        var c = controller();
        if (c == null || !host.getMainNode()
            .isOnline() || isCraftingLocked()) return false;
        boolean offered = false;
        for (var p : getAvailablePatterns()) if (p.getDefinition()
            .equals(pattern.getDefinition())) offered = true;
        if (!offered) return false;
        if (config.getSetting(Settings.BLOCK) != YesNo.YES) return true;
        Set<AEKey> keys = new HashSet<>();
        for (var input : pattern.getInputs()) for (var choice : input.getPossibleInputs()) if (choice != null) keys.add(
            choice.what()
                .dropSecondary());
        return !c.containsPendingPatternInput(keys);
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (!canDispatch(pattern)) return false;
        boolean accepted = controller().acceptPattern(pattern, inputs, true);
        if (accepted) onPatternAccepted(pattern);
        return accepted;
    }

    @Override
    public int eco$getAvailableParallelSlots() {
        var c = controller();
        return c == null || !host.getMainNode()
            .isOnline() || !c.canAcceptPattern() || isCraftingLocked() || getAvailablePatterns().isEmpty() ? 0
                : c.getMaxBatchParallelism();
    }

    @Override
    public boolean eco$pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputs, long count, UUID job) {
        if (!canDispatch(pattern)) return false;
        boolean accepted = controller().acceptPatternBatch(pattern, inputs, count, job);
        if (accepted) onPatternAccepted(pattern);
        return accepted;
    }

    @Override
    public boolean isBusy() {
        var c = controller();
        return c == null || !c.canAcceptPattern() || isCraftingLocked();
    }

    private boolean isCraftingLocked() {
        var mode = config.getSetting(Settings.LOCK_CRAFTING_MODE);
        return mode == LockCraftingMode.LOCK_WHILE_HIGH && redstone
            || mode == LockCraftingMode.LOCK_WHILE_LOW && !redstone
            || waitingForPulse
            || unlockStack != null;
    }

    private void onPatternAccepted(IPatternDetails pattern) {
        var controller = controller();
        if (controller != null && host.getWorldObj() != null)
            processingTarget = new cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target(
                host.getWorldObj().provider.dimensionId, controller.getBlockPos());
        var mode = config.getSetting(Settings.LOCK_CRAFTING_MODE);
        waitingForPulse = mode == LockCraftingMode.LOCK_UNTIL_PULSE;
        unlockStack = mode == LockCraftingMode.LOCK_UNTIL_RESULT ? pattern.getPrimaryOutput() : null;
        host.saveChanges();
    }

    public void onPatternResult(GenericStack result) {
        if (unlockStack != null && result != null
            && unlockStack.what()
                .equals(result.what())
            && result.amount() >= unlockStack.amount()) {
            unlockStack = null;
            host.saveChanges();
            requestProviderUpdate();
        }
    }

    public void onPatternAborted(GenericStack result) {
        if (unlockStack != null && result != null
            && unlockStack.what()
                .equals(result.what())
            && result.amount() == unlockStack.amount()) {
            unlockStack = null;
            host.saveChanges();
            requestProviderUpdate();
        }
    }

    public void updateRedstoneState() {
        boolean next = host.getWorldObj() != null && host.getWorldObj()
            .isBlockIndirectlyGettingPowered(host.xCoord, host.yCoord, host.zCoord);
        if (next && !redstone) waitingForPulse = false;
        if (next != redstone) {
            redstone = next;
            host.saveChanges();
            requestProviderUpdate();
        }
    }

    private void requestProviderUpdate() {
        if (host.getMainNode()
            .isReady() && host.getGridNode() != null) ICraftingProvider.requestUpdate(host.getMainNode());
    }

    @Override
    public void saveChanges() {
        host.saveChanges();
    }

    @Override
    public void onChangeInventory(IInventory inventory, int slot, InvOperation operation, ItemStack removed,
        ItemStack added) {
        host.saveChanges();
        host.markForClientUpdate();
        updatePatterns();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum setting, Enum value) {
        if (setting == Settings.LOCK_CRAFTING_MODE) {
            waitingForPulse = false;
            unlockStack = null;
        }
        host.saveChanges();
        requestProviderUpdate();
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        patterns.writeToNBT(tag.nativeTag(), "workstationPatterns");
        upgrades.writeToNBT(tag.nativeTag(), "workstationCapacityUpgrades");
        var cfg = new CompoundTag();
        config.writeToNBT(cfg.nativeTag());
        tag.put("providerConfig", cfg);
        tag.putBoolean("waitingForPulse", waitingForPulse);
        if (unlockStack != null) tag.put("unlockStack", GenericStack.writeTag(registries, unlockStack));
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        patterns.readFromNBT(tag.nativeTag(), "workstationPatterns");
        upgrades.readFromNBT(tag.nativeTag(), "workstationCapacityUpgrades");
        config.readFromNBT(
            tag.getCompound("providerConfig")
                .nativeTag());
        waitingForPulse = tag.getBoolean("waitingForPulse");
        unlockStack = tag.contains("unlockStack") ? GenericStack.readTag(registries, tag.getCompound("unlockStack"))
            : null;
        dirty = true;
    }
}
