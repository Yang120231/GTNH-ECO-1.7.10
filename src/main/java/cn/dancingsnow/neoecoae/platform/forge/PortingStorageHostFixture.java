package cn.dancingsnow.neoecoae.platform.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.StorageChannel;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageMachines;

/** Exercises real registered machines and the unchanged storage structure predicate, never the diagnostic shell. */
public final class PortingStorageHostFixture {

    private static final BlockPos HOST = new BlockPos(12, 240, 2);
    private static Pending pending;
    private static volatile boolean previewReady;

    public static boolean previewReady() {
        return previewReady;
    }

    public static void startPreview(WorldServer world, ICommandSender sender) {
        start(world, sender, true);
    }

    private PortingStorageHostFixture() {}

    public static void start(WorldServer world, ICommandSender sender) {
        start(world, sender, false);
    }

    private static void start(WorldServer world, ICommandSender sender, boolean preview) {
        if (pending != null) throw new IllegalStateException("Storage host check is already running");
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosed(new BlockPos(6, 239, 2), new BlockPos(13, 241, 3))
            .forEach(positions::add);
        for (BlockPos pos : positions) if (!world.isAirBlock(pos.getX(), pos.getY(), pos.getZ()))
            throw new IllegalStateException("Fixture requires unused air: " + pos);
        Pending task = new Pending(world, sender, positions);
        pending = task;
        task.preview = preview;
        previewReady = false;
        try {
            for (BlockPos pos : positions) place(task, pos, StorageMachines.STORAGE_CASING, Direction.NORTH);
            place(task, HOST, StorageMachines.STORAGE_SYSTEM_L9, Direction.NORTH);
            place(
                task,
                HOST.relative(Direction.EAST)
                    .relative(Direction.SOUTH),
                StorageMachines.STORAGE_INTERFACE,
                Direction.SOUTH);
            for (int column = 0; column < 4; column++) {
                BlockPos front = HOST.relative(Direction.WEST, column + 2);
                for (int y = -1; y <= 1; y++) {
                    BlockPos pos = front.above(y);
                    place(task, pos, StorageMachines.ECO_DRIVE, Direction.NORTH);
                    ECODriveBlockEntity drive = (ECODriveBlockEntity) WorldView.of(world)
                        .getBlockEntity(pos);
                    drive.setCellStack(new ItemStack(NativeContent.item("eco_item_storage_cell_256m")));
                    require(
                        drive.getCellInventory()
                            .insert(
                                AEItemKey.of(net.minecraft.init.Items.iron_ingot),
                                42,
                                Actionable.MODULATE,
                                IActionSource.empty())
                            == 42,
                        "populate real drive");
                }
                BlockPos back = front.relative(Direction.SOUTH);
                place(task, back, StorageMachines.STORAGE_VENT, Direction.SOUTH);
                place(task, back.above(), StorageMachines.ENERGY_CELL_L9, Direction.SOUTH);
                place(task, back.above(-1), StorageMachines.ENERGY_CELL_L9, Direction.SOUTH);
            }
            sender.addChatMessage(new ChatComponentText("NEOECOAE_PORT_STORAGE_HOST_STARTED members=48 drives=12"));
        } catch (RuntimeException failure) {
            cleanup(task);
            pending = null;
            throw failure;
        }
    }

    private static void place(Pending task, BlockPos pos, Holder<net.minecraft.block.Block> block, Direction facing) {
        BlockState state = ((NativeMachineBlock) block.value()).defaultBlockState()
            .setValue(NativeMachineBlock.FACING, facing);
        require(
            WorldView.of(task.world)
                .setBlock(pos, state, 2),
            "place native machine " + pos);
    }

    public static void tick() {
        Pending task = pending;
        if (task == null) return;
        try {
            if (task.world.getTotalWorldTime() - task.started < 3) return;
            if (!task.charged) {
                for (BlockPos pos : task.positions) {
                    TileEntity raw = WorldView.of(task.world)
                        .getBlockEntity(pos);
                    if (raw instanceof IAEPowerStorage) {
                        NEBlockEntity<?, ?> tile = (NEBlockEntity<?, ?>) raw;
                        if (tile.getMainNode()
                            .getNode() == null) return;
                    }
                }
                for (BlockPos pos : task.positions) {
                    TileEntity raw = WorldView.of(task.world)
                        .getBlockEntity(pos);
                    if (raw instanceof IAEPowerStorage)
                        ((IAEPowerStorage) raw).injectAEPower(10_000_000, Actionable.MODULATE);
                }
                task.charged = true;
            }
            if (task.world.getTotalWorldTime() < task.ready) return;
            ECOStorageSystemBlockEntity host = (ECOStorageSystemBlockEntity) WorldView.of(task.world)
                .getBlockEntity(HOST);
            require(host.isFormed() && host.getCluster() != null, "source storage structure forms");
            require(
                host.getCluster()
                    .getDrives()
                    .size() == 12
                    && host.getCluster()
                        .getEnergyCells()
                        .size() == 8,
                "source cluster owns drives and energy cells");
            int members = 0;
            for (var iterator = host.getCluster()
                .getBlockEntities(); iterator.hasNext();) {
                NEBlockEntity<?, ?> tile = (NEBlockEntity<?, ?>) iterator.next();
                members++;
                require(tile.getCluster() == host.getCluster(), "single source cluster owner");
                require(
                    tile.getMainNode()
                        .isOnline(),
                    "native storage member online: " + tile.getBlockPos());
            }
            require(members == 48, "all physical members attached");
            require(
                host.storageFailures()
                    .isEmpty(),
                "source storage stages have no faults: " + host.storageDiagnosticText());
            IStorageGrid storage = host.getMainNode()
                .getGrid()
                .nativeGrid()
                .getCache(IStorageGrid.class);
            var key = AEItemKey.of(net.minecraft.init.Items.iron_ingot);
            if (task.stage == 0) {
                require(!host.isInfiniteMode() && host.canUseNormalStorage(), "source normal mode ticked");
                require(
                    storage.getItemInventory()
                        .getAvailableItems(StorageChannel.ITEMS.createList())
                        .findPrecise(key.toNative(1))
                        .getStackSize() == 504,
                    "normal drives mounted natively");
                require(
                    storage.getItemInventory()
                        .extractItems(key.toNative(17), Actionable.MODULATE, new BaseActionSource())
                        .getStackSize() == 17,
                    "native extraction from source drives");
                host.setStoragePriority(73);
                var snapshot = cn.dancingsnow.neoecoae.gui.storage.StoragePanelState
                    .capture(host.storageUiConfig(null), 0, 33);
                var received = cn.dancingsnow.neoecoae.gui.storage.StoragePanelState.fromTag(
                    snapshot.toTag(cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY),
                    cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY);
                require(
                    received.cellCount() == 12 && received.cells()
                        .size() == 33
                        && received.cells()
                            .stream()
                            .filter(cell -> cell.position() != null)
                            .count() == 12,
                    "source UI pages preserve physical drives and padding");
                require(
                    received.totalBytes()
                        .equals(
                            java.math.BigInteger.valueOf(12)
                                .shiftLeft(28)),
                    "source UI capacity exceeds signed int without truncation");
                for (ECODriveBlockEntity drive : host.getCluster()
                    .getDrives()) {
                    require(
                        drive.getCellArray(StorageChannel.ITEMS)
                            .get(0)
                            .getPriority() == StoragePriority.mountPriority(73, false),
                        "native priority refresh");
                    NBTTagCompound saved = new NBTTagCompound();
                    drive.writeToNBT(saved);
                    ECODriveBlockEntity restored = (ECODriveBlockEntity) TileEntity.createAndLoadEntity(saved);
                    require(
                        restored != null && restored.getCellInventory()
                            .getAvailableStacks()
                            .get(key)
                            == drive.getCellInventory()
                                .getAvailableStacks()
                                .get(key),
                        "actual drive NBT restoration");
                }
                if (task.preview) {
                    task.sender.addChatMessage(
                        new ChatComponentText("NEOECOAE_PORT_STORAGE_UI_READY real_host=true native_grid=true"));
                    task.ticket.close();
                    pending = null;
                    previewReady = true;
                    return;
                }
                host.infiniteComponentInventory()
                    .setStackInSlot(0, new ItemStack(NativeContent.item("eco_infinite_cell_component"), 64));
                task.stage = 1;
                task.ready = task.world.getTotalWorldTime() + 160;
                task.sender.addChatMessage(
                    new ChatComponentText(
                        "NEOECOAE_PORT_STORAGE_NORMAL_OK members=48 drives=12 mounted_items=504 extracted=17 priority=73 native_nbt=true ui_snapshot=true"));
                return;
            }
            require(
                host.isFormedInfiniteMode() && host.getMissingInfiniteMembers() == 0,
                "source migration completed: mode=" + host.storageHostMode()
                    + " ticks="
                    + ((StorageMachines.Host) host).nativeTickCount
                    + " canUpdate="
                    + host.canUpdate()
                    + " component="
                    + host.infiniteComponentInventory()
                        .getStackInSlot(0)
                    + " members="
                    + host.getCluster()
                        .getDrives()
                        .stream()
                        .filter(
                            drive -> cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember
                                .isMember(drive.getCellStack()))
                        .count()
                    + " faults="
                    + host.storageDiagnosticText());
            require(
                storage.getItemInventory()
                    .getAvailableItems(StorageChannel.ITEMS.createList())
                    .findPrecise(key.toNative(1))
                    .getStackSize() == 487,
                "migrated domain natively mounted with conserved amount");
            require(
                storage.getItemInventory()
                    .extractItems(key.toNative(23), Actionable.MODULATE, new BaseActionSource())
                    .getStackSize() == 23,
                "native extraction from mounted domain");
            NBTTagCompound saved = new NBTTagCompound();
            host.writeToNBT(saved);
            ECOStorageSystemBlockEntity restored = (ECOStorageSystemBlockEntity) TileEntity.createAndLoadEntity(saved);
            require(
                restored != null && restored.getInfiniteDomainText()
                    .equals(host.getInfiniteDomainText()) && restored.getMissingInfiniteMembers() == 12,
                "native host restores domain and required member roster");
            require(
                restored.infiniteComponentInventory()
                    .getStackInSlot(0).stackSize == 64,
                "managed final inventory restores into existing instance");
            task.sender.addChatMessage(
                new ChatComponentText(
                    "NEOECOAE_PORT_STORAGE_INFINITE_HOST_OK members=12 migrated=487 extracted=23 host_domain_nbt=true"));
            cleanup(task);
            pending = null;
        } catch (RuntimeException failure) {
            NeoECOAE.LOG.error("NEOECOAE_PORT_STORAGE_HOST_FAILED", failure);
            task.sender.addChatMessage(new ChatComponentText("NEOECOAE_PORT_STORAGE_HOST_FAILED " + failure));
            cleanup(task);
            pending = null;
        }
    }

    private static void cleanup(Pending task) {
        try {
            for (BlockPos pos : task.positions) task.world.setBlockToAir(pos.getX(), pos.getY(), pos.getZ());
        } finally {
            task.ticket.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Pending {

        final WorldServer world;
        final ICommandSender sender;
        final List<BlockPos> positions;
        final long started;
        long ready;
        boolean charged, preview;
        int stage;
        final AcceptanceChunkTicket ticket;

        Pending(WorldServer world, ICommandSender sender, List<BlockPos> positions) {
            this.world = world;
            this.sender = sender;
            this.positions = positions;
            started = world.getTotalWorldTime();
            ready = started + 100;
            ticket = new AcceptanceChunkTicket(world, positions);
        }
    }
}
