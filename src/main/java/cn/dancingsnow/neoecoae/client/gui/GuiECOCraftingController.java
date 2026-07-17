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

import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipe;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipes;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOCraftingController;
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
        this.drawToolbarButton(
            TOOLBAR_OVERCLOCK_X,
            mouseX,
            mouseY,
            this.container.state().overclocked,
            AEA2ToolbarIconButton.POWER_UNIT_AE);
        this.drawToolbarButton(
            TOOLBAR_COOLING_X,
            mouseX,
            mouseY,
            this.container.state().activeCooling,
            AEA2ToolbarIconButton.TYPE_FILTER_ALL);
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

    private void drawToolbarButton(int x, int mouseX, int mouseY, boolean selected, AEA2ToolbarIconButton.Sprite icon) {
        AEA2ToolbarIconButton.draw(this, x, TOOLBAR_Y, mouseX, mouseY, TOOLBAR_SIZE, icon, selected);
    }

    private void drawToolbarTextAndTooltips(CraftingHostSnapshot state, int mouseX, int mouseY) {
        if (this.isMouseIn(TOOLBAR_OVERCLOCK_X, TOOLBAR_Y, TOOLBAR_SIZE, TOOLBAR_SIZE, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<>();
            this.hoveredLines.add(
                EnumChatFormatting.AQUA + tr(
                    state.overclocked ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off",
                    state.overclocked ? "Overclock: On" : "Overclock: Off"));
            this.addTranslatedLines(
                this.hoveredLines,
                "gui.neoecoae.crafting.overclocked.tooltip",
                "Boost performance.");
        } else if (this.isMouseIn(TOOLBAR_COOLING_X, TOOLBAR_Y, TOOLBAR_SIZE, TOOLBAR_SIZE, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<>();
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
        this.drawStatusRow(tr("gui.neoecoae.crafting.overclock", "OC"), state.overclocked, TOP_AREA_Y + 24);
        this.drawStatusRow(tr("gui.neoecoae.crafting.cooling_short", "Cool"), state.activeCooling, TOP_AREA_Y + 42);
        this.statusTooltip(state, mouseX, mouseY);
    }

    private void drawStatusRow(String label, boolean enabled, int y) {
        int lightX = STATUS_AREA_X + 7;
        this.drawTinyInsetLocal(lightX, y - 4, 15, 15, 0xFF2B2834);
        drawRect(
            lightX + 4,
            y,
            lightX + 11,
            y + 7,
            enabled ? HostUiStyle.DARK_TEXT_SUCCESS : HostUiStyle.DARK_TEXT_ERROR);
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
            enabled ? HostUiStyle.DARK_TEXT_SUCCESS : HostUiStyle.DARK_TEXT_ERROR);
    }

    private void statusTooltip(CraftingHostSnapshot state, int mouseX, int mouseY) {
        if (this.isMouseIn(STATUS_AREA_X + 7, TOP_AREA_Y + 23, STATUS_AREA_W - 14, 15, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<>();
            this.hoveredLines.add(
                EnumChatFormatting.AQUA + tr(
                    state.overclocked ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off",
                    state.overclocked ? "Overclock: On" : "Overclock: Off"));
            this.addTranslatedLines(
                this.hoveredLines,
                "gui.neoecoae.crafting.overclocked.tooltip",
                "Boost performance.");
        } else if (this.isMouseIn(STATUS_AREA_X + 7, TOP_AREA_Y + 44, STATUS_AREA_W - 14, 15, mouseX, mouseY)) {
            this.hoveredLines = new ArrayList<>();
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
        this.drawScaledText(
            tr("gui.neoecoae.crafting.ui.stats", "Statistics"),
            x,
            TOP_AREA_Y + 7,
            TEXT_SCALE,
            HostUiStyle.DARK_TEXT_PRIMARY);

        this.drawScaledSegments(
            x,
            TOP_AREA_Y + 21,
            new TextSegment(
                tr("gui.neoecoae.crafting.recipe_slots", "Recipe Slots") + ": ",
                HostUiStyle.DARK_TEXT_MUTED),
            new TextSegment(this.formatNumber(state.occupiedRecipeSlots), HostUiStyle.DARK_TEXT_USED),
            new TextSegment(" / ", HostUiStyle.DARK_TEXT_MUTED),
            new TextSegment(this.formatNumber(state.maxRecipeSlots), HostUiStyle.DARK_TEXT_VALUE));
        int barY = TOP_AREA_Y + 32;
        this.drawThickProgressBarLocal(
            x,
            barY,
            MODULE_AREA_W - 16,
            9,
            state.occupiedRecipeSlots,
            state.maxRecipeSlots,
            HostUiStyle.DARK_TEXT_VALUE);
        this.drawScaledSegments(
            x,
            TOP_AREA_Y + 45,
            new TextSegment(
                tr("gui.neoecoae.crafting.batch_parallel", "Batch Parallel") + ": ",
                HostUiStyle.DARK_TEXT_MUTED),
            new TextSegment(this.formatNumber(state.batchParallel), HostUiStyle.DARK_TEXT_BLUE));
        this.drawScaledSegments(
            x,
            TOP_AREA_Y + 56,
            new TextSegment(tr("gui.neoecoae.host.crafting.overflow", "Overflow") + ": ", HostUiStyle.DARK_TEXT_MUTED),
            new TextSegment(this.formatNumber(state.overflowThreads), HostUiStyle.DARK_TEXT_ORANGE),
            new TextSegment("  ", HostUiStyle.DARK_TEXT_MUTED),
            new TextSegment(recipeTimeMultiplier(state.effectiveOverclockTimes), HostUiStyle.DARK_TEXT_WARNING));
        if (this.isMouseIn(MODULE_AREA_X, TOP_AREA_Y, MODULE_AREA_W, TOP_AREA_H, mouseX, mouseY)) {
            this.hoveredLines = this.statsTooltip(state);
        }
    }

    private List<String> statsTooltip(CraftingHostSnapshot state) {
        List<String> lines = new ArrayList<>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.ui.stats", "Statistics"));
        lines.add(
            tr("gui.neoecoae.crafting.recipe_slots", "Recipe Slots") + ": "
                + this.formatNumber(state.occupiedRecipeSlots)
                + " / "
                + this.formatNumber(state.maxRecipeSlots));
        lines.add(
            tr("gui.neoecoae.crafting.batch_parallel", "Batch Parallel") + ": "
                + this.formatNumber(state.batchParallel));
        lines.add(
            tr("gui.neoecoae.host.crafting.overflow", "Overflow") + ": " + this.formatNumber(state.overflowThreads));
        return lines;
    }

    private static String recipeTimeMultiplier(int effectiveOverclockTimes) {
        int level = Math.max(0, Math.min(9, effectiveOverclockTimes));
        int ticks = (int) Math.ceil(10.0D / (level + 1));
        return String.format(java.util.Locale.US, "%.1fx", ticks / 10.0D);
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
            state.maxEnergyUsage,
            Math.max(1, state.energyGaugeReference),
            energyColor(state));
        this.drawCoolantGauge(state, coolantX);
        if (this.isMouseIn(energyX, GAUGE_BAR_Y, GAUGE_BAR_W, GAUGE_BAR_H, mouseX, mouseY)) {
            this.hoveredLines = this.energyTooltip(state);
        } else if (this.isMouseIn(coolantX, GAUGE_BAR_Y, COOLANT_GAUGE_W, GAUGE_BAR_H, mouseX, mouseY)) {
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
            this.drawWorkerCard(active.get(firstIndex + i), TASK_CARD_Y + i * TASK_CARD_STEP - offset, mouseX, mouseY);
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

    private void drawWorkerCard(CraftingHostSnapshot.WorkerEntry worker, int y, int mouseX, int mouseY) {
        boolean hovered = this.isMouseIn(TASK_CARD_X, y, TASK_CARD_W, TASK_CARD_H, mouseX, mouseY)
            && this.isMouseIn(TASK_LIST_X, TASK_LIST_Y, TASK_LIST_W, TASK_LIST_H, mouseX, mouseY);
        this.drawTinyInsetLocal(TASK_CARD_X, y, TASK_CARD_W, TASK_CARD_H, hovered ? 0xFF2A2535 : 0xFF201E27);
        this.drawScaledItemIcon(worker.outputStack, y + 1);
        int textX = TASK_CARD_X + 17;
        this.drawScaledFittedText(
            worker.outputName.isEmpty() ? tr("gui.neoecoae.crafting.task.status.queued", "Queued") : worker.outputName,
            textX,
            y + 2,
            TASK_CARD_W - 52,
            CARD_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawScaledText(
            this.formatNumber(worker.queueSize) + "/" + this.formatNumber(worker.queueCapacity),
            TASK_CARD_X + TASK_CARD_W - 33,
            y + 2,
            CARD_TEXT_SCALE,
            HostUiStyle.DARK_TEXT_VALUE);
        int barY = y + TASK_CARD_H - 4;
        int barW = TASK_CARD_W - 24;
        drawRect(textX, barY, textX + barW, barY + 2, 0xAA17141E);
        int fill = this.ratioLength(worker.progress, worker.totalProgress, barW);
        if (fill > 0) {
            drawRect(textX, barY, textX + fill, barY + 2, HostUiStyle.DARK_TEXT_SUCCESS);
        }
        if (hovered) {
            this.hoveredLines = this.workerTooltip(worker);
        }
    }

    private List<CraftingHostSnapshot.WorkerEntry> activeWorkers(CraftingHostSnapshot state) {
        List<CraftingHostSnapshot.WorkerEntry> active = new ArrayList<>();
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

    private void drawVerticalGaugeLocal(int x, long value, long max, int color) {
        this.drawGaugeFrame(x, GAUGE_BAR_W);
        int filled = this.ratioLength(value, max, GAUGE_BAR_H - 8);
        if (filled > 0) {
            int bottom = GAUGE_BAR_Y + GAUGE_BAR_H - 4;
            drawRect(x + 4, bottom - filled, x + GAUGE_BAR_W - 4, bottom, color);
            drawRect(x + 4, bottom - filled, x + GAUGE_BAR_W - 4, Math.min(bottom, bottom - filled + 2), 0x70FFFFFF);
        }
    }

    private void drawCoolantGauge(CraftingHostSnapshot state, int x) {
        this.drawGaugeFrame(x, COOLANT_GAUGE_W);
        int innerWidth = COOLANT_GAUGE_W - 8;
        int innerHeight = GAUGE_BAR_H - 8;
        int filled = this.ratioLength(state.coolant, Math.max(1, state.maxCoolant), innerHeight);
        if (filled <= 0) {
            return;
        }
        int fillY = GAUGE_BAR_Y + 4 + innerHeight - filled;
        Fluid fluid = this.coolantFluid(state);
        IIcon icon = fluid == null ? null : fluid.getStillIcon();
        drawRect(x + 4, fillY, x + 4 + innerWidth, GAUGE_BAR_Y + 4 + innerHeight, HostUiStyle.DARK_TEXT_BLUE);
        if (icon != null) {
            this.drawFluidFill(x + 4, fillY, innerWidth, filled, icon);
        }
    }

    private void drawGaugeFrame(int x, int width) {
        drawRect(x, GAUGE_BAR_Y, x + width, GAUGE_BAR_Y + GAUGE_BAR_H, HostUiStyle.DARK_PANEL_LIGHT_EDGE);
        drawRect(x + 1, GAUGE_BAR_Y + 1, x + width - 1, GAUGE_BAR_Y + GAUGE_BAR_H - 1, HostUiStyle.DARK_PANEL_OUTER);
        drawRect(x + 2, GAUGE_BAR_Y + 2, x + width - 2, GAUGE_BAR_Y + GAUGE_BAR_H - 2, HostUiStyle.DARK_PANEL_MIDDLE);
        drawRect(x + 3, GAUGE_BAR_Y + 3, x + width - 3, GAUGE_BAR_Y + GAUGE_BAR_H - 3, HostUiStyle.DARK_PANEL_OUTER);
        drawRect(x + 4, GAUGE_BAR_Y + 4, x + width - 4, GAUGE_BAR_Y + GAUGE_BAR_H - 4, 0xFF201E27);
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
        this.mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
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

    private List<String> energyTooltip(CraftingHostSnapshot state) {
        List<String> lines = new ArrayList<>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.energy_usage", "Energy Usage"));
        lines.add(
            this.formatNumber(state.maxEnergyUsage) + " / "
                + this.formatNumber(Math.max(1L, state.energyGaugeReference))
                + " AE/t");
        lines.add(this.percentText(state.maxEnergyUsage, Math.max(1L, state.energyGaugeReference)));
        return lines;
    }

    private List<String> coolantTooltip(CraftingHostSnapshot state) {
        List<String> lines = new ArrayList<>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting.coolant", "Coolant"));
        lines.add(
            String.format(
                java.util.Locale.US,
                tr("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s"),
                this.formatNumber(state.coolant),
                this.formatNumber(state.maxCoolant)));
        lines.add(this.percentText(state.coolant, state.maxCoolant));
        lines.add(
            state.coolantMaxOverclock <= 0
                ? tr("gui.neoecoae.crafting.coolant_max_overclock.none", "Current Coolant Max Overclock: None")
                : translate("gui.neoecoae.crafting.coolant_max_overclock", state.coolantMaxOverclock));
        return lines;
    }

    private List<String> workerTooltip(CraftingHostSnapshot.WorkerEntry worker) {
        List<String> lines = new ArrayList<>();
        if (worker.outputStack != null) {
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

    private void drawScaledSegments(float x, float y, TextSegment... segments) {
        float drawX = x;
        for (TextSegment segment : segments) {
            this.drawScaledText(segment.text, drawX, y, TEXT_SCALE, segment.color);
            drawX += this.fontRendererObj.getStringWidth(segment.text) * TEXT_SCALE;
        }
    }

    private void drawScaledFittedText(String text, int x, int y, int maxWidth, float scale, int color) {
        int unscaledWidth = Math.max(0, Math.round(maxWidth / Math.max(0.01F, scale)));
        this.drawScaledText(this.fit(text, unscaledWidth), x, y, scale, color);
    }

    private void drawScaledItemIcon(ItemStack stack, int y) {
        if (stack == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(TASK_CARD_X + 3, y, 220.0F);
        GL11.glScalef(CARD_ICON_SCALE, CARD_ICON_SCALE, 1.0F);
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

    private int ratioLength(long used, long total, int length) {
        if (length <= 0 || total <= 0L || used <= 0L) {
            return 0;
        }
        long clamped = Math.min(used, total);
        return (int) Math.max(1L, Math.min(length, clamped * length / total));
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
        for (String line : split) {
            lines.add(EnumChatFormatting.GRAY + line);
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

    private static final class TextSegment {

        private final String text;
        private final int color;

        private TextSegment(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}
