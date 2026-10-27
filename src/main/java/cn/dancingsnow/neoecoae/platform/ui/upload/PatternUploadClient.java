package cn.dancingsnow.neoecoae.platform.ui.upload;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client entry points, attached through Forge GUI events rather than remapped GUI mixins: upload/auto buttons,
 * right-click encode, the auto-target tooltip and the in-world target marker. All state is bound to one window.
 */
public final class PatternUploadClient {

    private static volatile PatternUploadPackets.AutoTarget autoTarget;
    private static int highlightDimension, highlightX, highlightY, highlightZ;
    private static long highlightUntil;

    private GuiImgButton encodeButton;
    private boolean rightDown;

    /** Called from the network thread; one immutable message replaces the previous one. */
    public static void autoTarget(PatternUploadPackets.AutoTarget message) {
        autoTarget = message;
    }

    @SideOnly(Side.CLIENT)
    static PatternUploadPackets.AutoTarget currentAutoTarget() {
        PatternUploadPackets.AutoTarget target = autoTarget;
        var screen = Minecraft.getMinecraft().currentScreen;
        if (target == null || !(screen instanceof GuiContainer)
            || ((GuiContainer) screen).inventorySlots.windowId != target.windowId) return null;
        return target;
    }

    public static synchronized void highlight(int dimension, int x, int y, int z) {
        highlightDimension = dimension;
        highlightX = x;
        highlightY = y;
        highlightZ = z;
        highlightUntil = System.currentTimeMillis() + 10_000L;
    }

    /** NEI filled a pattern terminal; an empty map clears the previous hint for this window. */
    @SideOnly(Side.CLIENT)
    public static void captureHint(GuiContainer gui, String recipeMapId, ItemStack circuit) {
        if (!(gui instanceof GuiPatternTerm)) return;
        PatternUploadPackets.hint(gui.inventorySlots.windowId, recipeMapId == null ? "" : recipeMapId, circuit);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        encodeButton = null;
        if (!(event.gui instanceof GuiPatternTerm)) return;
        for (Object button : event.buttonList)
            if (button instanceof GuiImgButton && !(button instanceof PatternUploadButton)
                && ((GuiImgButton) button).getSetting() == Settings.ACTIONS
                && ((GuiImgButton) button).getCurrentValue() == ActionItems.ENCODE)
                encodeButton = (GuiImgButton) button;
        if (encodeButton == null) return;
        int x = encodeButton.xPosition + PlanningSettings.patternUploadButtonOffsetX;
        int y = encodeButton.yPosition + PlanningSettings.patternUploadButtonOffsetY;
        event.buttonList.add(new PatternUploadButton(x, y, false));
        event.buttonList.add(new PatternUploadButton(x, y + 20, true));
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiButton button = event.button;
        if (!(event.gui instanceof GuiPatternTerm) || !(button instanceof PatternUploadButton)) return;
        PatternUploadPackets.request(
            ((GuiContainer) event.gui).inventorySlots.windowId,
            ((PatternUploadButton) button).automatic ? PatternUploadPackets.AUTO : PatternUploadPackets.OPEN);
        event.setCanceled(true);
    }

    /** Forge 1.7.10 has no GUI mouse event; detect a right-click edge over AE2's encode button instead. */
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void draw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        boolean down = Mouse.isButtonDown(1);
        boolean pressed = down && !rightDown;
        rightDown = down;
        if (!pressed || !(event.gui instanceof GuiPatternTerm) || encodeButton == null) return;
        if (encodeButton.mousePressed(Minecraft.getMinecraft(), event.mouseX, event.mouseY)) PatternUploadPackets
            .request(((GuiContainer) event.gui).inventorySlots.windowId, PatternUploadPackets.ENCODE_AND_OPEN);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void render(RenderWorldLastEvent event) {
        int dimension, x, y, z;
        synchronized (PatternUploadClient.class) {
            if (System.currentTimeMillis() > highlightUntil) return;
            dimension = highlightDimension;
            x = highlightX;
            y = highlightY;
            z = highlightZ;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.renderViewEntity == null
            || minecraft.theWorld.provider.dimensionId != dimension) return;
        Block block = minecraft.theWorld.getBlock(x, y, z);
        if (block == null || block.getMaterial() == Material.air) return;
        block.setBlockBoundsBasedOnState(minecraft.theWorld, x, y, z);
        AxisAlignedBB box = block.getSelectedBoundingBoxFromPool(minecraft.theWorld, x, y, z);
        if (box == null) return;
        var view = minecraft.renderViewEntity;
        float partial = event.partialTicks;
        double viewX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial;
        double viewY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial;
        double viewZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial;
        box = box.expand(0.002, 0.002, 0.002)
            .getOffsetBoundingBox(-viewX, -viewY, -viewZ);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            GL11.glColor4f(1.0F, 0.1F, 0.1F, 0.95F);
            RenderGlobal.drawOutlinedBoundingBox(box, -1);
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }
}
