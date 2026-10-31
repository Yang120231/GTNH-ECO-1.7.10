package cn.dancingsnow.neoecoae.platform.gt;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.networking.IGridHost;
import appeng.helpers.IInterfaceHost;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;

/** Real GT/AE2 integration acceptance, never manufactures a result or advances a machine timer. */
public final class NativeGTMachineAcceptance {

    private static final int X = 32, Y = 240, Z = 32;
    private int stage;
    private long deadline, startEu, euSpent;
    private boolean observedProgress;
    private IGregTechTileEntity furnace;

    public static void register() {
        if (Boolean.getBoolean("neoecoae.gtMachineAcceptance")) cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new NativeGTMachineAcceptance());
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)
            || event.world.provider.dimensionId != 0
            || stage == 4) return;
        WorldServer world = (WorldServer) event.world;
        try {
            if (stage == 0) {
                world.setBlock(X, Y - 1, Z, Blocks.stone);
                ItemStack machine = ItemList.Machine_LV_E_Furnace.get(1);
                var player = FakePlayerFactory.getMinecraft(world);
                player.setPosition(X + .5, Y, Z - 2);
                require(
                    machine.tryPlaceItemIntoWorld(player, world, X, Y - 1, Z, 1, .5F, 1, .5F),
                    "Place original GT machine item through its real item-use API");
                furnace = (IGregTechTileEntity) world.getTileEntity(X, Y, Z);
                require(
                    furnace != null && furnace.getMetaTileEntity() instanceof MTEBasicMachine,
                    "Real native GT basic machine, not a substitute tile");
                MTEBasicMachine basic = (MTEBasicMachine) furnace.getMetaTileEntity();
                basic.setMainFacing(ForgeDirection.NORTH);
                furnace.setFrontFacing(ForgeDirection.SOUTH);
                basic.mItemTransfer = true;
                basic.mAllowInputFromOutputSide = true;
                var blocks = AEApi.instance()
                    .definitions()
                    .blocks();
                world.setBlock(
                    X,
                    Y,
                    Z + 1,
                    blocks.iface()
                        .maybeBlock()
                        .get(),
                    0,
                    3);
                world.setBlock(
                    X + 1,
                    Y,
                    Z + 1,
                    blocks.drive()
                        .maybeBlock()
                        .get(),
                    0,
                    3);
                world.setBlock(
                    X + 2,
                    Y,
                    Z + 1,
                    blocks.energyCellCreative()
                        .maybeBlock()
                        .get(),
                    0,
                    3);
                TileEntity drive = world.getTileEntity(X + 1, Y, Z + 1);
                IInventory inventory = (IInventory) drive.getClass()
                    .getMethod("getInternalInventory")
                    .invoke(drive);
                inventory.setInventorySlotContents(
                    0,
                    AEApi.instance()
                        .definitions()
                        .items()
                        .cell1k()
                        .maybeStack(1)
                        .get());
                ((IInterfaceHost) world.getTileEntity(X, Y, Z + 1)).getInterfaceDuality()
                    .getPatterns()
                    .setInventorySlotContents(0, pattern());
                deadline = world.getTotalWorldTime() + 400;
                stage = 1;
                return;
            }
            require(world.getTotalWorldTime() <= deadline, "Native GT acceptance stage timeout: " + stage);
            TileEntity provider = world.getTileEntity(X, Y, Z + 1);
            var node = ((IGridHost) provider).getGridNode(ForgeDirection.UNKNOWN);
            if (node == null || !node.isActive()) return;
            GridView grid = GridView.of(node.getGrid());
            if (stage == 1) {
                var configured = (MTEBasicMachine) furnace.getMetaTileEntity();
                // GT 5.09.54 removed mHasBeenUpdated; wait until the machine has run its first tick.
                if (furnace.getTimer() < 2) return;
                configured.setMainFacing(ForgeDirection.NORTH);
                furnace.setFrontFacing(ForgeDirection.SOUTH);
                configured.mItemTransfer = true;
                configured.mAllowInputFromOutputSide = true;
                var service = grid.captureCraftingService(world);
                var patterns = service.getCraftingFor(AEItemKey.of(new ItemStack(Blocks.stone)));
                if (patterns.isEmpty()) return;
                require(patterns.size() == 1, "Exactly one actual native provider pattern");
                var media = service.getProviders(patterns.get(0));
                require(media.size() == 1, "Exactly one actual AE2 interface");
                furnace.increaseStoredEnergyUnits(furnace.getEUCapacity(), false);
                startEu = furnace.getStoredEU();
                KeyCounter inputs = new KeyCounter();
                inputs.add(AEItemKey.of(new ItemStack(Blocks.cobblestone)), 1);
                require(
                    media.get(0)
                        .pushPattern(patterns.get(0), new KeyCounter[] { inputs }) && inputs.isEmpty(),
                    "Original AE2 interface took input ownership and inserted into GT");
                MTEBasicMachine basic = (MTEBasicMachine) furnace.getMetaTileEntity();
                require(
                    furnace.getStackInSlot(basic.getInputSlot()) != null,
                    "Input physically resides in original GT inventory before processing");
                stage = 2;
                deadline = world.getTotalWorldTime() + 1600;
                return;
            }
            var snapshot = NativeGregTech.inspect((TileEntity) furnace);
            require(snapshot != null, "Native bridge can inspect actual GT progress");
            if (snapshot.progress() > 0 && snapshot.maxProgress() > 0) observedProgress = true;
            euSpent = Math.max(euSpent, startEu - snapshot.storedEu());
            long output = grid.getStorageService()
                .getCachedInventory()
                .get(AEItemKey.of(new ItemStack(Blocks.stone)));
            if (output == 0) return;
            require(
                output == 1 && observedProgress && euSpent > 0,
                "One real GT result returned through original interface to finite ME storage, with real ticks/EU");
            var basic = (MTEBasicMachine) furnace.getMetaTileEntity();
            require(
                furnace.getStackInSlot(basic.getInputSlot()) == null,
                "Native GT consumed the original delivered input");
            NeoECOAE.LOG.info(
                "NEOECOAE_NATIVE_GT_PROCESSING_OK meta={} class={} real_progress=true eu_spent={} provider_dispatch=1 me_stone=1",
                snapshot.metaTileId(),
                snapshot.machineClass(),
                euSpent);
            stage = 4;
            if (Boolean.getBoolean("neoecoae.gtMachineAcceptanceStopAfter")) MinecraftServer.getServer()
                .initiateShutdown();
        } catch (Throwable failure) {
            stage = 4;
            NeoECOAE.LOG.error("NEOECOAE_NATIVE_GT_PROCESSING_FAILED", failure);
            MinecraftServer.getServer()
                .initiateShutdown();
        }
    }

    private static ItemStack pattern() {
        ItemStack stack = AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("crafting", false);
        NBTTagList input = new NBTTagList(), output = new NBTTagList();
        input.appendTag(new ItemStack(Blocks.cobblestone).writeToNBT(new NBTTagCompound()));
        output.appendTag(new ItemStack(Blocks.stone).writeToNBT(new NBTTagCompound()));
        tag.setTag("in", input);
        tag.setTag("out", output);
        stack.setTagCompound(tag);
        return stack;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
