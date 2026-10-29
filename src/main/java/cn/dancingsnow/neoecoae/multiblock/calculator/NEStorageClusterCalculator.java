package cn.dancingsnow.neoecoae.multiblock.calculator;

import java.util.List;
import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.registry.NativeEnergyCellBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageMachines;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class NEStorageClusterCalculator extends NEClusterCalculator<NEStorageCluster> {

    public NEStorageClusterCalculator(NEBlockEntity<NEStorageCluster, ?> t) {
        super(t);
    }

    @Override
    public NEStorageCluster createCluster(ServerWorldView level, BlockPos min, BlockPos max) {
        return new NEStorageCluster(min, max);
    }

    @Override
    protected int maxLength() {
        return PlanningSettings.Multiblock.storageSystemMaxLength;
    }

    @Override
    protected Holder<Block> casing() {
        return StorageMachines.STORAGE_CASING;
    }

    @Override
    public boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max) {
        Optional<ControllerContext<ECOStorageSystemBlockEntity>> contextResult = findUniqueController(
            level,
            min,
            max,
            ECOStorageSystemBlockEntity.class);
        if (!contextResult.isPresent()) return false;
        ControllerContext<ECOStorageSystemBlockEntity> context = contextResult
            .orElseThrow(java.util.NoSuchElementException::new);
        ECOStorageSystemBlockEntity controller = context.controller();
        BlockPos controllerPos = context.position();
        StorageTierDescriptor tier = controller.getTier();
        Direction front = context.front();
        Direction back = context.back();
        Direction top = context.top();
        Direction down = context.down();
        Direction interfaceSide = context.right();
        Direction expandSide = interfaceSide.getOpposite();

        if (verifyStructure(level, controllerPos, tier, front, back, top, down, interfaceSide, expandSide)) {
            controller.setMirrored(false);
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, expandSide, interfaceSide)) {
            controller.setMirrored(true);
            return true;
        }
        controller.setMirrored(false);
        return false;
    }

    private boolean verifyStructure(ServerWorldView level, BlockPos controllerPos, StorageTierDescriptor tier,
        Direction front, Direction back, Direction top, Direction down, Direction staticSide, Direction expandSide) {
        if (!validateCasing(level, controllerPos, top, down, staticSide)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateInterface(
            level,
            controllerPos.relative(staticSide)
                .relative(back),
            top,
            down)) return false;
        if (!validateBlock(level, controllerPos.relative(top), BlockState::is, StorageMachines.STORAGE_CASING)) {
            return false;
        }
        if (!validateBlock(level, controllerPos.relative(down), BlockState::is, StorageMachines.STORAGE_CASING)) {
            return false;
        }
        BlockPos transitionCenter = controllerPos.relative(expandSide);
        if (!validateCasing(level, transitionCenter, top, down)) return false;
        if (!validateCasing(level, transitionCenter.relative(back), top, down)) return false;

        BlockPos firstStorageColumn = transitionCenter.relative(expandSide);
        BlockPos storageBlocksStart = firstStorageColumn.relative(top);
        BlockPos storageBlocksEnd = expandTowards(
            level,
            expandSide,
            firstStorageColumn.relative(down),
            matchingStateFacing(StorageMachines.ECO_DRIVE, front));
        if (!validateBlocks(
            level,
            storageBlocksStart,
            storageBlocksEnd,
            state -> state.is(StorageMachines.ECO_DRIVE)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front)) {
            return false;
        }
        BlockPos ventStart = firstStorageColumn.relative(back);
        Optional<BlockPos> ventEndResult = validateBlockLine(
            level,
            expandSide,
            ventStart,
            matchingStateFacing(StorageMachines.STORAGE_VENT, back));
        if (!ventEndResult.isPresent()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos upperEnergyCellStart = firstStorageColumn.relative(back)
            .relative(top);
        Optional<BlockPos> upperEnergyCellResult = validateBlockLine(
            level,
            expandSide,
            upperEnergyCellStart,
            (state, pos) -> ECOMultiblockParts.matchesTiered(
                ECOMultiblockParts.context(level, pos),
                ECOMultiblockParts.Role.ENERGY_CELL,
                tier,
                state.getBlock() instanceof NativeEnergyCellBlock,
                back,
                cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity.class));
        if (!upperEnergyCellResult.isPresent()) {
            return false;
        }
        BlockPos upperEnergyCellEnd = upperEnergyCellResult.orElseThrow(java.util.NoSuchElementException::new);
        if (upperEnergyCellEnd.equals(upperEnergyCellStart) && !validateBlock(
            level,
            upperEnergyCellStart,
            state -> ECOMultiblockParts.matchesTiered(
                ECOMultiblockParts.context(level, upperEnergyCellEnd),
                ECOMultiblockParts.Role.ENERGY_CELL,
                tier,
                state.getBlock() instanceof NativeEnergyCellBlock,
                back,
                cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity.class))) {
            return false;
        }
        BlockPos lowerEnergyCellStart = firstStorageColumn.relative(back)
            .relative(down);
        Optional<BlockPos> lowerEnergyCellResult = validateBlockLine(
            level,
            expandSide,
            lowerEnergyCellStart,
            (state, pos) -> ECOMultiblockParts.matchesTiered(
                ECOMultiblockParts.context(level, pos),
                ECOMultiblockParts.Role.ENERGY_CELL,
                tier,
                state.getBlock() instanceof NativeEnergyCellBlock,
                back,
                cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity.class));
        if (!lowerEnergyCellResult.isPresent()) {
            return false;
        }
        BlockPos lowerEnergyCellEnd = lowerEnergyCellResult.orElseThrow(java.util.NoSuchElementException::new);

        BlockPos.MutableBlockPos tailCasing = storageBlocksEnd.mutable()
            .move(expandSide)
            .move(top);
        List<BlockPos> tailCasingPoses = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            upperEnergyCellEnd.relative(expandSide),
            lowerEnergyCellEnd.relative(expandSide),
            ventEnd.relative(expandSide),
            tailCasing.immutable(),
            tailCasing.relative(top),
            tailCasing.relative(down));
        if (!ensureSameSurface(tailCasingPoses)) {
            return false;
        }
        return validateBlocks(level, tailCasingPoses, BlockState::is, StorageMachines.STORAGE_CASING);
    }

    @Override
    public boolean isValidBlockEntity(TileEntity te) {
        return (te instanceof NEBlockEntity<?, ?>neBlockEntity
            && neBlockEntity.getCalculator() instanceof NEStorageClusterCalculator);
    }

    protected boolean validateInterface(ServerWorldView level, BlockPos interfacePos, Direction top, Direction down) {
        return validateInterface(
            level,
            interfacePos,
            top,
            down,
            StorageMachines.STORAGE_INTERFACE,
            StorageMachines.STORAGE_CASING);
    }
}
