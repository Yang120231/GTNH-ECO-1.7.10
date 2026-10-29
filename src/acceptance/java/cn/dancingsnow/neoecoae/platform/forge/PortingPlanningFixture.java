package cn.dancingsnow.neoecoae.platform.forge;

import java.util.List;
import java.util.concurrent.Future;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGridHost;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.helpers.IInterfaceHost;
import appeng.items.contents.CellConfig;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CalculationStrategy;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeDriveReaders;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;

/** Opt-in native grid acceptance. Its processing pattern is diagnostic data, never a gameplay recipe. */
public final class PortingPlanningFixture {

    private static final BlockPos DRIVE = new BlockPos(25, 238, 2);
    private static final BlockPos POWER = new BlockPos(26, 238, 2);
    private static final BlockPos ME_CHEST = new BlockPos(27, 238, 2);
    private static final BlockPos PROVIDER = new BlockPos(28, 238, 2);
    private static final BlockPos OUTPUT = new BlockPos(29, 238, 2);
    private static Pending pending;

    private PortingPlanningFixture() {}

    public static void start(WorldServer world, ICommandSender sender) {
        if (pending != null) throw new IllegalStateException("Planning fixture already running");
        List<BlockPos> positions = java.util.Arrays.asList(DRIVE, POWER, ME_CHEST, PROVIDER, OUTPUT);
        for (var pos : positions)
            require(world.isAirBlock(pos.getX(), pos.getY(), pos.getZ()), "Unused fixture region " + pos);
        Pending task = new Pending(world, sender, positions);
        pending = task;
        try {
            var blocks = AEApi.instance()
                .definitions()
                .blocks();
            place(
                world,
                DRIVE,
                blocks.drive()
                    .maybeBlock()
                    .get());
            place(
                world,
                POWER,
                blocks.energyCell()
                    .maybeBlock()
                    .get());
            place(
                world,
                ME_CHEST,
                blocks.chest()
                    .maybeBlock()
                    .get());
            place(
                world,
                PROVIDER,
                blocks.iface()
                    .maybeBlock()
                    .get());
            place(world, OUTPUT, net.minecraft.init.Blocks.chest);
            IInventory drive = inventory(tile(world, DRIVE));
            ItemStack finite = new ItemStack(NativeContent.item("eco_item_storage_cell_16m"));
            cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCells.getCellInventory(finite, null)
                .insert(
                    AEItemKey.of(net.minecraft.init.Items.iron_ingot),
                    64,
                    Actionable.MODULATE,
                    IActionSource.empty());
            drive.setInventorySlotContents(0, finite);
            drive.setInventorySlotContents(1, creative(net.minecraft.init.Items.diamond));
            inventory(tile(world, ME_CHEST)).setInventorySlotContents(1, creative(net.minecraft.init.Items.emerald));
            var patterns = ((IInterfaceHost) tile(world, PROVIDER)).getInterfaceDuality()
                .getPatterns();
            patterns.setInventorySlotContents(
                0,
                pattern(
                    false,
                    new ItemStack(net.minecraft.init.Items.iron_ingot),
                    new ItemStack(net.minecraft.init.Items.gold_ingot)));
            patterns.setInventorySlotContents(
                1,
                pattern(
                    true,
                    new ItemStack(net.minecraft.init.Blocks.log),
                    new ItemStack(net.minecraft.init.Blocks.planks, 4)));
            task.ready = world.getTotalWorldTime() + 80;
            sender.addChatMessage(new ChatComponentText("NEOECOAE_PORT_PLANNING_STARTED wait_ticks=80"));
        } catch (RuntimeException failure) {
            cleanup(task);
            pending = null;
            throw failure;
        }
    }

    public static void tick() {
        var task = pending;
        if (task == null) return;
        try {
            TileEntity energy = tile(task.world, POWER);
            if (energy instanceof IAEPowerStorage && energy instanceof IGridHost
                && ((IGridHost) energy).getGridNode(ForgeDirection.UNKNOWN) != null)
                ((IAEPowerStorage) energy).injectAEPower(200000, Actionable.MODULATE);
            if (task.world.getTotalWorldTime() < task.ready) return;
            var node = ((IGridHost) tile(task.world, PROVIDER)).getGridNode(ForgeDirection.UNKNOWN);
            require(node != null && node.isActive(), "Real interface has native power and channel");
            GridView grid = GridView.of(node.getGrid());
            NativePatternTrackingCache tracking = grid.nativeGrid()
                .getCache(NativePatternTrackingCache.class);
            if (task.future == null) {
                appeng.me.cache.CraftingGridCache nativeCrafting = grid.nativeGrid()
                    .getCache(appeng.api.networking.crafting.ICraftingGrid.class);
                nativeCrafting.setEmitable(
                    AEItemKey.of(net.minecraft.init.Items.nether_star)
                        .toNative(1));
                var unbounded = ECOPlannerInventory.collectUnboundedKeys(grid);
                require(
                    unbounded.contains(AEItemKey.of(net.minecraft.init.Items.diamond)),
                    "ME drive creative identity");
                require(
                    unbounded.contains(AEItemKey.of(net.minecraft.init.Items.emerald)),
                    "ME chest creative identity reads cell slot 1");
                var host = (IChestOrDrive) tile(task.world, ME_CHEST);
                require(
                    NativeDriveReaders.cellItem(host, 0) == AEApi.instance()
                        .definitions()
                        .items()
                        .cellCreative()
                        .maybeItem()
                        .get(),
                    "Logical chest cell maps to native slot 1");
                var service = grid.captureCraftingService(task.world);
                require(
                    service.canEmitFor(AEItemKey.of(net.minecraft.init.Items.nether_star)),
                    "Emitter-only key is frozen without stock or a recipe");
                var crafting = service.getCraftingFor(AEItemKey.of(new ItemStack(net.minecraft.init.Blocks.planks)));
                require(
                    crafting.size() == 1 && crafting.get(0) instanceof NativeCraftingPattern,
                    "Native vanilla recipe captured with exact recipe identity");
                var input = new KeyCounter();
                input.add(AEItemKey.of(new ItemStack(net.minecraft.init.Blocks.log)), 1);
                var assembled = ((NativeCraftingPattern) crafting.get(0)).assemble(
                    cn.dancingsnow.neoecoae.platform.minecraft.crafting.CraftingInput
                        .of(1, 1, java.util.Collections.singletonList(new ItemStack(net.minecraft.init.Blocks.log))),
                    task.world);
                require(
                    assembled != null
                        && assembled.getItem()
                            == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.planks)
                        && assembled.stackSize == 4,
                    "Native recipe recomputes original result");
                task.revision = tracking.revision();
                task.future = ECOPlanningService.begin(
                    task.world,
                    grid,
                    IActionSource.empty(),
                    AEItemKey.of(net.minecraft.init.Items.gold_ingot),
                    3,
                    CalculationStrategy.REPORT_MISSING_ITEMS,
                    ECOPlannerOptions.from(null));
                task.deadline = task.world.getTotalWorldTime() + 400;
                return;
            }
            require(task.world.getTotalWorldTime() <= task.deadline, "Source planning completed before timeout");
            if (!task.future.isDone()) return;
            var plan = task.future.get();
            require(
                !plan.simulation() && plan.finalOutput()
                    .amount() == 3
                    && plan.patternTimes()
                        .size() == 1,
                "Source async plan uses real native processing pattern");
            var service = grid.captureCraftingService(task.world);
            var pattern = service.getCraftingFor(AEItemKey.of(net.minecraft.init.Items.gold_ingot))
                .get(0);
            var providers = service.getProviders(pattern);
            require(providers.size() == 1, "Exactly one native interface medium");
            KeyCounter input = new KeyCounter();
            input.add(AEItemKey.of(net.minecraft.init.Items.iron_ingot), 1);
            require(
                providers.get(0)
                    .pushPattern(pattern, new KeyCounter[] { input }) && input.isEmpty(),
                "Native provider takes ownership of accepted input");
            IInventory output = inventory(tile(task.world, OUTPUT));
            int accepted = 0;
            for (int i = 0; i < output.getSizeInventory(); i++) {
                ItemStack stack = output.getStackInSlot(i);
                if (stack != null && stack.getItem() == net.minecraft.init.Items.iron_ingot)
                    accepted += stack.stackSize;
            }
            require(accepted == 1, "Real adjacent inventory received exactly one input");
            long before = tracking.revision();
            ((IInterfaceHost) tile(task.world, PROVIDER)).getInterfaceDuality()
                .getPatterns()
                .setInventorySlotContents(0, null);
            require(tracking.revision() > before, "Real native pattern removal invalidates source snapshot");
            task.sender.addChatMessage(
                new ChatComponentText(
                    "NEOECOAE_PORT_PLANNING_OK source_async_plan=true native_creative_drive=true native_creative_chest=true original_recipe=true emitter_only=true provider_dispatch=1 revision_invalidated=true"));
            cleanup(task);
            pending = null;
        } catch (Exception failure) {
            NeoECOAE.LOG.error("NEOECOAE_PORT_PLANNING_FAILED", failure);
            task.sender.addChatMessage(new ChatComponentText("NEOECOAE_PORT_PLANNING_FAILED " + failure));
            cleanup(task);
            pending = null;
        }
    }

    private static ItemStack creative(net.minecraft.item.Item item) {
        ItemStack stack = AEApi.instance()
            .definitions()
            .items()
            .cellCreative()
            .maybeStack(1)
            .get();
        new CellConfig(stack).putAEStackInSlot(0, appeng.util.item.AEItemStack.create(new ItemStack(item)));
        return stack;
    }

    private static ItemStack pattern(boolean crafting, ItemStack input, ItemStack output) {
        ItemStack stack = AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("crafting", crafting);
        NBTTagList in = new NBTTagList(), out = new NBTTagList();
        in.appendTag(input.writeToNBT(new NBTTagCompound()));
        out.appendTag(output.writeToNBT(new NBTTagCompound()));
        tag.setTag("in", in);
        tag.setTag("out", out);
        stack.setTagCompound(tag);
        return stack;
    }

    private static IInventory inventory(TileEntity tile) {
        if (tile instanceof IInventory) return (IInventory) tile;
        try {
            return (IInventory) tile.getClass()
                .getMethod("getInternalInventory")
                .invoke(tile);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native fixture inventory", failure);
        }
    }

    private static TileEntity tile(WorldServer world, BlockPos pos) {
        return world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void place(WorldServer world, BlockPos pos, Block block) {
        world.setBlock(pos.getX(), pos.getY(), pos.getZ(), block, 0, 3);
    }

    private static void cleanup(Pending task) {
        if (task.future != null) task.future.cancel(true);
        try {
            for (var pos : task.positions) task.world.setBlockToAir(pos.getX(), pos.getY(), pos.getZ());
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
        final AcceptanceChunkTicket ticket;
        long ready, deadline, revision;
        Future<ICraftingPlan> future;

        Pending(WorldServer world, ICommandSender sender, List<BlockPos> positions) {
            this.world = world;
            this.sender = sender;
            this.positions = positions;
            ticket = new AcceptanceChunkTicket(world, positions);
        }
    }
}
