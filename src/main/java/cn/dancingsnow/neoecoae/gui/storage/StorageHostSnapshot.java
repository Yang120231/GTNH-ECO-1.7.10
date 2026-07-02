package cn.dancingsnow.neoecoae.gui.storage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageDomainData;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMode;
import cn.dancingsnow.neoecoae.storage.item.IECOStorageMatrixItem;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECODrive;
import io.netty.buffer.ByteBuf;

public final class StorageHostSnapshot {

    private static final int MAX_TYPE_STATS = 32;
    private static final int MAX_MATRIX_CELLS = 256;

    public static final StorageHostSnapshot EMPTY = new StorageHostSnapshot(
        false,
        "L4",
        ECOStorageHostMode.UNFORMED.getId(),
        0,
        0,
        16,
        0,
        false,
        0L,
        BigInteger.ZERO,
        0L,
        0L,
        0L,
        false,
        Collections.<TypeStat>emptyList(),
        Collections.<MatrixCell>emptyList());

    public final boolean formed;
    public final String tier;
    public final String hostMode;
    public final int infiniteComponentCount;
    public final int formedDriveCount;
    public final int requiredDriveCount;
    public final int priority;
    public final boolean allDrivesL9;
    public final long usedBytes;
    public final BigInteger preciseUsedBytes;
    public final long totalBytes;
    public final long usedTypes;
    public final long totalTypes;
    public final boolean canTakeInfiniteComponent;
    public final List<TypeStat> typeStats;
    public final List<MatrixCell> matrixCells;

    private StorageHostSnapshot(boolean formed, String tier, String hostMode, int infiniteComponentCount,
        int formedDriveCount, int requiredDriveCount, int priority, boolean allDrivesL9, long usedBytes,
        BigInteger preciseUsedBytes, long totalBytes, long usedTypes, long totalTypes, boolean canTakeInfiniteComponent,
        List<TypeStat> typeStats, List<MatrixCell> matrixCells) {
        this.formed = formed;
        this.tier = tier;
        this.hostMode = hostMode;
        this.infiniteComponentCount = infiniteComponentCount;
        this.formedDriveCount = formedDriveCount;
        this.requiredDriveCount = requiredDriveCount;
        this.priority = priority;
        this.allDrivesL9 = allDrivesL9;
        this.usedBytes = usedBytes;
        this.preciseUsedBytes = preciseUsedBytes == null ? BigInteger.ZERO : preciseUsedBytes.max(BigInteger.ZERO);
        this.totalBytes = totalBytes;
        this.usedTypes = usedTypes;
        this.totalTypes = totalTypes;
        this.canTakeInfiniteComponent = canTakeInfiniteComponent;
        this.typeStats = Collections.unmodifiableList(typeStats);
        this.matrixCells = Collections.unmodifiableList(matrixCells);
    }

    public static StorageHostSnapshot create(TileECOController controller) {
        if (controller == null) {
            return EMPTY;
        }
        List<ECOFormationBlockPos> positions = controller.getFormedMemberBlocks();
        List<MatrixCell> cells = new ArrayList<MatrixCell>();
        TypeAccumulator itemStats = new TypeAccumulator("item", "Items");
        TypeAccumulator fluidStats = new TypeAccumulator("fluid", "Fluids");
        long usedBytes = 0L;
        BigInteger preciseUsedBytes = BigInteger.ZERO;
        long totalBytes = 0L;
        long totalTypes = 0L;
        long usedTypes = 0L;

        boolean hostDomainStorage = controller.canUseHostDomainStorage();
        if (hostDomainStorage && controller.getWorldObj() != null) {
            ECOStorageBackend domain = ECOStorageDomainData.get(controller.getWorldObj())
                .getDomain(controller.getHostDomainId());
            if (domain != null) {
                preciseUsedBytes = domain.getUsed()
                    .toBigInteger();
                usedBytes = saturatedLong(preciseUsedBytes);
                usedTypes = domain.getTypeCount();
                addBackendStats(domain, itemStats, fluidStats);
            }
        }

        MatrixGridLayout layout = MatrixGridLayout.from(controller, positions);
        for (int i = 0; i < positions.size() && cells.size() < MAX_MATRIX_CELLS; i++) {
            ECOFormationBlockPos pos = positions.get(i);
            TileEntity tile = controller.getWorldObj() == null ? null
                : controller.getWorldObj()
                    .getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            int row = layout.rowFor(pos);
            int column = layout.columnFor(pos);
            MatrixCell cell = MatrixCell.empty(row, column);
            if (tile instanceof TileECODrive) {
                MatrixRead matrix = MatrixRead.fromDrive((TileECODrive) tile);
                cell = MatrixCell.fromMatrix(row, column, matrix);
                if (cell.hasCell) {
                    totalBytes = saturatedAdd(totalBytes, cell.totalBytes);
                    totalTypes = saturatedAdd(totalTypes, cell.totalTypes);
                    addDriveStats(matrix, itemStats, fluidStats);
                    if (!hostDomainStorage) {
                        usedBytes = saturatedAdd(usedBytes, cell.usedBytes);
                        preciseUsedBytes = preciseUsedBytes.add(BigInteger.valueOf(cell.usedBytes));
                        usedTypes = saturatedAdd(usedTypes, cell.usedTypes);
                    }
                }
            }
            cells.add(cell);
        }

        List<TypeStat> stats = new ArrayList<TypeStat>();
        addStat(stats, itemStats);
        addStat(stats, fluidStats);
        return new StorageHostSnapshot(
            controller.isFormed(),
            controller.getTier()
                .name(),
            controller.getHostMode()
                .getId(),
            controller.getInfiniteStorageComponentCount(),
            positions.size(),
            controller.getRequiredInfiniteDriveCount(),
            controller.getPriority(),
            controller.areAllFormedDrivesL9MatricesForDisplay(),
            usedBytes,
            preciseUsedBytes,
            totalBytes,
            usedTypes,
            totalTypes,
            controller.canTakeInfiniteStorageComponent(),
            stats,
            cells);
    }

    public void write(ByteBuf buf) {
        buf.writeBoolean(this.formed);
        writeString(buf, this.tier);
        writeString(buf, this.hostMode);
        buf.writeInt(this.infiniteComponentCount);
        buf.writeInt(this.formedDriveCount);
        buf.writeInt(this.requiredDriveCount);
        buf.writeInt(this.priority);
        buf.writeBoolean(this.allDrivesL9);
        buf.writeLong(this.usedBytes);
        writeBigInteger(buf, this.preciseUsedBytes);
        buf.writeLong(this.totalBytes);
        buf.writeLong(this.usedTypes);
        buf.writeLong(this.totalTypes);
        buf.writeBoolean(this.canTakeInfiniteComponent);
        int typeCount = Math.min(this.typeStats.size(), MAX_TYPE_STATS);
        buf.writeInt(typeCount);
        for (int i = 0; i < typeCount; i++) {
            this.typeStats.get(i)
                .write(buf);
        }
        int cellCount = Math.min(this.matrixCells.size(), MAX_MATRIX_CELLS);
        buf.writeInt(cellCount);
        for (int i = 0; i < cellCount; i++) {
            this.matrixCells.get(i)
                .write(buf);
        }
    }

    public static StorageHostSnapshot read(ByteBuf buf) {
        boolean formed = buf.readBoolean();
        String tier = readString(buf);
        String hostMode = readString(buf);
        int infiniteComponentCount = buf.readInt();
        int formedDriveCount = buf.readInt();
        int requiredDriveCount = buf.readInt();
        int priority = buf.readInt();
        boolean allDrivesL9 = buf.readBoolean();
        long usedBytes = safeLong(buf.readLong());
        BigInteger preciseUsedBytes = readBigInteger(buf);
        long totalBytes = safeLong(buf.readLong());
        long usedTypes = safeLong(buf.readLong());
        long totalTypes = safeLong(buf.readLong());
        boolean canTakeInfiniteComponent = buf.readBoolean();
        int typeCount = Math.min(Math.max(0, buf.readInt()), MAX_TYPE_STATS);
        List<TypeStat> typeStats = new ArrayList<TypeStat>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            typeStats.add(TypeStat.read(buf));
        }
        int cellCount = Math.min(Math.max(0, buf.readInt()), MAX_MATRIX_CELLS);
        List<MatrixCell> matrixCells = new ArrayList<MatrixCell>(cellCount);
        for (int i = 0; i < cellCount; i++) {
            matrixCells.add(MatrixCell.read(buf));
        }
        return new StorageHostSnapshot(
            formed,
            tier,
            hostMode,
            infiniteComponentCount,
            formedDriveCount,
            requiredDriveCount,
            priority,
            allDrivesL9,
            usedBytes,
            preciseUsedBytes,
            totalBytes,
            usedTypes,
            totalTypes,
            canTakeInfiniteComponent,
            typeStats,
            matrixCells);
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static long safeLong(long value) {
        return Math.max(0L, value);
    }

    private static long saturatedLong(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            return 0L;
        }
        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        return value.compareTo(max) >= 0 ? Long.MAX_VALUE : value.longValue();
    }

    private static void addDriveStats(MatrixRead matrix, TypeAccumulator itemStats, TypeAccumulator fluidStats) {
        if (matrix == null || !matrix.hasCell || matrix.nonPortable || matrix.backend == null) {
            return;
        }
        addBackendStats(matrix.backend, itemStats, fluidStats);
    }

    private static void addBackendStats(ECOStorageBackend backend, TypeAccumulator itemStats,
        TypeAccumulator fluidStats) {
        for (java.util.Map.Entry<ECOStorageKey, cn.dancingsnow.neoecoae.storage.core.ECOAmount> entry : backend
            .getEntriesView()
            .entrySet()) {
            TypeAccumulator stats = accumulatorFor(
                entry.getKey()
                    .getChannel(),
                itemStats,
                fluidStats);
            stats.usedBytes = saturatedAdd(
                stats.usedBytes,
                entry.getValue()
                    .toLongSaturated());
            stats.usedTypes = saturatedAdd(stats.usedTypes, 1L);
        }
    }

    private static TypeAccumulator accumulatorFor(String typeId, TypeAccumulator itemStats,
        TypeAccumulator fluidStats) {
        return "fluid".equals(typeId) ? fluidStats : itemStats;
    }

    private static void addStat(List<TypeStat> stats, TypeAccumulator accumulator) {
        if (accumulator.usedBytes > 0L || accumulator.totalBytes > 0L
            || accumulator.usedTypes > 0L
            || accumulator.totalTypes > 0L) {
            stats.add(
                new TypeStat(
                    accumulator.typeId,
                    accumulator.displayName,
                    accumulator.usedTypes,
                    accumulator.totalTypes,
                    accumulator.usedBytes,
                    accumulator.totalBytes));
        }
    }

    private static final class TypeAccumulator {

        private final String typeId;
        private final String displayName;
        private long usedTypes;
        private long totalTypes;
        private long usedBytes;
        private long totalBytes;

        private TypeAccumulator(String typeId, String displayName) {
            this.typeId = typeId;
            this.displayName = displayName;
        }
    }

    private static final class MatrixRead {

        private final boolean hasCell;
        private final String tier;
        private final String mode;
        private final boolean nonPortable;
        private final long usedBytes;
        private final long totalBytes;
        private final long usedTypes;
        private final long totalTypes;
        private final ECOStorageBackend backend;

        private MatrixRead(boolean hasCell, String tier, String mode, boolean nonPortable, long usedBytes,
            long totalBytes, long usedTypes, long totalTypes, ECOStorageBackend backend) {
            this.hasCell = hasCell;
            this.tier = tier;
            this.mode = mode;
            this.nonPortable = nonPortable;
            this.usedBytes = usedBytes;
            this.totalBytes = totalBytes;
            this.usedTypes = usedTypes;
            this.totalTypes = totalTypes;
            this.backend = backend;
        }

        private static MatrixRead empty() {
            return new MatrixRead(false, "", ECOStorageCellMode.PORTABLE.getId(), false, 0L, 0L, 0L, 0L, null);
        }

        private static MatrixRead fromDrive(TileECODrive drive) {
            ItemStack stack = drive.getCellStack();
            if (stack == null || !(stack.getItem() instanceof IECOStorageMatrixItem)) {
                return empty();
            }
            IECOStorageMatrixItem matrix = (IECOStorageMatrixItem) stack.getItem();
            String fallbackTier = stack
                .getItem() instanceof cn.dancingsnow.neoecoae.all.NEStorageItems.ECOStorageCellItem
                    ? ((cn.dancingsnow.neoecoae.all.NEStorageItems.ECOStorageCellItem) stack.getItem()).getTier()
                    : "";
            String tier = ECOStorageCellAccess.readTier(stack, fallbackTier);
            String mode = ECOStorageCellMetadata.getMode(stack)
                .getId();
            long totalBytes = matrix.getDisplayBytes(stack);
            long totalTypes = 0L;
            boolean nonPortable = ECOStorageCellMetadata.hasNonPortableState(stack);
            if (nonPortable) {
                return new MatrixRead(
                    true,
                    tier,
                    mode,
                    true,
                    ECOStorageCellMetadata.getSummaryUsed(stack),
                    totalBytes,
                    ECOStorageCellMetadata.getSummaryTypes(stack),
                    totalTypes,
                    null);
            }
            ECOStorageBackend backend = ECOStorageCellAccess.load(stack);
            return new MatrixRead(
                true,
                tier,
                mode,
                false,
                backend.getUsed()
                    .toLongSaturated(),
                totalBytes,
                backend.getTypeCount(),
                totalTypes,
                backend);
        }
    }

    private static final class MatrixGridLayout {

        private static final int ROWS = 3;

        private final int controllerX;
        private final int controllerZ;
        private final int maxY;
        private final ForgeDirection expandSide;
        private final int minHorizontal;

        private MatrixGridLayout(int controllerX, int controllerZ, int maxY, ForgeDirection expandSide,
            int minHorizontal) {
            this.controllerX = controllerX;
            this.controllerZ = controllerZ;
            this.maxY = maxY;
            this.expandSide = expandSide;
            this.minHorizontal = minHorizontal;
        }

        private static MatrixGridLayout from(TileECOController controller, List<ECOFormationBlockPos> positions) {
            int maxY = Integer.MIN_VALUE;
            int minHorizontal = Integer.MAX_VALUE;
            for (ECOFormationBlockPos pos : positions) {
                maxY = Math.max(maxY, pos.getY());
                minHorizontal = Math.min(minHorizontal, horizontalCoordinate(pos));
            }
            return new MatrixGridLayout(
                controller.xCoord,
                controller.zCoord,
                maxY == Integer.MIN_VALUE ? 0 : maxY,
                expandSideFor(controller),
                minHorizontal == Integer.MAX_VALUE ? 0 : minHorizontal);
        }

        private int rowFor(ECOFormationBlockPos pos) {
            return Math.max(0, Math.min(ROWS - 1, this.maxY - pos.getY()));
        }

        private int columnFor(ECOFormationBlockPos pos) {
            int distance = (pos.getX() - this.controllerX) * this.expandSide.offsetX
                + (pos.getZ() - this.controllerZ) * this.expandSide.offsetZ;
            if (distance > 0) {
                return distance - 1;
            }
            return Math.max(0, horizontalCoordinate(pos) - this.minHorizontal);
        }

        private static ForgeDirection expandSideFor(TileECOController controller) {
            ForgeDirection rotated = rotateClockwise(
                controller.getFacing()
                    .getDirection());
            return controller.isMirrored() ? rotated : rotated.getOpposite();
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

        private static int horizontalCoordinate(ECOFormationBlockPos pos) {
            return Math.abs(pos.getX()) >= Math.abs(pos.getZ()) ? pos.getX() : pos.getZ();
        }
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 256);
        buf.writeShort(length);
        buf.writeBytes(bytes, 0, length);
    }

    private static String readString(ByteBuf buf) {
        int length = Math.min(Math.max(0, buf.readUnsignedShort()), 256);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeBigInteger(ByteBuf buf, BigInteger value) {
        byte[] bytes = (value == null ? "0"
            : value.max(BigInteger.ZERO)
                .toString()).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int length = Math.min(bytes.length, 2048);
        buf.writeShort(length);
        buf.writeBytes(bytes, 0, length);
    }

    private static BigInteger readBigInteger(ByteBuf buf) {
        int length = Math.min(Math.max(0, buf.readUnsignedShort()), 2048);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        if (length <= 0) {
            return BigInteger.ZERO;
        }
        try {
            BigInteger value = new BigInteger(new String(bytes, java.nio.charset.StandardCharsets.US_ASCII));
            return value.signum() < 0 ? BigInteger.ZERO : value;
        } catch (NumberFormatException ignored) {
            return BigInteger.ZERO;
        }
    }

    public static final class TypeStat {

        public final String typeId;
        public final String displayName;
        public final long usedTypes;
        public final long totalTypes;
        public final long usedBytes;
        public final long totalBytes;

        public TypeStat(String typeId, String displayName, long usedTypes, long totalTypes, long usedBytes,
            long totalBytes) {
            this.typeId = typeId;
            this.displayName = displayName;
            this.usedTypes = usedTypes;
            this.totalTypes = totalTypes;
            this.usedBytes = usedBytes;
            this.totalBytes = totalBytes;
        }

        private void write(ByteBuf buf) {
            writeString(buf, this.typeId);
            writeString(buf, this.displayName);
            buf.writeLong(this.usedTypes);
            buf.writeLong(this.totalTypes);
            buf.writeLong(this.usedBytes);
            buf.writeLong(this.totalBytes);
        }

        private static TypeStat read(ByteBuf buf) {
            return new TypeStat(
                readString(buf),
                readString(buf),
                safeLong(buf.readLong()),
                safeLong(buf.readLong()),
                safeLong(buf.readLong()),
                safeLong(buf.readLong()));
        }
    }

    public static final class MatrixCell {

        public final int row;
        public final int column;
        public final boolean hasCell;
        public final String tier;
        public final String mode;
        public final long usedBytes;
        public final long totalBytes;
        public final long usedTypes;
        public final long totalTypes;

        private MatrixCell(int row, int column, boolean hasCell, String tier, String mode, long usedBytes,
            long totalBytes, long usedTypes, long totalTypes) {
            this.row = row;
            this.column = column;
            this.hasCell = hasCell;
            this.tier = tier;
            this.mode = mode;
            this.usedBytes = usedBytes;
            this.totalBytes = totalBytes;
            this.usedTypes = usedTypes;
            this.totalTypes = totalTypes;
        }

        private static MatrixCell empty(int row, int column) {
            return new MatrixCell(row, column, false, "", ECOStorageCellMode.PORTABLE.getId(), 0L, 0L, 0L, 0L);
        }

        private static MatrixCell fromMatrix(int row, int column, MatrixRead matrix) {
            if (matrix == null || !matrix.hasCell) {
                return empty(row, column);
            }
            return new MatrixCell(
                row,
                column,
                true,
                matrix.tier,
                matrix.mode,
                matrix.usedBytes,
                matrix.totalBytes,
                matrix.usedTypes,
                matrix.totalTypes);
        }

        private void write(ByteBuf buf) {
            buf.writeInt(this.row);
            buf.writeInt(this.column);
            buf.writeBoolean(this.hasCell);
            writeString(buf, this.tier);
            writeString(buf, this.mode);
            buf.writeLong(this.usedBytes);
            buf.writeLong(this.totalBytes);
            buf.writeLong(this.usedTypes);
            buf.writeLong(this.totalTypes);
        }

        private static MatrixCell read(ByteBuf buf) {
            return new MatrixCell(
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                readString(buf),
                readString(buf),
                safeLong(buf.readLong()),
                safeLong(buf.readLong()),
                safeLong(buf.readLong()),
                safeLong(buf.readLong()));
        }
    }
}
