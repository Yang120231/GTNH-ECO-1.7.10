package cn.dancingsnow.neoecoae.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import net.minecraftforge.fluids.Fluid;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.gui.container.ContainerECOCraftingController;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipe;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipes;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerLayout;
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
    private static final int EDGE = 6;
    private static final int GAP = 6;
    private static final int HEADER_X = 6;
    private static final int HEADER_Y = 9;
    private static final int TOP_AREA_Y = 27;
    private static final int TOP_AREA_H = 70;
    private static final int STATUS_AREA_X = EDGE;
    private static final int STATUS_AREA_W = 76;
    private static final int MODULE_AREA_X = STATUS_AREA_X + STATUS_AREA_W + GAP;
    private static final int MODULE_AREA_Y = TOP_AREA_Y;
    private static final int MODULE_AREA_W = 114;
    private static final int GAUGE_AREA_X = MODULE_AREA_X + MODULE_AREA_W + GAP;
    private static final int GAUGE_AREA_W = 96;
    private static final int TASK_PANEL_X = 176;
    private static final int TASK_PANEL_Y = 102;
    private static final int TASK_PANEL_W = 122;
    private static final int TASK_PANEL_H = 88;
    private static final int MODULE_GRID_X = MODULE_AREA_X + 7;
    private static final int MODULE_GRID_Y = MODULE_AREA_Y + 31;
    private static final int MODULE_GRID_W = MODULE_AREA_W - 14;
    private static final int MODULE_GRID_H = 35;
    private static final int MODULE_STATS_Y = MODULE_AREA_Y + TOP_AREA_H - 23;
    private static final int MODULE_PROGRESS_Y = MODULE_AREA_Y + TOP_AREA_H - 10;
    private static final int MODULE_PROGRESS_H = 4;
    private static final int MODULE_CELL_MAX = 12;
    private static final int GAUGE_BAR_Y = TOP_AREA_Y + 26;
    private static final int GAUGE_BAR_H = 32;
    private static final int GAUGE_BAR_W = 20;
    private static final int COOLANT_GAUGE_W = 23;
    private static final int GAUGE_GAP = 14;
    private static final int TOOLBAR_SIZE = 16;
    private static final int TOOLBAR_GAP = 4;
    private static final int TOOLBAR_Y = 4;
    private static final int TOOLBAR_COOLING_X = CraftingControllerLayout.WIDTH - EDGE - TOOLBAR_SIZE;
    private static final int TOOLBAR_OVERCLOCK_X = TOOLBAR_COOLING_X - TOOLBAR_GAP - TOOLBAR_SIZE;
    private static final int TASK_CARD_X = TASK_PANEL_X + 8;
    private static final int TASK_CARD_Y = TASK_PANEL_Y + 19;
    private static final int TASK_CARD_W = TASK_PANEL_W - 16;
    private static final int TASK_CARD_H = 16;
    private static final int TASK_CARD_STEP = 18;
    private static final int TASK_LIST_X = TASK_CARD_X;
    private static final int TASK_LIST_Y = TASK_CARD_Y;
    private static final int TASK_LIST_W = TASK_PANEL_W - 16;
    private static final int TASK_LIST_H = TASK_PANEL_H - 23;
    private static final int TASK_VISIBLE_CARDS = 3;
    private static final int TASK_SCROLL_STEP = 18;
    private static final float TEXT_SCALE = 0.95F;
    private static final float MODULE_TEXT_SCALE = 0.82F;
    private static final float CARD_TEXT_SCALE = 0.72F;
    private static final float CARD_ICON_SCALE = 0.75F;

    private final ContainerECOCraftingController container;
    private final TileECOController controller;
    private GuiButton overclockButton;
    private GuiButton coolingButton;
    private List<String> hoveredLines;
    private int taskScrollPixels;

    public GuiECOCraftingController(InventoryPlayer playerInventory, TileECOController controller) {
        this(new ContainerECOCraftingController(playerInventory, controller));
    }

    private GuiECOCraftingController(ContainerECOCraftingController container) {
        super(container, CraftingControllerLayout.WIDTH, CraftingControllerLayout.HEIGHT);
        this.container = container;
        this.controller = container.getController();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.overclockButton = this.addInvisibleButton(OVERCLOCK_BUTTON_ID, TOOLBAR_OVERCLOCK_X);
        this.coolingButton = this.addInvisibleButton(COOLING_BUTTON_ID, TOOLBAR_COOLING_X);
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
            CraftingControllerLayout.INVENTORY_X,
            CraftingControllerLayout.INVENTORY_Y,
            CraftingControllerLayout.HOTBAR_Y);
        this.drawToolbarButton(TOOLBAR_OVERCLOCK_X, mouseX, mouseY, true, this.container.state().overclocked);
        this.drawToolbarButton(TOOLBAR_COOLING_X, mouseX, mouseY, true, this.container.state().activeCooling);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        CraftingHostSnapshot state = this.container.state();
        this.drawHeader(state);
        this.drawToolbarTextAndTooltips(state, mouseX, mouseY);
        this.drawStatusPanel(state, mouseX, mouseY);
        this.drawStatsPanel(state, mouseX, mouseY);
        this.drawGaugePanel(state, mouseX, mouseY);
        this.drawTaskPanel(state, mouseX, mouseY);
        this.drawLocalText(
            tr("gui.neoecoae.common.inventory", "Inventory"),
            CraftingControllerLayout.INVENTORY_X,
            102,
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
            this.hostBlockTitle("crafting", state.tier, "ECO Crafting Host " + state.tier),
            HEADER_X,
            HEADER_Y,
            HostUiStyle.HOST_TITLE);
        String status = tr("gui.neoecoae.machine.formed", "Formed") + ": " + yesNo(state.formed);
        this.drawLocalRight(
            status,
            TOOLBAR_OVERCLOCK_X - 8,
            HEADER_Y,
            active ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_BAD);
    }

    private void drawToolbarButton(int x, int mouseX, int mouseY, boolean enabled, boolean selected) {
        CraftingHostSnapshot state = this.container.state();
        AEA2ToolbarIconButton.Sprite icon = x == TOOLBAR_OVERCLOCK_X
            ? state.overclocked ? AEA2ToolbarIconButton.LEVEL_ENERGY : AEA2ToolbarIconButton.POWER_UNIT_AE
            : AEA2ToolbarIconButton.TYPE_FILTER_FLUIDS;
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
            TOP_AREA_Y + 24,
            HostUiStyle.DARK_TEXT_SUCCESS);
        this.drawStatusRow(
            tr("gui.neoecoae.crafting.cooling_short", "Cool"),
            state.activeCooling,
            TOP_AREA_Y + 42,
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
        }
    }

    private void drawStatsPanel(CraftingHostSnapshot state, int mouseX, int mouseY) {
        int x = MODULE_AREA_X + 8;
        this.drawScaledText(tr("gui.neoecoae.crafting.ui.stats", "Statistics"), x, TOP_AREA_Y + 7,
            TEXT_SCALE, HostUiStyle.DARK_TEXT_PRIMARY);

        String slots = tr("gui.neoecoae.crafting.recipe_slots", "Recipe Slots") + ": "
            + this.formatNumber(state.occupiedRecipeSlots) + " / " + this.formatNumber(state.maxRecipeSlots);
        this.drawScaledFittedText(slots, x, TOP_AREA_Y + 21, MODULE_AREA_W - 16, TEXT_SCALE,
            HostUiStyle.DARK_TEXT_MUTED);
        int barY = TOP_AREA_Y + 32;
        this.drawThickProgressBarLocal(
            x,
            barY,
            MODULE_AREA_W - 16,
            9,
            state.occupiedRecipeSlots,
            state.maxRecipeSlots,
            HostUiStyle.DARK_TEXT_VALUE);
        this.drawScaledFittedText(tr("gui.neoecoae.crafting.batch_parallel", "Batch Parallel") + ": "
            + this.formatNumber(state.batchParallel), x, TOP_AREA_Y + 45, MODULE_AREA_W - 16, TEXT_SCALE,
            HostUiStyle.DARK_TEXT_MUTED);
        String overflow = tr("gui.neoecoae.host.crafting.overflow", "Overflow") + ": "
            + this.formatNumber(state.overflowThreads);
        this.drawScaledFittedText(overflow + "  " + recipeTimeMultiplier(state.effectiveOverclockTimes), x,
            TOP_AREA_Y + 56, MODULE_AREA_W - 16, TEXT_SCALE, HostUiStyle.DARK_TEXT_MUTED);
        if (this.isMouseIn(MODULE_AREA_X, TOP_AREA_Y, MODULE_AREA_W, TOP_AREA_H, mouseX, mouseY)) {
            this.hoveredLines = this.statsTooltip(state);
        }
    }

    private List<String> statsTooltip(CraftingHostSnapshot state) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.ui.stats", "Statistics"));
        lines.add(tr("gui.neoecoae.crafting.recipe_slots", "Recipe Slots") + ": "
            + this.formatNumber(state.occupiedRecipeSlots) + " / " + this.formatNumber(state.maxRecipeSlots));
        lines.add(tr("gui.neoecoae.crafting.batch_parallel", "Batch Parallel") + ": "
            + this.formatNumber(state.batchParallel));
        lines.add(tr("gui.neoecoae.host.crafting.overflow", "Overflow") + ": "
            + this.formatNumber(state.overflowThreads));
        return lines;
    }

    private static String recipeTimeMultiplier(int effectiveOverclockTimes) {
        int level = Math.max(0, Math.min(9, effectiveOverclockTimes));
        int ticks = (int) Math.ceil(10.0D / (level + 1));
        return String.format(java.util.Locale.US, "%.1fx", ticks / 10.0D);
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
        int pairW = GAUGE_BAR_W + COOLANT_GAUGE_W + GAUGE_GAP;
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
        this.drawCoolantGauge(state, coolantX);
        int hoverTop = GAUGE_BAR_Y;
        int hoverBottom = GAUGE_BAR_Y + GAUGE_BAR_H;
        int energyHoverLeft = energyX;
        int energyHoverRight = energyX + GAUGE_BAR_W;
        int coolantHoverLeft = coolantX;
        int coolantHoverRight = coolantX + COOLANT_GAUGE_W;
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
        this.drawGaugeFrame(x, y, width, height);
        int filled = this.ratioWidth(value, max, height - 8);
        if (filled > 0) {
            int bottom = y + height - 4;
            drawRect(x + 4, bottom - filled, x + width - 4, bottom, color);
            drawRect(x + 4, bottom - filled, x + width - 4, Math.min(bottom, bottom - filled + 2), 0x70FFFFFF);
        }
    }

    private void drawCoolantGauge(CraftingHostSnapshot state, int x) {
        this.drawGaugeFrame(x, GAUGE_BAR_Y, COOLANT_GAUGE_W, GAUGE_BAR_H);
        int innerWidth = COOLANT_GAUGE_W - 8;
        int innerHeight = GAUGE_BAR_H - 8;
        int filled = this.ratioWidth(state.coolant, Math.max(1, state.maxCoolant), innerHeight);
        if (filled <= 0) {
            return;
        }
        int fillY = GAUGE_BAR_Y + 4 + innerHeight - filled;
        Fluid fluid = this.coolantFluid(state);
        IIcon icon = fluid == null ? null : fluid.getStillIcon();
        drawRect(x + 4, fillY, x + 4 + innerWidth, GAUGE_BAR_Y + 4 + innerHeight,
            HostUiStyle.DARK_TEXT_BLUE);
        if (icon != null) {
            this.drawFluidFill(x + 4, fillY, innerWidth, filled, icon);
        }
    }

    private void drawGaugeFrame(int x, int y, int width, int height) {
        drawRect(x, y, x + width, y + height, HostUiStyle.DARK_PANEL_LIGHT_EDGE);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, HostUiStyle.DARK_PANEL_OUTER);
        drawRect(x + 2, y + 2, x + width - 2, y + height - 2, HostUiStyle.DARK_PANEL_MIDDLE);
        drawRect(x + 3, y + 3, x + width - 3, y + height - 3, HostUiStyle.DARK_PANEL_OUTER);
        drawRect(x + 4, y + 4, x + width - 4, y + height - 4, 0xFF201E27);
    }

    private Fluid coolantFluid(CraftingHostSnapshot state) {
        if (state.coolant <= 0) {
            return null;
        }
        for (ECOCoolingRecipe recipe : ECOCoolingRecipes.all()) {
            if (recipe.getMaxOverclock() == state.coolantMaxOverclock) {
                return recipe.getInputFluid();
            }
        }
        return null;
    }

    private void drawFluidFill(int x, int y, int width, int height, IIcon icon) {
        this.mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        for (int offsetY = 0; offsetY < height; offsetY += 16) {
            int partHeight = Math.min(16, height - offsetY);
            this.drawFluidIconPart(x, y + offsetY, width, partHeight, icon);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawFluidIconPart(int x, int y, int width, int height, IIcon icon) {
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
            this.formatNumber(state.maxEnergyUsage) + " / "
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
        lines.add(state.coolantMaxOverclock <= 0
            ? tr("gui.neoecoae.crafting.coolant_max_overclock.none", "Current Coolant Max Overclock: None")
            : translate("gui.neoecoae.crafting.coolant_max_overclock", Integer.valueOf(state.coolantMaxOverclock)));
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
