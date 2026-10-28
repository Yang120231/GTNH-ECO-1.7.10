package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ICraftingCPUTableHolder;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmReport;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmRules;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmState;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * ECO-owned crafting report (1.21's ECOCraftConfirmScreen) over AE2's confirmation container. The container, its CPU
 * list and the server-side job lifecycle remain AE2's; this screen only reads the ECO state the server mirrors.
 */
@SideOnly(Side.CLIENT)
public final class ECOCraftConfirmGui extends AEBaseGui implements ICraftingCPUTableHolder {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "neoecoae",
        "textures/guis/eco_craftingreport_cycle.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation(
        "neoecoae",
        "textures/guis/crafting_widgets.png");
    private static final ResourceLocation STATES = new ResourceLocation(
        "neoecoae",
        "textures/gui/vendor/ae2/states.png");
    private static final int TEXTURE_SIZE = 384;
    private static final int WIDTH = 330;
    private static final int HEIGHT = 260;

    private static final int TEXT_LIGHT = 0xF2F3F7;
    private static final int TEXT_DARK = 0x20212B;
    private static final int MISSING_OVERLAY = 0x1AFF0000;
    private static final int CYCLE_OVERLAY = 0x1AB86BFF;

    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 27;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 22;
    private static final int COLUMNS = 3;
    private static final int ROWS = 7;
    private static final int CYCLE_X = 237;
    private static final int CYCLE_STATUS_X = 237;
    private static final int CYCLE_STATUS_Y = 7;
    private static final int HEADER_LEFT = 53;
    private static final int HEADER_TOP = 5;
    private static final int HEADER_RIGHT = 155;
    private static final int HEADER_BOTTOM = 20;
    private static final long LARGE_CYCLE_INDICATOR_DELAY_NANOS = 500_000_000L;
    private static final MathContext TIME_PRECISION = new MathContext(5, RoundingMode.HALF_UP);

    private final ContainerCraftConfirm container;
    private final GuiCraftingCPUTable cpuTable;
    private final GuiScrollbar tableScroll = new GuiScrollbar();
    private final GuiScrollbar cycleScroll = new GuiScrollbar();
    private final long openedNanos = System.nanoTime();
    private GuiButton start;
    private GuiButton selectCpu;
    private GuiButton cancel;
    private GuiButton retry;
    private GuiButton back;
    private GuiButton graph;
    private boolean lastSubmissionForced;
    @Nullable
    private Integer selectedCycleComponent;
    private ECOCraftConfirmState state = ECOCraftConfirmState.NONE;
    @Nullable
    private ECOCraftConfirmReport report;
    @Nullable
    private List<String> tooltip;
    @Nullable
    private ItemStack hoveredStack;

    public ECOCraftConfirmGui(ContainerCraftConfirm container) {
        super(container);
        this.container = container;
        this.xSize = WIDTH;
        this.ySize = HEIGHT;
        this.cpuTable = new GuiCraftingCPUTable(
            this,
            container.cpuTable,
            status -> container.cpuCraftingSameItem(status) && container.cpuMatches(status));
    }

    @Override
    public GuiCraftingCPUTable getCPUTable() {
        return cpuTable;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        selectCpu = new GuiButton(0, guiLeft + 19, guiTop + 193, 198, 20, "");
        cancel = new GuiButton(1, guiLeft + 6, guiTop + 232, 50, 20, GuiText.Cancel.getLocal());
        start = new GuiButton(2, guiLeft + 166, guiTop + 232, 64, 20, GuiText.Start.getLocal());
        retry = new GuiButton(
            3,
            guiLeft + 40,
            guiTop + 160,
            62,
            20,
            I18n.format("gui.neoecoae.crafting_report.error.retry"));
        back = new GuiButton(
            4,
            guiLeft + 120,
            guiTop + 160,
            62,
            20,
            I18n.format("gui.neoecoae.crafting_report.error.back"));
        buttonList.add(selectCpu);
        buttonList.add(cancel);
        buttonList.add(start);
        buttonList.add(retry);
        buttonList.add(back);
        graph = new GuiButton(5, guiLeft + 60, guiTop + 232, 102, 20, I18n.format("gui.neoecoae.crafting_graph.open"));
        buttonList.add(graph);
        tableScroll.setLeft(218)
            .setTop(TABLE_Y)
            .setHeight(160);
        cycleScroll.setLeft(312)
            .setTop(TABLE_Y)
            .setHeight(160);
        refresh();
    }

    /** Pulls the mirrored state and derives every control from it, like 1.21's updateBeforeRender. */
    private void refresh() {
        state = ECOCraftConfirmClient.state(container);
        report = ECOCraftConfirmClient.report(container);
        boolean shift = isShiftKeyDown();
        boolean force = ECOCraftConfirmRules.isForceStart(state, shift);
        boolean error = state.error != null;
        var gate = ECOCraftConfirmRules.startGate(state, shift, container.hasNoCPU());
        start.enabled = !error && report != null && gate == ECOCraftConfirmRules.StartGate.READY;
        start.displayString = force ? I18n.format("gui.neoecoae.force_start") : GuiText.Start.getLocal();
        selectCpu.enabled = !error && (ECOCraftConfirmRules.isStartable(state, shift) || force);
        selectCpu.displayString = cpuButtonLabel();
        start.visible = selectCpu.visible = !error;
        retry.visible = back.visible = error;
        int materials = report == null ? 0 : report.materials.size();
        tableScroll.setRange(0, Math.max(0, (materials + COLUMNS - 1) / COLUMNS - ROWS), 1);
        int cycles = report == null ? 0
            : report.cycles.items()
                .size();
        cycleScroll.setRange(0, Math.max(0, cycles - ROWS), 1);
        if (selectedCycleComponent != null && (report == null || report.cycles.items()
            .stream()
            .noneMatch(entry -> entry.componentId() == selectedCycleComponent))) selectedCycleComponent = null;
        // The graph is fetched on demand for the plan of the current report version.
        graph.enabled = state.reportReady && report != null;
    }

    private String cpuButtonLabel() {
        if (container.hasNoCPU()) return GuiText.NoCraftingCPUs.getLocal();
        String name;
        if (container.getSelectedCpu() == -1) name = GuiText.Automatic.getLocal();
        else if (container.getName() != null && !container.getName()
            .isEmpty())
            name = container.getName()
                .substring(
                    0,
                    Math.min(
                        20,
                        container.getName()
                            .length()));
        else name = "#" + container.getSelectedCpu();
        return GuiText.CraftingCPU.getLocal() + ": " + name;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refresh();
        tooltip = null;
        hoveredStack = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tooltip == null && selectCpu.visible
            && mouseX >= selectCpu.xPosition
            && mouseY >= selectCpu.yPosition
            && mouseX < selectCpu.xPosition + selectCpu.width
            && mouseY < selectCpu.yPosition + selectCpu.height) tooltip = diagnosticTooltip();
        if (tooltip == null && start.visible
            && ECOCraftConfirmRules
                .usesBigOrderSubmission(state, ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown()))
            && !state.bigOrderCpuAvailable
            && mouseX >= start.xPosition
            && mouseY >= start.yPosition
            && mouseX < start.xPosition + start.width
            && mouseY < start.yPosition + start.height)
            tooltip = Collections.singletonList(I18n.format("tooltip.neoecoae.big_order.requires_cpu"));
        if (tooltip == null && start.visible
            && ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown())
            && mouseX >= start.xPosition
            && mouseY >= start.yPosition
            && mouseX < start.xPosition + start.width
            && mouseY < start.yPosition + start.height)
            tooltip = Collections.singletonList(I18n.format("tooltip.neoecoae.force_start"));
        if (tooltip != null && !tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(BACKGROUND);
        Gui.func_146110_a(offsetX, offsetY, 0, 0, WIDTH, HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        int localX = mouseX - offsetX;
        int localY = mouseY - offsetY;
        drawCpuTierGlyph();
        drawHeader(localX, localY);
        drawCycleStatus();
        drawComplexTreeHint();
        if (report != null) {
            drawMaterials(localX, localY);
            drawCycleItems(localX, localY);
        }
        drawCenteredScaled(cpuStatusLine(), 109, 218, 212, cpuStatusColor(), false);
        GL11.glColor4f(1, 1, 1, 1);
        tableScroll.draw(this);
        cycleScroll.draw(this);
        if (state.error != null) drawError();
    }

    private void drawCpuTierGlyph() {
        int u;
        switch (state.selectedCpuTier) {
            case 4:
                u = 0;
                break;
            case 6:
                u = 13;
                break;
            case 9:
                u = 26;
                break;
            default:
                return;
        }
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(WIDGETS);
        Gui.func_146110_a(38, 5, u, 0, 13, 18, 288, 256);
    }

    private void drawHeader(int localX, int localY) {
        boolean ready = state.reportReady && report != null;
        String bytes;
        if (!ready) bytes = I18n.format("gui.neoecoae.crafting_report.bytes_pending");
        else if (unknownBytes()) bytes = I18n.format("gui.neoecoae.crafting_report.bytes_unknown");
        else {
            // 1.21's fitted summary: the widest exact or compact amount whose labelled text still fits.
            String amount = HostText.fitHugeAmount(
                state.displayedBytes(),
                86,
                text -> fontRendererObj
                    .getStringWidth(I18n.format("gui.neoecoae.crafting_report.bytes_only", byteUnit(text))));
            bytes = I18n.format("gui.neoecoae.crafting_report.bytes_only", byteUnit(amount));
        }
        float scale = Math.min(1.15F, 98F / Math.max(1, fontRendererObj.getStringWidth(bytes) + 1));
        GL11.glPushMatrix();
        GL11.glTranslatef(54, HEADER_TOP + (HEADER_BOTTOM - HEADER_TOP - 8 * scale) / 2F + 1F, 0);
        GL11.glScalef(scale, scale, 1);
        fontRendererObj.drawStringWithShadow(bytes, 0, 0, 0xFFFFFF);
        GL11.glPopMatrix();
        if (!state.reportReady && state.cyclePlanningEnabled
            && System.nanoTime() - openedNanos >= LARGE_CYCLE_INDICATOR_DELAY_NANOS) {
            long millis = (System.nanoTime() - openedNanos) / 1_000_000L;
            int fill = 1 + (int) (millis % 2_000L) * 99 / 1_999;
            Gui.drawRect(54, 19, 153, 20, 0xFF6A6A72);
            Gui.drawRect(54, 19, 54 + fill, 20, 0xFF3D9B62);
        }
        if (ready) {
            // Calculation time beside the byte summary; the exact value stays in the header tooltip.
            GL11.glPushMatrix();
            GL11.glTranslatef(HEADER_RIGHT + 8, 4, 0);
            GL11.glScalef(0.5F, 0.5F, 1);
            fontRendererObj.drawString(
                I18n.format("gui.neoecoae.crafting_report.elapsed", formatMillis(state.calculationNanos)),
                0,
                0,
                TEXT_DARK);
            GL11.glPopMatrix();
        }
        if (localX >= HEADER_LEFT && localX < HEADER_RIGHT && localY >= HEADER_TOP && localY < HEADER_BOTTOM)
            tooltip = headerTooltip(ready, bytes);
    }

    private List<String> headerTooltip(boolean ready, String bytes) {
        List<String> lines = new ArrayList<>();
        if (!ready) {
            lines.add(
                I18n.format(
                    state.reportReady ? "gui.neoecoae.crafting_report.loading_details"
                        : "gui.neoecoae.crafting_report.calculating"));
            lines.add(bytes);
            return lines;
        }
        lines.add(
            I18n.format(
                "gui.neoecoae.crafting_report.elapsed",
                DisplayNumbers.grouped(
                    BigDecimal.valueOf(state.calculationNanos, 6)
                        .toPlainString())));
        if (unknownBytes()) lines.add(bytes);
        else {
            // Keep the exact BigInteger here, including values beyond the compact formatter's units.
            String exact = DisplayNumbers.grouped(
                state.displayedBytes()
                    .toString());
            if (exact.length() <= 32) lines.add(I18n.format("gui.neoecoae.crafting_report.bytes_only", exact + " B"));
            else {
                lines.add(I18n.format("gui.neoecoae.crafting_report.bytes_only", ""));
                for (int from = 0; from < exact.length(); from += 32) {
                    int to = Math.min(exact.length(), from + 32);
                    lines.add(exact.substring(from, to) + (to == exact.length() ? " B" : ""));
                }
            }
        }
        return lines;
    }

    private static String byteUnit(String amount) {
        return amount.contains("e") ? amount + " B" : DisplayNumbers.bytes(amount);
    }

    static String formatMillis(long nanos) {
        if (nanos <= 0) return "0.0000";
        BigDecimal millis = BigDecimal.valueOf(nanos, 6)
            .round(TIME_PRECISION);
        int integerDigits = millis.precision() - millis.scale();
        int displayScale = Math.max(0, TIME_PRECISION.getPrecision() - integerDigits);
        return DisplayNumbers.grouped(
            millis.setScale(displayScale, RoundingMode.HALF_UP)
                .toPlainString());
    }

    private void drawCycleStatus() {
        boolean detected = state.cycleDetected;
        boolean enabled = state.cyclePlanningEnabled;
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(STATES);
        Gui.func_146110_a(CYCLE_STATUS_X, CYCLE_STATUS_Y, detected && enabled ? 16 : 32, 240, 16, 16, 256, 256);
        String key = !detected
            ? state.status == PlanningStatus.CYCLE_UNRESOLVED
                ? "gui.neoecoae.crafting_report.cycle_not_detected_preview"
                : "gui.neoecoae.crafting_report.cycle_not_detected"
            : enabled ? "gui.neoecoae.crafting_report.cycle_planning_enabled"
                : "gui.neoecoae.crafting_report.cycle_planning_disabled";
        drawLeftScaled(
            I18n.format(key),
            CYCLE_STATUS_X + 18,
            CYCLE_STATUS_Y + 4,
            WIDTH - CYCLE_STATUS_X - 22,
            TEXT_DARK);
    }

    private void drawComplexTreeHint() {
        if (state.status != PlanningStatus.CYCLE_UNRESOLVED) return;
        String key = report != null && report.anySeedShortfallEstimate()
            ? "gui.neoecoae.crafting_report.seed_requirement_unknown"
            : "gui.neoecoae.crafting_report.complex_tree_hint";
        drawCenteredScaled(I18n.format(key), WIDTH / 2, -11, WIDTH, 0xFFCC66, true);
    }

    private void drawMaterials(int localX, int localY) {
        List<ECOCraftConfirmReport.Row> rows = report.materials;
        int first = tableScroll.getCurrentScroll() * COLUMNS;
        for (int i = 0; i < ROWS * COLUMNS; i++) {
            int index = first + i;
            if (index >= rows.size()) break;
            ECOCraftConfirmReport.Row row = rows.get(index);
            int cellX = TABLE_X + (i % COLUMNS) * (CELL_WIDTH + 1);
            int cellY = TABLE_Y + (i / COLUMNS) * (CELL_HEIGHT + 1);
            int overlay = row.missing.signum() > 0 ? MISSING_OVERLAY
                : report.isCycleParticipant(row.key) ? CYCLE_OVERLAY : 0;
            if (overlay != 0) Gui.drawRect(cellX, cellY, cellX + CELL_WIDTH, cellY + CELL_HEIGHT, overlay);
            List<String> lines = new ArrayList<>(3);
            if (row.stored.signum() > 0)
                lines.add(GuiText.FromStorage.getLocal() + ": " + compact(row.key, row.stored));
            if (row.missing.signum() > 0) lines.add(missingLine(row.key, compact(row.key, row.missing)));
            if (row.craft.signum() > 0) lines.add(GuiText.ToCraft.getLocal() + ": " + compact(row.key, row.craft));
            if (lines.isEmpty()) lines.add(I18n.format("gui.neoecoae.crafting_report.quantity_unknown"));
            drawCell(row.key, lines, cellX, cellY);
            if (localX >= cellX && localX < cellX + CELL_WIDTH && localY >= cellY && localY < cellY + CELL_HEIGHT) {
                hover(row.key);
                tooltip = materialTooltip(row);
            }
        }
    }

    private String missingLine(AEKey key, String amount) {
        if (report.isEstimatedStartupSeed(key))
            return I18n.format("gui.neoecoae.crafting_report.seed_shortfall_estimate", amount);
        if (report.isMissingStartupSeed(key))
            return I18n.format("gui.neoecoae.crafting_report.missing_startup_seed") + ": " + amount;
        return GuiText.Missing.getLocal() + ": " + amount;
    }

    private List<String> materialTooltip(ECOCraftConfirmReport.Row row) {
        List<String> lines = keyTooltip(row.key);
        if (row.stored.signum() > 0) lines.add(GuiText.FromStorage.getLocal() + ": " + full(row.key, row.stored));
        if (row.missing.signum() > 0) {
            lines.add(missingLine(row.key, full(row.key, row.missing)));
            if (report.isEstimatedStartupSeed(row.key))
                lines.add(I18n.format("gui.neoecoae.crafting_report.seed_shortfall_estimate_hint"));
        }
        if (row.craft.signum() > 0) lines.add(GuiText.ToCraft.getLocal() + ": " + full(row.key, row.craft));
        if (row.requested.signum() > 0) lines.add(
            I18n.format(
                "gui.neoecoae.crafting_report.requested_exact",
                BigNumberFormatter.format(row.requested, 1, true)));
        return lines;
    }

    private void drawCycleItems(int localX, int localY) {
        List<ECOCycleItemList.Entry> items = report.cycles.items();
        int first = cycleScroll.getCurrentScroll();
        for (int row = 0; row < ROWS; row++) {
            int index = first + row;
            if (index >= items.size()) break;
            ECOCycleItemList.Entry entry = items.get(index);
            int cellY = TABLE_Y + row * (CELL_HEIGHT + 1);
            boolean hovered = localX >= CYCLE_X && localX < CYCLE_X + CELL_WIDTH
                && localY >= cellY
                && localY < cellY + CELL_HEIGHT;
            boolean selected = selectedCycleComponent != null && selectedCycleComponent == entry.componentId();
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(BACKGROUND);
            Gui.func_146110_a(
                CYCLE_X,
                cellY,
                0,
                hovered || selected ? 287 : 265,
                CELL_WIDTH,
                CELL_HEIGHT,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
            drawCell(entry.what(), Collections.singletonList(cycleTotalLine(entry, false)), CYCLE_X, cellY);
            if (hovered) {
                hover(entry.what());
                tooltip = cycleTooltip(entry);
            }
        }
    }

    private List<String> cycleTooltip(ECOCycleItemList.Entry entry) {
        List<String> lines = keyTooltip(entry.what());
        if (entry.componentId() >= 0) lines.add("Cycle #" + entry.componentId());
        lines.add(cycleTotalLine(entry, true));
        if (entry.seedParallelism() >= 0) {
            lines.add(I18n.format("gui.neoecoae.crafting_report.seed_parallelism", entry.seedParallelism()));
            lines.add(EnumChatFormatting.GRAY + I18n.format("gui.neoecoae.crafting_report.seed_parallelism_hint"));
        }
        if (entry.seedShortfallIsEstimate())
            lines.add(I18n.format("gui.neoecoae.crafting_report.seed_shortfall_estimate_hint"));
        else if (report.isMissingStartupSeed(entry.what()))
            lines.add(I18n.format("gui.neoecoae.crafting_report.missing_startup_seed"));
        return lines;
    }

    private static String cycleTotalLine(ECOCycleItemList.Entry entry, boolean full) {
        if (entry.exactMissing()
            .signum() > 0)
            return I18n.format(
                entry.seedShortfallIsEstimate() ? "gui.neoecoae.crafting_report.seed_shortfall_estimate"
                    : "gui.neoecoae.crafting_graph.details.missing",
                cycleAmount(entry.exactMissing(), full));
        if (!entry.totalNetOutputKnown() && entry.displayedTotal()
            .signum() == 0) return I18n.format("gui.neoecoae.crafting_report.quantity_unknown");
        return I18n.format(
            entry.isCycleProduct() ? "gui.neoecoae.crafting_report.total_produced"
                : "gui.neoecoae.crafting_report.total_consumed",
            cycleAmount(entry.displayedTotal(), full));
    }

    private static String cycleAmount(BigInteger amount, boolean full) {
        return full ? BigNumberFormatter.format(amount, 1, true) : HostText.hugeStackAmount(amount);
    }

    /** AE2's table cell: up to three half-size lines right-aligned against the stack icon. */
    private void drawCell(AEKey key, List<String> lines, int cellX, int cellY) {
        int itemX = cellX + CELL_WIDTH - 19;
        float lineHeight = fontRendererObj.FONT_HEIGHT * 0.5F + 1;
        float textY = cellY + (CELL_HEIGHT - lines.size() * lineHeight) / 2F + 1;
        GL11.glPushMatrix();
        GL11.glScalef(0.5F, 0.5F, 1);
        for (String line : lines) {
            int width = fontRendererObj.getStringWidth(line);
            fontRendererObj.drawString(line, (int) ((itemX - 2 - width * 0.5F) * 2), (int) (textY * 2), TEXT_LIGHT);
            textY += lineHeight;
        }
        GL11.glPopMatrix();
        drawStack(key, itemX, cellY + 3);
    }

    private void drawStack(AEKey key, int x, int y) {
        IAEStack<?> stack = key.toNative(1);
        if (stack == null) return;
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        try {
            stack.drawInGui(mc, x, y);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glPopMatrix();
        }
    }

    private void hover(AEKey key) {
        IAEStack<?> stack = key.toNative(1);
        hoveredStack = stack == null ? null : stack.getItemStackForNEI();
    }

    private List<String> keyTooltip(AEKey key) {
        List<String> lines = new ArrayList<>();
        IAEStack<?> stack = key.toNative(1);
        ItemStack display = stack == null ? null : stack.getItemStackForNEI();
        if (display != null && display.getItem() != null) {
            try {
                @SuppressWarnings("unchecked")
                List<String> native_ = display.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
                lines.addAll(native_);
            } catch (RuntimeException ignored) {
                lines.clear();
            }
        }
        if (lines.isEmpty()) lines.add(key.getDisplayName());
        return lines;
    }

    private static String compact(AEKey key, BigInteger amount) {
        return BigNumberFormatter.format(amount, Math.max(1, key.getAmountPerUnit()), false);
    }

    private static String full(AEKey key, BigInteger amount) {
        return BigNumberFormatter.format(amount, Math.max(1, key.getAmountPerUnit()), true);
    }

    private boolean unknownBytes() {
        if (state.status == PlanningStatus.CYCLE_UNRESOLVED)
            return state.reportReady && (report == null || report.materials.isEmpty());
        return isDiagnosticShell();
    }

    private boolean isDiagnosticShell() {
        return report != null && state.reportReady
            && ECOCraftConfirmRules.isDiagnosticShell(state.status, state.simulation, report.materials.isEmpty());
    }

    private String cpuStatusLine() {
        if (!state.reportReady) return I18n.format("gui.neoecoae.crafting_report.calculating");
        if (report == null) return I18n.format("gui.neoecoae.crafting_report.loading_details");
        if (state.status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) return I18n.format(
            state.bigOrderCpuAvailable ? "gui.neoecoae.big_order.single_task" : "gui.neoecoae.big_order.requires_cpu");
        if (state.status == PlanningStatus.CYCLE_UNRESOLVED) return I18n.format(
            report.anySeedShortfallEstimate() ? "gui.neoecoae.crafting_report.seed_requirement_unknown"
                : !report.materials.isEmpty() ? "gui.neoecoae.crafting_report.search_incomplete"
                    : "gui.neoecoae.crafting_report.planning_failed");
        if (isDiagnosticShell()) return I18n.format("gui.neoecoae.crafting_report.planning_failed");
        if (state.simulation) return I18n.format("gui.neoecoae.crafting_report.partial_plan");
        if (container.hasNoCPU()) return I18n.format("gui.neoecoae.crafting_report.no_cpu");
        if (container.getSelectedCpu() != -1 && container.getCpuAvailableBytes() > 0) return I18n.format(
            "gui.neoecoae.crafting_report.cpu_status",
            NEByteFormatter.formatCpuStorage(container.getCpuAvailableBytes()),
            NEByteFormatter.formatCpuCoProcessors(container.getCpuCoProcessors()));
        return "";
    }

    private int cpuStatusColor() {
        if (state.status == PlanningStatus.CYCLE_UNRESOLVED) return 0x604100;
        if (isDiagnosticShell()) return 0x752121;
        return TEXT_DARK;
    }

    private @Nullable List<String> diagnosticTooltip() {
        if (!state.reportReady
            || state.diagnostic.isEmpty() && state.status != PlanningStatus.CYCLE_UNRESOLVED && !isDiagnosticShell())
            return null;
        List<String> lines = new ArrayList<>();
        if (state.status != null) lines.add(state.status.name());
        for (String line : state.diagnostic.split("\n"))
            if (!line.isEmpty()) lines.addAll(fontRendererObj.listFormattedStringToWidth(line, 280));
        return lines.isEmpty() ? null : lines;
    }

    private void drawError() {
        Gui.drawRect(TABLE_X, TABLE_Y, TABLE_X + 3 * (CELL_WIDTH + 1) - 1, 188, 0xF0202028);
        int y = TABLE_Y + 10;
        y = drawWrapped(EnumChatFormatting.BOLD + I18n.format("gui.neoecoae.crafting_report.error.title"), y, 0xFF6A75);
        y = drawWrapped(I18n.format(state.error.translationKey()), y + 6, TEXT_LIGHT);
        if (!state.errorDetail.isEmpty()) drawWrapped(state.errorDetail, y + 6, 0xAAA4B2);
    }

    private int drawWrapped(String text, int y, int color) {
        @SuppressWarnings("unchecked")
        List<String> lines = fontRendererObj.listFormattedStringToWidth(text, 3 * (CELL_WIDTH + 1) - 12);
        for (String line : lines) {
            if (y > 150) break;
            fontRendererObj.drawString(
                line,
                TABLE_X + (3 * (CELL_WIDTH + 1) - fontRendererObj.getStringWidth(line)) / 2,
                y,
                color);
            y += fontRendererObj.FONT_HEIGHT + 1;
        }
        return y;
    }

    private void drawCenteredScaled(String text, int centerX, int y, int maxWidth, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        int width = fontRendererObj.getStringWidth(text);
        float scale = Math.min(1F, maxWidth / (float) Math.max(1, width));
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX - width * scale / 2F, y, 0);
        GL11.glScalef(scale, scale, 1);
        if (shadow) fontRendererObj.drawStringWithShadow(text, 0, 0, color);
        else fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void drawLeftScaled(String text, int x, int y, int maxWidth, int color) {
        int width = fontRendererObj.getStringWidth(text);
        float scale = Math.min(1F, maxWidth / (float) Math.max(1, width));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + (1 - scale) * 4, 0);
        GL11.glScalef(scale, scale, 1);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        super.actionPerformed(button);
        if (button == start) submit(ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown()));
        else if (button == selectCpu) cpuTable.cycleCPU(Mouse.isButtonDown(1));
        else if (button == cancel) goBack();
        else if (button == retry) submit(lastSubmissionForced);
        else if (button == back) ECOCraftConfirmPackets.clearError(container.windowId);
        else if (button == graph) openGraph();
    }

    private void submit(boolean forced) {
        if (state.error == null && !start.enabled) return;
        lastSubmissionForced = forced;
        // An extended amount or a forced cycle shortage goes to an ECO CPU as a big order (1.21).
        if (ECOCraftConfirmRules.usesBigOrderSubmission(state, forced))
            ECOCraftConfirmPackets.startBigOrder(container.windowId, forced);
        else ECOCraftConfirmPackets.start(container.windowId, forced);
    }

    /** Opens the planning graph of the plan on screen; Back in the graph returns here with the same container. */
    private void openGraph() {
        if (report == null || !state.reportReady) return;
        mc.displayGuiScreen(
            new cn.dancingsnow.neoecoae.client.gui.graph.ECOCraftingGraphScreen(
                this,
                container,
                state.reportVersion,
                selectedCycleComponent,
                report.cycles.items()));
    }

    private void goBack() {
        try {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis());
        } catch (Exception failure) {
            mc.thePlayer.closeScreen();
        }
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (state.error == null && start.enabled)
                submit(ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown()));
            return;
        }
        super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int localX = mouseX - guiLeft;
        int localY = mouseY - guiTop;
        tableScroll.click(this, localX, localY);
        cycleScroll.click(this, localX, localY);
        if (button == 0 && report != null && state.error == null) {
            ECOCycleItemList.Entry entry = cycleEntryAt(localX, localY);
            if (entry != null) {
                int component = entry.componentId();
                selectedCycleComponent = component < 0
                    || selectedCycleComponent != null && selectedCycleComponent == component ? null : component;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceClick) {
        tableScroll.clickMove(mouseY - guiTop);
        cycleScroll.clickMove(mouseY - guiTop);
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected boolean mouseWheelEvent(int mouseX, int mouseY, int wheel) {
        int localX = mouseX - guiLeft;
        if (localX >= CYCLE_X) cycleScroll.wheel(wheel);
        else tableScroll.wheel(wheel);
        return true;
    }

    private @Nullable ECOCycleItemList.Entry cycleEntryAt(int localX, int localY) {
        if (localX < CYCLE_X || localX >= CYCLE_X + CELL_WIDTH || localY < TABLE_Y) return null;
        int row = (localY - TABLE_Y) / (CELL_HEIGHT + 1);
        if (row >= ROWS || localY >= TABLE_Y + row * (CELL_HEIGHT + 1) + CELL_HEIGHT) return null;
        int index = cycleScroll.getCurrentScroll() + row;
        List<ECOCycleItemList.Entry> items = report.cycles.items();
        return index < items.size() ? items.get(index) : null;
    }

    @Override
    public ItemStack getHoveredStack() {
        return hoveredStack;
    }
}
