package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.util.IInterfaceViewable;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public final class PatternUploadSession {

    private final UUID id = UUID.randomUUID();
    private final EntityPlayerMP player;
    private final IGrid grid;
    private final IGridNode sourceNode;
    private final ItemStack pattern;
    private final IInventory sourceInventory;
    private final int sourceSlot;
    private final boolean processing;
    private final List<PatternUploadTarget> targets;
    private final long createdAt = System.currentTimeMillis();

    private PatternUploadSession(EntityPlayerMP player, IGrid grid, IGridNode sourceNode, ItemStack pattern,
        IInventory sourceInventory, int sourceSlot, boolean processing, List<PatternUploadTarget> targets) {
        this.player = player;
        this.grid = grid;
        this.sourceNode = sourceNode;
        this.pattern = pattern.copy();
        this.sourceInventory = sourceInventory;
        this.sourceSlot = sourceSlot;
        this.processing = processing;
        this.targets = Collections.unmodifiableList(targets);
    }

    public UUID getId() {
        return this.id;
    }

    public EntityPlayerMP getPlayer() {
        return this.player;
    }

    public ItemStack getPattern() {
        return this.pattern.copy();
    }

    public boolean isProcessing() {
        return this.processing;
    }

    public List<PatternUploadTarget> getTargets() {
        return this.targets;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.createdAt > 60_000L || !this.isSourceNetworkValid();
    }

    public boolean upload(String targetId) {
        if (this.isExpired()) return false;
        ItemStack current = this.sourceInventory == null ? null : this.sourceInventory.getStackInSlot(this.sourceSlot);
        if (current == null || !current.isItemEqual(this.pattern)
            || !ItemStack.areItemStackTagsEqual(current, this.pattern)) {
            return false;
        }
        for (PatternUploadTarget target : this.targets) {
            if (target.getId()
                .equals(targetId) && target.getGrid() == this.grid) {
                if (target.insert(this.pattern)) {
                    this.sourceInventory.setInventorySlotContents(this.sourceSlot, null);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static PatternUploadSession create(EntityPlayerMP player, IGrid grid, IGridNode sourceNode,
        ItemStack pattern, IInventory sourceInventory, int sourceSlot, boolean processing) {
        List<PatternUploadTarget> targets = discover(grid, pattern, player.worldObj, processing);
        return new PatternUploadSession(
            player,
            grid,
            sourceNode,
            pattern,
            sourceInventory,
            sourceSlot,
            processing,
            targets);
    }

    private static List<PatternUploadTarget> discover(IGrid grid, ItemStack pattern, net.minecraft.world.World world,
        boolean processing) {
        List<PatternUploadTarget> result = new ArrayList<>();
        if (grid == null || pattern == null) return result;
        ICraftingPatternDetails details = pattern.getItem() instanceof ICraftingPatternItem
            ? ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world)
            : null;
        if (details == null) return result;
        int serial = 0;
        List<PatternUploadTarget> ecoTargets = new ArrayList<>();
        Set<TileCraftingPatternBus> seenEcoBuses = Collections
            .newSetFromMap(new IdentityHashMap<TileCraftingPatternBus, Boolean>());
        for (Class<? extends IGridHost> type : AEApi.instance()
            .registries()
            .interfaceTerminal()
            .getSupportedClasses()) {
            for (IGridNode node : grid.getMachines(type)) {
                IGridHost machine = node.getMachine();
                if (!(machine instanceof IInterfaceViewable)) continue;
                IInterfaceViewable viewable = (IInterfaceViewable) machine;
                if (!viewable.shouldDisplay() || viewable.getPatterns() == null) continue;
                PatternUploadTarget target = PatternUploadTarget
                    .interfaceTarget("interface-" + serial++, viewable, grid);
                if (target.isCompatible(processing, details) && target.firstEmptySlot(pattern) >= 0) result.add(target);
            }
        }
        for (IGridNode node : grid.getMachines(TileECOInterface.class)) {
            IGridHost machine = node.getMachine();
            if (!(machine instanceof TileECOInterface)) continue;
            TileECOInterface ecoInterface = (TileECOInterface) machine;
            if (ecoInterface.getSubsystem() != ECOControllerSubsystem.CRAFTING) continue;
            TileECOController controller = ecoInterface.getBoundController();
            if (controller == null || !controller.isFormed()) continue;
            for (TileCraftingPatternBus bus : controller.getCraftingPatternBuses()) {
                if (!seenEcoBuses.add(bus)) continue;
                PatternUploadTarget target = PatternUploadTarget.ecoTarget("eco-" + serial++, bus, ecoInterface, grid);
                if (target.isCompatible(processing, details)) ecoTargets.add(target);
            }
        }
        Collections.sort(ecoTargets, PatternUploadSession::comparePosition);
        PatternUploadTarget availableEcoTarget = null;
        boolean allEcoTargetsFull = !ecoTargets.isEmpty();
        for (PatternUploadTarget target : ecoTargets) {
            if (target.firstEmptySlot(pattern) >= 0) {
                availableEcoTarget = target;
                break;
            }
            if (target.getEmptySlots() > 0) allEcoTargetsFull = false;
        }
        if (availableEcoTarget != null) {
            result.add(availableEcoTarget);
        } else if (allEcoTargetsFull) {
            result.add(ecoTargets.get(0));
        }
        Collections.sort(result, new Comparator<PatternUploadTarget>() {

            @Override
            public int compare(PatternUploadTarget left, PatternUploadTarget right) {
                int writable = Boolean.compare(left.firstEmptySlot(pattern) < 0, right.firstEmptySlot(pattern) < 0);
                if (writable != 0) return writable;
                int compatibility = Integer
                    .compare(left.compatibilityRank(processing, details), right.compatibilityRank(processing, details));
                if (compatibility != 0) return compatibility;
                int kind = Integer.compare(priority(left), priority(right));
                if (kind != 0) return kind;
                int slots = Integer.compare(right.getEmptySlots(), left.getEmptySlots());
                if (slots != 0) return slots;
                return comparePosition(left, right);
            }
        });
        return result;
    }

    private boolean isSourceNetworkValid() {
        return this.sourceNode != null && this.sourceNode.isActive() && this.sourceNode.getGrid() == this.grid;
    }

    private static int comparePosition(PatternUploadTarget left, PatternUploadTarget right) {
        int dimension = Integer.compare(left.getDimension(), right.getDimension());
        if (dimension != 0) return dimension;
        int x = Integer.compare(left.getX(), right.getX());
        if (x != 0) return x;
        int y = Integer.compare(left.getY(), right.getY());
        if (y != 0) return y;
        int z = Integer.compare(left.getZ(), right.getZ());
        if (z != 0) return z;
        return left.getId()
            .compareTo(right.getId());
    }

    private static int priority(PatternUploadTarget target) {
        switch (target.getKind()) {
            case GT_CRAFTING_INPUT:
                return 0;
            case ECO_PATTERN_BUS:
                return 1;
            case AE2_INTERFACE:
                return 2;
            default:
                return 3;
        }
    }
}
