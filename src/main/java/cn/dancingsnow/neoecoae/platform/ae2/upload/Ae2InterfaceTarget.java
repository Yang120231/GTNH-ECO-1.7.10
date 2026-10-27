package cn.dancingsnow.neoecoae.platform.ae2.upload;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.util.IInterfaceViewable;
import cn.dancingsnow.neoecoae.compat.ae2fc.upload.DualInterfaceUpload;
import cn.dancingsnow.neoecoae.compat.gtnl.upload.SuperInputHatchUpload;
import cn.dancingsnow.neoecoae.compat.proghatches.upload.ProgrammableHatchUpload;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteProbe;
import cn.dancingsnow.neoecoae.crafting.upload.PatternSlotAccess;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;

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
        if (ProgrammableHatchUpload.isHatch(viewable)) return Kind.PROGRAMMABLE_HATCH;
        if (SuperInputHatchUpload.isHatch(viewable)) return Kind.GTNL_SUPER_INPUT;
        Kind probed = probe.kind(viewable);
        if (probed != null) return probed;
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
        String name = viewable.getName();
        return name == null || name.isEmpty() ? kind.translationKey() : name;
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
    public boolean isPresent(IGrid grid) {
        TileEntity tile = tile();
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
        boolean plainInterface = kind == Kind.AE2_INTERFACE || kind == Kind.AE2_DUAL_INTERFACE;
        if (!context.processing) {
            if (!plainInterface) return MatchResult
                .of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.processing_only");
            return facesMolecularAssembler()
                ? new MatchResult(MatchResult.Level.EXACT, null, null, null, null, "crafting", false)
                : MatchResult.of(MatchResult.Level.UNVERIFIED, "gui.neoecoae.pattern_upload.reason.no_assembler");
        }
        MatchResult routed = probe.match(viewable, context, kind == Kind.PROGRAMMABLE_HATCH);
        return routed != null ? routed
            : MatchResult.of(MatchResult.Level.UNVERIFIED, "gui.neoecoae.pattern_upload.reason.no_route");
    }

    private boolean facesMolecularAssembler() {
        ItemStack display = viewable.getDisplayRep();
        ItemStack assembler = AEApi.instance()
            .definitions()
            .blocks()
            .molecularAssembler()
            .maybeStack(1)
            .orNull();
        return display != null && assembler != null && display.isItemEqual(assembler);
    }
}
