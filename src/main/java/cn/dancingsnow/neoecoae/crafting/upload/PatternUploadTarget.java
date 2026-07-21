package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.util.IInterfaceViewable;
import appeng.helpers.IInterfaceHost;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/** A server-owned, short-lived upload destination. */
public final class PatternUploadTarget {

    public enum Kind {
        AE2_INTERFACE,
        GT_CRAFTING_INPUT,
        ECO_PATTERN_BUS
    }

    private final String id;
    private final Kind kind;
    private final String name;
    private final ItemStack icon;
    private final ItemStack routingIcon;
    private final RecipeMap<?> recipeMap;
    private final IInventory inventory;
    private final int slotCount;
    private final int x;
    private final int y;
    private final int z;
    private final int dimension;
    private final IGrid grid;
    private final Object host;

    private PatternUploadTarget(String id, Kind kind, String name, ItemStack icon, ItemStack routingIcon,
        RecipeMap<?> recipeMap, IInventory inventory, int slotCount, int x, int y, int z, int dimension, IGrid grid,
        Object host) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.icon = icon == null ? null : icon.copy();
        this.routingIcon = routingIcon == null ? null : routingIcon.copy();
        this.recipeMap = recipeMap;
        this.inventory = inventory;
        this.slotCount = Math.max(0, Math.min(slotCount, inventory == null ? 0 : inventory.getSizeInventory()));
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.grid = grid;
        this.host = host;
    }

    public static PatternUploadTarget interfaceTarget(String id, IInterfaceViewable viewable, IGrid grid) {
        TileEntity tile = viewable.getTileEntity();
        int dimension = tile == null || tile.getWorldObj() == null ? 0 : tile.getWorldObj().provider.dimensionId;
        ItemStack routingIcon = viewable.getDisplayRep();
        ItemStack icon = routingIcon;
        if (icon == null) {
            icon = viewable.getSelfRep();
        }
        Kind kind = viewable.getClass()
            .getName()
            .contains("MTEHatchCraftingInputME") ? Kind.GT_CRAFTING_INPUT : Kind.AE2_INTERFACE;
        return new PatternUploadTarget(
            id,
            kind,
            viewable.getName(),
            icon,
            routingIcon,
            recipeMap(viewable, routingIcon),
            viewable.getPatterns(),
            viewable.numSlots(),
            tile == null ? 0 : tile.xCoord,
            tile == null ? 0 : tile.yCoord,
            tile == null ? 0 : tile.zCoord,
            dimension,
            grid,
            viewable);
    }

    public static PatternUploadTarget ecoTarget(String id, TileCraftingPatternBus bus, TileECOInterface owner,
        IGrid grid) {
        int dimension = bus.getWorldObj() == null ? 0 : bus.getWorldObj().provider.dimensionId;
        ItemStack busIcon = bus.getBlockType() == null ? null
            : new ItemStack(bus.getBlockType(), 1, bus.getBlockMetadata());
        return new PatternUploadTarget(
            id,
            Kind.ECO_PATTERN_BUS,
            bus.getInventoryName(),
            busIcon,
            null,
            null,
            bus,
            bus.getSizeInventory(),
            bus.xCoord,
            bus.yCoord,
            bus.zCoord,
            dimension,
            grid,
            owner);
    }

    public String getId() {
        return this.id;
    }

    public Kind getKind() {
        return this.kind;
    }

    public String getName() {
        return this.name == null ? "" : this.name;
    }

    public ItemStack getIcon() {
        return this.icon == null ? null : this.icon.copy();
    }

    public int getSlotCount() {
        return this.slotCount;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getDimension() {
        return this.dimension;
    }

    public IGrid getGrid() {
        return this.grid;
    }

    public Object getHost() {
        return this.host;
    }

    public boolean isCompatible(boolean processing, ICraftingPatternDetails details) {
        return this.compatibilityRank(processing, details) >= 0;
    }

    public int compatibilityRank(boolean processing, ICraftingPatternDetails details) {
        if (this.kind == Kind.ECO_PATTERN_BUS) return processing ? 2 : 0;
        if (processing) {
            if (PatternRecipeMatcher.matches(this.recipeMap, details)) return 0;
            if (this.kind == Kind.AE2_INTERFACE && this.host instanceof IInterfaceViewable) {
                List<RecipeMap<?>> adjacentMaps = adjacentRecipeMaps((IInterfaceViewable) this.host);
                for (RecipeMap<?> adjacentMap : adjacentMaps) {
                    if (PatternRecipeMatcher.matches(adjacentMap, details)) return 0;
                }
                if (this.recipeMap != null || !adjacentMaps.isEmpty()) return 1;
            }
            return this.kind == Kind.GT_CRAFTING_INPUT && this.recipeMap != null ? 1 : -1;
        }
        ItemStack display = this.routingIcon;
        if (display == null) return -1;
        ItemStack molecular = AEApi.instance()
            .definitions()
            .blocks()
            .molecularAssembler()
            .maybeStack(1)
            .orNull();
        boolean molecularTarget = molecular != null && display.isItemEqual(molecular);
        return molecularTarget ? 0 : -1;
    }

    public int getEmptySlots() {
        int empty = 0;
        for (int i = 0; i < this.slotCount; i++) {
            if (this.inventory.getStackInSlot(i) == null) empty++;
        }
        return empty;
    }

    public int firstEmptySlot(ItemStack pattern) {
        if (pattern == null || this.inventory == null) return -1;
        for (int i = 0; i < this.slotCount; i++) {
            ItemStack existing = this.inventory.getStackInSlot(i);
            if (existing != null && existing.isItemEqual(pattern)
                && ItemStack.areItemStackTagsEqual(existing, pattern)) {
                return -2;
            }
            if (existing == null && this.inventory.isItemValidForSlot(i, pattern)) {
                return i;
            }
        }
        return -1;
    }

    public boolean insert(ItemStack pattern) {
        if (!this.isStillOnGrid()) return false;
        int slot = this.firstEmptySlot(pattern);
        if (slot < 0) return false;
        this.inventory.setInventorySlotContents(slot, pattern.copy());
        return true;
    }

    private boolean isStillOnGrid() {
        if (!(this.host instanceof IGridHost)) return false;
        IGridNode node = ((IGridHost) this.host).getGridNode(ForgeDirection.UNKNOWN);
        return node != null && node.isActive() && node.getGrid() == this.grid;
    }

    private static RecipeMap<?> recipeMap(IInterfaceViewable viewable, ItemStack routingIcon) {
        if (viewable instanceof MTEHatchCraftingInputME) {
            return ((MTEHatchCraftingInputME) viewable).mRecipeMap;
        }
        if (routingIcon == null || routingIcon.getItem() != Item.getItemFromBlock(GregTechAPI.sBlockMachines)) {
            return null;
        }
        int id = routingIcon.getItemDamage();
        if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length) return null;
        IMetaTileEntity machine = GregTechAPI.METATILEENTITIES[id];
        if (machine instanceof RecipeMapWorkable) return ((RecipeMapWorkable) machine).getRecipeMap();
        if (machine instanceof MTEHatchInputBus) return ((MTEHatchInputBus) machine).mRecipeMap;
        return null;
    }

    private static List<RecipeMap<?>> adjacentRecipeMaps(IInterfaceViewable viewable) {
        if (!(viewable instanceof IInterfaceHost)) return Collections.emptyList();
        TileEntity tile = viewable.getTileEntity();
        if (tile == null || tile.getWorldObj() == null) return Collections.emptyList();
        List<RecipeMap<?>> maps = new ArrayList<>();
        for (ForgeDirection direction : ((IInterfaceHost) viewable).getTargets()) {
            if (direction == null || direction == ForgeDirection.UNKNOWN) continue;
            TileEntity adjacent = tile.getWorldObj()
                .getTileEntity(
                    tile.xCoord + direction.offsetX,
                    tile.yCoord + direction.offsetY,
                    tile.zCoord + direction.offsetZ);
            if (!(adjacent instanceof IGregTechTileEntity)) continue;
            IMetaTileEntity machine = ((IGregTechTileEntity) adjacent).getMetaTileEntity();
            RecipeMap<?> map = null;
            if (machine instanceof RecipeMapWorkable) {
                map = ((RecipeMapWorkable) machine).getRecipeMap();
            } else if (machine instanceof MTEHatchInputBus) {
                map = ((MTEHatchInputBus) machine).mRecipeMap;
            }
            if (map != null && !maps.contains(map)) maps.add(map);
        }
        return maps;
    }
}
