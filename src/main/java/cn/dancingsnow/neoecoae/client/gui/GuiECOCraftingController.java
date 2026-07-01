package cn.dancingsnow.neoecoae.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOCraftingController;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingHostSnapshot;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.PacketCraftingHostAction;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiECOCraftingController extends GuiHostMachineBase {

    private static final int OVERCLOCK_BUTTON_ID = 7701;
    private static final int COOLING_BUTTON_ID = 7702;
    private static final int CLEAR_COOLANT_BUTTON_ID = 7703;
    private static final int EDGE = 7;
    private static final int GAP = 7;
    private static final int HEADER_Y = EDGE;
    private static final int TOP_AREA_Y = 30;
    private static final int TOP_AREA_H = 88;
    private static final int STATUS_AREA_X = EDGE;
    private static final int STATUS_AREA_W = 66;
    private static final int MODULE_AREA_X = STATUS_AREA_X + STATUS_AREA_W + GAP;
    private static final int MODULE_AREA_Y = TOP_AREA_Y;
    private static final int MODULE_AREA_W = 132;
    private static final int GAUGE_AREA_X = MODULE_AREA_X + MODULE_AREA_W + GAP;
    private static final int GAUGE_AREA_W = 78;
    private static final int TASK_PANEL_X = EDGE + 18 * 9 + GAP;
    private static final int TASK_PANEL_Y = TOP_AREA_Y + TOP_AREA_H + GAP;
    private static final int TASK_PANEL_W = 121;
    private static final int TASK_PANEL_H = 84;
    private static final int MODULE_GRID_X = MODULE_AREA_X + 7;
    private static final int MODULE_GRID_Y = MODULE_AREA_Y + 31;
    private static final int MODULE_GRID_W = MODULE_AREA_W - 14;
    private static final int MODULE_GRID_H = 35;
    private static final int MODULE_STATS_Y = MODULE_AREA_Y + TOP_AREA_H - 23;
    private static final int MODULE_PROGRESS_Y = MODULE_AREA_Y + TOP_AREA_H - 10;
    private static final int MODULE_PROGRESS_H = 4;
    private static final int MODULE_CELL_MAX = 12;
    private static final int GAUGE_BAR_Y = TOP_AREA_Y + 26;
    private static final int GAUGE_BAR_H = 45;
    private static final int GAUGE_BAR_W = 24;
    private static final int GAUGE_GAP = 7;
    private static final int GAUGE_HOVER_BOTTOM_PADDING = 11;
    private static final int TOOLBAR_SIZE = 16;
    private static final int TOOLBAR_GAP = 3;
    private static final int TOOLBAR_Y = EDGE;
    private static final int TOOLBAR_CLEAR_X = HostUiLayouts.CRAFTING.width() - EDGE - TOOLBAR_SIZE;
    private static final int TOOLBAR_COOLING_X = TOOLBAR_CLEAR_X - TOOLBAR_GAP - TOOLBAR_SIZE;
    private static final int TOOLBAR_OVERCLOCK_X = TOOLBAR_COOLING_X - TOOLBAR_GAP - TOOLBAR_SIZE;
    private static final int TASK_CARD_X = TASK_PANEL_X + 7;
    private static final int TASK_CARD_Y = TASK_PANEL_Y + 20;
    private static final int TASK_CARD_W = TASK_PANEL_W - 14;
    private static final int TASK_CARD_H = 14;
    private static final int TASK_CARD_STEP = 15;
    private static final int TASK_LIST_X = TASK_CARD_X;
    private static final int TASK_LIST_Y = TASK_CARD_Y;
    private static final int TASK_LIST_W = TASK_PANEL_W - 14;
    private static final int TASK_LIST_H = TASK_CARD_STEP * 4 - 1;
    private static final int TASK_VISIBLE_CARDS = 4;
    private static final int TASK_SCROLL_STEP = 9;
    private static final float TEXT_SCALE = 0.95F;
    private static final float MODULE_TEXT_SCALE = 0.82F;
    private static final float CARD_TEXT_SCALE = 0.72F;
    private static final float CARD_ICON_SCALE = 0.75F;

    private final ContainerECOCraftingController container;
    private final TileECOController controller;
    private GuiButton overclockButton;
    private GuiButton coolingButton;
    private GuiButton clearCoolantButton;
    private List<String> hoveredLines;
    private int taskScrollPixels;

    public GuiECOCraftingController(InventoryPlayer playerInventory, TileECOController controller) {
        this(new ContainerECOCraftingController(playerInventory, controller));
    }

    private GuiECOCraftingController(ContainerECOCraftingController container) {
        super(container, HostUiLayouts.CRAFTING.width(), HostUiLayouts.CRAFTING.height());
        this.container = container;
        this.controller = container.getController();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.overclockButton = this.addInvisibleButton(OVERCLOCK_BUTTON_ID, TOOLBAR_OVERCLOCK_X);
        this.coolingButton = this.addInvisibleButton(COOLING_BUTTON_ID, TOOLBAR_COOLING_X);
        this.clearCoolantButton = this.addInvisibleButton(CLEAR_COOLANT_BUTTON_ID, TOOLBAR_CLEAR_X);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == this.overclockButton) {
            this.sendAction(PacketCraftingHostAction.Action.TOGGLE_OVERCLOCK);
            return;
        }
        if (button == this.coolingButton) {
            this.sendAction(PacketCraftingHostAction.Action.TOGGLE_ACTIVE_COOLING);
            return;
        }
        if (button == this.clearCoolantButton) {
            this.sendAction(PacketCraftingHostAction.Action.CLEAR_COOLANT);
            return;
        }
        super.actionPerformed(button);
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
        if (!this.isMouseIn(TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H, mouseX, mouseY)) {
            return;
        }
        int maxScroll = this.maxTaskScroll(this.container.state());
        if (maxScroll <= 0) {
            this.updateTaskScroll(0);
            return;
        }
        int direction = wheel < 0 ? TASK_SCROLL_STEP : -TASK_SCROLL_STEP;
        this.updateTaskScroll(this.taskScrollPixels + direction);
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
        this.drawDarkInsetRect(STATUS_AREA_X, TOP_AREA_Y, STATUS_AREA_W, TOP_AREA_H);
        this.drawDarkInsetRect(MODULE_AREA_X, MODULE_AREA_Y, MODULE_AREA_W, TOP_AREA_H);
        this.drawDarkInsetRect(GAUGE_AREA_X, TOP_AREA_Y, GAUGE_AREA_W, TOP_AREA_H);
        this.drawDarkInsetRect(TASK_PANEL_X, TASK_PANEL_Y, TASK_PANEL_W, TASK_PANEL_H);
        this.drawPlayerInventorySlots(
            HostUiLayouts.CRAFTING.inventoryX(),
            HostUiLayouts.CRAFTING.inventoryY(),
            HostUiLayouts.CRAFTING.hotbarY());
        this.drawToolbarButton(TOOLBAR_OVERCLOCK_X, mouseX, mouseY, true, this.container.state().overclocked);
        this.drawToolbarButton(TOOLBAR_COOLING_X, mouseX, mouseY, true, this.container.state().activeCooling);
        this.drawToolbarButton(TOOLBAR_CLEAR_X, mouseX, mouseY, this.container.state().coolant > 0, false);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        CraftingHostSnapshot state = this.container.state();
        this.drawHeader(state);
        this.drawToolbarTextAndTooltips(state, mouseX, mouseY);
        this.drawStatusPanel(state, mouseX, mouseY);
        this.drawModulePreview(state, mouseX, mouseY);
        this.drawGaugePanel(state, mouseX, mouseY);
        this.drawTaskPanel(state, mouseX, mouseY);
        this.drawLocalText(
            tr("gui.neoecoae.common.inventory", "Inventory"),
            HostUiLayouts.CRAFTING.inventoryX(),
            TASK_PANEL_Y,
            HostUiStyle.TEXT_MUTED);
    }

    private GuiButton addInvisibleButton(int id, int x) {
        GuiButton button = new InvisibleButton(
            id,
            this.guiLeft + x,
            this.guiTop + TOOLBAR_Y,
            TOOLBAR_SIZE,
            TOOLBAR_SIZE);
        this.buttonList.add(button);
        return button;
    }

    private void sendAction(PacketCraftingHostAction.Action action) {
        NENetwork.CHANNEL.sendToServer(new PacketCraftingHostAction(this.controller, action));
    }

    private void drawHeader(CraftingHostSnapshot state) {
        boolean active = state.formed;
        this.drawLocalText(
            tr("gui.neoecoae.crafting_ui.title", "ECO Crafting Host") + " " + state.tier,
            EDGE,
            HEADER_Y,
            HostUiStyle.TEXT_PRIMARY);
        String status = tr("gui.neoecoae.machine.formed", "Formed") + ": "
            + yesNo(state.formed)
            + "   "
            + tr("gui.neoecoae.machine.active", "Active")
            + ": "
            + yesNo(active);
        this.drawLocalRight(
            status,
            TOOLBAR_OVERCLOCK_X - GAP,
            HEADER_Y,
            active ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_BAD);
    }

    private void drawToolbarButton(int x, int mouseX, int mouseY, boolean enabled, boolean selected) {
        CraftingHostSnapshot state = this.container.state();
        AEA2ToolbarIconButton.Sprite icon = x == TOOLBAR_OVERCLOCK_X
            ? state.overclocked ? AEA2ToolbarIconButton.LEVEL_ENERGY : AEA2ToolbarIconButton.POWER_UNIT_AE
            : x == TOOLBAR_COOLING_X ? AEA2ToolbarIconButton.TYPE_FILTER_FLUIDS
                : state.coolant > 0 ? AEA2ToolbarIconButton.CONDENSER_OUTPUT_TRASH
                    : AEA2ToolbarIconButton.BACKGROUND_TRASH;
        AEA2ToolbarIconButton.draw(this, x, TOOLBAR_Y, mouseX, mouseY, TOOLBAR_SIZE, icon, enabled, selected);
    }

    private void drawToolbarTextAndTooltips(CraftingHostSnapshot state, int mouseX, int mouseY) {
        if (this.isMouseIn(TOOLBAR_OVERCLOCK_X, TOOLBAR_Y, TOOLBAR_SIZE, TOOLBAR_SIZE, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<String>();
            this.hoveredLines.add(
                EnumChatFormatting.AQUA + tr(
                    state.overclocked ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off",
                    state.overclocked ? "Overclock: On" : "Overclock: Off"));
            this.addTranslatedLines(
                this.hoveredLines,
                "gui.neoecoae.crafting.overclocked.tooltip",
                "Boost performance.");
        } else if (this.isMouseIn(TOOLBAR_COOLING_X, TOOLBAR_Y, TOOLBAR_SIZE, TOOLBAR_SIZE, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<String>();
            this.hoveredLines.add(
                EnumChatFormatting.AQUA + tr(
                    state.activeCooling ? "gui.neoecoae.crafting.active_cooling.on"
                        : "gui.neoecoae.crafting.active_cooling.off",
                    state.activeCooling ? "Active Cooling: On" : "Active Cooling: Off"));
            this.addTranslatedLines(this.hoveredLines, "gui.neoecoae.crafting.active_cooling.tooltip", "Use coolant.");
        } else if (this.isMouseIn(TOOLBAR_CLEAR_X, TOOLBAR_Y, TOOLBAR_SIZE, TOOLBAR_SIZE, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<String>();
            this.hoveredLines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.clear_coolant", "Clear"));
            this.addTranslatedLines(
                this.hoveredLines,
                "gui.neoecoae.crafting.clear_coolant.tooltip",
                "Clear cached coolant.");
        }
    }

    private void drawStatusPanel(CraftingHostSnapshot state, int mouseX, int mouseY) {
        this.drawScaledText(
            tr("gui.neoecoae.crafting.status", "Status"),
            STATUS_AREA_X + 7,
            TOP_AREA_Y + 7,
            TEXT_SCALE,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawStatusRow(
            tr("gui.neoecoae.crafting.overclock", "OC"),
            state.overclocked,
            TOP_AREA_Y + 27,
            HostUiStyle.DARK_TEXT_SUCCESS);
        this.drawStatusRow(
            tr("gui.neoecoae.crafting.cooling_short", "Cool"),
            state.activeCooling,
            TOP_AREA_Y + 48,
            HostUiStyle.DARK_TEXT_SUCCESS);
        this.drawStatusRow(
            tr("gui.neoecoae.crafting.waste_short", "Waste"),
            state.coolant > 0,
            TOP_AREA_Y + 69,
            HostUiStyle.DARK_TEXT_SUCCESS);
        this.statusTooltip(state, mouseX, mouseY);
    }

    private void drawStatusRow(String label, boolean enabled, int y, int enabledColor) {
        int lightX = STATUS_AREA_X + 7;
        this.drawTinyInsetLocal(lightX, y - 4, 15, 15, 0xFF2B2834);
        drawRect(lightX + 4, y, lightX + 11, y + 7, enabled ? enabledColor : HostUiStyle.DARK_TEXT_ERROR);
        int labelX = lightX + 22;
        int valueRight = STATUS_AREA_X + STATUS_AREA_W - 7;
        String value = tr(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off", enabled ? "On" : "Off");
        int valueW = this.fontRendererObj.getStringWidth(value);
        this.drawScaledFittedText(
            label,
            labelX,
            y,
            Math.max(8, valueRight - labelX - valueW - 4),
            TEXT_SCALE,
            HostUiStyle.DARK_TEXT_MUTED);
        this.drawScaledText(
            value,
            valueRight - valueW * TEXT_SCALE,
            y,
            TEXT_SCALE,
            enabled ? enabledColor : HostUiStyle.DARK_TEXT_ERROR);
    }

    private void statusTooltip(CraftingHostSnapshot state, int mouseX, int mouseY) {
        if (this.isMouseIn(STATUS_AREA_X + 7, TOP_AREA_Y + 23, STATUS_AREA_W - 14, 15, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<String>();
            this.hoveredLines.add(
                EnumChatFormatting.AQUA + tr(
                    state.overclocked ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off",
                    state.overclocked ? "Overclock: On" : "Overclock: Off"));
            this.addTranslatedLines(
                this.hoveredLines,
                "gui.neoecoae.crafting.overclocked.tooltip",
                "Boost performance.");
        } else if (this.isMouseIn(STATUS_AREA_X + 7, TOP_AREA_Y + 44, STATUS_AREA_W - 14, 15, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<String>();
            this.hoveredLines.add(
                EnumChatFormatting.AQUA + tr(
                    state.activeCooling ? "gui.neoecoae.crafting.active_cooling.on"
                        : "gui.neoecoae.crafting.active_cooling.off",
                    state.activeCooling ? "Active Cooling: On" : "Active Cooling: Off"));
            this.addTranslatedLines(this.hoveredLines, "gui.neoecoae.crafting.active_cooling.tooltip", "Use coolant.");
        } else if (this.isMouseIn(STATUS_AREA_X + 7, TOP_AREA_Y + 65, STATUS_AREA_W - 14, 15, mouseX, mouseY)) {
            this.hoveredLines = this.coolantTooltip(state);
        }
    }

    private void drawModulePreview(CraftingHostSnapshot state, int mouseX, int mouseY) {
        String title = tr("gui.neoecoae.crafting.module_preview", "Structure Preview");
        String counts = "FT " + this.formatNumber(state.parallelCoreCount)
            + "   FX "
            + this.formatNumber(state.workerCount);
        int countWidth = Math.round(this.fontRendererObj.getStringWidth(counts) * MODULE_TEXT_SCALE);
        this.drawScaledFittedText(
            title,
            MODULE_AREA_X + 7,
            MODULE_AREA_Y + 7,
            MODULE_AREA_W - 14 - countWidth - 5,
            MODULE_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawScaledText(
            counts,
            MODULE_AREA_X + MODULE_AREA_W - 7 - countWidth,
            MODULE_AREA_Y + 7,
            MODULE_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_VALUE);
        this.drawModuleGrid(state);
        this.drawModuleStats(state);
        if (this.isMouseIn(MODULE_AREA_X + 7, MODULE_AREA_Y + 7, MODULE_AREA_W - 14, 11, mouseX, mouseY)
            || this.isMouseIn(MODULE_GRID_X, MODULE_GRID_Y, MODULE_GRID_W, MODULE_GRID_H, mouseX, mouseY)
            || this.isMouseIn(MODULE_AREA_X + 7, MODULE_STATS_Y - 1, MODULE_AREA_W - 14, 22, mouseX, mouseY)) {
            this.hoveredLines = this.moduleTooltip(state);
        }
    }

    private void drawModuleGrid(CraftingHostSnapshot state) {
        int columns = Math.max(1, state.workerCount);
        int cell = Math
            .max(6, Math.min(MODULE_CELL_MAX, Math.min(MODULE_GRID_W / Math.max(1, columns), MODULE_GRID_H / 3)));
        int contentW = columns * cell;
        int startX = MODULE_GRID_X + Math.max(0, (MODULE_GRID_W - contentW) / 2);
        int startY = MODULE_GRID_Y + Math.max(0, (MODULE_GRID_H - cell * 3) / 2);
        int maxColumns = Math.max(1, MODULE_GRID_W / Math.max(1, cell));
        int visibleColumns = Math.min(columns, maxColumns);
        for (int column = 0; column < visibleColumns; column++) {
            int x = startX + column * cell;
            this.drawModuleCell(x, startY, cell, column * 2 < state.parallelCoreCount, false, false, state);
            this.drawModuleCell(
                x,
                startY + cell,
                cell,
                column < state.workerCount,
                true,
                this.workerActive(state, column),
                state);
            this.drawModuleCell(
                x,
                startY + cell * 2,
                cell,
                column * 2 + 1 < state.parallelCoreCount,
                false,
                false,
                state);
        }
        if (columns > visibleColumns) {
            this.drawLocalRight(
                "+" + (columns - visibleColumns),
                MODULE_AREA_X + MODULE_AREA_W - 7,
                MODULE_GRID_Y + 13,
                HostUiStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawModuleCell(int x, int y, int size, boolean active, boolean worker, boolean busy,
        CraftingHostSnapshot state) {
        this.drawTinyInsetLocal(x, y, size, size, 0xFF1B1822);
        int pad = size >= 10 ? 2 : 1;
        int inner = Math.max(1, size - pad * 2);
        int color;
        if (!active) {
            color = 0x66000000;
        } else if (worker) {
            color = busy ? HostUiStyle.DARK_TEXT_SUCCESS : HostUiStyle.DARK_TEXT_BLUE;
        } else {
            color = HostUiStyle.tierColor(state.tier);
        }
        drawRect(x + pad, y + pad, x + pad + inner, y + pad + inner, color);
        if (active) {
            drawRect(x + pad, y + pad, x + pad + inner, y + pad + 1, 0x70FFFFFF);
        }
    }

    private boolean workerActive(CraftingHostSnapshot state, int index) {
        return index >= 0 && index < state.workerEntries.size() && state.workerEntries.get(index).queueSize > 0;
    }

    private void drawModuleStats(CraftingHostSnapshot state) {
        String tasks = tr("gui.neoecoae.crafting.recipe_slots", "Task Slots") + " "
            + this.formatNumber(state.runningWorkerCount)
            + " / "
            + this.formatNumber(state.workerCount);
        String free = tr("gui.neoecoae.crafting.batch_parallel", "Free Parallel") + " "
            + this.formatNumber(Math.max(0, state.workerCount - state.runningWorkerCount));
        int freeWidth = Math.round(this.fontRendererObj.getStringWidth(free) * MODULE_TEXT_SCALE);
        this.drawScaledFittedText(
            tasks,
            MODULE_AREA_X + 7,
            MODULE_STATS_Y + 4,
            MODULE_AREA_W - 14 - freeWidth - 5,
            MODULE_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_MUTED);
        this.drawScaledText(
            free,
            MODULE_AREA_X + MODULE_AREA_W - 7 - freeWidth,
            MODULE_STATS_Y + 4,
            MODULE_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_VALUE);
        int progressX = MODULE_AREA_X + 7;
        int progressW = MODULE_AREA_W - 14;
        drawRect(
            progressX,
            MODULE_PROGRESS_Y,
            progressX + progressW,
            MODULE_PROGRESS_Y + MODULE_PROGRESS_H,
            0xAA17141E);
        int fill = this.ratioWidth(state.runningWorkerCount, state.workerCount, progressW);
        if (fill > 0) {
            drawRect(
                progressX,
                MODULE_PROGRESS_Y,
                progressX + fill,
                MODULE_PROGRESS_Y + MODULE_PROGRESS_H,
                HostUiStyle.DARK_TEXT_SUCCESS);
            drawRect(progressX, MODULE_PROGRESS_Y, progressX + fill, MODULE_PROGRESS_Y + 1, 0x70FFFFFF);
        }
    }

    private void drawGaugePanel(CraftingHostSnapshot state, int mouseX, int mouseY) {
        this.drawScaledFittedText(
            tr("gui.neoecoae.crafting.energy_cooling", "Energy/Cooling"),
            GAUGE_AREA_X + 7,
            TOP_AREA_Y + 7,
            GAUGE_AREA_W - 14,
            TEXT_SCALE,
            HostUiStyle.DARK_TEXT_PRIMARY);
        int pairW = GAUGE_BAR_W * 2 + GAUGE_GAP;
        int energyX = GAUGE_AREA_X + (GAUGE_AREA_W - pairW) / 2;
        int coolantX = energyX + GAUGE_BAR_W + GAUGE_GAP;
        this.drawVerticalGaugeLocal(
            energyX,
            GAUGE_BAR_Y,
            GAUGE_BAR_W,
            GAUGE_BAR_H,
            state.maxEnergyUsage,
            Math.max(1, state.energyGaugeReference),
            energyColor(state));
        this.drawVerticalGaugeLocal(
            coolantX,
            GAUGE_BAR_Y,
            GAUGE_BAR_W,
            GAUGE_BAR_H,
            state.coolant,
            Math.max(1, state.maxCoolant),
            HostUiStyle.DARK_TEXT_BLUE);
        this.drawScaledCenteredText(
            tr("gui.neoecoae.crafting.energy_short", "AE"),
            energyX - 7,
            GAUGE_BAR_Y + GAUGE_BAR_H + 2,
            GAUGE_BAR_W + 14,
            TEXT_SCALE,
            HostUiStyle.DARK_TEXT_MUTED);
        this.drawScaledCenteredText(
            tr("gui.neoecoae.crafting.cooling_short", "Cool"),
            coolantX - 7,
            GAUGE_BAR_Y + GAUGE_BAR_H + 2,
            GAUGE_BAR_W + 14,
            TEXT_SCALE,
            HostUiStyle.DARK_TEXT_MUTED);
        int hoverTop = GAUGE_BAR_Y;
        int hoverBottom = GAUGE_BAR_Y + GAUGE_BAR_H + GAUGE_HOVER_BOTTOM_PADDING;
        int energyHoverLeft = energyX - 7;
        int energyHoverRight = energyX + GAUGE_BAR_W + 7;
        int coolantHoverLeft = coolantX - 7;
        int coolantHoverRight = coolantX + GAUGE_BAR_W + 7;
        if (this.isMouseIn(
            energyHoverLeft,
            hoverTop,
            energyHoverRight - energyHoverLeft,
            hoverBottom - hoverTop,
            mouseX,
            mouseY)) {
            this.hoveredLines = this.energyTooltip(state);
        } else if (this.isMouseIn(
            coolantHoverLeft,
            hoverTop,
            coolantHoverRight - coolantHoverLeft,
            hoverBottom - hoverTop,
            mouseX,
            mouseY)) {
            this.hoveredLines = this.coolantTooltip(state);
        }
    }

    private int energyColor(CraftingHostSnapshot state) {
        if (state.maxEnergyUsage >= state.energyGaugeReference * 9L / 10L) {
            return HostUiStyle.DARK_TEXT_ERROR;
        }
        if (state.maxEnergyUsage >= state.energyGaugeReference / 2L) {
            return HostUiStyle.DARK_TEXT_WARNING;
        }
        return HostUiStyle.DARK_TEXT_SUCCESS;
    }

    private void drawTaskPanel(CraftingHostSnapshot state, int mouseX, int mouseY) {
        this.drawLocalText(
            tr("gui.neoecoae.crafting.tasks", "Crafting Tasks"),
            TASK_PANEL_X + 8,
            TASK_PANEL_Y + 6,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalRight(
            this.formatNumber(state.queuedWorkCount),
            TASK_PANEL_X + TASK_PANEL_W - 8,
            TASK_PANEL_Y + 6,
            HostUiStyle.DARK_TEXT_VALUE);
        List<CraftingHostSnapshot.WorkerEntry> active = activeWorkers(state);
        this.updateTaskScroll(Math.min(this.taskScrollPixels, this.maxTaskScroll(active)));
        if (active.isEmpty()) {
            this.drawLocalCentered(
                tr("gui.neoecoae.crafting.no_tasks", "No tasks"),
                TASK_PANEL_X,
                TASK_PANEL_Y + TASK_PANEL_H / 2 - 4,
                TASK_PANEL_W,
                HostUiStyle.DARK_TEXT_MUTED);
            return;
        }
        int firstIndex = Math.max(0, this.taskScrollPixels / TASK_CARD_STEP);
        int offset = this.taskScrollPixels % TASK_CARD_STEP;
        int visible = Math.min(active.size() - firstIndex, TASK_VISIBLE_CARDS + (offset > 0 ? 1 : 0));
        this.beginScissor(TASK_LIST_X, TASK_LIST_Y, TASK_LIST_W, TASK_LIST_H);
        for (int i = 0; i < visible; i++) {
            this.drawWorkerCard(
                active.get(firstIndex + i),
                TASK_CARD_X,
                TASK_CARD_Y + i * TASK_CARD_STEP - offset,
                mouseX,
                mouseY);
        }
        this.endScissor();
        if (active.size() > TASK_VISIBLE_CARDS) {
            this.drawTaskScrollbar(active.size());
        }
    }

    private void drawTaskScrollbar(int activeCount) {
        int trackX = TASK_PANEL_X + TASK_PANEL_W - 5;
        int trackY = TASK_LIST_Y;
        int trackH = TASK_LIST_H;
        drawRect(trackX, trackY, trackX + 2, trackY + trackH, 0xAA17141E);
        int contentHeight = activeCount * TASK_CARD_STEP - 1;
        int thumbH = Math.max(8, trackH * trackH / Math.max(trackH, contentHeight));
        int maxScroll = Math.max(1, contentHeight - trackH);
        int thumbY = trackY + (trackH - thumbH) * this.taskScrollPixels / maxScroll;
        drawRect(trackX, thumbY, trackX + 2, thumbY + thumbH, HostUiStyle.DARK_TEXT_VALUE);
    }

    private void drawWorkerCard(CraftingHostSnapshot.WorkerEntry worker, int x, int y, int mouseX, int mouseY) {
        boolean hovered = this.isMouseIn(x, y, TASK_CARD_W, TASK_CARD_H, mouseX, mouseY)
            && this.isMouseIn(TASK_LIST_X, TASK_LIST_Y, TASK_LIST_W, TASK_LIST_H, mouseX, mouseY);
        this.drawTinyInsetLocal(x, y, TASK_CARD_W, TASK_CARD_H, hovered ? 0xFF2A2535 : 0xFF201E27);
        this.drawScaledItemIcon(worker.outputStack, x + 3, y + 1, CARD_ICON_SCALE);
        int textX = x + 17;
        this.drawScaledFittedText(
            worker.outputName.isEmpty() ? tr("gui.neoecoae.crafting.task.status.queued", "Queued") : worker.outputName,
            textX,
            y + 2,
            TASK_CARD_W - 52,
            CARD_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawScaledText(
            this.formatNumber(worker.queueSize) + "/" + this.formatNumber(worker.queueCapacity),
            x + TASK_CARD_W - 33,
            y + 2,
            CARD_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_VALUE);
        int barX = textX;
        int barY = y + TASK_CARD_H - 4;
        int barW = TASK_CARD_W - 24;
        drawRect(barX, barY, barX + barW, barY + 2, 0xAA17141E);
        int fill = this.ratioWidth(worker.progress, worker.totalProgress, barW);
        if (fill > 0) {
            drawRect(barX, barY, barX + fill, barY + 2, HostUiStyle.DARK_TEXT_SUCCESS);
        }
        if (hovered) {
            this.hoveredLines = this.workerTooltip(worker);
        }
    }

    private List<CraftingHostSnapshot.WorkerEntry> activeWorkers(CraftingHostSnapshot state) {
        List<CraftingHostSnapshot.WorkerEntry> active = new ArrayList<CraftingHostSnapshot.WorkerEntry>();
        for (CraftingHostSnapshot.WorkerEntry worker : state.workerEntries) {
            if (worker.queueSize > 0) {
                active.add(worker);
            }
        }
        return active;
    }

    private int maxTaskScroll(CraftingHostSnapshot state) {
        return this.maxTaskScroll(activeWorkers(state));
    }

    private int maxTaskScroll(List<CraftingHostSnapshot.WorkerEntry> active) {
        return Math.max(0, active.size() * TASK_CARD_STEP - 1 - TASK_LIST_H);
    }

    private void updateTaskScroll(int nextScroll) {
        this.taskScrollPixels = Math.max(0, Math.min(nextScroll, this.maxTaskScroll(this.container.state())));
    }

    private void drawVerticalGaugeLocal(int x, int y, int width, int height, long value, long max, int color) {
        this.drawTinyInsetLocal(x, y, width, height, 0xFF201E27);
        int filled = this.ratioWidth(value, max, height - 4);
        if (filled > 0) {
            int bottom = y + height - 2;
            drawRect(x + 2, bottom - filled, x + width - 2, bottom, color);
            drawRect(x + 2, bottom - filled, x + width - 2, Math.min(bottom, bottom - filled + 1), 0x70FFFFFF);
        }
    }

    private List<String> moduleTooltip(CraftingHostSnapshot state) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.module_preview", "Structure Preview"));
        lines.add(tr("gui.neoecoae.crafting_ui.workers", "Workers") + ": " + this.formatNumber(state.workerCount));
        lines.add(
            tr("gui.neoecoae.crafting_ui.parallel_cores", "Parallel Cores") + ": "
                + this.formatNumber(state.parallelCoreCount));
        lines.add(tr("gui.neoecoae.crafting_ui.parallel", "Parallel") + ": " + this.formatNumber(state.parallelCount));
        lines.add(
            tr("gui.neoecoae.crafting.recipe_slots", "Task Slots") + ": "
                + this.formatNumber(state.runningWorkerCount)
                + " / "
                + this.formatNumber(state.workerCount));
        lines.add(
            tr("gui.neoecoae.crafting_ui.queued_work", "Queued Work") + ": "
                + this.formatNumber(state.queuedWorkCount)
                + " / "
                + this.formatNumber(state.workQueueCapacity));
        return lines;
    }

    private List<String> energyTooltip(CraftingHostSnapshot state) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.energy_usage", "Energy Usage"));
        lines.add(
            this.formatNumber(state.maxEnergyUsage)
                + " / "
                + this.formatNumber(Math.max(1L, state.energyGaugeReference))
                + " AE/t");
        lines.add(this.percentText(state.maxEnergyUsage, Math.max(1L, state.energyGaugeReference)));
        return lines;
    }

    private List<String> coolantTooltip(CraftingHostSnapshot state) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.coolant", "Coolant"));
        lines.add(
            String.format(
                java.util.Locale.US,
                tr("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s"),
                this.formatNumber(state.coolant),
                this.formatNumber(state.maxCoolant)));
        lines.add(this.percentText(state.coolant, state.maxCoolant));
        return lines;
    }

    private List<String> workerTooltip(CraftingHostSnapshot.WorkerEntry worker) {
        List<String> lines = new ArrayList<String>();
        if (worker.outputStack != null) {
            @SuppressWarnings("unchecked")
            List<String> itemLines = worker.outputStack
                .getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
            lines.addAll(itemLines);
        } else {
            lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.task.status.queued", "Queued"));
        }
        lines.add(
            EnumChatFormatting.GRAY + tr("gui.neoecoae.crafting.task.status.running", "Running")
                + " #"
                + (worker.index + 1));
        lines.add(
            EnumChatFormatting.GRAY + tr("gui.neoecoae.crafting.worker_queue", "Queue")
                + ": "
                + this.formatNumber(worker.queueSize)
                + " / "
                + this.formatNumber(worker.queueCapacity));
        lines.add(
            EnumChatFormatting.GRAY + tr("gui.neoecoae.crafting.worker_progress", "Progress")
                + ": "
                + this.percentText(worker.progress, worker.totalProgress));
        return lines;
    }

    private void drawScaledText(String text, float x, float y, float scale, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 200.0F);
        GL11.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void drawScaledFittedText(String text, int x, int y, int maxWidth, float scale, int color) {
        int unscaledWidth = Math.max(0, Math.round(maxWidth / Math.max(0.01F, scale)));
        this.drawScaledText(this.fit(text, unscaledWidth), x, y, scale, color);
    }

    private void drawScaledCenteredText(String text, int x, int y, int width, float scale, int color) {
        int textWidth = this.fontRendererObj.getStringWidth(text);
        this.drawScaledText(text, x + (width - textWidth * scale) / 2.0F, y, scale, color);
    }

    private void drawScaledItemIcon(ItemStack stack, int x, int y, float scale) {
        if (stack == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 220.0F);
        GL11.glScalef(scale, scale, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        this.drawLocalItemIcon(stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glPopMatrix();
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (this.fontRendererObj.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.fontRendererObj.getStringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && this.fontRendererObj.getStringWidth(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private int ratioWidth(long used, long total, int width) {
        if (width <= 0 || total <= 0L || used <= 0L) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(used, total));
        return (int) Math.max(1L, Math.min(width, clamped * width / total));
    }

    private String percentText(long value, long max) {
        if (max <= 0L || value <= 0L) {
            return "0%";
        }
        long percent = Math.max(0L, Math.min(100L, value * 100L / max));
        return percent + "%";
    }

    private void addTranslatedLines(List<String> lines, String key, String fallback) {
        String translated = tr(key, fallback);
        String[] split = translated.split("\\\\n|\\n");
        for (int i = 0; i < split.length; i++) {
            lines.add(EnumChatFormatting.GRAY + split[i]);
        }
    }

    private static String tr(String key, String fallback) {
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }

    private static final class InvisibleButton extends GuiButton {

        private InvisibleButton(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height, "");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {}
    }
}
