package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeSnapshotSession;

/** Bounded viewport of physical pattern slots. Tokens bind each click to its original bus, slot and stack. */
public final class NativePatternInterfaceSession extends NativeSnapshotSession {

    private final ECOMachineInterfaceBlockEntity<?> host;
    private final IdentityHashMap<ECOCraftingPatternBusBlockEntity, Map<Integer, Target>> slots = new IdentityHashMap<>();
    private final Map<Integer, Target> targets = new HashMap<>();
    private volatile String inputs = "", outputs = "";
    private volatile int offset;
    private static final int OPERATION_TRANSFER = 1, OPERATION_ORGANIZE = 2;
    static final int TRANSFER_DONE = 0, TRANSFER_UNAVAILABLE = 1, TRANSFER_NO_TARGET = 2;
    private int serial, recovered, remaining, duplicates;
    private int lastOperation, operationSerial, transferState;
    private int transferred, alreadyPresent, noSpace, incompatible;

    public NativePatternInterfaceSession(ECOMachineInterfaceBlockEntity<?> host) {
        super(host);
        this.host = host;
    }

    public String search(boolean output) {
        return output ? outputs : inputs;
    }

    public void search(boolean output, String query) {
        String bounded = query == null ? "" : query.substring(0, Math.min(128, query.length()));
        if (output) outputs = bounded;
        else inputs = bounded;
        offset = 0;
    }

    private List<ECOCraftingPatternBusBlockEntity> buses() {
        if (!(host.getCluster() instanceof NECraftingCluster) || host.getCluster()
            .isDestroyed()) return java.util.Collections.emptyList();
        var buses = new ArrayList<>(((NECraftingCluster) host.getCluster()).getPatternDomainBuses());
        buses.sort(
            Comparator.comparingLong(
                bus -> bus.getBlockPos()
                    .asLong()));
        return buses;
    }

    @Override
    protected CompoundTag capture() {
        var buses = buses();
        slots.keySet()
            .removeIf(bus -> !buses.contains(bus));
        targets.values()
            .removeIf(target -> !buses.contains(target.bus));
        var matches = new ArrayList<Target>();
        String inputQuery = inputs.toLowerCase(Locale.ROOT)
            .trim(),
            outputQuery = outputs.toLowerCase(Locale.ROOT)
                .trim();
        int occupied = 0;
        for (var bus : buses) for (int index = 0; index < bus.getInternalInventory()
            .size(); index++) {
                ItemStack stack = bus.getInternalInventory()
                    .getStackInSlot(index);
                if (!ItemStacks.isEmpty(stack)) occupied++;
                var inventoryTargets = slots.computeIfAbsent(bus, ignored -> new HashMap<>());
                Target target = inventoryTargets.get(index);
                if (target == null || !ItemStack.areItemStacksEqual(target.expected, stack)) {
                    if (target != null) targets.remove(target.token);
                    target = new Target(++serial, bus, index, ItemStack.copyItemStack(stack));
                    inventoryTargets.put(index, target);
                    targets.put(target.token, target);
                }
                if (inputQuery.isEmpty() && outputQuery.isEmpty()
                    || !inputQuery.isEmpty() && terms(target, false).contains(inputQuery)
                    || !outputQuery.isEmpty() && terms(target, true).contains(outputQuery)) matches.add(target);
            }
        int rows = (matches.size() + 8) / 9;
        offset = Math.max(0, Math.min(Math.max(0, rows - 6), offset));
        var tag = new CompoundTag();
        tag.putInt("offset", offset);
        tag.putInt("rows", rows);
        tag.putInt("occupied", occupied);
        tag.putInt("recovered", recovered);
        tag.putInt("remaining", remaining);
        tag.putInt("duplicates", duplicates);
        tag.putInt("operation", lastOperation);
        tag.putInt("operationSerial", operationSerial);
        tag.putInt("transferState", transferState);
        tag.putInt("transferred", transferred);
        tag.putInt("alreadyPresent", alreadyPresent);
        tag.putInt("noSpace", noSpace);
        tag.putInt("incompatible", incompatible);
        tag.putBoolean("online", host.isTargetOnline());
        var page = new ListTag();
        for (int index = offset * 9; index < Math.min(matches.size(), offset * 9 + 54); index++) {
            var target = matches.get(index);
            var row = new CompoundTag();
            row.putInt("token", target.token);
            row.put("stack", ItemStacks.save(RegistryAccess.EMPTY, target.expected));
            row.put("display", ItemStacks.save(RegistryAccess.EMPTY, displayStack(target.expected)));
            row.putLong(
                "bus",
                target.bus.getBlockPos()
                    .asLong());
            row.putInt("slot", target.slot);
            page.add(row);
        }
        tag.put("page", page);
        return tag;
    }

    /** Match the modern pattern slot: show its primary product while retaining the physical pattern for clicks. */
    ItemStack displayStack(ItemStack stack) {
        if (ItemStacks.isEmpty(stack) || !(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem))
            return stack;
        try {
            var pattern = ((appeng.api.implementations.ICraftingPatternItem) stack.getItem())
                .getPatternForItem(stack, host.getWorldObj());
            if (pattern != null && pattern.getOutputs() != null) for (var output : pattern.getOutputs()) {
                if (output != null && !ItemStacks.isEmpty(output.getItemStack())) return output.getItemStack();
            }
        } catch (RuntimeException invalid) { /* Keep malformed patterns visible so players can remove them. */ }
        return stack;
    }

    private String terms(Target target, boolean output) {
        if (ItemStacks.isEmpty(target.expected)) return "";
        try {
            var pattern = ((appeng.api.implementations.ICraftingPatternItem) target.expected.getItem())
                .getPatternForItem(target.expected, host.getWorldObj());
            if (pattern == null) return target.expected.getDisplayName()
                .toLowerCase(Locale.ROOT);
            var builder = new StringBuilder();
            var keys = output ? pattern.getCondensedOutputs() : pattern.getCondensedInputs();
            for (var key : keys) if (key != null) builder.append(
                key.getItemStack()
                    .getDisplayName())
                .append(' ');
            return builder.toString()
                .toLowerCase(Locale.ROOT);
        } catch (RuntimeException invalid) {
            return target.expected.getDisplayName()
                .toLowerCase(Locale.ROOT);
        }
    }

    @Override
    protected void apply(int action, int argument) {
        if (action == 0) {
            offset = Math.max(0, Math.min(65535, argument));
            return;
        }
        if (action == 3) {
            organize();
            return;
        }
        if (action == 4) {
            transfer();
            return;
        }
        if (action != 1 && action != 2) throw new IllegalArgumentException("Unknown pattern preview action " + action);
        var target = targets.get(argument);
        if (target == null || !buses().contains(target.bus)
            || target.bus.isInvalid()
            || !ItemStack.areItemStacksEqual(
                target.expected,
                target.bus.getInternalInventory()
                    .getStackInSlot(target.slot)))
            return;
        var player = getSyncManager().getPlayer();
        var inventory = target.bus.getInternalInventory();
        ItemStack stack = inventory.getStackInSlot(target.slot), cursor = player.inventory.getItemStack();
        if (action == 2) {
            if (ItemStacks.isEmpty(stack)) return;
            var moving = stack.copy();
            if (!player.inventory.addItemStackToInventory(moving) && moving.stackSize == stack.stackSize) return;
            inventory.setStackInSlot(target.slot, moving.stackSize == 0 ? null : moving);
        } else if (ItemStacks.isEmpty(cursor)) {
            player.inventory.setItemStack(inventory.extractItem(target.slot, 1, false));
        } else if (ItemStacks.isEmpty(stack)) {
            player.inventory.setItemStack(inventory.insertItem(target.slot, cursor, false));
        }
        player.inventory.markDirty();
        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
    }

    /**
     * Compacts every bus into one ordered run: recovers invalid and duplicate patterns to the player, then lays the
     * rest out by primary product so the preview reads alphabetically from its first slot.
     */
    private void organize() {
        lastOperation = OPERATION_ORGANIZE;
        recovered = remaining = duplicates = 0;
        operationSerial++;
        var seen = new java.util.HashSet<cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey>();
        var retained = new ArrayList<Sorted>();
        var positions = new ArrayList<Target>();
        var player = getSyncManager().getPlayer();
        for (var bus : buses()) for (int slot = 0; slot < bus.getInternalInventory()
            .size(); slot++) {
                var inventory = bus.getInternalInventory();
                var stack = inventory.getStackInSlot(slot);
                positions.add(new Target(0, bus, slot, null));
                if (ItemStacks.isEmpty(stack)) continue;
                boolean duplicate = !seen.add(cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(stack));
                boolean invalid = !duplicate && !inventory.isItemValid(slot, stack);
                if (duplicate || invalid) {
                    var moving = stack.copy();
                    if (player.inventory.addItemStackToInventory(moving) || moving.stackSize < stack.stackSize) {
                        if (duplicate) duplicates++;
                        else recovered++;
                        if (moving.stackSize > 0) retained.add(new Sorted(moving, sortKey(moving)));
                    } else {
                        remaining++;
                        retained.add(new Sorted(stack.copy(), sortKey(stack)));
                    }
                } else retained.add(new Sorted(stack.copy(), sortKey(stack)));
            }
        retained.sort(Comparator.comparing(sorted -> sorted.key));
        for (int index = 0; index < positions.size(); index++) {
            var position = positions.get(index);
            var inventory = position.bus.getInternalInventory();
            ItemStack next = index < retained.size() ? retained.get(index).stack : null;
            // Rewriting an unchanged slot would still invalidate its catalog entry and dirty the bus.
            if (!ItemStack.areItemStacksEqual(inventory.getStackInSlot(position.slot), next))
                inventory.setStackInSlot(position.slot, next);
        }
        player.inventory.markDirty();
        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
    }

    /** Primary product name first, then its registry identity, so equal names still order deterministically. */
    String sortKey(ItemStack pattern) {
        ItemStack output = displayStack(pattern);
        boolean decoded = output != pattern;
        String name = ItemStacks.isEmpty(output) ? "" : output.getDisplayName();
        Object id = ItemStacks.isEmpty(output) ? null
            : net.minecraft.item.Item.itemRegistry.getNameForObject(output.getItem());
        String nbt = pattern.getTagCompound() == null ? ""
            : pattern.getTagCompound()
                .toString();
        // Undecodable patterns sort last so the readable ones stay together at the top.
        return (decoded ? "0" : "1") + name.toLowerCase(Locale.ROOT)
            + '\u0000'
            + id
            + '\u0000'
            + String.format("%08d", ItemStacks.isEmpty(output) ? 0 : output.getItemDamage())
            + '\u0000'
            + nbt;
    }

    /**
     * Moves crafting patterns out of every other pattern host on the grid into free bus slots. Patterns the buses
     * already hold are cleared from their source and refunded as a blank pattern into the network.
     */
    private void transfer() {
        lastOperation = OPERATION_TRANSFER;
        transferred = alreadyPresent = noSpace = incompatible = 0;
        transferState = TRANSFER_DONE;
        operationSerial++;
        var grid = host.getMainNode()
            .getGrid();
        if (grid == null || !host.getMainNode()
            .isOnline()) {
            transferState = TRANSFER_UNAVAILABLE;
            return;
        }
        var buses = buses();
        if (buses.isEmpty()) {
            transferState = TRANSFER_NO_TARGET;
            return;
        }
        // Index the buses once: free slots in preview order and the patterns they already hold. Inserting through
        // insertItem would decode the pattern again for every occupied slot it walks past.
        var free = new java.util.ArrayDeque<Target>();
        var present = new java.util.HashSet<cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey>();
        for (var bus : buses) for (int slot = 0; slot < bus.getInternalInventory()
            .size(); slot++) {
                ItemStack stack = bus.getInternalInventory()
                    .getStackInSlot(slot);
                if (ItemStacks.isEmpty(stack)) free.add(new Target(0, bus, slot, null));
                else present.add(cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(stack));
            }
        var seen = java.util.Collections.newSetFromMap(new IdentityHashMap<IInventory, Boolean>());
        for (var node : grid.nativeGrid()
            .getNodes()) {
            Object machine;
            IInventory source;
            try {
                machine = node.getMachine();
                if (!(machine instanceof appeng.api.util.IInterfaceViewable) || machine.getClass()
                    .getName()
                    .startsWith("cn.dancingsnow.neoecoae.")) continue;
                source = ((appeng.api.util.IInterfaceViewable) machine).getPatterns();
            } catch (RuntimeException rebuilding) {
                continue;
            }
            if (source == null || !seen.add(source)) continue;
            boolean changed = false;
            for (int slot = 0; slot < source.getSizeInventory(); slot++) {
                var stack = source.getStackInSlot(slot);
                if (ItemStacks.isEmpty(stack)
                    || !(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem)) continue;
                if (!isCraftingPattern(stack)) {
                    incompatible++;
                    continue;
                }
                var key = cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(stack);
                if (present.contains(key)) {
                    if (!refundBlank(grid, stack.stackSize)) {
                        noSpace++;
                        continue;
                    }
                    source.setInventorySlotContents(slot, null);
                    alreadyPresent++;
                    changed = true;
                    continue;
                }
                var target = free.poll();
                if (target == null) {
                    noSpace++;
                    continue;
                }
                target.bus.getInternalInventory()
                    .setStackInSlot(target.slot, ItemStacks.copyWithCount(stack, 1));
                present.add(key);
                // Bus slots hold one pattern; extra copies are duplicates and are refunded as blanks when possible.
                if (stack.stackSize > 1 && !refundBlank(grid, stack.stackSize - 1))
                    source.setInventorySlotContents(slot, ItemStacks.copyWithCount(stack, stack.stackSize - 1));
                else source.setInventorySlotContents(slot, null);
                transferred++;
                changed = true;
            }
            if (changed) source.markDirty();
        }
    }

    private boolean isCraftingPattern(ItemStack stack) {
        try {
            var pattern = ((appeng.api.implementations.ICraftingPatternItem) stack.getItem())
                .getPatternForItem(stack, host.getWorldObj());
            return pattern != null && pattern.isCraftable();
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    /** All-or-nothing: the source may only be cleared once the whole refund landed in network storage. */
    private boolean refundBlank(cn.dancingsnow.neoecoae.platform.ae2.grid.GridView grid, int count) {
        var blank = appeng.api.AEApi.instance()
            .definitions()
            .materials()
            .blankPattern()
            .maybeStack(count)
            .orNull();
        if (blank == null) return false;
        try {
            var key = cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(blank);
            var inventory = grid.getStorageService()
                .getInventory();
            var source = cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource.ofMachine(host);
            return inventory.insert(key, count, appeng.api.config.Actionable.SIMULATE, source) == count
                && inventory.insert(key, count, appeng.api.config.Actionable.MODULATE, source) == count;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    public CompoundTag entry(int index) {
        var page = state().getList("page", 10);
        return index < page.size() ? page.getCompound(index) : new CompoundTag();
    }

    private static final class Sorted {

        final ItemStack stack;
        final String key;

        Sorted(ItemStack stack, String key) {
            this.stack = stack;
            this.key = key;
        }
    }

    private static final class Target {

        final int token, slot;
        final ECOCraftingPatternBusBlockEntity bus;
        final ItemStack expected;

        Target(int token, ECOCraftingPatternBusBlockEntity bus, int slot, ItemStack expected) {
            this.token = token;
            this.bus = bus;
            this.slot = slot;
            this.expected = expected;
        }
    }
}
