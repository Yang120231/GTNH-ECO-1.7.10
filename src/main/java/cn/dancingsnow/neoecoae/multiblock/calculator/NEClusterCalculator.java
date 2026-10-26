package cn.dancingsnow.neoecoae.multiblock.calculator;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirements;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IOrientationStrategy;
import cn.dancingsnow.neoecoae.platform.ae2.grid.OrientationStrategies;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public abstract class NEClusterCalculator<C extends NECluster<C>> extends MBCalculator<NEBlockEntity<C, ?>, C> {

    protected final NEBlockEntity<C, ?> owner;

    public NEClusterCalculator(NEBlockEntity<C, ?> t) {
        super(t);
        owner = t;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateBlockEntities(C c, ServerWorldView level, BlockPos min, BlockPos max) {
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            TileEntity candidate = level.getBlockEntity(blockPos);
            if (!(candidate instanceof NEBlockEntity<?, ?>) || !isValidBlockEntity(candidate)) {
                this.disconnect();
                return;
            }
            NEBlockEntity<C, ?> blockEntity = (NEBlockEntity<C, ?>) candidate;
            c.addBlockEntity(blockEntity);
        }
        c.getBlockEntities()
            .forEachRemaining(it -> {
                if (it instanceof NEBlockEntity<?, ?>blockEntity) {
                    updateClusterUnchecked(blockEntity, c);
                }
            });
        c.updateFormed(true);
        this.onClusterAttached(c);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void updateClusterUnchecked(NEBlockEntity<?, ?> blockEntity, NECluster<?> cluster) {
        ((NEBlockEntity) blockEntity).updateCluster(cluster);
    }

    protected void onClusterAttached(C cluster) {}

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        if (sizeX > sizeZ) {
            return sizeX <= maxLength() && sizeY == 3 && sizeZ == 2;
        } else {
            return sizeZ <= maxLength() && sizeY == 3 && sizeX == 2;
        }
    }

    protected abstract int maxLength();

    protected abstract Holder<Block> casing();

    @FunctionalInterface
    public interface Factory<C extends NECluster<C>> {

        NEClusterCalculator<C> create(NEBlockEntity<C, ?> blockEntity);
    }

    public static boolean validateBlock(WorldView level, BlockPos pos, Predicate<BlockState> fn) {
        return fn.test(level.getBlockState(pos));
    }

    public static <T> boolean validateBlock(WorldView level, BlockPos pos, BiPredicate<BlockState, T> fn, T value) {
        BlockState actual = level.getBlockState(pos);
        boolean accepted = fn.test(actual, value);
        Block block = value instanceof Block direct ? direct
            : value instanceof Holder<?>holder && holder.value() instanceof Block held ? held : null;
        if (block == null) return accepted;
        BlockState expected = BlockState.defaultState(block);
        var role = ECOMultiblockParts.acceptsExpected(ECOMultiblockParts.context(level, pos), expected);
        return ECOMultiblockRequirements.accepts(
            new ECOMultiblockRequirements.Context(level, pos.immutable(), expected, actual),
            role == null ? accepted : role);
    }

    public static BlockPos expandTowards(WorldView level, Direction direction, BlockPos start, Holder<Block> type) {
        return expandTowards(level, direction, start, type.value());
    }

    public static BlockPos expandTowards(WorldView level, Direction direction, BlockPos start, Block type) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        // Reuse the mutable cursor while probing long structures to avoid one allocation per block.
        while (level.getBlockState(mutable.relative(direction))
            .is(type)) {
            mutable.move(direction);
        }
        return mutable.immutable();
    }

    public static BlockPos expandTowards(WorldView level, Direction direction, BlockPos start,
        Predicate<BlockState> fn) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        while (fn.test(level.getBlockState(mutable.relative(direction)))) {
            mutable.move(direction);
        }
        return mutable.immutable();
    }

    public static BlockPos expandTowards(WorldView level, Direction direction, BlockPos start,
        BiPredicate<BlockState, BlockPos> fn) {
        BlockPos.MutableBlockPos mutable = start.mutable();
        BlockPos pos = mutable.relative(direction);
        while (fn.test(level.getBlockState(pos), pos)) {
            mutable.move(direction);
            pos = mutable.relative(direction);
        }
        return mutable.immutable();
    }

    public static boolean validateBlocks(WorldView level, BlockPos from, BlockPos to, Predicate<BlockState> fn) {
        for (BlockPos blockPos : BlockPos.betweenClosed(from, to)) {
            if (!fn.test(level.getBlockState(blockPos))) {
                return false;
            }
        }
        return true;
    }

    public static <T> boolean validateBlocks(WorldView level, Iterable<BlockPos> iterable,
        BiPredicate<BlockState, T> fn, T value) {
        for (BlockPos blockPos : iterable) {
            if (!validateBlock(level, blockPos, fn, value)) {
                return false;
            }
        }
        return true;
    }

    public static <T> boolean validateBlocks(WorldView level, BlockPos from, BlockPos to, BiPredicate<BlockState, T> fn,
        T value) {
        return validateBlocks(level, BlockPos.betweenClosed(from, to), fn, value);
    }

    protected final boolean validateCasing(ServerWorldView level, BlockPos centerPos, Direction top, Direction down,
        Holder<Block> casing) {
        if (!validateBlock(level, centerPos, BlockState::is, casing)) {
            return false;
        }
        if (!validateBlock(level, centerPos.relative(top), BlockState::is, casing)) {
            return false;
        }
        return validateBlock(level, centerPos.relative(down), BlockState::is, casing);
    }

    protected final boolean validateCasing(ServerWorldView level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, casing());
    }

    protected final boolean validateCasing(ServerWorldView level, BlockPos origin, Direction top, Direction down,
        Direction direction) {
        return validateCasing(level, origin.relative(direction), top, down);
    }

    protected boolean validateInterface(ServerWorldView level, BlockPos interfacePos, Direction top, Direction down,
        Holder<Block> interfaceType, Holder<Block> casingType) {
        if (!validateBlock(level, interfacePos, BlockState::is, interfaceType)) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, casingType)) {
            return false;
        }
        return validateBlock(level, interfacePos.relative(down), BlockState::is, casingType);
    }

    protected static boolean ensureSameSurface(List<BlockPos> list) {
        int x = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(list)
            .getX();
        int y = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(list)
            .getY();
        int z = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(list)
            .getZ();
        boolean sameX = true;
        boolean sameY = true;
        boolean sameZ = true;
        for (BlockPos blockPos : list) {
            if (blockPos.getX() != x) {
                sameX = false;
            }
            if (blockPos.getY() != y) {
                sameY = false;
            }
            if (blockPos.getZ() != z) {
                sameZ = false;
            }
            x = blockPos.getX();
            y = blockPos.getY();
            z = blockPos.getZ();
        }
        return sameX || sameY || sameZ;
    }

    protected static Optional<BlockPos> validateBlockLine(WorldView level, Direction expandDirection, BlockPos start,
        BiPredicate<BlockState, BlockPos> blockPredicate) {
        if (!validateBlock(level, start, it -> blockPredicate.test(it, start))) {
            return Optional.empty();
        }
        BlockPos end = expandTowards(level, expandDirection, start, blockPredicate);
        return Optional.of(end);
    }

    protected final <T extends NEBlockEntity<?, ?>> Optional<ControllerContext<T>> findUniqueController(
        ServerWorldView level, BlockPos min, BlockPos max, Class<T> controllerType) {
        Set<BlockPos> validControllerPositions = MultiBlockUtil.allPossibleController(min, max);
        Optional<T> controller = findUnique(
            BlockPos.betweenClosed(min, max),
            pos -> controllerType.isInstance(level.getBlockEntity(pos)) ? controllerType.cast(level.getBlockEntity(pos))
                : null);
        if (!controller.isPresent()) {
            return Optional.empty();
        }

        T blockEntity = controller.orElseThrow(java.util.NoSuchElementException::new);
        if (!validControllerPositions.contains(blockEntity.getBlockPos())) {
            return Optional.empty();
        }
        BlockState state = blockEntity.getBlockState();
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction back = strategy.getSide(state, RelativeSide.BACK);
        Direction top = strategy.getSide(state, RelativeSide.TOP);
        Direction left = strategy.getSide(state, RelativeSide.LEFT);
        return Optional.of(
            new ControllerContext<>(
                blockEntity,
                blockEntity.getBlockPos(),
                state,
                back.getOpposite(),
                back,
                top,
                top.getOpposite(),
                left,
                left.getOpposite()));
    }

    protected final <T extends NEBlockEntity<?, ?>> Optional<T> findCandidateController(ServerWorldView level,
        BlockPos min, BlockPos max, Class<T> controllerType) {
        return findUnique(MultiBlockUtil.allPossibleController(min, max), pos -> {
            TileEntity candidate = level.getBlockEntity(pos);
            return controllerType.isInstance(candidate) ? controllerType.cast(candidate) : null;
        });
    }

    static <T> Optional<T> findUnique(Iterable<BlockPos> positions, Function<BlockPos, T> finder) {
        T found = null;
        for (BlockPos pos : positions) {
            T candidate = finder.apply(pos);
            if (candidate == null) {
                continue;
            }
            if (found != null) {
                return Optional.empty();
            }
            found = candidate;
        }
        return Optional.ofNullable(found);
    }

    protected static BiPredicate<BlockState, BlockPos> matchingStateFacing(Holder<Block> block, Direction facing) {
        BlockState expected = BlockState.defaultState(block.value());
        if (expected.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            expected = expected.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        BlockState wanted = expected;
        return (state, pos) -> {
            var role = ECOMultiblockParts
                .acceptsExpected(new ECOMultiblockParts.Context(null, pos.immutable(), state, null), wanted);
            return ECOMultiblockRequirements.accepts(
                new ECOMultiblockRequirements.Context(null, pos.immutable(), wanted, state),
                role == null ? state.is(block) && state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                    .orElse(facing) == facing : role);
        };
    }

    @com.github.bsideup.jabel.Desugar
    protected record ControllerContext<T extends NEBlockEntity<?, ?>> (T controller, BlockPos position,
        BlockState state, Direction front, Direction back, Direction top, Direction down, Direction left,
        Direction right) {}
}
