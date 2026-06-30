package cn.dancingsnow.neoecoae.client.gui;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.container.ContainerCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiCraftingPatternBus extends GuiHostMachineBase {

    private static final int UI_WIDTH = 176;
    private static final int UI_HEIGHT = 246;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = ContainerCraftingPatternBus.PATTERN_GRID_X;
    private static final int GRID_Y = ContainerCraftingPatternBus.PATTERN_GRID_Y;
    private static final int INVENTORY_X = ContainerCraftingPatternBus.PLAYER_INVENTORY_X;
    private static final int INVENTORY_Y = ContainerCraftingPatternBus.PLAYER_INVENTORY_Y;
    private static final int HOTBAR_Y = ContainerCraftingPatternBus.PLAYER_HOTBAR_Y;
    private static final int PAGE_BUTTON_Y = 4;
    private static final int PAGE_BUTTON_W = 12;
    private static final int PAGE_BUTTON_H = 14;
    private static final int PAGE_PREV_BUTTON_X = UI_WIDTH - 34;
    private static final int PAGE_NEXT_BUTTON_X = UI_WIDTH - 16;
    private static final int PAGE_TEXT_RIGHT_X = PAGE_PREV_BUTTON_X - 5;
    private static final int PAGE_ARROW_ENABLED = 0xFF30343F;
    private static final int PAGE_ARROW_HOVER = 0xFF1F4E86;
    private static final int PAGE_ARROW_DISABLED = 0xFF7C8294;
    private static final ResourceLocation PATTERN_SLOT_OVERLAY = new ResourceLocation(
        NeoECOAE.MODID,
        "textures/gui/widget/pattern_overlay.png");

    private final TileCraftingPatternBus bus;
    private final ContainerCraftingPatternBus container;
    private final Map<ItemStack, IAEStack<?>> outputCache = new WeakHashMap<ItemStack, IAEStack<?>>();

    public GuiCraftingPatternBus(InventoryPlayer playerInventory, TileCraftingPatternBus bus) {
        this(new ContainerCraftingPatternBus(playerInventory, bus), bus);
    }

    private GuiCraftingPatternBus(ContainerCraftingPatternBus container, TileCraftingPatternBus bus) {
        super(container, UI_WIDTH, UI_HEIGHT);
        this.container = container;
        this.bus = bus;
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        for (int row = 0; row < ContainerCraftingPatternBus.ROWS; row++) {
            for (int column = 0; column < ContainerCraftingPatternBus.COLUMNS; column++) {
                int x = GRID_X + column * SLOT_SIZE;
                int y = GRID_Y + row * SLOT_SIZE;
                int index = column + row * ContainerCraftingPatternBus.COLUMNS;
                Slot slot = (Slot) this.inventorySlots.inventorySlots.get(index);
                this.drawPatternSlot(this.guiLeft + x, this.guiTop + y, !slot.getHasStack());
            }
        }
        this.drawPlayerInventorySlots(INVENTORY_X, INVENTORY_Y, HOTBAR_Y);
        this.drawPageButtons(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.drawTitleAndPage();
        this.drawLocalText(
            this.translate("gui.neoecoae.common.inventory"),
            INVENTORY_X,
            INVENTORY_Y - 11,
            HostUiStyle.TEXT_MUTED);
        this.drawPatternOutputs(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            if (this.isMouseIn(PAGE_PREV_BUTTON_X, PAGE_BUTTON_Y, PAGE_BUTTON_W, PAGE_BUTTON_H, mouseX, mouseY)
                && this.container.currentPage() > 0) {
                this.container.setClientPage(this.container.currentPage() - 1);
                this.mc.playerController
                    .sendEnchantPacket(this.inventorySlots.windowId, ContainerCraftingPatternBus.ACTION_PREVIOUS_PAGE);
                return;
            }
            if (this.isMouseIn(PAGE_NEXT_BUTTON_X, PAGE_BUTTON_Y, PAGE_BUTTON_W, PAGE_BUTTON_H, mouseX, mouseY)
                && this.container.currentPage() + 1 < this.container.pageCount()) {
                this.container.setClientPage(this.container.currentPage() + 1);
                this.mc.playerController
                    .sendEnchantPacket(this.inventorySlots.windowId, ContainerCraftingPatternBus.ACTION_NEXT_PAGE);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void drawPatternOutputs(int mouseX, int mouseY) {
        for (int index = 0; index < ContainerCraftingPatternBus.PATTERN_SLOTS_PER_PAGE; index++) {
            Slot slot = (Slot) this.inventorySlots.inventorySlots.get(index);
            IAEStack<?> output = this.outputFor(slot.getStack());
            if (output == null) {
                continue;
            }
            int frameX = slot.xDisplayPosition - 1;
            int frameY = slot.yDisplayPosition - 1;
            GL11.glPushMatrix();
            GL11.glTranslatef(0.0F, 0.0F, 220.0F);
            this.drawPatternSlot(frameX, frameY, false);
            this.renderAeStack(output, slot.xDisplayPosition, slot.yDisplayPosition);
            if (this.isLocalMouseIn(frameX, frameY, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                drawRect(frameX + 1, frameY + 1, frameX + 17, frameY + 17, 0x44FFFFFF);
            }
            GL11.glPopMatrix();
        }
    }

    private void drawPageButtons(int mouseX, int mouseY) {
        this.drawPageButton(PAGE_PREV_BUTTON_X, mouseX, mouseY, this.container.currentPage() > 0, false);
        this.drawPageButton(
            PAGE_NEXT_BUTTON_X,
            mouseX,
            mouseY,
            this.container.currentPage() + 1 < this.container.pageCount(),
            true);
    }

    private void drawPageButton(int x, int mouseX, int mouseY, boolean enabled, boolean next) {
        boolean hovered = this.isMouseIn(x, PAGE_BUTTON_Y, PAGE_BUTTON_W, PAGE_BUTTON_H, mouseX, mouseY);
        this.drawButtonTexture(x, PAGE_BUTTON_Y, PAGE_BUTTON_W, PAGE_BUTTON_H, hovered, enabled, false);
        int color = !enabled ? PAGE_ARROW_DISABLED : hovered ? PAGE_ARROW_HOVER : PAGE_ARROW_ENABLED;
        this.drawPageArrow(this.guiLeft + x + 3, this.guiTop + PAGE_BUTTON_Y + 4, next, color);
    }

    private void drawPageArrow(int x, int y, boolean next, int color) {
        for (int row = 0; row < 6; row++) {
            int width = row < 3 ? row + 1 : 6 - row;
            int arrowX = next ? x + row : x + 5 - row;
            drawRect(arrowX, y + row, arrowX + width, y + row + 1, color);
        }
    }

    private void drawTitleAndPage() {
        String pageText = this.container.currentPage() + 1 + " / " + this.container.pageCount();
        int titleMaxWidth = Math.max(0, PAGE_TEXT_RIGHT_X - this.fontRendererObj.getStringWidth(pageText) - 14);
        this.drawLocalText(
            this.truncate(this.translate("container.neoecoae.crafting_pattern_bus"), titleMaxWidth),
            8,
            7,
            HostUiStyle.TEXT_PRIMARY);
        this.drawLocalRight(pageText, PAGE_TEXT_RIGHT_X, 7, HostUiStyle.TEXT_PRIMARY);
    }

    private String truncate(String text, int maxWidth) {
        if (this.fontRendererObj.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.fontRendererObj.getStringWidth(ellipsis);
        int length = text.length();
        while (length > 0
            && this.fontRendererObj.getStringWidth(text.substring(0, length)) + ellipsisWidth > maxWidth) {
            length--;
        }
        return length <= 0 ? ellipsis : text.substring(0, length) + ellipsis;
    }

    private IAEStack<?> outputFor(ItemStack pattern) {
        if (pattern == null || !(pattern.getItem() instanceof ICraftingPatternItem)) {
            return null;
        }
        if (this.outputCache.containsKey(pattern)) {
            return this.outputCache.get(pattern);
        }

        World world = this.bus.getWorldObj() != null ? this.bus.getWorldObj() : this.mc.theWorld;
        if (world == null) {
            return null;
        }

        ICraftingPatternDetails details = ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world);
        IAEStack<?> output = null;
        if (details != null) {
            IAEStack<?>[] outputs = details.getCondensedAEOutputs();
            if (outputs.length > 0) {
                output = outputs[0];
            }
        }
        this.outputCache.put(pattern, output);
        return output;
    }

    private void renderAeStack(IAEStack<?> stack, int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        stack.drawInGui(this.mc, x, y);
        stack.drawOverlayInGui(this.mc, x, y, true, true, false, false);
        GL11.glPopAttrib();
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    private boolean isLocalMouseIn(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawPatternSlot(int x, int y, boolean drawOverlay) {
        this.drawSlotTexture(x, y);
        if (drawOverlay) {
            this.drawTexture(
                PATTERN_SLOT_OVERLAY,
                x,
                y,
                SLOT_SIZE,
                SLOT_SIZE,
                0,
                0,
                SLOT_SIZE,
                SLOT_SIZE,
                SLOT_SIZE,
                SLOT_SIZE);
        }
    }
}
