package cn.dancingsnow.neoecoae.blocks.entity.crafting;

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
    private final ECOCraftingPatternBusCatalog catalog = new ECOCraftingPatternBusCatalog(
        inventory,
        this::decodePattern);
    private boolean updateQueued;

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

    /**
     * Patterns physically stored in this bus. A Network Switch group shares workers, not advertising: a push to
     * this bus already reaches every group worker through {@link NECraftingCluster#collectDispatchCandidateWorkers},
     * so publishing the whole group's buses here would only register each pattern once per bus in the group.
     */
    public List<IPatternDetails> getAvailablePatterns() {
        if (worldObj == null || !formed) return Collections.emptyList();
        return catalog.patterns(worldObj);
    }

    ECOCraftingPatternBusCatalog catalogForTest() {
        return catalog;
    }

    /** Decodes one stored stack. Package-private so tests can supply patterns without a recipe registry. */
    NativePatternDetails decodePattern(net.minecraft.item.ItemStack stack, net.minecraft.world.World world) {
        if (!(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem)) return null;
        var nativePattern = ((appeng.api.implementations.ICraftingPatternItem) stack.getItem())
            .getPatternForItem(stack, world);
        return nativePattern != null && nativePattern.isCraftable()
            ? NativePatternDetails.capture(nativePattern, world, Collections.emptyList())
            : null;
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return pushPattern(pattern, inputs, null);
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, UUID job) {
        return dispatcher.pushPattern(pattern, inputs, job);
    }

    /**
     * Native AE2 CPU bridge. The table is laid out like the pattern's sparse inputs (3x3 for crafting), and a
     * substitution-enabled crafting pattern may hold an accepted substitute rather than the encoded item, so a
     * slot is attributed by its position first and only then by item identity. A stack that cannot be attributed
     * rejects the push, which leaves the inputs with the AE2 CPU instead of silently dropping them.
     */
    public boolean pushPattern(appeng.api.networking.crafting.ICraftingPatternDetails details,
        net.minecraft.inventory.InventoryCrafting table) {
        NativePatternDetails pattern = formed ? catalog.find(worldObj, details) : null;
        if (pattern == null) pattern = NativePatternDetails.capture(details, worldObj, Collections.emptyList());
        KeyCounter[] inputs = bridgeInputs(pattern, details.isCraftable(), table);
        return inputs != null && pushPattern(pattern, inputs, null);
    }

    @org.jetbrains.annotations.Nullable
    static KeyCounter[] bridgeInputs(NativePatternDetails pattern, boolean craftable,
        net.minecraft.inventory.InventoryCrafting table) {
        int size = table.getSizeInventory();
        AEKey[] keys = new AEKey[size];
        long[] amounts = new long[size];
        for (int slot = 0; slot < size; slot++) {
            var stack = table instanceof appeng.util.inv.MEInventoryCrafting
                ? ((appeng.util.inv.MEInventoryCrafting) table).getAEStackInSlot(slot)
                : appeng.util.item.AEItemStack.create(table.getStackInSlot(slot));
            if (stack == null) continue;
            keys[slot] = AEKey.fromNative(stack);
            if (keys[slot] == null) return null;
            amounts[slot] = stack.getStackSize();
        }
        return bridgeInputs(pattern, craftable, keys, amounts);
    }

    /** Attributes each table slot ({@code null} key = empty) to a condensed input, or rejects the whole push. */
    @org.jetbrains.annotations.Nullable
    static KeyCounter[] bridgeInputs(NativePatternDetails pattern, boolean craftable, AEKey[] keys, long[] amounts) {
        IPatternDetails.IInput[] condensed = pattern.getInputs();
        var sparse = pattern.getSparseInputs();
        boolean positional = craftable || keys.length == sparse.length;
        KeyCounter[] inputs = new KeyCounter[condensed.length];
        for (int i = 0; i < inputs.length; i++) inputs[i] = new KeyCounter();
        for (int slot = 0; slot < keys.length; slot++) {
            AEKey key = keys[slot];
            if (key == null) continue;
            boolean encoded = positional && slot < sparse.length && sparse[slot] != null;
            // Crafting slots are positional by contract; processing tables only fall back to position.
            int index = craftable && encoded ? matchByKey(condensed, sparse[slot].what()) : matchByKey(condensed, key);
            if (index < 0 && encoded) index = matchByKey(condensed, sparse[slot].what());
            if (index < 0) return null;
            inputs[index].add(key, amounts[slot]);
        }
        return inputs;
    }

    private static int matchByKey(IPatternDetails.IInput[] condensed, AEKey key) {
        for (int i = 0; i < condensed.length; i++)
            for (var possible : condensed[i].getPossibleInputs()) if (possible.what()
                .equals(key)) return i;
        return -1;
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

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        catalog.invalidate(slot);
        saveChangedInventory(inv);
    }

    public void saveChangedInventory(AppEngInternalInventory inv) {
        setChanged();
        refreshAdvertisedPatterns();
    }

    /**
     * Requests one AE2 pattern re-index at the end of the current server tick. AE2 rv3 rebuilds the whole grid's
     * pattern table synchronously for every change event, and one slot write alone reports twice (slot write and
     * markDirty), so bulk uploads and chunk loads must not post an event per mutation.
     */
    public void refreshAdvertisedPatterns() {
        if (updateQueued || worldObj != null && worldObj.isRemote) return;
        updateQueued = true;
        if (!cn.dancingsnow.neoecoae.platform.network.ServerTasks.captureSession()
            .execute(this::flushAdvertisedPatterns)) {
            // No server tick loop (client side or a stopped server): nothing would ever flush, so post now.
            flushAdvertisedPatterns();
        }
    }

    private void flushAdvertisedPatterns() {
        updateQueued = false;
        if (isRemoved()) return;
        postPatternChange();
    }

    /** Posts the AE2 pattern change event. Package-private so tests can count events without a live grid. */
    void postPatternChange() {
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
