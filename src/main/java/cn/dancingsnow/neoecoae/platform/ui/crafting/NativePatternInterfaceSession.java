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
    private int serial, recovered, remaining;

    public NativePatternInterfaceSession(ECOMachineInterfaceBlockEntity<?> host) {
        super(host);
        this.host = host;
    }

    public String search(boolean output) { return output ? outputs : inputs; }
    public void search(boolean output, String query) {
        String bounded = query == null ? "" : query.substring(0, Math.min(128, query.length()));
        if (output) outputs = bounded;
        else inputs = bounded;
        offset = 0;
    }

    private List<ECOCraftingPatternBusBlockEntity> buses() {
        if (!(host.getCluster() instanceof NECraftingCluster) || host.getCluster().isDestroyed()) return java.util.Collections.emptyList();
        var buses = new ArrayList<>(((NECraftingCluster) host.getCluster()).getPatternDomainBuses());
        buses.sort(Comparator.comparingLong(bus -> bus.getBlockPos().asLong()));
        return buses;
    }

    @Override
    protected CompoundTag capture() {
        var buses = buses();
        slots.keySet().removeIf(bus -> !buses.contains(bus));
        targets.values().removeIf(target -> !buses.contains(target.bus));
        var matches = new ArrayList<Target>();
        String inputQuery = inputs.toLowerCase(Locale.ROOT).trim(), outputQuery = outputs.toLowerCase(Locale.ROOT).trim();
        int occupied = 0;
        for (var bus : buses) for (int index = 0; index < bus.getInternalInventory().size(); index++) {
            ItemStack stack = bus.getInternalInventory().getStackInSlot(index);
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
        var page = new ListTag();
        for (int index = offset * 9; index < Math.min(matches.size(), offset * 9 + 54); index++) {
            var target = matches.get(index);
            var row = new CompoundTag();
            row.putInt("token", target.token);
            row.put("stack", ItemStacks.save(RegistryAccess.EMPTY, target.expected));
            row.putLong("bus", target.bus.getBlockPos().asLong());
            row.putInt("slot", target.slot);
            page.add(row);
        }
        tag.put("page", page);
        return tag;
    }

    private String terms(Target target, boolean output) {
        if (ItemStacks.isEmpty(target.expected)) return "";
        try {
            var pattern = ((appeng.api.implementations.ICraftingPatternItem) target.expected.getItem())
                .getPatternForItem(target.expected, host.getWorldObj());
            if (pattern == null) return target.expected.getDisplayName().toLowerCase(Locale.ROOT);
            var builder = new StringBuilder();
            var keys = output ? pattern.getCondensedOutputs() : pattern.getCondensedInputs();
            for (var key : keys) if (key != null) builder.append(key.getItemStack().getDisplayName()).append(' ');
            return builder.toString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException invalid) { return target.expected.getDisplayName().toLowerCase(Locale.ROOT); }
    }

    @Override
    protected void apply(int action, int argument) {
        if (action == 0) { offset = Math.max(0, Math.min(65535, argument)); return; }
        if (action == 3) { organize(); return; }
        if (action == 4) { transfer(); return; }
        if (action != 1 && action != 2) throw new IllegalArgumentException("Unknown pattern preview action " + action);
        var target = targets.get(argument);
        if (target == null || !buses().contains(target.bus) || target.bus.isInvalid()
            || !ItemStack.areItemStacksEqual(target.expected, target.bus.getInternalInventory().getStackInSlot(target.slot))) return;
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

    private void organize() {
        recovered = remaining = 0;
        var seen = new java.util.HashSet<cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey>();
        var retained = new ArrayList<ItemStack>();
        var positions = new ArrayList<Target>();
        var player = getSyncManager().getPlayer();
        for (var bus : buses()) for (int slot = 0; slot < bus.getInternalInventory().size(); slot++) {
            var inventory = bus.getInternalInventory();
            var stack = inventory.getStackInSlot(slot);
            positions.add(new Target(0, bus, slot, null));
            if (ItemStacks.isEmpty(stack)) continue;
            boolean duplicate = !seen.add(cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(stack));
            if (duplicate || !inventory.isItemValid(slot, stack)) {
                var moving = stack.copy();
                if (player.inventory.addItemStackToInventory(moving) || moving.stackSize < stack.stackSize) {
                    recovered++;
                    if (moving.stackSize > 0) retained.add(moving);
                } else { remaining++; retained.add(stack.copy()); }
            } else retained.add(stack.copy());
        }
        retained.sort(Comparator.comparing(stack -> stack.getDisplayName().toLowerCase(Locale.ROOT)));
        for (int index = 0; index < positions.size(); index++) {
            var position = positions.get(index);
            position.bus.getInternalInventory().setStackInSlot(position.slot, index < retained.size() ? retained.get(index) : null);
        }
        player.inventory.markDirty();
        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
    }

    private void transfer() {
        var grid = host.getMainNode().getGrid();
        if (grid == null || !host.getMainNode().isOnline()) return;
        var buses = buses();
        var seen = java.util.Collections.newSetFromMap(new IdentityHashMap<IInventory, Boolean>());
        for (var node : grid.nativeGrid().getNodes()) {
            if (!(node.getMachine() instanceof appeng.helpers.IInterfaceHost)) continue;
            IInventory source = ((appeng.helpers.IInterfaceHost) node.getMachine()).getPatterns();
            if (source == null || !seen.add(source)) continue;
            for (int slot = 0; slot < source.getSizeInventory(); slot++) {
                var stack = source.getStackInSlot(slot);
                if (ItemStacks.isEmpty(stack)) continue;
                var moving = stack.copy();
                for (var bus : buses) {
                    var target = bus.getInternalInventory();
                    for (int index = 0; index < target.size() && !ItemStacks.isEmpty(moving); index++)
                        moving = target.insertItem(index, moving, false);
                    if (ItemStacks.isEmpty(moving)) break;
                }
                if (ItemStacks.count(moving) != stack.stackSize) source.setInventorySlotContents(slot, moving);
            }
            source.markDirty();
        }
    }

    public CompoundTag entry(int index) {
        var page = state().getList("page", 10);
        return index < page.size() ? page.getCompound(index) : new CompoundTag();
    }

    private static final class Target {
        final int token, slot;
        final ECOCraftingPatternBusBlockEntity bus;
        final ItemStack expected;
        Target(int token, ECOCraftingPatternBusBlockEntity bus, int slot, ItemStack expected) {
            this.token = token; this.bus = bus; this.slot = slot; this.expected = expected;
        }
    }
}
