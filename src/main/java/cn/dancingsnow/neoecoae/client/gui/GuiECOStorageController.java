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
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_ROW_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_ROW_STEP;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_TOP;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_LEGEND_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.MATRIX_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.PRIORITY_TAB_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.PRIORITY_TAB_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_DARK_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_H;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_W;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_X;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.RIGHT_Y;
import static cn.dancingsnow.neoecoae.client.gui.StorageControllerLayout.STORAGE_GAUGE_H;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

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
    private static final Map<String, Integer> MATRIX_SCROLL_OFFSETS = new HashMap<String, Integer>();

    private final ContainerECOStorageController container;
    private final TileECOController controller;
    private GuiTabButton priorityButton;
    private List<String> hoveredLines;
    private int matrixScrollColumn;
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
        if (!this.isMouseIn(MATRIX_GRID_AREA_X, this.matrixGridY(), MATRIX_GRID_AREA_W, this.matrixGridHeight(), mouseX,
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
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.hoveredLines != null && !this.hoveredLines.isEmpty()) {
            this.drawTooltip(this.hoveredLines, mouseX, mouseY);
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
        this.drawDarkInsetRect(MATRIX_X, MATRIX_Y, MATRIX_W, MATRIX_H);
        this.drawSlotTexture(this.guiLeft + COMPONENT_SLOT_X, this.guiTop + COMPONENT_SLOT_Y);
        this.drawPlayerInventorySlots(HostUiLayouts.STORAGE.inventoryX(), HostUiLayouts.STORAGE.inventoryY(),
                HostUiLayouts.STORAGE.hotbarY());
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        StorageHostSnapshot state = this.container.state();
        this.drawHeader(state);
        this.drawMonitor(state);
        this.drawUsage(state, mouseX, mouseY);
        this.drawComponent(state, mouseX, mouseY);
        this.drawMatrices(state, mouseX, mouseY);
        this.drawLocalText(tr("container.inventory", "Inventory"), HostUiLayouts.STORAGE.inventoryX(), 159,
                HostUiStyle.TEXT_MUTED);
        this.drawPriorityTooltip(state, mouseX, mouseY);
    }

    private void drawPriorityTooltip(StorageHostSnapshot state, int mouseX, int mouseY) {
        if (this.priorityButton != null && this.isMouseIn(PRIORITY_TAB_X, PRIORITY_TAB_Y, TAB_SIZE, TAB_SIZE, mouseX,
            mouseY)) {
            List<String> lines = new ArrayList<String>();
            lines.add(GuiText.Priority.getLocal());
            lines.add(EnumChatFormatting.GRAY + this.formatNumber(state.priority));
            this.hoveredLines = lines;
        }
    }

    private void drawHeader(StorageHostSnapshot state) {
        this.drawLocalText(tr("gui.neoecoae.storage_ui.title", "ECO Storage Host") + " " + state.tier, 8, 8,
                HostUiStyle.TEXT_PRIMARY);
        String label = tr("gui.neoecoae.storage_ui.formed", "Formed") + ": ";
        String value = yesNo(state.formed);
        int width = this.fontRendererObj.getStringWidth(label) + this.fontRendererObj.getStringWidth(value);
        int x = 316 - width;
        x += this.drawLocalSegment(label, x, 8, HostUiStyle.TEXT_SECONDARY);
        this.drawLocalText(value, x, 8, state.formed ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_BAD);
    }

    private void drawMonitor(StorageHostSnapshot state) {
        int y = TEXT_Y;
        this.drawLocalText(tr("gui.neoecoae.storage_ui.energy_monitor", "Energy Monitor"), TEXT_X, y,
                HostUiStyle.DARK_TEXT_PRIMARY);
        y += TEXT_STEP;
        this.drawUsedTotal(tr("gui.neoecoae.storage_ui.energy_storage", "Energy Storage") + ": ", 0L, 0L, " AE", TEXT_X,
                y, false);
        y += TEXT_STEP + 4;
        this.drawLocalText(tr("gui.neoecoae.storage_ui.item_storage", "Item Storage"), TEXT_X, y,
                HostUiStyle.tierColor(state.tier));
        y += TEXT_STEP;
        this.drawLocalText(
                formatNumber(state.usedTypes) + " " + tr("gui.neoecoae.storage_ui.types", "Types"),
                TEXT_X,
                y,
                HostUiStyle.DARK_TEXT_USED);
        y += TEXT_STEP;
        this.drawUsedTotal("", state.usedBytes, state.totalBytes, " " + tr("gui.neoecoae.storage_ui.bytes", "Bytes"),
                TEXT_X, y, true);
        y += TEXT_STEP + 4;
        this.drawLocalText(tr("gui.neoecoae.storage_ui.host_mode", "Mode") + ": " + modeName(state.hostMode), TEXT_X, y,
                HostUiStyle.DARK_TEXT_VALUE);
        y += TEXT_STEP;
        this.drawLocalText(
                tr("gui.neoecoae.storage_ui.drives", "Drives") + ": " + state.formedDriveCount + " / "
                        + state.requiredDriveCount,
                TEXT_X, y, state.formedDriveCount >= state.requiredDriveCount ? HostUiStyle.DARK_TEXT_SUCCESS
                        : HostUiStyle.DARK_TEXT_MUTED);
        y += TEXT_STEP;
        this.drawLocalText(tr("gui.neoecoae.storage_ui.l9_ready", "All L9 Matrices") + ": " + yesNo(state.allDrivesL9),
                TEXT_X, y, state.allDrivesL9 ? HostUiStyle.DARK_TEXT_SUCCESS : HostUiStyle.DARK_TEXT_MUTED);
    }

    private void drawUsage(StorageHostSnapshot state, int mouseX, int mouseY) {
        this.drawLocalCentered(tr("gui.neoecoae.storage_ui.usage", "Usage"), RIGHT_X, RIGHT_Y + 8, RIGHT_W,
                HostUiStyle.DARK_TEXT_PRIMARY);
        boolean infiniteMode = isInfiniteMode(state);
        double ratio = infiniteMode ? 1.0D : this.animatedUsageRatio(ratio(state.usedBytes, state.totalBytes));
        this.drawTinyInsetLocal(RIGHT_DARK_X, RIGHT_DARK_Y, RIGHT_DARK_W, RIGHT_DARK_H, 0xFF201E27);
        if (infiniteMode) {
            this.drawStorageGauge(STORAGE_GAUGE_X, STORAGE_GAUGE_Y, ratio, HostUiStyle.MATRIX_USAGE_INFINITE);
        } else {
            this.drawStorageGauge(STORAGE_GAUGE_X, STORAGE_GAUGE_Y, ratio, false);
        }
        this.drawUsageDetails(state);
        this.drawLocalCenteredScaled(
                infiniteMode ? "\u221e" : percent(ratio),
                STORAGE_GAUGE_X,
                RIGHT_Y + 120,
                STORAGE_GAUGE_W,
                8,
                infiniteMode ? HostUiStyle.MATRIX_USAGE_INFINITE : HostUiStyle.usedValueColor(state.usedBytes,
                        state.totalBytes),
                USAGE_PERCENT_SCALE);
    }

    private void drawUsageDetails(StorageHostSnapshot state) {
        int y = USAGE_DETAIL_Y;
        boolean infiniteMode = isInfiniteMode(state);
        this.drawDetailLine(
                tr("gui.neoecoae.storage_ui.max_load", "Max Load") + ": "
                        + (infiniteMode ? "-" : percent(this.maxMatrixLoad(state))),
                y,
                HostUiStyle.DARK_TEXT_WARNING);
        y += USAGE_DETAIL_LINE_H;
        this.drawDetailLine(
                tr("gui.neoecoae.storage_ui.avg_load", "Avg Load") + ": "
                        + (infiniteMode ? "-" : percent(this.averageMatrixLoad(state))),
                y,
                HostUiStyle.DARK_TEXT_MUTED);
        y += USAGE_DETAIL_LINE_H;
        this.drawDetailLine(
                tr("gui.neoecoae.storage_ui.idle_matrices", "Idle") + ": "
                        + (infiniteMode ? "-" : this.idleMatrixCount(state)),
                y,
                HostUiStyle.DARK_TEXT_MUTED);
        y += USAGE_DETAIL_LINE_H + 4;
        for (StorageHostSnapshot.TypeStat stat : state.typeStats) {
            this.drawDetailLine(this.typeStatLine(stat), y, this.typeColor(stat.typeId));
            y += USAGE_DETAIL_LINE_H;
        }
    }

    private void drawDetailLine(String text, int y, int color) {
        this.drawLocalCenteredScaled(text, USAGE_DETAIL_X, y, USAGE_DETAIL_W, USAGE_DETAIL_LINE_H, color, 1F);
    }

    private void drawComponent(StorageHostSnapshot state, int mouseX, int mouseY) {
        if (this.isMouseIn(COMPONENT_SLOT_X, COMPONENT_SLOT_Y, 18, 18, mouseX, mouseY)) {
            List<String> lines = new ArrayList<String>();
            lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.storage_ui.infinite_component", "Infinite Component"));
            lines.add(state.infiniteComponentCount + " / 64");
            lines.add(tr("gui.neoecoae.storage_ui.infinite_hint",
                    "L9 + 64 components + 16 L9 matrices unlocks infinite mode"));
            this.hoveredLines = lines;
        }
    }

    private void drawMatrices(StorageHostSnapshot state, int mouseX, int mouseY) {
        this.drawLocalText(tr("gui.neoecoae.storage_ui.matrices", "Storage Matrices"), MATRIX_X + 8, MATRIX_Y + 8,
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

    private void drawMatrixCell(int x, int y, StorageHostSnapshot.MatrixCell cell, boolean hovered) {
        int color = cell != null && cell.hasCell ? this.matrixCellColor(cell)
                : HostUiStyle.MATRIX_USAGE_EMPTY;
        int border = hovered ? 0xFFE5E0F0 : cell != null && cell.hasCell ? 0xFF292331 : MATRIX_EMPTY_BORDER;
        drawRect(x, y, x + MATRIX_CELL_SIZE, y + MATRIX_CELL_SIZE, border);
        drawRect(x + 1, y + 1, x + MATRIX_CELL_SIZE - 1, y + MATRIX_CELL_SIZE - 1, color);
        if (cell != null && cell.hasCell) {
            drawRect(x + 2, y + 2, x + MATRIX_CELL_SIZE - 2, y + 4,
                    this.matrixCellHighlight(cell));
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
        return controller.getWorldObj().provider.dimensionId + ":" + controller.xCoord + ":" + controller.yCoord + ":"
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
        return typeName(stat.typeId) + ": " + formatStorageBytes(stat.usedBytes) + ", " + formatNumber(stat.usedTypes)
                + " " + tr("gui.neoecoae.storage_ui.types", "Types");
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
        this.drawLocalCenteredScaled(label, x + 8, y, MATRIX_LEGEND_W - 8, MATRIX_LEGEND_ROW_H,
                HostUiStyle.DARK_TEXT_MUTED, 0.72F);
    }

    private void drawTinyInsetLocal(int x, int y, int width, int height, int innerColor) {
        drawRect(x, y, x + width, y + height, HostUiStyle.DARK_PANEL_LIGHT_EDGE);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, HostUiStyle.DARK_PANEL_OUTER);
        drawRect(x + 2, y + 2, x + width - 2, y + height - 2, innerColor);
    }

    private List<String> matrixTooltip(StorageHostSnapshot.MatrixCell cell) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + cell.tier + " " + tr("gui.neoecoae.storage_ui.matrix", "Storage Matrix"));
        lines.add(tr("gui.neoecoae.storage_ui.mode", "Mode") + ": " + modeName(cell.mode));
        lines.add(tr("gui.neoecoae.storage_ui.bytes", "Bytes") + ": " + (isInfiniteMatrix(cell) ? "\u221e"
                : formatStorageBytes(cell.usedBytes) + " / " + formatStorageBytes(cell.totalBytes)));
        lines.add(tr("gui.neoecoae.storage_ui.types", "Types") + ": " + formatNumber(cell.usedTypes));
        return lines;
    }

    private List<String> emptyMatrixTooltip(int index) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.DARK_GRAY + tr("gui.neoecoae.storage_ui.matrix", "Storage Matrix") + " #"
                + (index + 1));
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

    private static boolean isInfiniteMatrix(StorageHostSnapshot.MatrixCell cell) {
        return cell != null && ("domain_member".equals(cell.mode) || "migrating".equals(cell.mode));
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
        double elapsed = Math.max(0.0D, Math.min(1.0D,
                (double) (now - this.usageAnimationStartMs) / (double) USAGE_ANIMATION_MS));
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
