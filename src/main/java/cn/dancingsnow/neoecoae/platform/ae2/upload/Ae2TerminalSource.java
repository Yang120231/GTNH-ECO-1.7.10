package cn.dancingsnow.neoecoae.platform.ae2.upload;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.StorageName;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.crafting.upload.PatternSlotAccess;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSource;

/** The encoded-pattern output slot of an AE2 pattern terminal (block, cable part or wireless host). */
public final class Ae2TerminalSource implements PatternUploadSource {

    /** AE2 keeps the blank pattern in slot 0 and the encoded output in slot 1. */
    public static final int OUTPUT_SLOT = 1;

    private final IPatternTerminal terminal;
    private final IInventory inventory;
    private final PatternSlotAccess slots;

    private Ae2TerminalSource(IPatternTerminal terminal, IInventory inventory) {
        this.terminal = terminal;
        this.inventory = inventory;
        this.slots = PatternSlotAccess.of(inventory, inventory.getSizeInventory(), hostTile(terminal));
    }

    public static Ae2TerminalSource of(ContainerPatternTerm container) {
        IPatternTerminal terminal = container.getPatternTerminal();
        if (terminal == null) return null;
        IInventory inventory = terminal.getInventoryByName(StorageName.CRAFTING_PATTERN.getName());
        return inventory == null || inventory.getSizeInventory() <= OUTPUT_SLOT ? null
            : new Ae2TerminalSource(terminal, inventory);
    }

    private static TileEntity hostTile(IPatternTerminal terminal) {
        if (terminal instanceof TileEntity) return (TileEntity) terminal;
        if (terminal instanceof appeng.parts.AEBasePart) return ((appeng.parts.AEBasePart) terminal).getTile();
        // Wireless hosts live in the player's inventory; their window ownership is checked by the session.
        return null;
    }

    @Override
    public PatternSlotAccess slots() {
        return slots;
    }

    @Override
    public int slot() {
        return OUTPUT_SLOT;
    }

    @Override
    public IGrid grid() {
        IGridNode node = node();
        return node == null || !node.isActive() ? null : node.getGrid();
    }

    private IGridNode node() {
        try {
            return terminal.getActionableNode();
        } catch (RuntimeException offline) {
            return null;
        }
    }

    @Override
    public boolean canEdit(EntityPlayerMP player) {
        IGrid grid = grid();
        if (grid == null || !slots.isLive()) return false;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        return security == null || security.hasPermission(player, SecurityPermissions.BUILD);
    }

    @Override
    public Object identity() {
        return inventory;
    }
}
