package cn.dancingsnow.neoecoae.platform.ae2.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternSlotAccess;
import cn.dancingsnow.neoecoae.crafting.upload.PatternStacks;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;

/**
 * One ECO pattern domain. Buses in the same switch domain are shown once and deduplicated together; the write still
 * lands in one concrete bus slot. Only crafting patterns are accepted, matching the bus validator.
 */
final class EcoPatternDomainTarget implements PatternUploadTarget {

    private final ECOMachineInterfaceBlockEntity<?> host;
    private final Object domain;

    EcoPatternDomainTarget(ECOMachineInterfaceBlockEntity<?> host, Object domain) {
        this.host = host;
        this.domain = domain;
    }

    Object domain() {
        return domain;
    }

    private NECraftingCluster cluster() {
        Object cluster = host.getCluster();
        return cluster instanceof NECraftingCluster && !((NECraftingCluster) cluster).isDestroyed()
            ? (NECraftingCluster) cluster
            : null;
    }

    private List<ECOCraftingPatternBusBlockEntity> buses() {
        NECraftingCluster cluster = cluster();
        if (cluster == null || cluster.getPatternDomain() != domain) return Collections.emptyList();
        List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>(cluster.getPatternDomainBuses());
        buses.removeIf(bus -> bus == null || bus.isInvalid());
        buses.sort(
            Comparator.comparingLong(
                bus -> bus.getBlockPos()
                    .asLong()));
        return buses;
    }

    @Override
    public Kind kind() {
        return Kind.ECO_PATTERN_DOMAIN;
    }

    @Override
    public String name() {
        return "gui.neoecoae.pattern_upload.target.eco";
    }

    @Override
    public ItemStack icon() {
        List<ECOCraftingPatternBusBlockEntity> buses = buses();
        if (buses.isEmpty() || buses.get(0)
            .getBlockType() == null) return null;
        return new ItemStack(
            buses.get(0)
                .getBlockType(),
            1,
            buses.get(0)
                .getBlockMetadata());
    }

    @Override
    public int dimension() {
        return host.getWorldObj() == null ? 0 : host.getWorldObj().provider.dimensionId;
    }

    @Override
    public int x() {
        return host.xCoord;
    }

    @Override
    public int y() {
        return host.yCoord;
    }

    @Override
    public int z() {
        return host.zCoord;
    }

    @Override
    public String positionKey() {
        return "eco:" + dimension() + ":" + x() + ":" + y() + ":" + z();
    }

    @Override
    public boolean isPresent(IGrid grid) {
        if (host.isInvalid() || host.getWorldObj() == null
            || host.getWorldObj()
                .getTileEntity(host.xCoord, host.yCoord, host.zCoord) != host
            || !host.isTargetOnline()) return false;
        IGridNode node = host.getGridNode();
        return node != null && node.getGrid() == grid && cluster() != null;
    }

    @Override
    public PatternSlotAccess slots() {
        return new DomainAccess(buses());
    }

    @Override
    public MatchResult match(PatternContext context) {
        return context.processing
            ? MatchResult.of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.eco_crafting_only")
            : new MatchResult(MatchResult.Level.EXACT, null, null, null, null, "crafting", false);
    }

    /** Concatenated view over the domain's buses; {@link #physical} resolves one bus slot for the transfer. */
    private static final class DomainAccess implements PatternSlotAccess {

        private final List<BusAccess> buses = new ArrayList<>();
        private final int size;

        DomainAccess(List<ECOCraftingPatternBusBlockEntity> buses) {
            int total = 0;
            for (ECOCraftingPatternBusBlockEntity bus : buses) {
                BusAccess access = new BusAccess(bus);
                this.buses.add(access);
                total += access.size();
            }
            size = total;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public SlotRef physical(int slot) {
            for (BusAccess bus : buses) {
                if (slot < bus.size()) return new SlotRef(bus, slot);
                slot -= bus.size();
            }
            throw new IndexOutOfBoundsException("pattern domain slot");
        }

        @Override
        public ItemStack get(int slot) {
            SlotRef ref = physical(slot);
            return ref.access.get(ref.slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            SlotRef ref = physical(slot);
            return ref.access.accepts(ref.slot, stack);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            SlotRef ref = physical(slot);
            ref.access.set(ref.slot, stack);
        }

        @Override
        public void changed() {
            for (BusAccess bus : buses) bus.changed();
        }

        @Override
        public boolean isLive() {
            for (BusAccess bus : buses) if (!bus.isLive()) return false;
            return true;
        }
    }

    private static final class BusAccess implements PatternSlotAccess {

        private final ECOCraftingPatternBusBlockEntity bus;

        BusAccess(ECOCraftingPatternBusBlockEntity bus) {
            this.bus = bus;
        }

        @Override
        public int size() {
            return bus.getInternalInventory()
                .size();
        }

        @Override
        public ItemStack get(int slot) {
            return bus.getInternalInventory()
                .getStackInSlot(slot);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            var inventory = bus.getInternalInventory();
            if (stack == null || slot < 0 || slot >= inventory.size() || !inventory.isItemValid(slot, stack))
                return false;
            ItemStack current = inventory.getStackInSlot(slot);
            int limit = Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
            return current == null ? stack.stackSize <= limit
                : PatternStacks.sameItem(current, stack) && current.stackSize + stack.stackSize <= limit;
        }

        @Override
        public void set(int slot, ItemStack stack) {
            // The inventory callback saves the bus and republishes its AE2 patterns.
            bus.getInternalInventory()
                .setStackInSlot(slot, stack);
        }

        @Override
        public void changed() {
            bus.markDirty();
        }

        @Override
        public boolean isLive() {
            return !bus.isInvalid() && bus.getWorldObj() != null
                && bus.getWorldObj()
                    .getTileEntity(bus.xCoord, bus.yCoord, bus.zCoord) == bus;
        }
    }
}
