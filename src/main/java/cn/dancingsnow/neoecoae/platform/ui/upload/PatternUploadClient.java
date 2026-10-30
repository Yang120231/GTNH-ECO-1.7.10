package cn.dancingsnow.neoecoae.platform.ui.upload;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
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
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client entry points, attached through Forge GUI events rather than remapped GUI mixins: upload/auto buttons,
 * right-click encode, the auto-target tooltip, the target list screen and the in-world target marker. All state is
 * bound to one window. Registered on both the Forge bus (GUI/render events) and the FML bus (client tick).
 */
public final class PatternUploadClient {

    private static volatile PatternUploadPackets.AutoTarget autoTarget;
    private static final AtomicReference<PatternUploadPackets.TargetList> PENDING_LIST = new AtomicReference<>();
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

    /**
     * A complete target list arrived. Called on the network thread (1.7.10 has no scheduled tasks): only the newest
     * list is kept and {@link #tick} hands it to the GUI on the client thread.
     */
    public static void targetList(PatternUploadPackets.TargetList list) {
        PENDING_LIST.set(list);
    }

    /** Client thread: the terminal window whose upload request may open the list; -1 for none. */
    private static int awaitingWindow = -1;
    private static long awaitingUntil;

    @SideOnly(Side.CLIENT)
    private static void expectList(int windowId) {
        awaitingWindow = windowId;
        awaitingUntil = System.currentTimeMillis() + 15_000L;
    }

    /** The list screen of this window was left; later replies to its actions must not reopen it. */
    @SideOnly(Side.CLIENT)
    static void screenClosed(int windowId) {
        if (awaitingWindow == windowId) awaitingWindow = -1;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        PatternUploadPackets.TargetList list = PENDING_LIST.getAndSet(null);
        if (list != null) deliver(list);
    }

    @SideOnly(Side.CLIENT)
    private static void deliver(PatternUploadPackets.TargetList list) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        if (screen instanceof PatternUploadScreen) {
            if (((PatternUploadScreen) screen).windowId == list.windowId) ((PatternUploadScreen) screen).accept(list);
            return;
        }
        if (!(screen instanceof GuiPatternTerm) || minecraft.thePlayer == null) return;
        GuiPatternTerm terminal = (GuiPatternTerm) screen;
        int windowId = terminal.inventorySlots.windowId;
        if (windowId != list.windowId || minecraft.thePlayer.openContainer != terminal.inventorySlots) return;
        if (awaitingWindow != windowId || System.currentTimeMillis() > awaitingUntil) return;
        awaitingWindow = -1;
        // An upload started from the terminal itself succeeded: stay on the terminal.
        if (list.event == PatternUploadPackets.EVENT_UPLOADED) return;
        // GuiContainer.onGuiClosed runs Container.onContainerClosed on the client, which would drop a stack held on
        // the cursor client-side only; the server window stays open, so keep the cursor stack untouched.
        ItemStack held = minecraft.thePlayer.inventory.getItemStack();
        minecraft.thePlayer.inventory.setItemStack(null);
        try {
            minecraft.displayGuiScreen(new PatternUploadScreen(terminal, list));
        } finally {
            minecraft.thePlayer.inventory.setItemStack(held);
        }
    }

    public static synchronized void highlight(int dimension, int x, int y, int z) {
        highlightDimension = dimension;
        highlightX = x;
        highlightY = y;
        highlightZ = z;
        highlightUntil = System.currentTimeMillis() + 30_000L;
    }

    /**
     * NEI filled a pattern terminal; an empty map clears the previous hint for this window. The category is NEI's
     * display name of the recipe tab (empty for crafting-table recipes) and prefills the target search.
     */
    @SideOnly(Side.CLIENT)
    public static void captureHint(GuiContainer gui, String recipeMapId, ItemStack circuit, String category) {
        if (!(gui instanceof GuiPatternTerm)) return;
        PatternUploadPackets.hint(
            gui.inventorySlots.windowId,
            recipeMapId == null ? "" : recipeMapId,
            circuit,
            category == null ? "" : category);
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
        // Remove the legacy 20px gap so existing configs (offset 51) align with AE2's pin button at guiLeft + 178.
        int x = encodeButton.xPosition + PlanningSettings.PatternUpload.patternUploadButtonOffsetX - 20;
        int y = encodeButton.yPosition + PlanningSettings.PatternUpload.patternUploadButtonOffsetY;
        event.buttonList.add(new PatternUploadButton(x, y, false));
        event.buttonList.add(new PatternUploadButton(x, y + 20, true));
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiButton button = event.button;
        if (!(event.gui instanceof GuiPatternTerm) || !(button instanceof PatternUploadButton)) return;
        int windowId = ((GuiContainer) event.gui).inventorySlots.windowId;
        expectList(windowId);
        PatternUploadPackets.request(
            windowId,
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
        if (!encodeButton.mousePressed(Minecraft.getMinecraft(), event.mouseX, event.mouseY)) return;
        int windowId = ((GuiContainer) event.gui).inventorySlots.windowId;
        expectList(windowId);
        PatternUploadPackets.request(windowId, PatternUploadPackets.ENCODE_AND_OPEN);
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
