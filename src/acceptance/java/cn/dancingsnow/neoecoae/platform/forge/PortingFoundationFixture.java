package cn.dancingsnow.neoecoae.platform.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.StorageChannel;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.sync.DescSynced;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;
import cn.dancingsnow.neoecoae.platform.sync.Persisted;
import cpw.mods.fml.common.registry.GameRegistry;

/** Test-only native shell for exercising the imported shared cluster/entity contracts before each real machine. */
public final class PortingFoundationFixture {

    private static final BlockEntityType<FixtureTile> TYPE = new BlockEntityType<>("porting_fixture", FixtureTile::new);
    private static final NativeMachineBlock BLOCK = new NativeMachineBlock(
        TYPE,
        ECOMultiblockParts.Role.CASING,
        "porting_fixture");
    private static Pending pending;

    private PortingFoundationFixture() {}

    public static void register() {
        if (!Boolean.getBoolean("neoecoae.portingAcceptance")) return;
        GameRegistry.registerBlock(BLOCK, "porting_fixture");
        GameRegistry.registerTileEntity(FixtureTile.class, "neoecoae.porting_fixture");
    }

    public static void start(WorldServer world, ICommandSender sender) {
        if (pending != null) throw new IllegalStateException("Foundation fixture is already running");
        List<BlockPos> positions = new ArrayList<>();
        BlockPos minimum = new BlockPos(2, 240, 2), maximum = new BlockPos(5, 242, 3), power = new BlockPos(1, 241, 2);
        BlockPos.betweenClosed(minimum, maximum)
            .forEach(positions::add);
        positions.add(power);
        for (BlockPos pos : positions) if (!world.isAirBlock(pos.getX(), pos.getY(), pos.getZ()))
            throw new IllegalStateException("Fixture requires an unused air region: " + pos);
        Pending task = new Pending(world, sender, positions, world.getTotalWorldTime() + 80);
        pending = task;
        try {
            for (BlockPos pos : BlockPos.betweenClosed(minimum, maximum))
                world.setBlock(pos.getX(), pos.getY(), pos.getZ(), BLOCK, 2, 3);
            Block powerBlock = AEApi.instance()
                .definitions()
                .blocks()
                .energyCell()
                .maybeBlock()
                .get();
            world.setBlock(power.getX(), power.getY(), power.getZ(), powerBlock, 0, 3);
            sender.addChatMessage(new ChatComponentText("NEOECOAE_PORT_FOUNDATION_STARTED wait_ticks=80"));
        } catch (RuntimeException failure) {
            cleanup(task);
            pending = null;
            throw failure;
        }
    }

    public static void tick() {
        Pending task = pending;
        if (task == null) return;
        try {
            if (!task.charged) {
                BlockPos pos = task.positions.get(task.positions.size() - 1);
                TileEntity raw = task.world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
                if (raw instanceof appeng.api.networking.IGridHost && ((appeng.api.networking.IGridHost) raw)
                    .getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN) != null) {
                    IAEPowerStorage energy = (IAEPowerStorage) raw;
                    energy.injectAEPower(energy.getAEMaxPower(), Actionable.MODULATE);
                    task.charged = true;
                }
            }
            if (task.world.getTotalWorldTime() < task.readyTick) return;
            require(task.charged, "native power node initialized before charging");
            IGrid grid = null;
            FixtureCluster cluster = null;
            for (BlockPos pos : task.positions) {
                TileEntity raw = task.world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
                if (!(raw instanceof FixtureTile)) continue;
                FixtureTile tile = (FixtureTile) raw;
                require(
                    tile.isFormed() && tile.getCluster() != null,
                    "imported cluster forms all native members: " + pos
                        + " ready="
                        + tile.getMainNode()
                            .isReady()
                        + " state="
                        + tile.getBlockState()
                            .getValues());
                if (cluster == null) cluster = tile.getCluster();
                else require(cluster == tile.getCluster(), "one shared cluster owner");
                require(
                    tile.getMainNode()
                        .isOnline(),
                    "actual AE2 power/channel state is online");
                IGrid actual = tile.getMainNode()
                    .getGrid()
                    .nativeGrid();
                if (grid == null) grid = actual;
                else require(grid == actual, "native multiblock nodes share the grid");
                NBTTagCompound saved = new NBTTagCompound();
                tile.writeToNBT(saved);
                FixtureTile restored = (FixtureTile) TileEntity.createAndLoadEntity(saved);
                require(
                    restored != null && restored.inventory()
                        .getStoredItemCount() == 42,
                    "native TileEvent restores managed cell NBT");
                require(!restored.isFormed(), "formation is derived, never restored as a live cluster");
            }
            require(cluster != null && cluster.getBlockEntities() != null, "fixture attached");
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            var key = cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(net.minecraft.init.Items.iron_ingot);
            var listing = storage.getItemInventory()
                .getAvailableItems(StorageChannel.ITEMS.createList());
            require(
                listing.findPrecise(key.toNative(1))
                    .getStackSize() == 24L * 42,
                "source inventories mounted through native storage cache");
            var extracted = storage.getItemInventory()
                .extractItems(key.toNative(17), Actionable.MODULATE, new BaseActionSource());
            require(
                extracted != null && extracted.getStackSize() == 17,
                "native network extraction reaches imported inventory");
            task.sender.addChatMessage(
                new ChatComponentText(
                    "NEOECOAE_PORT_FOUNDATION_OK members=24 native_grid=true managed_nbt=true mounted_items=1008 extracted=17"));
        } catch (RuntimeException failure) {
            NeoECOAE.LOG.error("NEOECOAE_PORT_FOUNDATION_FAILED", failure);
            task.sender.addChatMessage(new ChatComponentText("NEOECOAE_PORT_FOUNDATION_FAILED " + failure));
            cleanup(task);
            pending = null;
            return;
        }
        if (task.world.getTotalWorldTime() >= task.readyTick) {
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
        final long readyTick;
        boolean charged;
        final AcceptanceChunkTicket ticket;

        Pending(WorldServer world, ICommandSender sender, List<BlockPos> positions, long readyTick) {
            this.world = world;
            this.sender = sender;
            this.positions = positions;
            this.readyTick = readyTick;
            ticket = new AcceptanceChunkTicket(world, positions);
        }
    }

    public static final class FixtureTile extends NEBlockEntity<FixtureCluster, FixtureTile>
        implements ManagedHolder, appeng.api.storage.ICellContainer {

        @Persisted
        private ItemStack cell = new ItemStack(NativeContent.item("eco_item_storage_cell_16m"));
        @DescSynced
        private boolean sample = true;

        public FixtureTile() {
            super(TYPE, BlockPos.ZERO, BLOCK.defaultBlockState(), FixtureCalculator::new);
            inventory().insert(
                cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(net.minecraft.init.Items.iron_ingot),
                42,
                Actionable.MODULATE,
                IActionSource.empty());
            getMainNode().addService(IStorageProvider.class, mounts -> mounts.mount(inventory(), 0));
        }

        ECOStorageCell inventory() {
            return new ECOStorageCell(cell, this::setChanged);
        }
    }

    private static final class FixtureCluster extends NECluster<FixtureCluster> {

        FixtureCluster(BlockPos min, BlockPos max) {
            super(min, max);
        }
    }

    private static final class FixtureCalculator extends NEClusterCalculator<FixtureCluster> {

        FixtureCalculator(NEBlockEntity<FixtureCluster, ?> tile) {
            super(tile);
        }

        @Override
        protected int maxLength() {
            return 4;
        }

        @Override
        protected Holder<Block> casing() {
            return Holder.direct(BLOCK);
        }

        @Override
        public FixtureCluster createCluster(ServerWorldView world, BlockPos min, BlockPos max) {
            return new FixtureCluster(min, max);
        }

        @Override
        public boolean verifyInternalStructure(ServerWorldView world, BlockPos min, BlockPos max) {
            return validateBlocks(world, min, max, state -> state.is(BLOCK));
        }

        @Override
        public boolean isValidBlockEntity(TileEntity tile) {
            return tile instanceof FixtureTile;
        }
    }
}
