package cn.dancingsnow.neoecoae.platform.minecraft;

import java.util.Iterator;
import java.util.NoSuchElementException;

import appeng.api.util.WorldCoord;

/** Integer world coordinates. Mutable cursors explicitly copy when used as retained positions. */
public class BlockPos {

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    protected int x, y, z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos offset(BlockPos other) {
        return offset(other.x, other.y, other.z);
    }

    public BlockPos subtract(BlockPos other) {
        return offset(-other.x, -other.y, -other.z);
    }

    public BlockPos relative(Direction side) {
        return relative(side, 1);
    }

    public BlockPos relative(Direction side, int distance) {
        return offset(side.getStepX() * distance, side.getStepY() * distance, side.getStepZ() * distance);
    }

    public BlockPos above() {
        return relative(Direction.UP);
    }

    public BlockPos above(int amount) {
        return relative(Direction.UP, amount);
    }

    public BlockPos below() {
        return relative(Direction.DOWN);
    }

    public BlockPos immutable() {
        return this;
    }

    public MutableBlockPos mutable() {
        return new MutableBlockPos(x, y, z);
    }

    public WorldCoord nativeCoord() {
        return new WorldCoord(x, y, z);
    }

    public double distSqr(BlockPos other) {
        double dx = (double) x - other.x, dy = (double) y - other.y, dz = (double) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public long asLong() {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }

    public static BlockPos of(long packed) {
        return new BlockPos((int) (packed >> 38), (int) (packed << 52 >> 52), (int) (packed << 26 >> 38));
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos first, BlockPos second) {
        int minX = Math.min(first.x, second.x), minY = Math.min(first.y, second.y), minZ = Math.min(first.z, second.z);
        int maxX = Math.max(first.x, second.x), maxY = Math.max(first.y, second.y), maxZ = Math.max(first.z, second.z);
        return () -> new Iterator<BlockPos>() {

            private int nextX = minX, nextY = minY, nextZ = minZ;
            private boolean remaining = true;

            @Override
            public boolean hasNext() {
                return remaining;
            }

            @Override
            public BlockPos next() {
                if (!remaining) throw new NoSuchElementException();
                BlockPos result = new BlockPos(nextX, nextY, nextZ);
                if (nextX != maxX) nextX++;
                else if (nextY != maxY) {
                    nextX = minX;
                    nextY++;
                } else if (nextZ != maxZ) {
                    nextX = minX;
                    nextY = minY;
                    nextZ++;
                } else remaining = false;
                return result;
            }
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockPos && x == ((BlockPos) other).x
            && y == ((BlockPos) other).y
            && z == ((BlockPos) other).z;
    }

    @Override
    public int hashCode() {
        return (y + z * 31) * 31 + x;
    }

    @Override
    public String toString() {
        return "BlockPos[" + x + "," + y + "," + z + "]";
    }

    public static final class MutableBlockPos extends BlockPos {

        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        public MutableBlockPos set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public MutableBlockPos set(BlockPos pos) {
            return set(pos.x, pos.y, pos.z);
        }

        public MutableBlockPos move(Direction direction) {
            return move(direction, 1);
        }

        public MutableBlockPos move(Direction direction, int count) {
            return set(
                x + direction.getStepX() * count,
                y + direction.getStepY() * count,
                z + direction.getStepZ() * count);
        }

        @Override
        public BlockPos immutable() {
            return new BlockPos(x, y, z);
        }
    }
}
