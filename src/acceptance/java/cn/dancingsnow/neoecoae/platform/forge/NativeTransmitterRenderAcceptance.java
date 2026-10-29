package cn.dancingsnow.neoecoae.platform.forge;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelElement;
import cn.dancingsnow.neoecoae.client.render.model.ModelFace;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModelRenderLayer;
import cn.dancingsnow.neoecoae.client.render.model.ModernModel;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateHolder;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/** Reproducible real-world images of the unformed composite model; never included in the release jar. */
public final class NativeTransmitterRenderAcceptance {

    private static final Direction[] FACINGS = { Direction.NORTH, Direction.NORTH, Direction.NORTH, Direction.EAST,
        Direction.SOUTH, Direction.WEST };
    private final String worldName = "ECO_Transmitter_Render_" + System.currentTimeMillis();
    private final String label = System.getProperty("neoecoae.renderLabel", "diagnostic");
    private int ticks, stageTick, stage, view;
    private volatile boolean ready;
    private volatile Throwable failure;
    private boolean capture;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 4) return;
        Minecraft client = Minecraft.getMinecraft();
        ticks++;
        try {
            if (failure != null) throw new IllegalStateException("Server fixture failed", failure);
            if (ticks - stageTick > 1800) throw new IllegalStateException("Render acceptance timed out: " + stage);
            if (stage == 0 && client.currentScreen instanceof GuiMainMenu) {
                verifyWorldPasses(client);
                client.gameSettings.hideGUI = true;
                client.gameSettings.pauseOnLostFocus = false;
                client.gameSettings.renderDistanceChunks = 4;
                client.gameSettings.fovSetting = 45;
                client.gameSettings.thirdPersonView = 0;
                client.launchIntegratedServer(
                    worldName,
                    worldName,
                    new WorldSettings(20261029L, WorldSettings.GameType.CREATIVE, true, false, WorldType.FLAT)
                        .enableCommands());
                advance(1);
            } else if (stage == 1 && client.thePlayer != null
                && client.theWorld != null
                && client.getIntegratedServer() != null) {
                    advance(2);
                    ServerTasks.execute(() -> {
                        try {
                            var server = client.getIntegratedServer();
                            var world = server.worldServerForDimension(0);
                            var player = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
                            world.getGameRules()
                                .setOrCreateGameRule("doDaylightCycle", "false");
                            world.getGameRules()
                                .setOrCreateGameRule("doMobSpawning", "false");
                            world.setWorldTime(6000);
                            world.setBlock(8, 4, 8, Blocks.grass, 0, 3);
                            world
                                .setBlock(8, 5, 8, GameRegistry.findBlock("neoecoae", "computation_transmitter"), 2, 3);
                            player.capabilities.isFlying = true;
                            player.sendPlayerAbilities();
                            player.setPositionAndUpdate(10.3, 4.5, 5.2);
                            ready = true;
                        } catch (Throwable error) {
                            failure = error;
                        }
                    });
                } else if (stage == 2 && ready
                    && client.theWorld.getBlock(8, 5, 8)
                        == GameRegistry.findBlock("neoecoae", "computation_transmitter")) {
                            client.displayGuiScreen(null);
                            advance(3);
                            position(client);
                            client.renderGlobal.loadRenderers();
                        } else
                    if (stage == 3) {
                        position(client);
                        if (ticks - stageTick > 100) capture = true;
                    }
        } catch (Throwable error) {
            NeoECOAE.LOG.error("NEOECOAE_TRANSMITTER_RENDER_FAILED", error);
            advance(4);
            client.shutdown();
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !capture) return;
        capture = false;
        Minecraft client = Minecraft.getMinecraft();
        try {
            var tile = client.theWorld.getTileEntity(8, 5, 8);
            if (!(tile instanceof NativeStateHolder)) throw new IllegalStateException("Missing transmitter tile");
            var state = ((NativeStateHolder) tile).nativeBlockState();
            if (state.getValue(NativeMachineBlock.FORMED) || state.getValue(NativeMachineBlock.FACING) != FACINGS[view])
                throw new IllegalStateException("Unexpected transmitter state for view " + view);
            String name = "transmitter-" + label + "-" + view + ".png";
            NeoECOAE.LOG.info(
                "NEOECOAE_TRANSMITTER_SCREENSHOT {}",
                ScreenShotHelper
                    .saveScreenshot(
                        client.mcDataDir,
                        name,
                        client.displayWidth,
                        client.displayHeight,
                        client.getFramebuffer())
                    .getUnformattedText());
            if (++view == 6) {
                NeoECOAE.LOG
                    .info("NEOECOAE_TRANSMITTER_RENDER_CAPTURED label={} views={} save={}", label, view, worldName);
                advance(4);
                client.shutdown();
            } else {
                stageTick = ticks;
                if (view >= 3) {
                    final Direction nextFacing = FACINGS[view];
                    ServerTasks.execute(() -> {
                        try {
                            NativeMachineBlock block = (NativeMachineBlock) GameRegistry
                                .findBlock("neoecoae", "computation_transmitter");
                            WorldView.of(
                                client.getIntegratedServer()
                                    .worldServerForDimension(0))
                                .setBlockAndUpdate(
                                    new BlockPos(8, 5, 8),
                                    block.defaultBlockState()
                                        .setValue(NativeMachineBlock.FACING, nextFacing));
                        } catch (Throwable error) {
                            failure = error;
                        }
                    });
                }
                client.renderGlobal.loadRenderers();
            }
        } catch (Throwable error) {
            NeoECOAE.LOG.error("NEOECOAE_TRANSMITTER_RENDER_FAILED", error);
            advance(4);
            client.shutdown();
        }
    }

    private void position(Minecraft client) {
        double dx = 1.8, dz = -3.3;
        if (view == 3) {
            dx = 3.3;
            dz = 1.8;
        }
        if (view == 4) {
            dx = -1.8;
            dz = 3.3;
        }
        if (view == 5) {
            dx = -3.3;
            dz = -1.8;
        }
        double eyeY = view == 1 ? 5.5 : view == 2 ? 4.8 : 7.2;
        double feetY = eyeY - client.thePlayer.getEyeHeight();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float pitch = (float) Math.toDegrees(Math.atan2(eyeY - 5.5, Math.sqrt(dx * dx + dz * dz)));
        client.thePlayer.capabilities.isFlying = true;
        client.thePlayer.motionX = client.thePlayer.motionY = client.thePlayer.motionZ = 0;
        client.thePlayer.setPositionAndRotation(8.5 + dx, feetY, 8.5 + dz, yaw, pitch);
        client.thePlayer.prevPosX = client.thePlayer.lastTickPosX = client.thePlayer.posX;
        client.thePlayer.prevPosY = client.thePlayer.lastTickPosY = client.thePlayer.posY;
        client.thePlayer.prevPosZ = client.thePlayer.lastTickPosZ = client.thePlayer.posZ;
        client.thePlayer.prevRotationYaw = yaw;
        client.thePlayer.prevRotationPitch = pitch;
    }

    private void advance(int next) {
        stage = next;
        stageTick = ticks;
    }

    private static void verifyWorldPasses(Minecraft client) throws Exception {
        ModernModel model = new ModernModel();
        addFace(
            model,
            "back",
            ModelRenderLayer.CUTOUT,
            ForgeDirection.NORTH,
            new double[] { 0, 0, 6 },
            new double[] { 16, 16, 16 });
        addFace(
            model,
            "glass",
            ModelRenderLayer.TRANSLUCENT,
            ForgeDirection.UP,
            new double[] { 0, 0, 0 },
            new double[] { 16, 6, 6 });
        addFace(
            model,
            "glass",
            ModelRenderLayer.TRANSLUCENT,
            ForgeDirection.DOWN,
            new double[] { 0, 10, 0 },
            new double[] { 16, 16, 6 });
        addFace(
            model,
            "glass",
            ModelRenderLayer.TRANSLUCENT,
            ForgeDirection.NORTH,
            new double[] { 1, 6, 1 },
            new double[] { 15, 10, 1 });
        BakedEcoModel baked = new BakedEcoModel(model);
        Map<String, IIcon> icons = new HashMap<>();
        icons.put(
            "back",
            client.getTextureMapBlocks()
                .getAtlasSprite("neoecoae:compute/transmitter/transmitter_north"));
        icons.put(
            "glass",
            client.getTextureMapBlocks()
                .getAtlasSprite("neoecoae:compute/transmitter/transmitter_north_glass"));
        Field worldPass = ForgeHooksClient.class.getDeclaredField("worldRenderPass");
        worldPass.setAccessible(true);
        int previousWorldPass = worldPass.getInt(null);
        int previousEntityPass = MinecraftForgeClient.getRenderPass();
        try {
            ForgeHooksClient.setRenderPass(-1);
            for (ModelFacing facing : ModelFacing.values()) {
                for (int pass = 0; pass <= 1; pass++) {
                    worldPass.setInt(null, pass);
                    Tessellator tessellator = Tessellator.instance;
                    tessellator.startDrawingQuads();
                    try {
                        // Fullbright, non-cullable fixture faces do not read the null world/block.
                        EcoModelRenderer.renderWorld(baked, facing, icons, null, 8, 5, 8, null);
                        int expected = pass == 0 ? 4 : 12;
                        int actual = tessellator.getVertexState(0, 0, 0)
                            .getVertexCount();
                        if (actual != expected) throw new IllegalStateException(
                            "World pass " + pass + " submitted " + actual + " vertices; expected " + expected);
                        NeoECOAE.LOG.info(
                            "NEOECOAE_TRANSMITTER_WORLD_PASS_OK facing={} world_pass={} entity_pass={} vertices={}",
                            facing,
                            pass,
                            MinecraftForgeClient.getRenderPass(),
                            actual);
                    } finally {
                        tessellator.draw();
                    }
                }
            }
        } finally {
            worldPass.setInt(null, previousWorldPass);
            ForgeHooksClient.setRenderPass(previousEntityPass);
        }
    }

    private static void addFace(ModernModel model, String texture, ModelRenderLayer layer, ForgeDirection normal,
        double[] from, double[] to) {
        ModelElement element = new ModelElement(from, to, false, layer);
        element.addFace(new ModelFace(normal, texture, null, 0, 0, 16, 16, 0, true, layer));
        model.getElements()
            .add(element);
    }
}
