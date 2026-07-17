package cn.dancingsnow.neoecoae.multiblock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECODrive;

/**
 * 1.7.10 placement-plan implementation for all three ECO structures.
 *
 * <p>
 * The existing formation scanner remains the source of truth after placement. This class only
 * generates the inverse plan, checks conflicts/materials, and places air cells on the server. It
 * intentionally never overwrites an occupied block, which makes the terminal safe to use beside
 * an existing base.
 * </p>
 */
public final class ECOStructureBuilder {

    public static final int DEFAULT_LENGTH = 1;
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 15;

    private ECOStructureBuilder() {}

    public static BuildResult preview(TileECOController controller, EntityPlayer player, int length) {
        return preview(controller, player, length, false);
    }

    public static BuildResult preview(TileECOController controller, EntityPlayer player, int length, boolean mirrored) {
        if (controller == null || controller.getWorldObj() == null || controller.getWorldObj().isRemote) {
            return BuildResult.invalid("invalid");
        }
        if (controller.isFormed()) {
            return BuildResult.invalid("formed");
        }
        Builder builder = new Builder(controller, clampLength(length), mirrored);
        builder.plan();
        return builder.evaluate(player, 0, null);
    }

    public static BuildResult build(TileECOController controller, EntityPlayer player, int length) {
        return build(controller, player, length, false);
    }

    public static BuildResult build(TileECOController controller, EntityPlayer player, int length, boolean mirrored) {
        if (controller == null || controller.getWorldObj() == null || controller.getWorldObj().isRemote) {
            return BuildResult.invalid("invalid");
        }
        if (player == null || !controller.isUseableByPlayer(player)) {
            return BuildResult.invalid("too_far");
        }
        if (controller.isFormed()) {
            return BuildResult.invalid("formed");
        }
        Builder builder = new Builder(controller, clampLength(length), mirrored);
        builder.plan();
        BuildResult before = builder.evaluate(player, 0, null);
        if (before.getError() != null || before.getConflicts() > 0
            || (!player.capabilities.isCreativeMode && before.getMissingMaterials() > 0)) {
            return before;
        }
        int placed = builder.place(player);
        ECOFormationResult formationResult = controller.scanFormation();
        return builder.evaluate(player, placed, formationResult);
    }

    /**
     * Dismantles every block tracked by the controller, preserving normal block and drive drops.
     * Infinite storage is only dismantled after its domain is empty; the controller's existing
     * removal guard is deliberately reused here.
     */
    public static DismantleResult dismantle(TileECOController controller, EntityPlayer player) {
        if (controller == null || controller.getWorldObj() == null || controller.getWorldObj().isRemote) {
            return DismantleResult.invalid("invalid");
        }
        if (player == null || !controller.isUseableByPlayer(player)) {
            return DismantleResult.invalid("too_far");
        }
        if (!controller.isFormed()) {
            return DismantleResult.invalid("not_formed");
        }
        if (controller.blocksWorldRemoval()) {
            return DismantleResult.invalid("infinite_locked");
        }

        World world = controller.getWorldObj();
        List<Pos> positions = new ArrayList<Pos>();
        Set<Long> seen = new HashSet<Long>();
        addPositions(positions, seen, controller.getHiddenBlocks());
        addPositions(positions, seen, controller.getFormedMemberBlocks());

        for (Pos position : positions) {
            TileEntity tile = world.getTileEntity(position.x, position.y, position.z);
            if (tile instanceof TileECODrive && !((TileECODrive) tile).canRemoveFromWorld()) {
                return DismantleResult.invalid("infinite_locked");
            }
        }

        int removed = 0;
        for (Pos position : positions) {
            Block block = world.getBlock(position.x, position.y, position.z);
            if (block == null || block.isAir(world, position.x, position.y, position.z)) {
                continue;
            }
            int metadata = world.getBlockMetadata(position.x, position.y, position.z);
            List<ItemStack> drops = block.getDrops(world, position.x, position.y, position.z, metadata, 0);
            if (!world.setBlockToAir(position.x, position.y, position.z)) {
                return DismantleResult.invalid("remove_failed");
            }
            for (ItemStack drop : drops) {
                giveOrDrop(world, player, drop, position);
            }
            removed++;
        }
        ECOFormationResult formationResult = controller.scanFormation();
        return new DismantleResult(removed, null, formationResult);
    }

    private static void addPositions(List<Pos> target, Set<Long> seen,
        List<cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos> source) {
        for (cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos position : source) {
            Pos pos = new Pos(position.getX(), position.getY(), position.getZ());
            if (seen.add(pos.key())) {
                target.add(pos);
            }
        }
    }

    private static void giveOrDrop(World world, EntityPlayer player, ItemStack stack, Pos position) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        ItemStack copy = stack.copy();
        if (!player.inventory.addItemStackToInventory(copy)) {
            world.spawnEntityInWorld(
                new EntityItem(world, position.x + 0.5D, position.y + 0.5D, position.z + 0.5D, copy));
        }
    }

    public static final class DismantleResult {

        private final int removedBlocks;
        private final String error;
        private final ECOFormationResult formationResult;

        private DismantleResult(int removedBlocks, String error, ECOFormationResult formationResult) {
            this.removedBlocks = removedBlocks;
            this.error = error;
            this.formationResult = formationResult;
        }

        private static DismantleResult invalid(String error) {
            return new DismantleResult(0, error, null);
        }

        public int getRemovedBlocks() {
            return this.removedBlocks;
        }

        public String getError() {
            return this.error;
        }

        public boolean isDismantled() {
            return this.error == null && this.removedBlocks > 0
                && this.formationResult != null
                && !this.formationResult.isFormed();
        }

        public ECOFormationResult getFormationResult() {
            return this.formationResult;
        }
    }

    private static int clampLength(int length) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
    }

    public static final class BuildResult {

        private final int plannedBlocks;
        private final int placedBlocks;
        private final int missingMaterials;
        private final int conflicts;
        private final int length;
        private final String error;
        private final ECOFormationResult formationResult;

        private BuildResult(int plannedBlocks, int placedBlocks, int missingMaterials, int conflicts, int length,
            String error, ECOFormationResult formationResult) {
            this.plannedBlocks = plannedBlocks;
            this.placedBlocks = placedBlocks;
            this.missingMaterials = missingMaterials;
            this.conflicts = conflicts;
            this.length = length;
            this.error = error;
            this.formationResult = formationResult;
        }

        private static BuildResult invalid(String error) {
            return new BuildResult(0, 0, 0, 0, 0, error, null);
        }

        public int getPlannedBlocks() {
            return this.plannedBlocks;
        }

        public int getPlacedBlocks() {
            return this.placedBlocks;
        }

        public int getMissingMaterials() {
            return this.missingMaterials;
        }

        public int getConflicts() {
            return this.conflicts;
        }

        public int getLength() {
            return this.length;
        }

        public String getError() {
            return this.error;
        }

        public ECOFormationResult getFormationResult() {
            return this.formationResult;
        }

        public boolean isFormed() {
            return this.formationResult != null && this.formationResult.isFormed();
        }

        public boolean canBuild() {
            return this.error == null && this.missingMaterials == 0 && this.conflicts == 0;
        }
    }

    private static final class Builder {

        private final TileECOController controller;
        private final World world;
        private final int length;
        private final Pos origin;
        private final boolean mirrored;
        private final ForgeDirection front;
        private final ForgeDirection back;
        private final ForgeDirection top = ForgeDirection.UP;
        private final ForgeDirection down = ForgeDirection.DOWN;
        private final ForgeDirection interfaceSide;
        private final ForgeDirection expandSide;
        private final Map<Pos, Placement> placements = new LinkedHashMap<Pos, Placement>();

        private Builder(TileECOController controller, int length, boolean mirrored) {
            this.controller = controller;
            this.world = controller.getWorldObj();
            this.length = length;
            this.mirrored = mirrored;
            this.origin = new Pos(controller.xCoord, controller.yCoord, controller.zCoord);
            this.front = controller.getFacing()
                .getDirection();
            this.back = this.front.getOpposite();
            ForgeDirection left = rotateClockwise(this.front);
            this.interfaceSide = mirrored ? left.getOpposite() : left;
            this.expandSide = mirrored ? left : left.getOpposite();
        }

        private void plan() {
            switch (this.controller.getSubsystem()) {
                case STORAGE:
                    this.planStorage();
                    break;
                case CRAFTING:
                    this.planCrafting();
                    break;
                case COMPUTATION:
                    this.planComputation();
                    break;
                default:
                    break;
            }
        }

        private BuildResult evaluate(EntityPlayer player, int placed, ECOFormationResult formationResult) {
            int conflicts = 0;
            Map<Item, Integer> required = new LinkedHashMap<Item, Integer>();
            for (Placement placement : this.placements.values()) {
                Block current = this.world.getBlock(placement.pos.x, placement.pos.y, placement.pos.z);
                int metadata = this.world.getBlockMetadata(placement.pos.x, placement.pos.y, placement.pos.z);
                if (current == placement.block && metadata == placement.metadata) {
                    continue;
                }
                if (!this.world.isAirBlock(placement.pos.x, placement.pos.y, placement.pos.z)) {
                    conflicts++;
                    continue;
                }
                Item item = Item.getItemFromBlock(placement.block);
                if (item != null) {
                    Integer count = required.get(item);
                    required.put(item, count == null ? 1 : count + 1);
                }
            }
            int missing = 0;
            if (player != null && !player.capabilities.isCreativeMode) {
                for (Map.Entry<Item, Integer> entry : required.entrySet()) {
                    missing += Math.max(0, entry.getValue() - countItem(player, entry.getKey()));
                }
            }
            return new BuildResult(
                this.placements.size(),
                placed,
                missing,
                conflicts,
                this.length,
                null,
                formationResult);
        }

        private int place(EntityPlayer player) {
            int placed = 0;
            for (Placement placement : this.placements.values()) {
                Block current = this.world.getBlock(placement.pos.x, placement.pos.y, placement.pos.z);
                int metadata = this.world.getBlockMetadata(placement.pos.x, placement.pos.y, placement.pos.z);
                if (current == placement.block && metadata == placement.metadata) {
                    continue;
                }
                Item item = Item.getItemFromBlock(placement.block);
                if (item == null || (!player.capabilities.isCreativeMode && !consumeItem(player, item))) {
                    continue;
                }
                boolean changed = this.world.setBlock(
                    placement.pos.x,
                    placement.pos.y,
                    placement.pos.z,
                    placement.block,
                    placement.metadata,
                    3);
                if (changed) {
                    placed++;
                } else {
                    player.inventory.addItemStackToInventory(new ItemStack(item));
                }
            }
            return placed;
        }

        private void planStorage() {
            this.placeColumn(this.origin.offset(this.interfaceSide), NEBlocks.storageCasing);
            this.placeColumn(this.origin.offset(this.back), NEBlocks.storageCasing);
            this.placeInterfaceColumn(
                this.origin.offset(this.interfaceSide)
                    .offset(this.back),
                NEBlocks.storageInterface,
                NEBlocks.storageCasing);
            this.place(this.origin.offset(this.top), NEBlocks.storageCasing);
            this.place(this.origin.offset(this.down), NEBlocks.storageCasing);

            Block energyCell = energyCell(this.controller.getTier());
            for (int i = 1; i <= this.length; i++) {
                Pos base = this.origin.offset(this.expandSide, i);
                this.placeDirectionalColumn(base, NEBlocks.ecoDrive, this.front);
                this.place(base.offset(this.back), NEBlocks.storageVent, this.back);
                this.place(
                    base.offset(this.back)
                        .offset(this.top),
                    energyCell,
                    this.back);
                this.place(
                    base.offset(this.back)
                        .offset(this.down),
                    energyCell,
                    this.back);
            }
            Pos tail = this.origin.offset(this.expandSide, this.length + 1);
            this.placeColumn(tail, NEBlocks.storageCasing);
            this.placeColumn(tail.offset(this.back), NEBlocks.storageCasing);
        }

        private void planCrafting() {
            this.placeColumn(this.origin.offset(this.interfaceSide), NEBlocks.craftingCasing);
            this.placeColumn(this.origin.offset(this.expandSide), NEBlocks.craftingCasing);
            this.placeColumn(this.origin.offset(this.back), NEBlocks.craftingCasing);
            this.place(this.origin.offset(this.top), NEBlocks.craftingCasing);
            this.place(this.origin.offset(this.down), NEBlocks.craftingCasing);
            this.placeColumn(
                this.origin.offset(this.back)
                    .offset(this.expandSide),
                NEBlocks.craftingCasing);

            Pos interfacePos = this.origin.offset(this.back)
                .offset(this.interfaceSide);
            this.place(interfacePos, NEBlocks.craftingInterface);
            this.place(interfacePos.offset(this.top), NEBlocks.inputHatch);
            this.place(interfacePos.offset(this.down), NEBlocks.outputHatch);

            Block parallelCore = craftingParallelCore(this.controller.getTier());
            for (int i = 0; i < this.length; i++) {
                Pos base = this.origin.offset(this.expandSide, i + 2);
                this.place(base, NEBlocks.craftingWorker, this.front);
                this.place(base.offset(this.top), parallelCore, this.front);
                this.place(base.offset(this.down), parallelCore, this.front);
                this.place(base.offset(this.back), NEBlocks.craftingVent, this.back);
                this.place(
                    base.offset(this.back)
                        .offset(this.top),
                    NEBlocks.craftingPatternBus,
                    this.back);
                this.place(
                    base.offset(this.back)
                        .offset(this.down),
                    NEBlocks.craftingPatternBus,
                    this.back);
            }
            Pos tail = this.origin.offset(this.expandSide, this.length + 2);
            this.placeColumn(tail, NEBlocks.craftingCasing);
            this.placeColumn(tail.offset(this.back), NEBlocks.craftingCasing);
        }

        private void planComputation() {
            this.placeColumn(this.origin.offset(this.interfaceSide), NEBlocks.computationCasing);
            this.placeColumn(this.origin.offset(this.expandSide), NEBlocks.computationCasing);
            this.placeColumn(this.origin.offset(this.back), NEBlocks.computationCasing);
            this.placeColumn(
                this.origin.offset(this.back)
                    .offset(this.expandSide),
                NEBlocks.computationCasing);
            this.placeInterfaceColumn(
                this.origin.offset(this.back)
                    .offset(this.interfaceSide),
                NEBlocks.computationInterface,
                NEBlocks.computationCasing);

            Block threadingCore = computationThreadingCore(this.controller.getTier());
            Block parallelCore = computationParallelCore(this.controller.getTier());
            for (int i = 0; i < this.length; i++) {
                Pos base = this.origin.offset(this.expandSide, i + 2);
                this.place(base, NEBlocks.computationTransmitter, this.front);
                this.place(base.offset(this.top), NEBlocks.computationDrive, this.front);
                this.place(base.offset(this.down), NEBlocks.computationDrive, this.front);
                this.place(base.offset(this.back), threadingCore, this.back);
                this.place(
                    base.offset(this.back)
                        .offset(this.top),
                    parallelCore,
                    this.back);
                this.place(
                    base.offset(this.back)
                        .offset(this.down),
                    parallelCore,
                    this.back);
            }

            Pos tail = this.origin.offset(this.expandSide, this.length + 2);
            this.place(tail, coolingController(this.controller.getTier()), this.expandSide);
            this.place(tail.offset(this.top), NEBlocks.computationCasing);
            this.place(tail.offset(this.down), NEBlocks.computationCasing);
            this.placeColumn(tail.offset(this.back), NEBlocks.computationCasing);
        }

        private void placeColumn(Pos center, Block block) {
            this.place(center, block);
            this.place(center.offset(this.top), block);
            this.place(center.offset(this.down), block);
        }

        private void placeDirectionalColumn(Pos center, Block block, ForgeDirection facing) {
            this.place(center, block, facing);
            this.place(center.offset(this.top), block, facing);
            this.place(center.offset(this.down), block, facing);
        }

        private void placeInterfaceColumn(Pos center, Block interfaceBlock, Block casing) {
            this.place(center, interfaceBlock);
            this.place(center.offset(this.top), casing);
            this.place(center.offset(this.down), casing);
        }

        private void place(Pos pos, Block block) {
            this.place(pos, block, 0);
        }

        private void place(Pos pos, Block block, ForgeDirection facing) {
            this.place(pos, block, metaFromDirection(facing));
        }

        private void place(Pos pos, Block block, int metadata) {
            if (pos.equals(this.origin)) {
                return;
            }
            this.placements.put(pos, new Placement(pos, block, metadata));
        }
    }

    private static final class Placement {

        private final Pos pos;
        private final Block block;
        private final int metadata;

        private Placement(Pos pos, Block block, int metadata) {
            this.pos = pos;
            this.block = block;
            this.metadata = metadata;
        }
    }

    private static final class Pos {

        private final int x;
        private final int y;
        private final int z;

        private Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private Pos offset(ForgeDirection direction) {
            return this.offset(direction, 1);
        }

        private Pos offset(ForgeDirection direction, int distance) {
            return new Pos(
                this.x + direction.offsetX * distance,
                this.y + direction.offsetY * distance,
                this.z + direction.offsetZ * distance);
        }

        private long key() {
            return (((long) this.x) * 31L + this.y) * 31L + this.z;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Pos)) {
                return false;
            }
            Pos other = (Pos) obj;
            return this.x == other.x && this.y == other.y && this.z == other.z;
        }

        @Override
        public int hashCode() {
            int result = this.x;
            result = 31 * result + this.y;
            return 31 * result + this.z;
        }
    }

    private static int countItem(EntityPlayer player, Item item) {
        int count = 0;
        if (player == null || player.inventory == null || item == null) {
            return count;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == item) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private static boolean consumeItem(EntityPlayer player, Item item) {
        if (player == null || player.inventory == null || item == null) {
            return false;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack == null || stack.getItem() != item) {
                continue;
            }
            stack.stackSize--;
            if (stack.stackSize <= 0) {
                player.inventory.mainInventory[i] = null;
            }
            return true;
        }
        return false;
    }

    private static Block energyCell(ECOControllerTier tier) {
        switch (tier) {
            case L6:
                return NEBlocks.energyCellL6;
            case L9:
                return NEBlocks.energyCellL9;
            case L4:
            default:
                return NEBlocks.energyCellL4;
        }
    }

    private static Block craftingParallelCore(ECOControllerTier tier) {
        switch (tier) {
            case L6:
                return NEBlocks.craftingParallelCoreL6;
            case L9:
                return NEBlocks.craftingParallelCoreL9;
            case L4:
            default:
                return NEBlocks.craftingParallelCoreL4;
        }
    }

    private static Block computationParallelCore(ECOControllerTier tier) {
        switch (tier) {
            case L6:
                return NEBlocks.computationParallelCoreL6;
            case L9:
                return NEBlocks.computationParallelCoreL9;
            case L4:
            default:
                return NEBlocks.computationParallelCoreL4;
        }
    }

    private static Block computationThreadingCore(ECOControllerTier tier) {
        switch (tier) {
            case L6:
                return NEBlocks.computationThreadingCoreL6;
            case L9:
                return NEBlocks.computationThreadingCoreL9;
            case L4:
            default:
                return NEBlocks.computationThreadingCoreL4;
        }
    }

    private static Block coolingController(ECOControllerTier tier) {
        switch (tier) {
            case L6:
                return NEBlocks.computationCoolingControllerL6;
            case L9:
                return NEBlocks.computationCoolingControllerL9;
            case L4:
            default:
                return NEBlocks.computationCoolingControllerL4;
        }
    }

    private static int metaFromDirection(ForgeDirection direction) {
        if (direction == ForgeDirection.EAST) {
            return ModelFacing.EAST.getMeta();
        }
        if (direction == ForgeDirection.SOUTH) {
            return ModelFacing.SOUTH.getMeta();
        }
        if (direction == ForgeDirection.WEST) {
            return ModelFacing.WEST.getMeta();
        }
        return ModelFacing.NORTH.getMeta();
    }

    private static ForgeDirection rotateClockwise(ForgeDirection direction) {
        switch (direction) {
            case NORTH:
                return ForgeDirection.EAST;
            case EAST:
                return ForgeDirection.SOUTH;
            case SOUTH:
                return ForgeDirection.WEST;
            case WEST:
                return ForgeDirection.NORTH;
            default:
                return direction;
        }
    }
}
