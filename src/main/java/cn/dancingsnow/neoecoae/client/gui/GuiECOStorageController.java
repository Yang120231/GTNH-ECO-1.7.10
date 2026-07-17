package cn.dancingsnow.neoecoae.client.gui;

import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.COMPONENT_SLOT_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.COMPONENT_SLOT_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.LEFT_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.LEFT_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.LEFT_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.LEFT_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_CELL_SIZE;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_EMPTY_BORDER;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_GRID_AREA_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_GRID_AREA_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_GRID_LABEL_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_GRID_ROWS;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_ROW_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_ROW_STEP;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_TOP;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.PRIORITY_TAB_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.PRIORITY_TAB_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_CONTENT_SHIFT_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.STORAGE_GAUGE_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.STORAGE_GAUGE_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.STORAGE_GAUGE_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.TAB_SIZE;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.TEXT_STEP;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.TEXT_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.TEXT_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.USAGE_DETAIL_LINE_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.USAGE_DETAIL_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.USAGE_DETAIL_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.USAGE_DETAIL_Y;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageController;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostSnapshot;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.PacketStorageHostAction;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiECOStorageController extends GuiHostMachineBase {

    private static final long USAGE_ANIMATION_MS = 500L;
    private static final double USAGE_ANIMATION_EPSILON = 0.0001D;
    private static final float USAGE_PERCENT_SCALE = 0.9F;
    private static final int MATRIX_SCROLL_THRESHOLD_COLUMNS = 10;
    private static final int MATRIX_SCROLL_STEP = 1;
    private static final int INFINITE_STORAGE_DIGITS = 6;
    private static final Map<String, Integer> MATRIX_SCROLL_OFFSETS = new HashMap<String, Integer>();
    private static final String[] EXTENDED_BYTE_UNITS = { "B", "K", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q" };

    private final ContainerECOStorageController container;
    private final TileECOController controller;
    private GuiTabButton priorityButton;
    private List<String> hoveredLines;
    private int matrixScrollColumn;
    private int hugeStackScrollRow;
    private double usageAnimationStart = 0.0D;
    private double usageAnimationTarget = -1.0D;
    private long usageAnimationStartMs = 0L;

    public GuiECOStorageController(InventoryPlayer playerInventory, TileECOController controller) {
        this(new ContainerECOStorageController(playerInventory, controller));
    }

    private GuiECOStorageController(ContainerECOStorageController container) {
        super(container, HostUiLayouts.STORAGE.width(), HostUiLayouts.STORAGE.height());
        this.container = container;
        this.controller = container.getController();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.matrixScrollColumn = savedMatrixScroll(this.controller);
        this.priorityButton = StoragePriorityTabs.priorityButton(this.guiLeft, this.guiTop, this.itemRender);
        this.buttonList.add(this.priorityButton);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (isInfiniteDisplay(this.container.state()) && this.isMouseIn(
            USAGE_DETAIL_X,
            USAGE_DETAIL_Y + USAGE_DETAIL_LINE_H * 4 + 1,
            USAGE_DETAIL_W,
            65,
            mouseX,
            mouseY)) {
            int max = Math.max(0, this.container.state().hugeStacks.size() - 3);
            this.hugeStackScrollRow = Math.max(0, Math.min(max, this.hugeStackScrollRow + (wheel < 0 ? 1 : -1)));
            return;
        }
        if (!this.isMouseIn(
            MATRIX_GRID_AREA_X,
            this.matrixGridY(),
            MATRIX_GRID_AREA_W,
            this.matrixGridHeight(),
            mouseX,
            mouseY)) {
            return;
        }
        int maxScroll = this.maxMatrixScroll(this.container.state());
        if (maxScroll <= 0) {
            this.updateMatrixScroll(0);
            return;
        }
        int direction = wheel < 0 ? MATRIX_SCROLL_STEP : -MATRIX_SCROLL_STEP;
        this.updateMatrixScroll(this.matrixScrollColumn + direction);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.hoveredLines = null;
        this.updateSlotPositions();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.hoveredLines != null && !this.hoveredLines.isEmpty()) {
            this.drawTooltip(this.hoveredLines, mouseX, mouseY);
        }
    }

    private void updateSlotPositions() {
        for (int index = 0; index < this.container.inventorySlots.size(); index++) {
            Slot slot = (Slot) this.container.inventorySlots.get(index);
            if (index == 0) {
                slot.xDisplayPosition = ContainerECOStorageController.INFINITE_COMPONENT_SLOT_X;
                slot.yDisplayPosition = ContainerECOStorageController.INFINITE_COMPONENT_SLOT_Y;
            } else {
                int playerIndex = index - 1;
                if (playerIndex < 27) {
                    slot.xDisplayPosition = LEFT_X + 1 + playerIndex % 9 * 18;
                    slot.yDisplayPosition = 148 + playerIndex / 9 * 18;
                } else {
                    slot.xDisplayPosition = LEFT_X + 1 + (playerIndex - 27) * 18;
                    slot.yDisplayPosition = 206;
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == this.priorityButton) {
            NENetwork.CHANNEL.sendToServer(
                new PacketStorageHostAction(this.controller, PacketStorageHostAction.Action.OPEN_PRIORITY));
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        if (this.priorityButton != null) {
            this.priorityButton.enabled = true;
        }
        this.drawDarkInsetRect(LEFT_X, LEFT_Y, LEFT_W, LEFT_H);
        this.drawDarkInsetRect(RIGHT_X, RIGHT_Y, RIGHT_W, RIGHT_H);
        // Background layers draw in screen coordinates. The local variant is only valid from the
        // foreground layer, where GuiContainer has already translated by guiLeft/guiTop.
        this.drawTinyInsetRect(RIGHT_DARK_X, RIGHT_DARK_Y, RIGHT_DARK_W, RIGHT_DARK_H, 0xFF201E27);
        this.drawSlotTexture(this.guiLeft + COMPONENT_SLOT_X, this.guiTop + COMPONENT_SLOT_Y);
        this.drawPlayerInventorySlots(LEFT_X, 147, 205);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        StorageHostSnapshot state = this.container.state();
        this.drawHeader(state);
        this.drawMonitor(state, mouseX, mouseY);
        this.drawUsage(state, mouseX, mouseY);
        this.drawComponent(state, mouseX, mouseY);
        this.drawLocalText(tr("container.inventory", "Inventory"), LEFT_X, 136, HostUiStyle.TEXT_MUTED);
        this.drawPriorityTooltip(state, mouseX, mouseY);
    }

    private void drawPriorityTooltip(StorageHostSnapshot state, int mouseX, int mouseY) {
        if (this.priorityButton != null
            && this.isMouseIn(PRIORITY_TAB_X, PRIORITY_TAB_Y, TAB_SIZE, TAB_SIZE, mouseX, mouseY)) {
            List<String> lines = new ArrayList<String>();
            lines.add(GuiText.Priority.getLocal());
            lines.add(EnumChatFormatting.GRAY + this.formatNumber(state.priority));
            this.hoveredLines = lines;
        }
    }

    private void drawHeader(StorageHostSnapshot state) {
        this.drawLocalText(
            this.hostBlockTitle("storage", state.tier, "ECO Storage Host " + state.tier),
            8,
            8,
            HostUiStyle.HOST_TITLE);
        String label = tr("gui.neoecoae.storage_ui.formed", "Formed") + ": ";
        String value = yesNo(state.formed);
        int width = this.fontRendererObj.getStringWidth(label) + this.fontRendererObj.getStringWidth(value);
        int x = 316 - width;
        x += this.drawLocalSegment(label, x, 8, HostUiStyle.TEXT_SECONDARY);
        this.drawLocalText(value, x, 8, state.formed ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_BAD);
    }

    private void drawMonitor(StorageHostSnapshot state, int mouseX, int mouseY) {
        int y = TEXT_Y;
        this.drawLocalText(
            tr("gui.neoecoae.storage_ui.energy_monitor", "Energy Monitor"),
            TEXT_X,
            y,
            HostUiStyle.DARK_TEXT_PRIMARY);
        y += TEXT_STEP;
        this.drawLocalText(
            tr("gui.neoecoae.storage_ui.energy_storage", "Energy Storage") + ": -",
            TEXT_X,
            y,
            HostUiStyle.DARK_TEXT_MUTED);
        y += TEXT_STEP + 4;
        this.drawLocalText(
            tr("gui.neoecoae.storage_ui.item_storage", "Item Storage"),
            TEXT_X,
            y,
            HostUiStyle.tierColor(state.tier));
        y += TEXT_STEP;
        this.drawLocalText(
            formatNumber(state.usedTypes) + " " + tr("gui.neoecoae.storage_ui.types", "Types"),
            TEXT_X,
            y,
            HostUiStyle.DARK_TEXT_USED);
        y += TEXT_STEP;
        this.drawStorageBytesLine(state, TEXT_X, y);
        if (this.isMouseIn(TEXT_X, y, LEFT_W - 16, this.fontRendererObj.FONT_HEIGHT, mouseX, mouseY)) {
            List<String> lines = new ArrayList<String>();
            String bytes = tr("gui.neoecoae.storage_ui.bytes", "Bytes");
            if (isInfiniteMode(state)) {
                lines.add(formatExactBytes(state.preciseUsedBytes) + " " + bytes);
            } else {
                lines.add(
                    formatExactBytes(BigInteger.valueOf(state.usedBytes)) + " / "
                        + formatExactBytes(BigInteger.valueOf(state.totalBytes))
                        + " "
                        + bytes);
            }
            this.hoveredLines = lines;
        }
    }

    private void drawUsage(StorageHostSnapshot state, int mouseX, int mouseY) {
        this.drawLocalCentered(
            tr("gui.neoecoae.storage_ui.system_load", "System Load"),
            RIGHT_X,
            RIGHT_Y + 2 + RIGHT_CONTENT_SHIFT_Y,
            RIGHT_W,
            HostUiStyle.DARK_TEXT_PRIMARY);
        boolean infiniteMode = isInfiniteMode(state);
        boolean migrating = isMigrating(state);
        double ratio = infiniteMode || migrating ? 1.0D
            : this.animatedUsageRatio(ratio(state.usedBytes, state.totalBytes));
        if (infiniteMode || migrating) {
            this.drawStorageGauge(STORAGE_GAUGE_X, STORAGE_GAUGE_Y, ratio, HostUiStyle.MATRIX_USAGE_INFINITE);
        } else {
            this.drawStorageGauge(STORAGE_GAUGE_X, STORAGE_GAUGE_Y, ratio, false);
        }
        this.drawUsageDetails(state);
        this.drawHugeStacks(state, mouseX, mouseY);
        this.drawLocalCenteredScaled(
            infiniteMode ? "\u221e" : migrating ? "..." : percent(ratio),
            STORAGE_GAUGE_X,
            RIGHT_DARK_Y + RIGHT_DARK_H - 12,
            STORAGE_GAUGE_W,
            8,
            infiniteMode || migrating ? HostUiStyle.MATRIX_USAGE_INFINITE
                : HostUiStyle.usedValueColor(state.usedBytes, state.totalBytes),
            USAGE_PERCENT_SCALE);
    }

    private void drawUsageDetails(StorageHostSnapshot state) {
        int y = USAGE_DETAIL_Y;
        boolean infiniteMode = isInfiniteMode(state);
        boolean migrating = isMigrating(state);
        this.drawDetailLine(
            tr("gui.neoecoae.storage_ui.current_load", "Current Load") + ": "
                + (infiniteMode ? tr("gui.neoecoae.storage_ui.infinite_value", "Infinite")
                    : migrating ? tr("gui.neoecoae.storage_ui.mode.migrating", "Migrating")
                        : percent(state.usedBytes, state.totalBytes)),
            y,
            HostUiStyle.DARK_TEXT_VALUE);
        y += USAGE_DETAIL_LINE_H;
        this.drawDetailLine(
            tr("gui.neoecoae.storage_ui.max_load", "Max Load") + ": "
                + (infiniteMode ? "MAX" : migrating ? "-" : percent(this.maxMatrixLoad(state))),
            y,
            infiniteMode || migrating ? HostUiStyle.MATRIX_USAGE_INFINITE : HostUiStyle.DARK_TEXT_WARNING);
        y += USAGE_DETAIL_LINE_H;
        this.drawDetailLine(
            tr("gui.neoecoae.storage_ui.status", "Status") + ": "
                + (infiniteMode ? tr("gui.neoecoae.storage_ui.mode.infinite", "Infinite")
                    : migrating ? tr("gui.neoecoae.storage_ui.mode.migrating", "Migrating")
                        : (state.formed ? tr("gui.neoecoae.storage_ui.formed", "Formed")
                            : tr("gui.neoecoae.storage_ui.mode.unformed", "Unformed"))),
            y,
            state.formed ? HostUiStyle.DARK_TEXT_SUCCESS : HostUiStyle.DARK_TEXT_WARNING);
        y += USAGE_DETAIL_LINE_H;
        this.drawDetailLine(
            tr("gui.neoecoae.storage_ui.idle_matrices", "Idle Matrices") + ": " + this.idleMatrixCount(state),
            y,
            HostUiStyle.DARK_TEXT_MUTED);
    }

    private void drawDetailLine(String text, int y, int color) {
        int textWidth = Math.max(1, this.fontRendererObj.getStringWidth(text));
        float scale = Math.min(1.0F, Math.max(0.55F, (float) (USAGE_DETAIL_W - 4) / textWidth));
        GL11.glPushMatrix();
        GL11.glTranslatef(
            USAGE_DETAIL_X + 2,
            y + (USAGE_DETAIL_LINE_H - this.fontRendererObj.FONT_HEIGHT * scale) / 2.0F,
            200.0F);
        GL11.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void drawHugeStacks(StorageHostSnapshot state, int mouseX, int mouseY) {
        if (!isInfiniteDisplay(state) || state.hugeStacks.isEmpty()) {
            return;
        }
        int x = USAGE_DETAIL_X;
        int y = USAGE_DETAIL_Y + USAGE_DETAIL_LINE_H * 4 + 1;
        int height = 65;
        int rowHeight = 18;
        int maxScroll = Math.max(0, state.hugeStacks.size() - 3);
        this.hugeStackScrollRow = Math.max(0, Math.min(maxScroll, this.hugeStackScrollRow));
        this.beginScissor(x, y, USAGE_DETAIL_W, height);
        for (int row = 0; row < 4 && this.hugeStackScrollRow + row < state.hugeStacks.size(); row++) {
            StorageHostSnapshot.HugeStack stack = state.hugeStacks.get(this.hugeStackScrollRow + row);
            int rowY = y + row * rowHeight;
            ItemStack itemStack = displayItemStack(stack);
            FluidStack fluidStack = displayFluidStack(stack);
            if (itemStack != null) {
                this.drawLocalItemIcon(itemStack, x, rowY + 1);
            } else if (fluidStack != null) {
                this.drawFluidIcon(fluidStack, x, rowY + 1);
            }
            int textX = x + 19;
            int textW = USAGE_DETAIL_W - 23;
            this.drawHugeStackText(
                displayName(stack, itemStack, fluidStack),
                textX,
                rowY + 1,
                textW,
                HostUiStyle.DARK_TEXT_VALUE);
            this.drawHugeStackText(
                formatInfiniteStorageBytes(stack.amount),
                textX,
                rowY + 9,
                textW,
                HostUiStyle.DARK_TEXT_USED);
            if (this.isMouseIn(x, rowY, USAGE_DETAIL_W, rowHeight, mouseX, mouseY)) {
                List<String> lines = new ArrayList<String>();
                lines.add(EnumChatFormatting.AQUA + displayName(stack, itemStack, fluidStack));
                lines.add(formatInfiniteStorageBytes(stack.amount));
                this.hoveredLines = lines;
            }
        }
        this.endScissor();
        if (maxScroll > 0) {
            int thumbH = Math.max(10, height * 3 / state.hugeStacks.size());
            int thumbY = y + (height - thumbH) * this.hugeStackScrollRow / maxScroll;
            drawRect(x + USAGE_DETAIL_W - 2, y, x + USAGE_DETAIL_W, y + height, 0xAA17141E);
            drawRect(x + USAGE_DETAIL_W - 2, thumbY, x + USAGE_DETAIL_W, thumbY + thumbH, 0xFF8377FF);
        }
    }

    private static String shortIdentity(String identity) {
        if (identity == null) {
            return "";
        }
        int separator = identity.indexOf(':');
        return separator >= 0 && separator + 1 < identity.length() ? identity.substring(separator + 1) : identity;
    }

    private void drawHugeStackText(String text, int x, int y, int width, int color) {
        float scale = 0.72F;
        int unscaledWidth = Math.max(1, (int) (width / scale));
        String fitted = this.fontRendererObj.trimStringToWidth(text, unscaledWidth);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 200.0F);
        GL11.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawString(fitted, 0, 0, color);
        GL11.glPopMatrix();
    }

    private static ItemStack displayItemStack(StorageHostSnapshot.HugeStack stack) {
        if (stack == null || !"item".equals(stack.channel)) {
            return null;
        }
        Item item = (Item) Item.itemRegistry.getObject(stack.identity);
        return item == null ? null : new ItemStack(item, 1, Math.max(0, stack.metadata));
    }

    private static FluidStack displayFluidStack(StorageHostSnapshot.HugeStack stack) {
        return stack == null || !"fluid".equals(stack.channel) ? null : FluidRegistry.getFluidStack(stack.identity, 1);
    }

    private static String displayName(StorageHostSnapshot.HugeStack stack, ItemStack item, FluidStack fluid) {
        if (item != null) {
            return item.getDisplayName();
        }
        if (fluid != null && fluid.getFluid() != null) {
            return fluid.getFluid()
                .getLocalizedName(fluid);
        }
        return shortIdentity(stack == null ? "" : stack.identity);
    }

    private void drawFluidIcon(FluidStack stack, int x, int y) {
        Fluid fluid = stack == null ? null : stack.getFluid();
        IIcon icon = fluid == null ? null : fluid.getIcon(stack);
        if (icon == null) {
            return;
        }
        this.mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawTexturedModelRectFromIcon(x, y, icon, 16, 16);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawComponent(StorageHostSnapshot state, int mouseX, int mouseY) {
        if (state.infiniteComponentCount > 0 && !state.canTakeInfiniteComponent) {
            drawRect(COMPONENT_SLOT_X, COMPONENT_SLOT_Y, COMPONENT_SLOT_X + 18, COMPONENT_SLOT_Y + 18, 0x99505050);
        }
        if (this.isMouseIn(COMPONENT_SLOT_X, COMPONENT_SLOT_Y, 18, 18, mouseX, mouseY)) {
            List<String> lines = new ArrayList<String>();
            lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.storage_ui.infinite_component", "Infinite Component"));
            lines.add(state.infiniteComponentCount + " / 64");
            if (state.infiniteComponentCount > 0 && !state.canTakeInfiniteComponent) {
                lines.add(
                    EnumChatFormatting.GRAY + tr(
                        "gui.neoecoae.storage_ui.infinite_component_locked",
                        "Cannot remove while infinite storage contains contents"));
            }
            lines.add(
                tr(
                    "gui.neoecoae.storage_ui.infinite_hint",
                    "L9 + 64 components + 16 L9 matrices unlocks infinite mode"));
            this.hoveredLines = lines;
        }
    }

    private void drawMatrices(StorageHostSnapshot state, int mouseX, int mouseY) {
        this.drawLocalText(
            tr("gui.neoecoae.storage_ui.matrices", "Storage Matrices"),
            MATRIX_X + 8,
            MATRIX_Y + 8,
            HostUiStyle.DARK_TEXT_PRIMARY);
        int columns = this.matrixColumnCount(state);
        int visibleColumns = Math.min(columns, this.visibleMatrixColumns());
        this.updateMatrixScroll(Math.min(this.matrixScrollColumn, this.maxMatrixScroll(state)));
        int totalCells = visibleColumns * MATRIX_GRID_ROWS;
        int gridX = MATRIX_GRID_AREA_X + (MATRIX_GRID_AREA_W - columns * MATRIX_CELL_SIZE) / 2;
        if (columns > visibleColumns) {
            gridX = MATRIX_GRID_AREA_X;
        }
        int gridY = this.matrixGridY();
        this.beginScissor(MATRIX_GRID_AREA_X, gridY, MATRIX_GRID_AREA_W, this.matrixGridHeight());
        for (int index = 0; index < totalCells; index++) {
            int column = this.matrixScrollColumn + index / MATRIX_GRID_ROWS;
            int row = index % MATRIX_GRID_ROWS;
            int visibleColumn = column - this.matrixScrollColumn;
            int x = gridX + visibleColumn * MATRIX_CELL_SIZE;
            int y = gridY + row * MATRIX_CELL_SIZE;
            StorageHostSnapshot.MatrixCell cell = this.findMatrixCell(state, row, column);
            boolean hovered = this.isMouseIn(x, y, MATRIX_CELL_SIZE, MATRIX_CELL_SIZE, mouseX, mouseY);
            this.drawMatrixCell(x, y, cell, hovered);
            if (hovered) {
                this.hoveredLines = cell != null && cell.hasCell ? this.matrixTooltip(cell)
                    : this.emptyMatrixTooltip(column * MATRIX_GRID_ROWS + row);
            }
        }
        this.endScissor();
        if (this.maxMatrixScroll(state) > 0) {
            this.drawMatrixScrollMarker(state);
        }
        this.drawLocalCentered(
            tr("gui.neoecoae.storage_ui.load_distribution", "Load Distribution"),
            MATRIX_GRID_AREA_X,
            MATRIX_GRID_LABEL_Y,
            MATRIX_GRID_AREA_W,
            HostUiStyle.DARK_TEXT_MUTED);
        this.drawMatrixLegend(MATRIX_LEGEND_X, MATRIX_LEGEND_TOP);
    }

    private int visibleMatrixColumns() {
        return Math.max(1, Math.min(MATRIX_SCROLL_THRESHOLD_COLUMNS, MATRIX_GRID_AREA_W / MATRIX_CELL_SIZE));
    }

    private int matrixGridY() {
        return MATRIX_Y + 24;
    }

    private int matrixGridHeight() {
        return MATRIX_GRID_ROWS * MATRIX_CELL_SIZE;
    }

    private int maxMatrixScroll(StorageHostSnapshot state) {
        return Math.max(0, this.matrixColumnCount(state) - this.visibleMatrixColumns());
    }

    private void updateMatrixScroll(int scrollColumn) {
        int maxScroll = this.maxMatrixScroll(this.container.state());
        int clamped = Math.max(0, Math.min(scrollColumn, maxScroll));
        if (this.matrixScrollColumn == clamped) {
            return;
        }
        this.matrixScrollColumn = clamped;
        MATRIX_SCROLL_OFFSETS.put(matrixScrollKey(this.controller), Integer.valueOf(clamped));
    }

    private void drawMatrixScrollMarker(StorageHostSnapshot state) {
        int trackX = MATRIX_GRID_AREA_X;
        int trackY = MATRIX_GRID_LABEL_Y - 4;
        int trackW = MATRIX_GRID_AREA_W;
        int maxScroll = Math.max(1, this.maxMatrixScroll(state));
        int thumbW = Math.max(8, trackW * this.visibleMatrixColumns() / Math.max(1, this.matrixColumnCount(state)));
        int thumbX = trackX + (trackW - thumbW) * this.matrixScrollColumn / maxScroll;
        drawRect(trackX, trackY, trackX + trackW, trackY + 2, 0xFF2C2833);
        drawRect(thumbX, trackY, thumbX + thumbW, trackY + 2, 0xFFBEB6D4);
    }

    private void drawUsedTotal(String prefix, long used, long total, String suffix, int x, int y,
        boolean storageBytes) {
        int cursor = this.drawLocalSegment(prefix, x, y, HostUiStyle.DARK_TEXT_MUTED);
        String usedText = storageBytes ? this.formatStorageBytes(used) : this.formatNumber(used);
        String totalText = storageBytes ? this.formatStorageBytes(total) : this.formatNumber(total);
        cursor += this.drawLocalSegment(usedText, x + cursor, y, HostUiStyle.usedValueColor(used, total));
        cursor += this.drawLocalSegment(" / ", x + cursor, y, HostUiStyle.DARK_TEXT_MUTED);
        cursor += this.drawLocalSegment(totalText, x + cursor, y, HostUiStyle.DARK_TEXT_VALUE);
        this.drawLocalText(suffix, x + cursor, y, HostUiStyle.DARK_TEXT_MUTED);
    }

    private void drawStorageBytesLine(StorageHostSnapshot state, int x, int y) {
        if (!isInfiniteMode(state)) {
            this.drawUsedTotal(
                "",
                state.usedBytes,
                state.totalBytes,
                " " + tr("gui.neoecoae.storage_ui.bytes", "Bytes"),
                x,
                y,
                true);
            return;
        }
        int cursor = this.drawLocalSegment(
            formatInfiniteStorageBytes(state.preciseUsedBytes),
            x,
            y,
            HostUiStyle.usedValueColor(state.usedBytes, state.totalBytes));
        this.drawLocalText(
            " " + tr("gui.neoecoae.storage_ui.bytes", "Bytes"),
            x + cursor,
            y,
            HostUiStyle.DARK_TEXT_MUTED);
    }

    private void drawMatrixCell(int x, int y, StorageHostSnapshot.MatrixCell cell, boolean hovered) {
        int color = cell != null && cell.hasCell ? this.matrixCellColor(cell) : HostUiStyle.MATRIX_USAGE_EMPTY;
        int border = hovered ? 0xFFE5E0F0 : cell != null && cell.hasCell ? 0xFF292331 : MATRIX_EMPTY_BORDER;
        drawRect(x, y, x + MATRIX_CELL_SIZE, y + MATRIX_CELL_SIZE, border);
        drawRect(x + 1, y + 1, x + MATRIX_CELL_SIZE - 1, y + MATRIX_CELL_SIZE - 1, color);
        if (cell != null && cell.hasCell) {
            drawRect(x + 2, y + 2, x + MATRIX_CELL_SIZE - 2, y + 4, this.matrixCellHighlight(cell));
        }
    }

    private int matrixCellColor(StorageHostSnapshot.MatrixCell cell) {
        return isInfiniteMatrix(cell) ? HostUiStyle.MATRIX_USAGE_INFINITE
            : HostUiStyle.matrixUsageColor(cell.usedBytes, cell.totalBytes);
    }

    private int matrixCellHighlight(StorageHostSnapshot.MatrixCell cell) {
        return isInfiniteMatrix(cell) ? 0xFFEAD4FF : HostUiStyle.matrixUsageHighlight(cell.usedBytes, cell.totalBytes);
    }

    private StorageHostSnapshot.MatrixCell findMatrixCell(StorageHostSnapshot state, int row, int column) {
        for (StorageHostSnapshot.MatrixCell cell : state.matrixCells) {
            if (cell.row == row && cell.column == column) {
                return cell;
            }
        }
        return null;
    }

    private int matrixColumnCount(StorageHostSnapshot state) {
        int columns = 0;
        for (StorageHostSnapshot.MatrixCell cell : state.matrixCells) {
            columns = Math.max(columns, cell.column + 1);
        }
        return Math.max(1, columns);
    }

    private static int savedMatrixScroll(TileECOController controller) {
        Integer value = MATRIX_SCROLL_OFFSETS.get(matrixScrollKey(controller));
        return value == null ? 0 : Math.max(0, value.intValue());
    }

    private static String matrixScrollKey(TileECOController controller) {
        if (controller == null || controller.getWorldObj() == null) {
            return "unknown";
        }
        return controller.getWorldObj().provider.dimensionId + ":"
            + controller.xCoord
            + ":"
            + controller.yCoord
            + ":"
            + controller.zCoord;
    }

    private double maxMatrixLoad(StorageHostSnapshot state) {
        double max = 0.0D;
        for (StorageHostSnapshot.MatrixCell cell : state.matrixCells) {
            if (cell.hasCell) {
                max = Math.max(max, ratio(cell.usedBytes, cell.totalBytes));
            }
        }
        return max;
    }

    private double averageMatrixLoad(StorageHostSnapshot state) {
        double total = 0.0D;
        int count = 0;
        for (StorageHostSnapshot.MatrixCell cell : state.matrixCells) {
            if (cell.hasCell) {
                total += ratio(cell.usedBytes, cell.totalBytes);
                count++;
            }
        }
        return count <= 0 ? 0.0D : total / (double) count;
    }

    private int idleMatrixCount(StorageHostSnapshot state) {
        int count = 0;
        for (StorageHostSnapshot.MatrixCell cell : state.matrixCells) {
            if (cell.hasCell && cell.usedBytes <= 0L && cell.usedTypes <= 0L) {
                count++;
            }
        }
        return count;
    }

    private String typeStatLine(StorageHostSnapshot.TypeStat stat) {
        return typeName(stat.typeId) + ": "
            + formatStorageBytes(stat.usedBytes)
            + ", "
            + formatNumber(stat.usedTypes)
            + " "
            + tr("gui.neoecoae.storage_ui.types", "Types");
    }

    private static String typeName(String typeId) {
        if ("fluid".equals(typeId)) {
            return tr("gui.neoecoae.storage_ui.graph.fluid", "Fluid");
        }
        return tr("gui.neoecoae.storage_ui.graph.item", "Item");
    }

    private static int typeColor(String typeId) {
        return "fluid".equals(typeId) ? HostUiStyle.MATRIX_USAGE_MEDIUM : HostUiStyle.MATRIX_USAGE_LOW;
    }

    private void drawMatrixLegend(int x, int y) {
        this.drawLegendKey(x, y, HostUiStyle.MATRIX_USAGE_LOW, "0-50%");
        this.drawLegendKey(x, y + MATRIX_LEGEND_ROW_STEP, HostUiStyle.MATRIX_USAGE_MEDIUM, "50-75%");
        this.drawLegendKey(x, y + MATRIX_LEGEND_ROW_STEP * 2, HostUiStyle.MATRIX_USAGE_HIGH, "75-90%");
        this.drawLegendKey(x, y + MATRIX_LEGEND_ROW_STEP * 3, HostUiStyle.MATRIX_USAGE_FULL, "90%+");
        this.drawLegendKey(
            x,
            y + MATRIX_LEGEND_ROW_STEP * 4,
            HostUiStyle.MATRIX_USAGE_INFINITE,
            tr("gui.neoecoae.storage_ui.legend.infinite", "Infinite"));
        this.drawLegendKey(
            x,
            y + MATRIX_LEGEND_ROW_STEP * 5,
            HostUiStyle.MATRIX_USAGE_EMPTY,
            tr("gui.neoecoae.storage_ui.legend.empty", "Empty"));
    }

    private void drawLegendKey(int x, int y, int color, String label) {
        drawRect(x, y + 1, x + 6, y + 7, color);
        this.drawLocalCenteredScaled(
            label,
            x + 8,
            y,
            MATRIX_LEGEND_W - 8,
            MATRIX_LEGEND_ROW_H,
            HostUiStyle.DARK_TEXT_MUTED,
            0.72F);
    }

    private List<String> matrixTooltip(StorageHostSnapshot.MatrixCell cell) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + cell.tier + " " + tr("gui.neoecoae.storage_ui.matrix", "Storage Matrix"));
        lines.add(tr("gui.neoecoae.storage_ui.mode", "Mode") + ": " + modeName(cell.mode));
        lines.add(
            tr("gui.neoecoae.storage_ui.bytes", "Bytes") + ": "
                + (isInfiniteMatrix(cell) ? "\u221e"
                    : formatStorageBytes(cell.usedBytes) + " / " + formatStorageBytes(cell.totalBytes)));
        lines.add(tr("gui.neoecoae.storage_ui.types", "Types") + ": " + formatNumber(cell.usedTypes));
        return lines;
    }

    private List<String> emptyMatrixTooltip(int index) {
        List<String> lines = new ArrayList<String>();
        lines.add(
            EnumChatFormatting.DARK_GRAY + tr("gui.neoecoae.storage_ui.matrix", "Storage Matrix") + " #" + (index + 1));
        lines.add(tr("gui.neoecoae.storage_ui.no_matrix_installed", "No storage matrix installed"));
        return lines;
    }

    private static String percent(long used, long total) {
        if (used <= 0L || total <= 0L) {
            return "0%";
        }
        return Math.round(ratio(used, total) * 100.0D) + "%";
    }

    private static String percent(double ratio) {
        return Math.round(Math.max(0.0D, Math.min(1.0D, ratio)) * 100.0D) + "%";
    }

    private static double ratio(long used, long total) {
        if (used <= 0L || total <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) used / (double) total));
    }

    private static boolean isInfiniteMode(StorageHostSnapshot state) {
        return state != null && "formed_infinite".equals(state.hostMode);
    }

    private static boolean isInfiniteDisplay(StorageHostSnapshot state) {
        return state != null && (isInfiniteMode(state) || "migrating_to_infinite".equals(state.hostMode)
            || "migrating".equals(state.hostMode));
    }

    private static boolean isMigrating(StorageHostSnapshot state) {
        return state != null && ("migrating_to_infinite".equals(state.hostMode) || "migrating".equals(state.hostMode));
    }

    private static boolean isInfiniteMatrix(StorageHostSnapshot.MatrixCell cell) {
        return cell != null && ("domain_member".equals(cell.mode) || "migrating".equals(cell.mode));
    }

    private static String formatInfiniteStorageBytes(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            return "0 B";
        }
        BigDecimal scaled = new BigDecimal(value);
        int unit = 0;
        BigDecimal base = BigDecimal.valueOf(1024L);
        while (scaled.compareTo(base) >= 0 && unit < EXTENDED_BYTE_UNITS.length - 1) {
            scaled = scaled.divide(base, MathContext.DECIMAL128);
            unit++;
        }
        scaled = scaled.round(new MathContext(INFINITE_STORAGE_DIGITS, RoundingMode.HALF_UP));
        if (scaled.compareTo(base) >= 0 && unit < EXTENDED_BYTE_UNITS.length - 1) {
            scaled = scaled.divide(base, MathContext.DECIMAL128)
                .round(new MathContext(INFINITE_STORAGE_DIGITS, RoundingMode.HALF_UP));
            unit++;
        }
        return scaled.stripTrailingZeros()
            .toPlainString() + " "
            + EXTENDED_BYTE_UNITS[unit];
    }

    private static String formatExactBytes(BigInteger value) {
        BigInteger safe = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        return String.format(Locale.US, "%,d", safe);
    }

    private double animatedUsageRatio(double target) {
        long now = System.currentTimeMillis();
        if (this.usageAnimationTarget < 0.0D) {
            this.usageAnimationStart = 0.0D;
            this.usageAnimationTarget = target;
            this.usageAnimationStartMs = now;
        } else if (Math.abs(this.usageAnimationTarget - target) > USAGE_ANIMATION_EPSILON) {
            this.usageAnimationStart = this.currentAnimatedUsageRatio(now);
            this.usageAnimationTarget = target;
            this.usageAnimationStartMs = now;
        }
        return this.currentAnimatedUsageRatio(now);
    }

    private double currentAnimatedUsageRatio(long now) {
        double elapsed = Math
            .max(0.0D, Math.min(1.0D, (double) (now - this.usageAnimationStartMs) / (double) USAGE_ANIMATION_MS));
        double eased = cubicBezierEase(elapsed);
        return this.usageAnimationStart + (this.usageAnimationTarget - this.usageAnimationStart) * eased;
    }

    private static double cubicBezierEase(double progress) {
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        for (int i = 0; i < 5; i++) {
            double x = cubicBezier(t, 0.25D, 0.25D);
            double slope = cubicBezierSlope(t, 0.25D, 0.25D);
            if (slope == 0.0D) {
                break;
            }
            t -= (x - progress) / slope;
            t = Math.max(0.0D, Math.min(1.0D, t));
        }
        return cubicBezier(t, 0.1D, 1.0D);
    }

    private static double cubicBezier(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * t * p1 + 3.0D * inverse * t * t * p2 + t * t * t;
    }

    private static double cubicBezierSlope(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * p1 + 6.0D * inverse * t * (p2 - p1) + 3.0D * t * t * (1.0D - p2);
    }

    private static String modeName(String mode) {
        if ("formed_infinite".equals(mode)) {
            return tr("gui.neoecoae.storage_ui.mode.infinite", "Infinite");
        }
        if ("migrating_to_infinite".equals(mode) || "migrating".equals(mode)) {
            return tr("gui.neoecoae.storage_ui.mode.migrating", "Migrating");
        }
        if ("domain_member".equals(mode)) {
            return tr("gui.neoecoae.storage_ui.mode.domain_member", "Infinite Member");
        }
        if ("formed_normal".equals(mode)) {
            return tr("gui.neoecoae.storage_ui.mode.normal", "Normal");
        }
        if ("unformed".equals(mode)) {
            return tr("gui.neoecoae.storage_ui.mode.unformed", "Unformed");
        }
        return tr("gui.neoecoae.storage_ui.mode.portable", "Portable");
    }

    private static String tr(String key, String fallback) {
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }
}
