package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingMedium.BlockingMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.helpers.DualityInterface;
import appeng.me.cache.CraftingGridCache;
import appeng.util.Platform;
import appeng.util.TunnelPatternExpander;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Line-by-line port of GTNH AE2 {@code CraftingCPUCluster#updateCraftingLogic}/{@code #executeCrafting}
 * (rv3-beta-990-GTNH) for jobs planned by AE2's own planner.
 *
 * <p>
 * Such a job must behave exactly like it would on a native crafting CPU with as many co-processors as the ECO host
 * pools: the native medium list, busy/blocking checks, round-robin, substitution, tunnel expansion, power, fake
 * crafting and the three-tick operation window are all reproduced here. None of ECO's FastPath, batch, scaled,
 * phase-barrier, probe-budget or dispatch-policy machinery participates. Only the CPU-owned inventory and the waiting
 * ledger are ECO's, and both are used with native semantics.
 * </p>
 */
final class ECOVanillaCraftingExecutor {

    /** Native medium list of a pattern ({@code CraftingGridCache#getMediums}). */
    @FunctionalInterface
    interface Mediums {

        List<ICraftingMedium> of(ICraftingPatternDetails details);
    }

    /** The two DualityInterface-specific branches of the native loop. */
    interface MediumTraits {

        MediumTraits NATIVE = new MediumTraits() {

            @Override
            public boolean isFakeCraftingMode(ICraftingMedium medium) {
                return medium instanceof DualityInterface && ((DualityInterface) medium).isFakeCraftingMode();
            }

            @Override
            public int patternCapacityUpgrades(ICraftingMedium medium) {
                return medium instanceof DualityInterface
                    ? ((DualityInterface) medium).getInstalledUpgrades(Upgrades.PATTERN_CAPACITY)
                    : 0;
            }
        };

        boolean isFakeCraftingMode(ICraftingMedium medium);

        int patternCapacityUpgrades(ICraftingMedium medium);

        /**
         * The crafting table handed to the medium: natively {@code new MEInventoryCrafting(new ContainerNull(), w, h)}.
         */
        default MEInventoryCrafting newTable(int width, int height) {
            return new MEInventoryCrafting(new ContainerNull(), width, height);
        }
    }

    private final ECOCraftingCPULogic host;
    private final MediumTraits traits;
    private final int[] usedOps = new int[3];
    private final HashSet<ICraftingMedium> knownBusyMediums = new HashSet<>();
    private final HashMap<ICraftingPatternDetails, List<ICraftingMedium>> parallelismProvider = new HashMap<>();
    private final Map<IPatternDetails, ECOExecutionTaskProgress> workableTasks = new LinkedHashMap<>();
    private int remainingOperations;
    private boolean somethingChanged;
    /** Native view of the CPU inventory for {@code findFuzzy}; rebuilt lazily once per executeCrafting pass. */
    private IItemList<IAEItemStack> fuzzyView;

    ECOVanillaCraftingExecutor(ECOCraftingCPULogic host) {
        this(host, MediumTraits.NATIVE);
    }

    ECOVanillaCraftingExecutor(ECOCraftingCPULogic host, MediumTraits traits) {
        this.host = host;
        this.traits = traits;
    }

    void reset() {
        Arrays.fill(usedOps, 0);
        knownBusyMediums.clear();
        parallelismProvider.clear();
        workableTasks.clear();
        fuzzyView = null;
    }

    /** {@code CraftingCPUCluster#updateCraftingLogic} after the native completion/cancel preamble. */
    void tick(ExecutingCraftingJob job, IEnergyGrid eg, CraftingGridCache cc, World world, int accelerator) {
        tick(job, eg, cc::getMediums, cc, world, accelerator);
    }

    /** @param tunnels the cache resolving tunnel patterns; null expands nothing, as natively without a cache */
    void tick(ExecutingCraftingJob job, IEnergyGrid eg, Mediums mediums, CraftingGridCache tunnels, World world,
        int accelerator) {
        if (job.tasks.isEmpty()) return;

        long operations = (long) Math.max(0, accelerator) + 1L - ((long) usedOps[0] + usedOps[1] + usedOps[2]);
        remainingOperations = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, operations));
        final int started = remainingOperations;

        // Shallow copy tasks so we may remove them after visiting
        workableTasks.clear();
        workableTasks.putAll(job.tasks);
        knownBusyMediums.clear();
        if (remainingOperations > 0) {
            do {
                somethingChanged = false;
                executeCrafting(job, eg, mediums, tunnels, world);
            } while (somethingChanged && remainingOperations > 0 && host.getJob() == job);
        }
        usedOps[2] = usedOps[1];
        usedOps[1] = usedOps[0];
        usedOps[0] = started - remainingOperations;

        knownBusyMediums.clear();
    }

    private void executeCrafting(ExecutingCraftingJob job, IEnergyGrid eg, Mediums mediums, CraftingGridCache cc,
        World world) {
        if (job.suspended) return;
        fuzzyView = null;

        final Iterator<Map.Entry<IPatternDetails, ECOExecutionTaskProgress>> craftingTaskIterator = workableTasks
            .entrySet()
            .iterator();

        while (craftingTaskIterator.hasNext()) {
            final Map.Entry<IPatternDetails, ECOExecutionTaskProgress> craftingEntry = craftingTaskIterator.next();
            final IPatternDetails pattern = craftingEntry.getKey();
            final ECOExecutionTaskProgress progress = craftingEntry.getValue();

            if (progress.value <= 0 || !(pattern instanceof NativePatternDetails)) {
                if (pattern instanceof NativePatternDetails)
                    parallelismProvider.remove(((NativePatternDetails) pattern).nativeDetails());
                craftingTaskIterator.remove();
                continue;
            }
            // A step the player explicitly paused from the ECO status page; never set automatically.
            if (job.isProcessingStepStopped(pattern)) {
                craftingTaskIterator.remove();
                continue;
            }

            final ICraftingPatternDetails details = ((NativePatternDetails) pattern).nativeDetails();
            final List<IAEStack<?>> condensedInputs = getExpandedCondensedInputs(details, cc);
            // Native AE2 throws here; a broken tunnel must stall only its own task instead of the server tick.
            if (condensedInputs == null || !canCraft(details, condensedInputs, world)) {
                craftingTaskIterator.remove(); // No need to revisit this task on next executeCrafting this tick
                continue;
            }

            boolean pushedPattern = false;
            boolean didPatternCraft;

            List<ICraftingMedium> mediumsList = mediums.of(details);
            List<ICraftingMedium> mediumListCheck = null;

            if (mediumsList.size() > 1) {
                mediumListCheck = parallelismProvider.getOrDefault(details, new ArrayList<>(mediumsList));
            }

            doWhileCraftingLoop: do {
                MEInventoryCrafting craftingInventory = null;
                didPatternCraft = false;

                if (mediumListCheck != null) {
                    if (mediumListCheck.isEmpty()) {
                        mediumListCheck = new ArrayList<>(mediumsList);
                    } else {
                        mediumsList = new ArrayList<>(mediumListCheck);
                    }
                }

                for (final ICraftingMedium medium : mediumsList) {
                    if (mediumListCheck != null) mediumListCheck.remove(medium);

                    if (progress.value <= 0 || knownBusyMediums.contains(medium)) {
                        continue;
                    }

                    if (medium.isBusy()) {
                        knownBusyMediums.add(medium);
                        continue;
                    }

                    // Find a valid craftingInventory for this craft. As natively, a table reused from a previous
                    // medium is pushed without a new power charge.
                    double sum = 0;
                    if (craftingInventory == null) {
                        final boolean craftable = details.isCraftable();
                        final List<IAEStack<?>> expandedInputs = craftable ? Arrays.asList(details.getAEInputs())
                            : getExpandedInputs(details, cc);
                        if (expandedInputs == null) break;

                        for (final IAEStack<?> anInput : expandedInputs) {
                            if (anInput != null) {
                                sum += (double) anInput.getStackSize() / anInput.getAmountPerUnit();
                            }
                        }
                        // upgraded interface uses more power
                        int upgrades = traits.patternCapacityUpgrades(medium);
                        if (upgrades > 0) sum *= Math.pow(4.0, upgrades);

                        // check if there is enough power
                        if (eg.extractAEPower(sum, Actionable.SIMULATE, PowerMultiplier.CONFIG) < sum - 0.01) continue;

                        craftingInventory = craftable ? traits.newTable(3, 3)
                            : traits.newTable(expandedInputs.size(), 1);

                        // Check if all items can be used for crafting.
                        boolean found = false;
                        for (int x = 0; x < expandedInputs.size(); x++) {
                            final IAEStack<?> slotInput = expandedInputs.get(x);
                            if (slotInput != null) {
                                found = false;
                                for (IAEStack<?> ias : getExtractItems(slotInput, details, world)) {
                                    IAEStack<?> tempStack = ias.copy();
                                    if (craftable && !details.isValidItemForSlot(x, tempStack, world)) continue;

                                    final IAEStack<?> aes = extract(tempStack, Actionable.MODULATE);
                                    if (aes != null) {
                                        found = true;
                                        craftingInventory.setInventorySlotContents(x, aes);
                                        if (!details.canBeSubstitute()
                                            && aes.getStackSize() == slotInput.getStackSize()) {
                                            postChange(slotInput);
                                            break;
                                        } else {
                                            postChange(aes);
                                        }
                                    }
                                }
                                if (!found) {
                                    break;
                                }
                            }
                        }

                        if (!found) {
                            // put stuff back.
                            returnItems(craftingInventory);
                            craftingInventory = null;
                            break;
                        }
                    }

                    if (medium.pushPattern(details, craftingInventory)) {
                        eg.extractAEPower(sum, Actionable.MODULATE, PowerMultiplier.CONFIG);
                        somethingChanged = true;
                        remainingOperations--;
                        pushedPattern = true;

                        if (!job.vanillaFakeCrafting && isFinalPattern(job, details)) {
                            if (traits.isFakeCraftingMode(medium)) {
                                job.vanillaFakeCrafting = true;
                            }
                        }

                        if (job.vanillaFakeCrafting && isFinalPattern(job, details)) {
                            progress.accept(1);
                            host.markCpuDirty();

                            if (progress.value <= 0) {
                                parallelismProvider.remove(details);
                                craftingTaskIterator.remove();

                                performFakeCrafting(job, details);

                                break;
                            } else {
                                performFakeCrafting(job, details);

                                // Native AE2 keeps the same table for the next medium here.
                                continue;
                            }
                        }

                        // Process output items.
                        for (final IAEStack<?> outputItemStack : details.getCondensedAEOutputs()) {
                            await(job, outputItemStack, false);
                        }

                        if (details.isCraftable()) {
                            if (world instanceof WorldServer) FMLCommonHandler.instance()
                                .firePlayerCraftingEvent(
                                    Platform.getPlayer((WorldServer) world),
                                    details.getOutput(craftingInventory, world),
                                    craftingInventory);
                            for (int x = 0; x < craftingInventory.getSizeInventory(); x++) {
                                final ItemStack output = Platform.getContainerItem(craftingInventory.getStackInSlot(x));
                                if (output != null) {
                                    await(job, AEItemStack.create(output), true);
                                }
                            }
                        }

                        craftingInventory = null; // hand off complete!
                        didPatternCraft = true;
                        host.markCpuDirty();

                        progress.accept(1);
                        if (progress.value <= 0) {
                            // This craftingEntry is done.
                            break doWhileCraftingLoop;
                        }

                        if (remainingOperations == 0) {
                            if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);
                            return;
                        }
                        // Smart blocking is fine sending the same recipe again.
                        if (medium.getBlockingMode() == BlockingMode.BLOCKING) break;

                        final List<IAEStack<?>> condensedInputsForRetry = getExpandedCondensedInputs(details, cc);
                        if (condensedInputsForRetry == null || !canCraft(details, condensedInputsForRetry, world)) {
                            break;
                        }
                    }
                }
                if (craftingInventory != null) {
                    // No suitable craftingInventory was found,
                    // put stuff back that was injected during the search.
                    returnItems(craftingInventory);
                }
            } while (didPatternCraft);

            if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);

            if (!pushedPattern) {
                // If in all mediums no pattern was pushed,
                // no need to revisit this task on next executeCrafting this tick
                craftingTaskIterator.remove();
            }
        }
    }

    private boolean canCraft(ICraftingPatternDetails details, List<IAEStack<?>> condensedInputs, World world) {
        for (IAEStack<?> g : condensedInputs) {
            if (getExtractItems(g, details, world).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<IAEStack<?>> getExpandedCondensedInputs(ICraftingPatternDetails details,
        CraftingGridCache cache) {
        if (details.isCraftable() || cache == null) {
            return Arrays.asList(details.getCondensedAEInputs());
        }
        final List<IAEStack<?>> expanded = TunnelPatternExpander
            .expandInputs(details.getCondensedAEInputs(), cache, null);
        if (expanded == null) {
            return null;
        }
        final IAEStack<?>[] condensed = appeng.helpers.PatternHelper
            .convertToCondensedAEList(expanded.toArray(new IAEStack<?>[0]));
        return Arrays.asList(condensed);
    }

    private static List<IAEStack<?>> getExpandedInputs(ICraftingPatternDetails details, CraftingGridCache cache) {
        if (details.isCraftable() || cache == null) {
            return Arrays.asList(details.getAEInputs());
        }
        return TunnelPatternExpander.expandInputs(details.getAEInputs(), cache, null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<IAEStack<?>> getExtractItems(IAEStack ingredient, ICraftingPatternDetails patternDetails,
        World world) {
        ArrayList<IAEStack<?>> list = new ArrayList<>();
        if (patternDetails.canSubstitute() && ingredient instanceof IAEItemStack) {
            final IAEItemStack aiss = (IAEItemStack) ingredient;
            for (IAEItemStack fuzz : fuzzyView().findFuzzy(aiss, FuzzyMode.IGNORE_ALL)) {
                if (!patternDetails.isCraftable() && available(fuzz) <= 0) continue;
                if (patternDetails.isCraftable()) {
                    final IAEStack<?>[] inputSlots = patternDetails.getAEInputs();
                    final IAEStack<?> finalIngredient = ingredient;
                    final int matchingSlot = IntStream.range(0, inputSlots.length)
                        .filter(idx -> inputSlots[idx] != null && Objects.equals(inputSlots[idx], finalIngredient))
                        .findFirst()
                        .orElse(-1);
                    if (matchingSlot < 0) {
                        continue;
                    }
                    if (!patternDetails.isValidItemForSlot(matchingSlot, fuzz, world)) {
                        // Skip invalid fuzzy matches
                        continue;
                    }
                }
                fuzz = fuzz.copy();
                fuzz.setStackSize(ingredient.getStackSize());
                final IAEStack<?> ais = extract(fuzz, Actionable.SIMULATE);

                if (ais != null && ais.getStackSize() == ingredient.getStackSize()) {
                    list.add(ais);
                    return list;
                } else if (ais != null && patternDetails.isCraftable()) {
                    ingredient = ingredient.copy();
                    ingredient.decStackSize(ais.getStackSize());
                    list.add(ais);
                }
            }
        } else {
            final IAEStack<?> extractItems = extract(ingredient, Actionable.SIMULATE);
            if (extractItems != null && extractItems.getStackSize() == ingredient.getStackSize()) {
                list.add(extractItems);
                return list;
            }
        }
        return list;
    }

    /** Native {@code MECraftingInventory#extractItems}: the available part of the request, or null. */
    private IAEStack<?> extract(IAEStack<?> request, Actionable mode) {
        AEKey key = AEKey.fromNative(request);
        if (key == null || request.getStackSize() <= 0) return null;
        long extracted = host.getInventory()
            .extract(key, request.getStackSize(), mode);
        if (extracted <= 0) return null;
        if (mode == Actionable.MODULATE) fuzzyView = null;
        return request.copy()
            .setStackSize(extracted);
    }

    private long available(IAEStack<?> stack) {
        AEKey key = AEKey.fromNative(stack);
        return key == null ? 0L : host.getInventory().list.get(key);
    }

    private IItemList<IAEItemStack> fuzzyView() {
        if (fuzzyView == null) {
            IItemList<IAEItemStack> view = AEApi.instance()
                .storage()
                .createItemList();
            for (var entry : host.getInventory().list) {
                if (entry.getKey() instanceof AEItemKey && entry.getLongValue() > 0) {
                    var stack = entry.getKey()
                        .toNative(entry.getLongValue());
                    if (stack instanceof IAEItemStack) view.add((IAEItemStack) stack);
                }
            }
            fuzzyView = view;
        }
        return fuzzyView;
    }

    private void returnItems(MEInventoryCrafting ic) {
        for (int x = 0; x < ic.getSizeInventory(); x++) {
            final IAEStack<?> aes = ic.getAEStackInSlot(x);
            if (aes != null) {
                AEKey key = AEKey.fromNative(aes);
                if (key != null && aes.getStackSize() > 0) host.getInventory()
                    .insert(key, aes.getStackSize(), Actionable.MODULATE);
            }
        }
        fuzzyView = null;
    }

    /** Native output handling: the CPU now waits for this stack (container items also extend the time estimate). */
    private void await(ExecutingCraftingJob job, IAEStack<?> stack, boolean container) {
        AEKey key = AEKey.fromNative(stack);
        if (key == null || stack.getStackSize() <= 0) return;
        job.waitingFor.insert(key, stack.getStackSize(), Actionable.MODULATE);
        if (container) job.timeTracker.addMaxItems(stack.getStackSize(), key.getType());
        host.postChange(key);
    }

    private void postChange(IAEStack<?> stack) {
        AEKey key = AEKey.fromNative(stack);
        if (key != null) host.postChange(key);
    }

    /**
     * Native {@code finalOutput#isFinalPattern}: the pattern whose condensed outputs equal those of the task producing
     * the requested output. Native stack equality is item/damage/NBT (fluid/NBT), i.e. {@link AEKey} equality.
     */
    private static boolean isFinalPattern(ExecutingCraftingJob job, ICraftingPatternDetails details) {
        ICraftingPatternDetails finalPattern = finalPattern(job);
        if (finalPattern == null) return false;
        IAEStack<?>[] patternOutputs = finalPattern.getAEOutputs();
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (patternOutputs == null || outputs.length != patternOutputs.length) return false;
        int matches = 0;
        for (IAEStack<?> aes : outputs) {
            AEKey key = AEKey.fromNative(aes);
            for (IAEStack<?> aes2 : patternOutputs) {
                if (key != null && key.equals(AEKey.fromNative(aes2))) matches++;
            }
        }
        return matches == patternOutputs.length;
    }

    /** Native {@code finalOutput#addOutputs}: the first task with a condensed output equal to the requested output. */
    private static ICraftingPatternDetails finalPattern(ExecutingCraftingJob job) {
        if (job.finalOutput == null) return null;
        AEKey requested = job.finalOutput.what();
        for (var pattern : job.tasks.keySet()) {
            if (!(pattern instanceof NativePatternDetails)) continue;
            var details = ((NativePatternDetails) pattern).nativeDetails();
            for (IAEStack<?> aes : details.getCondensedAEOutputs()) {
                if (requested.equals(AEKey.fromNative(aes))) return details;
            }
        }
        return null;
    }

    /** Native {@code finalOutput#performFakeCrafting}: the final product counts as delivered without returning. */
    private void performFakeCrafting(ExecutingCraftingJob job, ICraftingPatternDetails details) {
        if (job.finalOutput == null) return;
        AEKey finalKey = job.finalOutput.what();
        for (IAEStack<?> aes : details.getCondensedAEOutputs()) {
            AEKey key = AEKey.fromNative(aes);
            if (!finalKey.equals(key)) continue;
            long delivered = Math.min(aes.getStackSize(), Math.max(0L, job.remainingAmount));
            job.remainingAmount -= delivered;
            if (delivered > 0) job.timeTracker.decrementItems(delivered, key.getType());
            host.postChange(key);
        }
        host.markCpuDirty();
    }
}
