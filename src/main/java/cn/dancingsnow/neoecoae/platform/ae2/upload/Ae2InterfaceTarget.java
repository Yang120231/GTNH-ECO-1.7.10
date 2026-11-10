package cn.dancingsnow.neoecoae.platform.ae2.upload;

import java.util.Set;
import java.util.TreeSet;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.capability.Capabilities;

import appeng.api.crafting.ICraftingIconProvider;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.util.IInterfaceViewable;
import appeng.helpers.IInterfaceHost;
import appeng.util.InventoryAdaptor;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2fc.upload.DualInterfaceUpload;
import cn.dancingsnow.neoecoae.compat.gtnl.upload.SuperInputHatchUpload;
import cn.dancingsnow.neoecoae.compat.proghatches.upload.ProgrammableHatchUpload;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteProbe;
import cn.dancingsnow.neoecoae.crafting.upload.PatternSlotAccess;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.crafting.upload.RouteHint;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;

/** Any host listed by AE2's interface terminal: ME interfaces, 2-in-1 interfaces and GT/addon pattern hatches. */
final class Ae2InterfaceTarget implements PatternUploadTarget {

    private final IInterfaceViewable viewable;
    private final IGridNode node;
    private final PatternRouteProbe probe;
    private final Kind kind;
    private final ForgeDirection side;

    Ae2InterfaceTarget(IInterfaceViewable viewable, IGridNode node, PatternRouteProbe probe) {
        this.viewable = viewable;
        this.node = node;
        this.probe = probe;
        this.kind = kindOf(viewable, probe);
        this.side = viewable instanceof appeng.parts.AEBasePart ? ((appeng.parts.AEBasePart) viewable).getSide()
            : ForgeDirection.UNKNOWN;
    }

    private static Kind kindOf(IInterfaceViewable viewable, PatternRouteProbe probe) {
        if (viewable instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity) return Kind.ECO_WORKSTATION;
        if (ProgrammableHatchUpload.isHatch(viewable)) return Kind.PROGRAMMABLE_HATCH;
        if (SuperInputHatchUpload.isHatch(viewable)) return Kind.GTNL_SUPER_INPUT;
        Kind probed = probe.kind(viewable);
        if (probed != null) return probed;
        if (viewable.isCraftingPatternProvider()) {
            for (Class<?> type = viewable.getClass(); type != null; type = type.getSuperclass())
                if ("com.science.gtnl.common.machine.multiblock.AssemblerMatrix".equals(type.getName()))
                    return Kind.GTNL_ASSEMBLER_MATRIX;
            if (!(viewable instanceof IInterfaceHost)) return Kind.CRAFTING_ASSEMBLER;
        }
        return DualInterfaceUpload.isDualInterface(viewable) ? Kind.AE2_DUAL_INTERFACE : Kind.AE2_INTERFACE;
    }

    private TileEntity tile() {
        return viewable.getTileEntity();
    }

    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public String name() {
        String name = viewable.getRawName();
        return name == null || name.isEmpty() ? kind.translationKey() : name;
    }

    /**
     * What AE2 appends to the raw name, e.g. GT's ghost circuit " [1]". Derived from {@code getName()} because
     * {@code getNameSuffix()} returns String or IChatComponent depending on the AE2 build.
     */
    @Override
    public String nameSuffix() {
        String full = viewable.getName();
        String raw = viewable.getRawName();
        return full != null && raw != null && full.length() > raw.length() && full.startsWith(raw)
            ? full.substring(raw.length())
            : "";
    }

    @Override
    public ItemStack icon() {
        ItemStack icon = viewable.getDisplayRep();
        return icon != null ? icon : viewable.getSelfRep();
    }

    @Override
    public int dimension() {
        TileEntity tile = tile();
        return tile == null || tile.getWorldObj() == null ? 0 : tile.getWorldObj().provider.dimensionId;
    }

    @Override
    public int x() {
        TileEntity tile = tile();
        return tile == null ? 0 : tile.xCoord;
    }

    @Override
    public int y() {
        TileEntity tile = tile();
        return tile == null ? 0 : tile.yCoord;
    }

    @Override
    public int z() {
        TileEntity tile = tile();
        return tile == null ? 0 : tile.zCoord;
    }

    @Override
    public String positionKey() {
        return "if:" + dimension() + ":" + x() + ":" + y() + ":" + z() + ":" + side.ordinal();
    }

    @Override
    public Object storageIdentity() {
        return viewable.getPatterns();
    }

    @Override
    public boolean isPresent(IGrid grid) {
        // Recheck live visibility so a target hidden after discovery cannot receive an upload.
        if (!viewable.shouldDisplay()) return false;
        TileEntity tile = tile();
        if (viewable instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity workstation
            && (!workstation.isFormed() || workstation.getCluster() == null
                || workstation.getCluster()
                    .isDestroyed()
                || workstation.getCluster()
                    .getController() == null))
            return false;
        return node.isActive() && node.getGrid() == grid
            && tile != null
            && !tile.isInvalid()
            && tile.getWorldObj() != null
            && tile.getWorldObj()
                .getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile;
    }

    @Override
    public PatternSlotAccess slots() {
        IInventory patterns = viewable.getPatterns();
        return PatternSlotAccess.of(patterns, patternSlots(patterns), tile());
    }

    /** Only the pattern area; GT and GTNL hatches continue with circuit and manual slots. */
    private int patternSlots(IInventory patterns) {
        if (patterns == null) return 0;
        int slots = kind == Kind.GTNL_SUPER_INPUT ? SuperInputHatchUpload.patternSlots(viewable) : -1;
        if (slots <= 0) slots = viewable.numSlots();
        return Math.min(slots, patterns.getSizeInventory());
    }

    @Override
    public MatchResult match(PatternContext context) {
        boolean materialOrder = context.hint != null && context.hint.isMaterialOrder();
        if (viewable instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity workstation) {
            if (materialOrder) return MatchResult
                .of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.material_machine");
            if (context.details.isInputOnly()) return MatchResult
                .of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.tunnel_interface");
            if (context.inputError != null) return MatchResult.of(MatchResult.Level.INCOMPATIBLE, context.inputError);
            // Use the same non-mutating recipe check as the workstation's advertised pattern catalog.
            if (!workstation.getWorkstationProvider()
                .accepts(context.pattern))
                return MatchResult.of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.recipe");
            String category = !context.processing ? "ae2:crafting"
                : context.hint != null && !context.hint.getCategoryId()
                    .isEmpty() ? context.hint.getCategoryId() : "neoecoae:large_workstation";
            String selection = !context.processing ? "-"
                : NativeGregTech.isAvailable()
                    ? cn.dancingsnow.neoecoae.platform.gt.upload.GtPatternRoutes.selectionKey(context)
                    : context.hint == null ? "-" : context.hint.selectionKey();
            return new MatchResult(
                context.processing ? MatchResult.Level.ROUTED : MatchResult.Level.EXACT,
                null,
                null,
                workstation.getRawName(),
                workstation.getDisplayRep(),
                "neoecoae:large_workstation",
                false,
                category,
                selection);
        }
        boolean plainInterface = kind == Kind.AE2_INTERFACE || kind == Kind.AE2_DUAL_INTERFACE;
        if (context.details.isInputOnly()) return plainInterface
            ? MatchResult.of(MatchResult.Level.ACCEPTED, "gui.neoecoae.pattern_upload.reason.tunnel_definition")
            : MatchResult.of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.tunnel_interface");
        if (context.inputError != null) return MatchResult.of(MatchResult.Level.UNVERIFIED, context.inputError);
        if (materialOrder) {
            if (!plainInterface || viewable.isCraftingPatternProvider()
                || facesMolecularAssembler()
                || probe.match(viewable, context, false) != null)
                return MatchResult
                    .of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.material_machine");
            return facesContainer(viewable)
                ? new MatchResult(
                    MatchResult.Level.ACCEPTED,
                    null,
                    null,
                    null,
                    null,
                    "material_order",
                    false,
                    RouteHint.MATERIAL_ORDER_CATEGORY,
                    "-")
                : MatchResult
                    .of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.material_container");
        }
        if (!context.processing) {
            if (!plainInterface && !viewable.isCraftingPatternProvider()) return MatchResult
                .of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.processing_only");
            return viewable.isCraftingPatternProvider() || facesMolecularAssembler()
                ? new MatchResult(
                    MatchResult.Level.EXACT,
                    null,
                    null,
                    null,
                    null,
                    "crafting",
                    false,
                    "ae2:crafting",
                    "-")
                : MatchResult.of(MatchResult.Level.UNVERIFIED, "gui.neoecoae.pattern_upload.reason.no_assembler");
        }
        boolean nativeGt = NativeGregTech.isAvailable();
        String selection = nativeGt ? cn.dancingsnow.neoecoae.platform.gt.upload.GtPatternRoutes.selectionKey(context)
            : context.hint == null ? "?" : context.hint.selectionKey();
        ItemStack circuit = nativeGt
            ? cn.dancingsnow.neoecoae.platform.gt.upload.GtPatternRoutes.selectedCircuit(context)
            : context.hint == null ? null : context.hint.getCircuit();
        if ("!".equals(selection)) return MatchResult
            .of(MatchResult.Level.CIRCUIT_CONFLICT, "gui.neoecoae.pattern_upload.reason.circuits_multiple");
        if (context.hint != null && context.hint.hasRecipeMap()
            && (!nativeGt || !cn.dancingsnow.neoecoae.platform.gt.upload.GtPatternRoutes
                .isKnownMap(context.hint.getRecipeMapId())))
            return MatchResult
                .of(MatchResult.Level.UNVERIFIED, "gui.neoecoae.pattern_upload.reason.source_map_unknown");
        MatchResult routed = probe.match(viewable, context, kind == Kind.PROGRAMMABLE_HATCH);
        if (routed != null) return routed;
        if (context.hint != null && !context.hint.hasRecipeMap() && viewable instanceof IInterfaceHost) {
            IInterfaceHost host = (IInterfaceHost) viewable;
            TileEntity tile = host.getTileEntity();
            var targets = host.getTargets();
            Set<String> machines = new TreeSet<>();
            ItemStack shown = null;
            if (tile != null && tile.getWorldObj() != null && targets != null) for (ForgeDirection target : targets) {
                if (target == null || target == ForgeDirection.UNKNOWN) continue;
                TileEntity machine = tile.getWorldObj()
                    .getTileEntity(
                        tile.xCoord + target.offsetX,
                        tile.yCoord + target.offsetY,
                        tile.zCoord + target.offsetZ);
                ItemStack type = machineType(machine, target.getOpposite());
                if (!context.hint.matchesMachine(type)) continue;
                if (shown == null) shown = type;
                machines.add(
                    machine.getClass()
                        .getName() + "@"
                        + Item.itemRegistry.getNameForObject(type.getItem())
                        + ":"
                        + type.getItemDamage());
            }
            if (!machines.isEmpty()) {
                String category = context.hint.getCategoryId();
                return new MatchResult(
                    MatchResult.Level.ROUTED,
                    null,
                    circuit,
                    null,
                    shown,
                    category + "#" + selection + "#" + String.join(",", machines),
                    false,
                    category,
                    selection);
            }
        }
        return new MatchResult(
            MatchResult.Level.UNVERIFIED,
            "gui.neoecoae.pattern_upload.reason.no_route",
            circuit,
            null,
            null,
            "",
            false,
            context.hint == null ? "" : context.hint.getCategoryId(),
            selection);
    }

    /** Inspect every facing machine, rather than relying on AE2's first adjacent display icon. */
    private static ItemStack machineType(TileEntity machine, ForgeDirection face) {
        if (machine == null || machine.getWorldObj() == null || machine.getBlockType() == null) return null;
        var provider = Capabilities.getCapability(machine, ICraftingIconProvider.class);
        if (provider != null) {
            ItemStack icon = provider.getMachineCraftingIcon();
            if (icon != null) return icon.copy();
        }
        var block = machine.getBlockType();
        ItemStack type = new ItemStack(
            block,
            1,
            block.getDamageValue(machine.getWorldObj(), machine.xCoord, machine.yCoord, machine.zCoord));
        try {
            var hit = new MovingObjectPosition(
                machine.xCoord,
                machine.yCoord,
                machine.zCoord,
                face.ordinal(),
                Vec3.createVectorHelper(machine.xCoord + 0.5, machine.yCoord + 0.5, machine.zCoord + 0.5));
            ItemStack picked = block
                .getPickBlock(hit, machine.getWorldObj(), machine.xCoord, machine.yCoord, machine.zCoord, null);
            if (picked != null && picked.getItem() != null) type = picked;
        } catch (RuntimeException | LinkageError unsupportedPick) { /* Use the block's declared item form. */ }
        return type.getItem() == null ? null : type;
    }

    private boolean facesMolecularAssembler() {
        if (!(viewable instanceof IInterfaceHost host)) return false;
        TileEntity tile = host.getTileEntity();
        var targets = host.getTargets();
        if (tile == null || tile.getWorldObj() == null || targets == null) return false;
        for (ForgeDirection direction : targets) {
            if (direction == null || direction == ForgeDirection.UNKNOWN) continue;
            TileEntity adjacent = tile.getWorldObj()
                .getTileEntity(
                    tile.xCoord + direction.offsetX,
                    tile.yCoord + direction.offsetY,
                    tile.zCoord + direction.offsetZ);
            if (adjacent instanceof ICraftingMachine machine && machine.acceptsPlans()) return true;
        }
        return false;
    }

    /** Use AE2's insertion adapter so sided and modded containers follow the native interface's rules. */
    static boolean facesContainer(IInterfaceViewable viewable) {
        if (!(viewable instanceof IInterfaceHost host)) return false;
        TileEntity tile = host.getTileEntity();
        var targets = host.getTargets();
        if (tile == null || tile.getWorldObj() == null || targets == null) return false;
        for (ForgeDirection direction : targets) {
            if (direction == null || direction == ForgeDirection.UNKNOWN) continue;
            TileEntity adjacent = tile.getWorldObj()
                .getTileEntity(
                    tile.xCoord + direction.offsetX,
                    tile.yCoord + direction.offsetY,
                    tile.zCoord + direction.offsetZ);
            if (adjacent != null && !(adjacent instanceof IInterfaceHost)
                && InventoryAdaptor.getAdaptor(adjacent, direction.getOpposite()) != null) return true;
        }
        return false;
    }
}
