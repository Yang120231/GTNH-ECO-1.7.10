package cn.dancingsnow.neoecoae.multiblock;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public final class ECODebugStructureBuilder {

    public static final int DEFAULT_LENGTH = 3;

    private ECODebugStructureBuilder() {}

    public static BuildResult buildDefault(TileECOController controller) {
        return buildDefault(controller, DEFAULT_LENGTH);
    }

    public static BuildResult buildDefault(TileECOController controller, int length) {
        World world = controller.getWorldObj();
        if (world == null || world.isRemote) {
            return new BuildResult(0, ECOFormationResult.failed("client or missing world"));
        }

        Builder builder = new Builder(controller, Math.max(1, length));
        builder.build();
        ECOFormationResult result = controller.scanFormation();
        return new BuildResult(builder.getPlacedBlocks(), result);
    }

    public static class BuildResult {

        private final int placedBlocks;
        private final ECOFormationResult formationResult;

        private BuildResult(int placedBlocks, ECOFormationResult formationResult) {
            this.placedBlocks = placedBlocks;
            this.formationResult = formationResult;
        }

        public int getPlacedBlocks() {
            return this.placedBlocks;
        }

        public ECOFormationResult getFormationResult() {
            return this.formationResult;
        }
    }

    private static class Builder {

        private final TileECOController controller;
        private final World world;
        private final int length;
        private final Pos origin;
        private final ForgeDirection front;
        private final ForgeDirection back;
        private final ForgeDirection top = ForgeDirection.UP;
        private final ForgeDirection down = ForgeDirection.DOWN;
        private final ForgeDirection interfaceSide;
        private final ForgeDirection expandSide;
        private int placedBlocks;

        private Builder(TileECOController controller, int length) {
            this.controller = controller;
            this.world = controller.getWorldObj();
            this.length = length;
            this.origin = new Pos(controller.xCoord, controller.yCoord, controller.zCoord);
            this.front = controller.getFacing()
                .getDirection();
            this.back = this.front.getOpposite();
            this.interfaceSide = rotateClockwise(this.front);
            this.expandSide = this.interfaceSide.getOpposite();
        }

        private void build() {
            ECOControllerSubsystem subsystem = this.controller.getSubsystem();
            switch (subsystem) {
                case STORAGE:
                    this.buildStorage();
                    break;
                case CRAFTING:
                    this.buildCrafting();
                    break;
                case COMPUTATION:
                    this.buildComputation();
                    break;
                default:
                    break;
            }
        }

        private int getPlacedBlocks() {
            return this.placedBlocks;
        }

        private void buildStorage() {
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

        private void buildCrafting() {
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

        private void buildComputation() {
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

        private void place(Pos pos, Block block, int meta) {
            if (pos.equals(this.origin)) {
                return;
            }
            if (this.world.getBlock(pos.x, pos.y, pos.z) != block
                || this.world.getBlockMetadata(pos.x, pos.y, pos.z) != meta) {
                this.placedBlocks++;
            }
            this.world.setBlock(pos.x, pos.y, pos.z, block, meta, 3);
        }
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

    private static class Pos {

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
            result = 31 * result + this.z;
            return result;
        }
    }
}
