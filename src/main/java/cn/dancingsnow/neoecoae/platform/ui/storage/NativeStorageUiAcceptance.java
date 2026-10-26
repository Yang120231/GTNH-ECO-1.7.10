package cn.dancingsnow.neoecoae.platform.ui.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.forge.PortingStorageHostFixture;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Opt-in scene setup and server observations. Mouse gestures are deliberately left to the actual client. */
@SideOnly(Side.CLIENT)
public final class NativeStorageUiAcceptance extends CommandBase {

    private final String worldName = "ECO_Storage_UI_" + System.currentTimeMillis();
    private volatile EntityPlayerMP serverPlayer;
    private volatile Throwable failure;
    private ServerTasks.Session serverSession;
    private int stage, ticks;
    private boolean finished;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        ticks++;
        if (failure != null) {
            NeoECOAE.LOG.error("NEOECOAE_STORAGE_UI_FIXTURE_FAILED", failure);
            failure = null;
            stage = -1;
        }
        if (finished) {
            finished = false;
            if (client.theWorld != null) {
                client.theWorld.sendQuittingDisconnectingPacket();
                client.loadWorld(null);
            }
            client.displayGuiScreen(new GuiMainMenu());
            NeoECOAE.LOG.info("NEOECOAE_STORAGE_UI_FIXTURE_SAVED save={}", worldName);
            return;
        }
        if (stage == 0 && client.currentScreen instanceof GuiMainMenu) {
            com.cleanroommc.modularui.ModularUIConfig.guiDebugMode = false;
            stage = 1;
            client.launchIntegratedServer(
                worldName,
                worldName,
                new WorldSettings(9261026L, WorldSettings.GameType.CREATIVE, true, false, WorldType.FLAT)
                    .enableCommands());
        } else if (stage == 1 && client.thePlayer != null && client.getIntegratedServer() != null) {
            stage = 2;
            serverSession = ServerTasks.captureSession();
            serverSession.execute(() -> {
                try {
                    var server = client.getIntegratedServer();
                    serverPlayer = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
                    var world = server.worldServerForDimension(0);
                    world.getGameRules()
                        .setOrCreateGameRule("doDaylightCycle", "false");
                    world.setWorldTime(6000);
                    serverPlayer.capabilities.isFlying = true;
                    serverPlayer.sendPlayerAbilities();
                    serverPlayer.setPositionAndUpdate(12.5, 240, .5);
                    serverPlayer.setLocationAndAngles(12.5, 240, .5, 0, 0);
                    serverPlayer.inventory
                        .setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.water_bucket));
                    serverPlayer.inventory
                        .setInventorySlotContents(1, new ItemStack(net.minecraft.init.Items.iron_ingot, 64));
                    serverPlayer.inventory
                        .setInventorySlotContents(2, new ItemStack(NativeContent.item("eco_item_storage_cell_256m")));
                    serverPlayer.inventoryContainer.detectAndSendChanges();
                    PortingStorageHostFixture.startPreview(world, serverPlayer);
                } catch (Throwable error) {
                    failure = error;
                }
            });
        } else if (stage == 2 && PortingStorageHostFixture.previewReady()) {
            stage = 3;
            open("host");
            NeoECOAE.LOG.info(
                "NEOECOAE_STORAGE_UI_FIXTURE_READY save={} command=/neoecoae_ui_storage host|interface|snapshot|finish",
                worldName);
        } else if (stage == 3 && ticks % 100 == 0) observe();
    }

    @Override
    public String getCommandName() {
        return "neoecoae_ui_storage";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/neoecoae_ui_storage host|interface|snapshot|finish";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (stage != 3 || arguments.length != 1) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        String action = arguments[0];
        if (action.equals("host") || action.equals("interface")) open(action);
        else if (action.equals("snapshot")) {
            observe();
            NeoECOAE.LOG.info(
                "NEOECOAE_STORAGE_UI_SCREENSHOT {}",
                ScreenShotHelper
                    .saveScreenshot(
                        Minecraft.getMinecraft().mcDataDir,
                        Minecraft.getMinecraft().displayWidth,
                        Minecraft.getMinecraft().displayHeight,
                        Minecraft.getMinecraft()
                            .getFramebuffer())
                    .getUnformattedText());
        } else if (action.equals("finish")) {
            observe();
            finished = true;
            stage = 4;
        } else sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
    }

    private void open(String which) {
        serverSession.execute(() -> {
            try {
                com.cleanroommc.modularui.factory.GuiFactories.tileEntity()
                    .open(serverPlayer, which.equals("host") ? 12 : 13, 240, which.equals("host") ? 2 : 3);
            } catch (Throwable error) {
                failure = error;
            }
        });
    }

    private void observe() {
        serverSession.execute(() -> {
            try {
                var world = serverPlayer.worldObj;
                var host = (ECOStorageSystemBlockEntity) world.getTileEntity(12, 240, 2);
                var storageInterface = (ECOMachineInterfaceBlockEntity<?>) world.getTileEntity(13, 240, 3);
                NeoECOAE.LOG.info(
                    "NEOECOAE_STORAGE_UI_OBSERVE priority={} mode={} ignoreCreative={} excludeBus={} filters={} cursor={} sampleWater={} sampleIron={} activeMenu={}",
                    host.getStoragePriority(),
                    storageInterface.getStorageInterfaceMode(),
                    storageInterface.isIgnoringCreativeStorageInput(),
                    storageInterface.isExcludingStorageBusContainers(),
                    storageInterface.getStorageResourceFilters()
                        .nativeTag(),
                    serverPlayer.inventory.getItemStack(),
                    serverPlayer.inventory.getStackInSlot(0),
                    serverPlayer.inventory.getStackInSlot(1),
                    serverPlayer.openContainer.getClass()
                        .getSimpleName());
            } catch (Throwable error) {
                failure = error;
            }
        });
    }
}
