package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IInterfaceViewable;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import gregtech.api.recipe.RecipeMap;

public final class PatternUploadSession {

    private final UUID id = UUID.randomUUID();
    private final EntityPlayerMP player;
    private final IGrid grid;
    private final IGridNode sourceNode;
    /** The exact AE2 pattern-terminal container that created this session. */
    private final ContainerPatternTerm sourceContainer;
    private final ItemStack pattern;
    private final IInventory sourceInventory;
    private final int sourceSlot;
    private final boolean processing;
    private final PatternRouteKey routeKey;
    private final List<PatternUploadTarget> targets;
    private final long createdAt = System.currentTimeMillis();
    /** The exact container opened for the upload UI, once it has been created. */
    private Container uploadContainer;
    private PatternUploadTarget uploadedTarget;
    private ItemStack uploadedPattern;
    private int uploadedSlot = -1;

    private PatternUploadSession(EntityPlayerMP player, IGrid grid, IGridNode sourceNode,
        ContainerPatternTerm sourceContainer, ItemStack pattern, IInventory sourceInventory, int sourceSlot,
        boolean processing, PatternRouteKey routeKey, List<PatternUploadTarget> targets) {
        this.player = player;
        this.grid = grid;
        this.sourceNode = sourceNode;
        this.sourceContainer = sourceContainer;
        this.pattern = pattern == null ? null : pattern.copy();
        this.sourceInventory = sourceInventory;
        this.sourceSlot = sourceSlot;
        this.processing = processing;
        this.routeKey = routeKey;
        this.targets = Collections.unmodifiableList(targets);
    }

    public UUID getId() {
        return this.id;
    }

    public EntityPlayerMP getPlayer() {
        return this.player;
    }

    public IInventory getSourceInventory() {
        return this.sourceInventory;
    }

    public int getSourceSlot() {
        return this.sourceSlot;
    }

    public boolean matchesPattern(ItemStack candidate) {
        return candidate == null ? this.pattern == null : matches(candidate, this.pattern);
    }

    public ItemStack getPattern() {
        return this.pattern == null ? null : this.pattern.copy();
    }

    public ICraftingPatternDetails getPatternDetails() {
        if (this.pattern == null) return null;
        return safePatternDetails(this.pattern, this.player == null ? null : this.player.worldObj);
    }

    public ItemStack getPatternOutput() {
        ICraftingPatternDetails details = this.patternDetails();
        if (details == null) return null;
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (outputs == null) return null;
        for (IAEStack<?> output : outputs) {
            if (!(output instanceof IAEItemStack)) continue;
            ItemStack stack = ((IAEItemStack) output).getItemStack();
            if (stack != null) {
                ItemStack display = stack.copy();
                display.stackSize = 1;
                return display;
            }
        }
        return null;
    }

    public boolean isProcessing() {
        return this.processing;
    }

    public PatternRouteKey getRouteKey() {
        return this.routeKey;
    }

    public ItemStack getAutoUploadCircuit() {
        PatternUploadTarget target = this.getAutoUploadTarget();
        if (target != null) {
            ItemStack circuit = target.getCircuit(this.routeKey, this.patternDetails());
            if (circuit != null) return circuit;
        }
        ItemStack patternCircuit = PatternUploadTarget.patternCircuit(this.patternDetails());
        if (patternCircuit != null) return patternCircuit;
        return this.routeKey == null ? null : this.routeKey.getCircuit();
    }

    public List<PatternUploadTarget> getTargets() {
        return this.targets;
    }

    public boolean isUploaded() {
        return this.uploadedTarget != null && this.uploadedPattern != null;
    }

    public boolean canAutoUpload() {
        return this.getAutoUploadTarget() != null;
    }

    public void bindUploadContainer(Object container) {
        this.uploadContainer = container instanceof Container ? (Container) container : null;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.createdAt > 60_000L || !this.isSourceNetworkValid();
    }

    public boolean upload(String targetId) {
        if (this.isExpired() || this.isUploaded() || this.pattern == null) return false;
        ItemStack current = this.sourceInventory == null ? null : this.sourceInventory.getStackInSlot(this.sourceSlot);
        if (current == null || current.stackSize != this.pattern.stackSize
            || !current.isItemEqual(this.pattern)
            || !ItemStack.areItemStackTagsEqual(current, this.pattern)) {
            return false;
        }
        for (PatternUploadTarget target : this.targets) {
            if (target.getId()
                .equals(targetId) && target.getGrid() == this.grid
                && target.isExactMatch(this.processing, this.patternDetails(), this.routeKey)
                && target.isWritable(this.processing, this.patternDetails(), this.pattern, this.routeKey)) {
                int slot = target.firstEmptySlot(this.pattern);
                if (slot >= 0 && target.insertAt(slot, this.pattern)) {
                    this.sourceInventory.setInventorySlotContents(this.sourceSlot, null);
                    if (this.sourceInventory.getStackInSlot(this.sourceSlot) != null) {
                        target.removeExact(slot, this.pattern);
                        return false;
                    }
                    this.uploadedTarget = target;
                    this.uploadedPattern = this.pattern.copy();
                    this.uploadedSlot = slot;
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public boolean autoUpload() {
        if (this.isExpired() || this.isUploaded() || this.pattern == null) return false;
        PatternUploadTarget target = this.getAutoUploadTarget();
        return target != null && this.upload(target.getId());
    }

    /** Returns the target that auto-upload would use, without mutating the network. */
    public PatternUploadTarget getAutoUploadTarget() {
        if (this.isExpired() || this.isUploaded()) return null;
        ICraftingPatternDetails details = this.patternDetails();

        // Crafting patterns prefer ECO, then the molecular assembler interface.
        if (!this.processing) {
            PatternUploadTarget eco = null;
            PatternUploadTarget molecular = null;
            for (PatternUploadTarget target : this.targets) {
                if (!target.isWritable(false, details, this.pattern, this.routeKey)
                    || target.compatibilityRank(false, details) != 0) continue;
                if (target.getKind() == PatternUploadTarget.Kind.ECO_PATTERN_BUS && eco == null) {
                    eco = target;
                } else if ((target.getKind() == PatternUploadTarget.Kind.AE2_INTERFACE
                    || target.getKind() == PatternUploadTarget.Kind.AE2_DUAL_INTERFACE) && molecular == null) {
                        molecular = target;
                    }
            }
            return eco != null ? eco : molecular;
        }

        PatternUploadTarget exact = null;
        for (PatternUploadTarget target : this.targets) {
            if (!target.isWritable(true, details, this.pattern, this.routeKey)) continue;
            if (target.isExactMatch(true, details, this.routeKey)
                && (exact == null || compareTargets(target, exact, this.pattern, true, details, this.routeKey) < 0))
                exact = target;
        }
        return exact;
    }

    public boolean undoUpload() {
        if (this.isExpired() || !this.isUploaded()) return false;
        ItemStack current = this.sourceInventory == null ? null : this.sourceInventory.getStackInSlot(this.sourceSlot);
        if (current != null) return false;
        if (!this.uploadedTarget.removeExact(this.uploadedSlot, this.uploadedPattern)) return false;
        this.sourceInventory.setInventorySlotContents(this.sourceSlot, this.uploadedPattern.copy());
        if (!this.matches(this.sourceInventory.getStackInSlot(this.sourceSlot), this.uploadedPattern)) {
            this.uploadedTarget.insertAt(this.uploadedSlot, this.uploadedPattern);
            return false;
        }
        this.uploadedTarget = null;
        this.uploadedPattern = null;
        this.uploadedSlot = -1;
        return true;
    }

    public static PatternUploadSession create(EntityPlayerMP player, IGrid grid, IGridNode sourceNode,
        ItemStack pattern, IInventory sourceInventory, int sourceSlot, boolean processing) {
        return create(player, grid, sourceNode, pattern, sourceInventory, sourceSlot, processing, null);
    }

    public static PatternUploadSession create(EntityPlayerMP player, IGrid grid, IGridNode sourceNode,
        ItemStack pattern, IInventory sourceInventory, int sourceSlot, boolean processing, PatternRouteKey routeKey) {
        List<PatternUploadTarget> targets = discover(grid, pattern, player.worldObj, processing, routeKey);
        PatternRouteKey effectiveRouteKey = routeKey;
        if (routeKey != null && !routeKey.isEmpty() && targets.isEmpty() && !isKnownRecipeMap(routeKey)) {
            // A stale NEI context must not hide a valid manually-entered pattern. Re-infer from the
            // currently connected target declarations instead.
            targets = discover(grid, pattern, player.worldObj, processing, null);
            effectiveRouteKey = null;
        }
        return new PatternUploadSession(
            player,
            grid,
            sourceNode,
            currentPatternContainer(player),
            pattern,
            sourceInventory,
            sourceSlot,
            processing,
            effectiveRouteKey,
            targets);
    }

    private static boolean isKnownRecipeMap(PatternRouteKey routeKey) {
        if (routeKey == null || routeKey.isEmpty()) return false;
        try {
            return RecipeMap.getFromOldIdentifier(routeKey.getRecipeMapId()) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<PatternUploadTarget> discover(IGrid grid, ItemStack pattern, net.minecraft.world.World world,
        boolean processing, PatternRouteKey routeKey) {
        List<PatternUploadTarget> result = new ArrayList<>();
        if (grid == null) return result;
        ICraftingPatternDetails details = pattern == null ? null : safePatternDetails(pattern, world);
        if (pattern != null && details == null) return result;
        int serial = 0;
        Set<IGridNode> seenInterfaces = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        List<PatternUploadTarget> ecoTargets = new ArrayList<>();
        Set<TileCraftingPatternBus> seenEcoBuses = Collections
            .newSetFromMap(new IdentityHashMap<TileCraftingPatternBus, Boolean>());
        // GT does not register every build of the crafting input hatch with AE2's terminal
        // class index. Enumerate the concrete class explicitly as well; the identity set keeps
        // this from duplicating a target returned by the registry scan below.
        for (IGridNode node : grid.getMachines(gregtech.common.tileentities.machines.MTEHatchCraftingInputME.class)) {
            if (!seenInterfaces.add(node)) continue;
            IGridHost machine = node.getMachine();
            if (!(machine instanceof IInterfaceViewable)) continue;
            try {
                IInterfaceViewable viewable = (IInterfaceViewable) machine;
                if (!isDiscoverableInterface(viewable)) continue;
                PatternUploadTarget target = PatternUploadTarget
                    .interfaceTarget("interface-" + serial++, viewable, grid);
                if (details == null || target.isRecipeCompatible(processing, details, routeKey)) result.add(target);
            } catch (RuntimeException ignored) {
                // A GT hatch can disappear while its proxy is rebuilding.
            }
        }
        // The interface-terminal registry is the authoritative source for GT/AE2 addon
        // interfaces. In particular, GT's ME crafting input hatch can expose its proxy node
        // rather than the hatch as node.getMachine(), so scanning only grid.getNodes() misses
        // the assembly/bus targets. Keep the node scan as a fallback for late-registered hosts.
        for (Class<? extends IGridHost> type : AEApi.instance()
            .registries()
            .interfaceTerminal()
            .getSupportedClasses()) {
            for (IGridNode node : grid.getMachines(type)) {
                IGridHost machine = node.getMachine();
                if (!(machine instanceof IInterfaceViewable) || !seenInterfaces.add(node)) continue;
                try {
                    IInterfaceViewable viewable = (IInterfaceViewable) machine;
                    if (!isDiscoverableInterface(viewable)) continue;
                    PatternUploadTarget target = PatternUploadTarget
                        .interfaceTarget("interface-" + serial++, viewable, grid);
                    if (details == null || target.isRecipeCompatible(processing, details, routeKey)) result.add(target);
                } catch (RuntimeException ignored) {
                    // A machine can be removed or rebuilt while the AE grid is being scanned.
                }
            }
        }
        for (IGridNode node : grid.getNodes()) {
            if (!seenInterfaces.add(node)) continue;
            IGridHost machine = node.getMachine();
            if (!(machine instanceof IInterfaceViewable)) continue;
            try {
                IInterfaceViewable viewable = (IInterfaceViewable) machine;
                if (!isDiscoverableInterface(viewable)) continue;
                PatternUploadTarget target = PatternUploadTarget
                    .interfaceTarget("interface-" + serial++, viewable, grid);
                if (details == null || target.isRecipeCompatible(processing, details, routeKey)) result.add(target);
            } catch (RuntimeException ignored) {
                // A machine can be removed or rebuilt while the AE grid is being scanned. Skip
                // only that node; a transient target must never crash the terminal or server.
            }
        }
        for (IGridNode node : grid.getNodes()) {
            if (!(node.getMachine() instanceof IPatternUploadProvider)) continue;
            try {
                IPatternUploadProvider provider = (IPatternUploadProvider) node.getMachine();
                if (!provider.isPatternUploadActive()) continue;
                PatternUploadTarget target = PatternUploadTarget.providerTarget("provider-" + serial++, provider, grid);
                if (target.isRecipeCompatible(processing, details, routeKey)) result.add(target);
            } catch (RuntimeException ignored) {
                // Optional adapters are allowed to disappear during network rebuilds.
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
                if (details == null || target.isRecipeCompatible(processing, details, routeKey)) ecoTargets.add(target);
            }
        }
        Collections.sort(ecoTargets, PatternUploadSession::comparePosition);
        PatternUploadTarget availableEcoTarget = null;
        for (PatternUploadTarget target : ecoTargets) {
            int slot = target.firstEmptySlot(pattern);
            if (slot >= 0 || slot == -2) {
                availableEcoTarget = target;
                break;
            }
        }
        if (availableEcoTarget != null) {
            result.add(availableEcoTarget);
        } else if (!ecoTargets.isEmpty()) {
            result.add(ecoTargets.get(0));
        }
        Collections.sort(result, new Comparator<PatternUploadTarget>() {

            @Override
            public int compare(PatternUploadTarget left, PatternUploadTarget right) {
                return compareTargets(left, right, pattern, processing, details, routeKey);
            }
        });
        return result;
    }

    private static boolean isDiscoverableInterface(IInterfaceViewable viewable) {
        if (viewable == null || viewable.getPatterns() == null) return false;
        // GT's MTEHatchCraftingInputME uses showPattern only to control whether AE2 lists
        // its patterns in the normal crafting provider. It remains a valid upload inventory
        // even when that display flag is disabled, so do not hide the assembly/bus target.
        return viewable.shouldDisplay()
            || viewable instanceof gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
    }

    private static int compareTargets(PatternUploadTarget left, PatternUploadTarget right, ItemStack pattern,
        boolean processing, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        int compatibility = Integer.compare(
            left.compatibilityRank(processing, details, routeKey),
            right.compatibilityRank(processing, details, routeKey));
        if (compatibility != 0) return compatibility;
        int writable = Boolean.compare(left.firstEmptySlot(pattern) < 0, right.firstEmptySlot(pattern) < 0);
        if (writable != 0) return writable;
        int kind = Integer.compare(priority(left), priority(right));
        if (kind != 0) return kind;
        int slots = Integer.compare(right.getEmptySlots(), left.getEmptySlots());
        if (slots != 0) return slots;
        return comparePosition(left, right);
    }

    private ICraftingPatternDetails patternDetails() {
        return this.getPatternDetails();
    }

    private static ICraftingPatternDetails safePatternDetails(ItemStack pattern, net.minecraft.world.World world) {
        if (pattern == null || pattern.getItem() == null || !(pattern.getItem() instanceof ICraftingPatternItem))
            return null;
        try {
            return ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isSourceNetworkValid() {
        if (this.player == null || this.sourceContainer == null) return false;
        Container currentContainer = this.player.openContainer;
        if (currentContainer != this.sourceContainer && currentContainer != this.uploadContainer) return false;
        return this.sourceNode != null && this.sourceNode.isActive() && this.sourceNode.getGrid() == this.grid;
    }

    private static ContainerPatternTerm currentPatternContainer(EntityPlayerMP player) {
        if (player == null || !(player.openContainer instanceof ContainerPatternTerm)) return null;
        return (ContainerPatternTerm) player.openContainer;
    }

    private static boolean matches(ItemStack left, ItemStack right) {
        return left != null && right != null
            && left.stackSize == right.stackSize
            && left.isItemEqual(right)
            && ItemStack.areItemStackTagsEqual(left, right);
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
            case GT_CRAFTING_INPUT_BUS:
                return 0;
            case ECO_PATTERN_BUS:
                return 1;
            case PROGRAMMABLE_HATCH:
                return 1;
            case AE2_INTERFACE:
            case AE2_DUAL_INTERFACE:
                return 2;
            default:
                return 3;
        }
    }
}
