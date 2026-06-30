package cn.dancingsnow.neoecoae.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.gui.container.ContainerCraftingHatch;
import cn.dancingsnow.neoecoae.tile.TileCraftingHatch;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiCraftingHatch extends GuiHostMachineBase {

    private static final int UI_WIDTH = 176;
    private static final int UI_HEIGHT = 155;
    private static final int FLUID_SLOT_SIZE = 18;
    private static final int FLUID_SLOT_X = (UI_WIDTH - FLUID_SLOT_SIZE) / 2;
    private static final int FLUID_SLOT_Y = 28;
    private static final int FLUID_ICON_X = FLUID_SLOT_X + 1;
    private static final int FLUID_ICON_Y = FLUID_SLOT_Y + 1;
    private static final int FLUID_ICON_SIZE = 16;
    private static final int AMOUNT_Y = FLUID_SLOT_Y + FLUID_SLOT_SIZE + 6;

    private final ContainerCraftingHatch container;
    private final TileCraftingHatch hatch;
    private List<String> hoveredLines;

    public GuiCraftingHatch(InventoryPlayer playerInventory, TileCraftingHatch hatch) {
        this(new ContainerCraftingHatch(playerInventory, hatch), hatch);
    }

    private GuiCraftingHatch(ContainerCraftingHatch container, TileCraftingHatch hatch) {
        super(container, UI_WIDTH, UI_HEIGHT);
        this.container = container;
        this.hatch = hatch;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.hoveredLines = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.hoveredLines != null && !this.hoveredLines.isEmpty()) {
            this.drawTooltip(this.hoveredLines, mouseX, mouseY);
        }
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawSlotTexture(this.guiLeft + FLUID_SLOT_X, this.guiTop + FLUID_SLOT_Y);
        this.drawFluidIcon();
        this.drawPlayerInventorySlots(
            ContainerCraftingHatch.PLAYER_INVENTORY_X,
            ContainerCraftingHatch.PLAYER_INVENTORY_Y,
            ContainerCraftingHatch.PLAYER_HOTBAR_Y);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.drawLocalText(
            this.translate(
                this.hatch != null && this.hatch.isInput() ? "container.neoecoae.crafting_input_hatch"
                    : "container.neoecoae.crafting_output_hatch"),
            8,
            7,
            HostUiStyle.TEXT_PRIMARY);
        this.drawLocalCentered(
            this.fluidAmountText(),
            FLUID_SLOT_X - 55,
            AMOUNT_Y,
            FLUID_SLOT_SIZE + 110,
            HostUiStyle.TEXT_VALUE);
        this.drawLocalText(
            this.translate("gui.neoecoae.common.inventory"),
            ContainerCraftingHatch.PLAYER_INVENTORY_X,
            ContainerCraftingHatch.PLAYER_INVENTORY_Y - 11,
            HostUiStyle.TEXT_MUTED);
        if (this.isMouseIn(FLUID_SLOT_X, FLUID_SLOT_Y, FLUID_SLOT_SIZE, FLUID_SLOT_SIZE, mouseX, mouseY)) {
            this.hoveredLines = this.fluidTooltip();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0
            && this.isMouseIn(FLUID_SLOT_X, FLUID_SLOT_Y, FLUID_SLOT_SIZE, FLUID_SLOT_SIZE, mouseX, mouseY)) {
            this.mc.playerController
                .sendEnchantPacket(this.inventorySlots.windowId, ContainerCraftingHatch.ACTION_FLUID_SLOT_CLICK);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void drawFluidIcon() {
        int amount = this.container.getFluidAmount();
        int capacity = Math.max(1, this.container.getCapacity());
        Fluid fluid = this.fluid();
        IIcon icon = fluid == null ? null : fluid.getIcon(new FluidStack(fluid, Math.max(1, amount)));
        if (amount <= 0 || icon == null) {
            return;
        }
        int fill = Math.max(1, Math.min(FLUID_ICON_SIZE, amount * FLUID_ICON_SIZE / capacity));
        int x = this.guiLeft + FLUID_ICON_X;
        int y = this.guiTop + FLUID_ICON_Y + FLUID_ICON_SIZE - fill;
        this.mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        for (int drawY = 0; drawY < fill; drawY += 16) {
            int height = Math.min(16, fill - drawY);
            for (int drawX = 0; drawX < FLUID_ICON_SIZE; drawX += 16) {
                int width = Math.min(16, FLUID_ICON_SIZE - drawX);
                this.drawIconPart(x + drawX, y + drawY, width, height, icon);
            }
        }
        drawRect(
            this.guiLeft + FLUID_ICON_X,
            this.guiTop + FLUID_ICON_Y,
            this.guiLeft + FLUID_ICON_X + FLUID_ICON_SIZE,
            this.guiTop + FLUID_ICON_Y + 1,
            0x55FFFFFF);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawIconPart(int x, int y, int width, int height, IIcon icon) {
        float minU = icon.getMinU();
        float maxU = minU + (icon.getMaxU() - minU) * width / 16.0F;
        float minV = icon.getMinV();
        float maxV = minV + (icon.getMaxV() - minV) * height / 16.0F;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, this.zLevel, minU, maxV);
        tessellator.addVertexWithUV(x + width, y + height, this.zLevel, maxU, maxV);
        tessellator.addVertexWithUV(x + width, y, this.zLevel, maxU, minV);
        tessellator.addVertexWithUV(x, y, this.zLevel, minU, minV);
        tessellator.draw();
    }

    private String fluidAmountText() {
        return this.formatNumber(this.container.getFluidAmount()) + " / "
            + this.formatNumber(this.container.getCapacity())
            + " mB";
    }

    private List<String> fluidTooltip() {
        List<String> lines = new ArrayList<String>();
        Fluid fluid = this.fluid();
        if (fluid == null || this.container.getFluidAmount() <= 0) {
            lines.add(EnumChatFormatting.AQUA + this.translate("gui.neoecoae.crafting_hatch.empty"));
        } else {
            FluidStack stack = new FluidStack(fluid, this.container.getFluidAmount());
            lines.add(EnumChatFormatting.AQUA + stack.getLocalizedName());
        }
        lines.add(EnumChatFormatting.GRAY + this.fluidAmountText());
        lines.add(
            EnumChatFormatting.DARK_GRAY
                + (this.hatch != null && this.hatch.isInput() ? this.translate("gui.neoecoae.crafting_hatch.input_hint")
                    : this.translate("gui.neoecoae.crafting_hatch.output_hint")));
        return lines;
    }

    private Fluid fluid() {
        int id = this.container.getFluidId();
        return id < 0 ? null : FluidRegistry.getFluid(id);
    }
}
