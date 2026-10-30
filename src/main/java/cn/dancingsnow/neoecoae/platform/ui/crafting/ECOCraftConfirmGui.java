package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
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

import appeng.api.config.CraftingSortOrder;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiCraftingList;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiSimpleImgButton;
import appeng.client.gui.widgets.ICraftingCPUTableHolder;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.util.ColorPickHelper;
import appeng.util.Platform;
import appeng.util.RoundHelper;
import appeng.util.item.IAEStackList;
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
 * Besides the ECO report it carries every control of GTNH's own confirmation page, and shows the ECO planning graph
 * in place of the tables as its tree view.
 */
@SideOnly(Side.CLIENT)
public final class ECOCraftConfirmGui extends AEBaseGui implements ICraftingCPUTableHolder {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "neoecoae",
        "textures/guis/eco_craftingreport_cycle.png");
    private static final ResourceLocation TREE_BACKGROUND = new ResourceLocation(
        "neoecoae",
        "textures/guis/eco_crafting_tree.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation(
        "neoecoae",
        "textures/guis/crafting_widgets.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 384;
    private static final int TREE_TEXTURE_WIDTH = 432;
    private static final int TREE_TEXTURE_HEIGHT = 288;
    private static final int WIDTH = 330;
    private static final int HEIGHT = 260;
    // The texture carries GTNH's CPU card column left of the report; every report coordinate stays panel-relative.
    private static final int CPU_PANEL_WIDTH = 96;
    private static final int CPU_PANEL_HEIGHT = 214;
    private static final int CPU_SCROLL_LEFT = -10;
    private static final int CPU_SCROLL_HEIGHT = 160;
    private static final int DRAWN_WIDTH = CPU_PANEL_WIDTH + WIDTH + 1;

    private static final int TEXT_LIGHT = 0xF2F3F7;
    private static final int TEXT_DARK = 0x20212B;
    private static final int MISSING_OVERLAY = 0x1AFF0000;
    private static final int CYCLE_OVERLAY = 0x1AB86BFF;

    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 27;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 22;
    private static final int ROW_PITCH = CELL_HEIGHT + 1;
    private static final int COLUMNS = 3;
    /** Rows of the compact layout; the tall layout repeats the texture's middle row strip. */
    private static final int BASE_ROWS = 7;
    private static final int CYCLE_X = 237;
    private static final int CYCLE_STATUS_Y = 191;
    private static final int CYCLE_STATUS_WIDTH = 86;
    private static final int SEARCH_X = 222;
    private static final int SEARCH_Y = 5;
    private static final int SEARCH_WIDTH = 102;
    private static final int SEARCH_HEIGHT = 14;
    private static final int SEARCH_HINT = 0xFF707A84;
    /** The tree texture's ECO logo sits 94px further left than the report texture's (x 10 instead of 104). */
    private static final int TREE_HEADER_SHIFT = -94;
    private static final int HEADER_LEFT = 53;
    private static final int HEADER_TOP = 5;
    private static final int HEADER_RIGHT = 155;
    private static final int HEADER_BOTTOM = 20;
    /** The graph area of the tree texture, panel-relative. */
    private static final int VIEW_X = 11 - CPU_PANEL_WIDTH;
    private static final int VIEW_Y = 29;
    private static final int VIEW_WIDTH = 405;
    private static final int VIEW_HEIGHT = 170;
    /** Side buttons right of the panel and below the CPU column, as on GTNH's page. */
    private static final int SIDE_X = WIDTH + 2;
    /**
     * Width from the CPU column to the side buttons. NEI places its panels from {@code (width - xSize) / 2} assuming a
     * centred GUI, so xSize spans everything drawn and the page is centred on it.
     */
    private static final int FULL_WIDTH = CPU_PANEL_WIDTH + SIDE_X + 18;
    /** AE2's CPU table lays its cards from y=19; the ECO column starts them level with the report table. */
    private static final int CPU_LIST_SHIFT = TABLE_Y - 19;
    private static final long LARGE_CYCLE_INDICATOR_DELAY_NANOS = 500_000_000L;

    private final ContainerCraftConfirm container;
    private final GuiCraftingCPUTable cpuTable;
    private final GuiScrollbar tableScroll = new GuiScrollbar();
    private final GuiScrollbar cycleScroll = new GuiScrollbar();
    private final long openedNanos = System.nanoTime();
    /** One search box for both views: it filters the tables and searches the planning tree. */
    private GuiTextField search;
    /** The query last handed to the tree view, so it is only re-searched when the text changes. */
    @Nullable
    private String treeQuery;
    private final List<GuiButton> cpuSortButtons = new ArrayList<>();
    private GuiButton start;
    private GuiButton startWithFollow;
    private GuiButton selectCpu;
    private GuiButton cancel;
    private GuiButton retry;
    private GuiButton back;
    private GuiButton graph;
    private GuiImgButton sortByButton;
    private GuiImgButton sortDirButton;
    private GuiSimpleImgButton styleButton;
    private GuiSimpleImgButton viewButton;
    private GuiSimpleImgButton saveButton;
    /** The ECO graph's view buttons, in the header outside the graph area. */
    private GuiButton treeFitButton;
    private GuiButton treeDepthButton;
    private GuiButton treeMissingButton;
    private boolean lastSubmissionForced;
    private boolean lastSubmissionFollow;
    /** Page height: 0 short, 1 medium, 2 tall, 3 full screen. */
    private int style;
    private int rows = BASE_ROWS;
    private CraftingSortOrder sortMode;
    private SortDir sortDir;
    /** Tree view: the ECO planning graph replaces the tables and the CPU column. */
    private boolean treeMode;
    @Nullable
    private PlanTreeView planTree;
    private float partialTicks;
    @Nullable
    private Integer selectedCycleComponent;
    private ECOCraftConfirmState state = ECOCraftConfirmState.NONE;
    @Nullable
    private ECOCraftConfirmReport report;
    /** Report rows matching the search in the chosen order, rebuilt only when one of their inputs changes. */
    private List<ECOCraftConfirmReport.Row> visibleMaterials = Collections.emptyList();
    @Nullable
    private ECOCraftConfirmReport filteredReport;
    private String filteredQuery = "";
    @Nullable
    private CraftingSortOrder filteredSort;
    @Nullable
    private SortDir filteredDir;
    @Nullable
    private List<String> tooltip;
    @Nullable
    private ItemStack hoveredStack;

    public ECOCraftConfirmGui(ContainerCraftConfirm container) {
        super(container);
        this.container = container;
        this.xSize = FULL_WIDTH;
        this.ySize = HEIGHT;
        Enum<?> terminalStyle = AEConfig.instance.getConfigManager()
            .getSetting(Settings.TERMINAL_STYLE);
        this.style = terminalStyle == TerminalStyle.FULL ? 3 : terminalStyle == TerminalStyle.TALL ? 2 : 0;
        this.sortMode = (CraftingSortOrder) AEConfig.instance.settings.getSetting(Settings.CRAFTING_SORT_BY);
        this.sortDir = (SortDir) AEConfig.instance.settings.getSetting(Settings.SORT_DIRECTION);
        applyCpuTableGeometry();
        this.cpuTable = new GuiCraftingCPUTable(
            this,
            container.cpuTable,
            status -> container.cpuCraftingSameItem(status) && container.cpuMatches(status));
    }

    /** The table's geometry is global; AE2's own confirm and status screens reset it the same way when opened. */
    private void applyCpuTableGeometry() {
        GuiCraftingCPUTable.CPU_TABLE_SLOTS = rows;
        GuiCraftingCPUTable.CPU_TABLE_HEIGHT = rows * GuiCraftingCPUTable.CPU_TABLE_SLOT_HEIGHT + 27;
    }

    /** Centres AE2's CPU scrollbar on the ECO column's narrower track. */
    private static void alignCpuScrollbar(GuiCraftingCPUTable table, int height) {
        try {
            Field field = GuiCraftingCPUTable.class.getDeclaredField("cpuScrollbar");
            field.setAccessible(true);
            ((GuiScrollbar) field.get(table)).setLeft(CPU_SCROLL_LEFT)
                .setHeight(height);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // AE2's default placement is a few pixels off the track but still usable.
        }
    }

    @Override
    public GuiCraftingCPUTable getCPUTable() {
        return cpuTable;
    }

    /** AE2's terminal style icons: short, medium (the tall icon), tall, full screen. */
    private static final int[] STYLE_ICONS = { 16 * 13 + 1, 16 * 13, 16 * 13, 16 * 13 + 2 };
    private static final int MEDIUM_ROWS = 11;

    /** Table rows of a style; the taller styles fill the screen height minus AE2's terminal margins. */
    private int rowsFor(int pageStyle) {
        int tall = BASE_ROWS + Math.max(0, (height - 64 - HEIGHT) / ROW_PITCH);
        int full = BASE_ROWS + Math.max(0, (height - 24 - HEIGHT) / ROW_PITCH);
        switch (pageStyle) {
            case 1:
                return Math.min(MEDIUM_ROWS, full);
            case 2:
                return tall;
            case 3:
                return full;
            default:
                return BASE_ROWS;
        }
    }

    private String styleTooltip() {
        String value;
        switch (style) {
            case 1:
                value = I18n.format("gui.neoecoae.crafting_report.style_medium");
                break;
            case 2:
                value = ButtonToolTips.TerminalStyle_Tall.getLocal();
                break;
            case 3:
                value = ButtonToolTips.TerminalStyle_Full.getLocal();
                break;
            default:
                value = ButtonToolTips.TerminalStyle_Small.getLocal();
        }
        return ButtonToolTips.TerminalStyle.getLocal() + "\n" + EnumChatFormatting.GRAY + value;
    }

    /** Extra height of the tall layout over the compact one. */
    private int extra() {
        return (rows - BASE_ROWS) * ROW_PITCH;
    }

    @Override
    public void initGui() {
        rows = rowsFor(style);
        ySize = HEIGHT + extra();
        applyCpuTableGeometry();
        alignCpuScrollbar(cpuTable, CPU_SCROLL_HEIGHT + extra());
        super.initGui();
        // guiLeft stays the report's origin; the CPU column hangs left of it like AE2's.
        guiLeft += CPU_PANEL_WIDTH;
        int bottom = guiTop + 232 + extra();
        buttonList.clear();
        cpuTable.addButtons(buttonList, guiLeft, guiTop);
        cpuSortButtons.clear();
        for (Object button : buttonList) cpuSortButtons.add((GuiButton) button);
        selectCpu = new GuiButton(0, guiLeft + 19, guiTop + 193 + extra(), 198, 20, "");
        cancel = new FitButton(1, guiLeft + 4, bottom, 46, 20, GuiText.Cancel.getLocal());
        graph = new FitButton(5, guiLeft + 52, bottom, 56, 20, I18n.format("gui.neoecoae.crafting_graph.open"));
        start = new FitButton(2, guiLeft + 110, bottom, 60, 20, GuiText.Start.getLocal());
        startWithFollow = new FitButton(6, guiLeft + 172, bottom, 60, 20, GuiText.StartWithFollow.getLocal());
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
        sortByButton = new GuiImgButton(guiLeft + SIDE_X, guiTop + 8, Settings.CRAFTING_SORT_BY, sortMode);
        sortDirButton = new GuiImgButton(guiLeft + SIDE_X, guiTop + 28, Settings.SORT_DIRECTION, sortDir);
        viewButton = new GuiSimpleImgButton(
            guiLeft + SIDE_X,
            guiTop + 48,
            13 * 16 + 3,
            GuiText.SwitchCraftingSimulationDisplayMode.getLocal());
        styleButton = new GuiSimpleImgButton(guiLeft - 18, guiTop + ySize - 18, STYLE_ICONS[style], styleTooltip());
        saveButton = new GuiSimpleImgButton(
            guiLeft - 36,
            guiTop + ySize - 18,
            16 * 9,
            ButtonToolTips.SaveAsImage.getLocal());
        buttonList.add(selectCpu);
        buttonList.add(cancel);
        buttonList.add(graph);
        buttonList.add(start);
        buttonList.add(startWithFollow);
        buttonList.add(retry);
        buttonList.add(back);
        buttonList.add(sortByButton);
        buttonList.add(sortDirButton);
        buttonList.add(viewButton);
        buttonList.add(styleButton);
        buttonList.add(saveButton);
        String query = search == null ? "" : search.getText();
        boolean focused = search != null && search.isFocused();
        search = new GuiTextField(
            fontRendererObj,
            guiLeft + SEARCH_X + 1,
            guiTop + SEARCH_Y + 1,
            SEARCH_WIDTH - 2,
            SEARCH_HEIGHT - 2);
        search.setMaxStringLength(64);
        search.setText(query);
        search.setFocused(focused);
        treeQuery = null;
        treeFitButton = new FitButton(7, guiLeft + 62, guiTop + 5, 44, 14, "");
        treeDepthButton = new FitButton(8, guiLeft + 108, guiTop + 5, 50, 14, "");
        treeMissingButton = new FitButton(9, guiLeft + 160, guiTop + 5, 58, 14, "");
        buttonList.add(treeFitButton);
        buttonList.add(treeDepthButton);
        buttonList.add(treeMissingButton);
        if (planTree == null) planTree = new PlanTreeView(this, container);
        planTree.place(VIEW_X, VIEW_Y, VIEW_WIDTH, VIEW_HEIGHT + extra());
        tableScroll.setLeft(218)
            .setTop(TABLE_Y)
            .setHeight(CPU_SCROLL_HEIGHT + extra());
        cycleScroll.setLeft(312)
            .setTop(TABLE_Y)
            .setHeight(CPU_SCROLL_HEIGHT + extra());
        refresh();
    }

    /** Pulls the mirrored state and derives every control from it, like 1.21's updateBeforeRender. */
    private void refresh() {
        state = ECOCraftConfirmClient.state(container);
        report = ECOCraftConfirmClient.report(container);
        boolean shift = isShiftKeyDown();
        boolean force = ECOCraftConfirmRules.isForceStart(state, shift);
        boolean error = state.error != null;
        boolean list = !treeMode;
        var gate = ECOCraftConfirmRules.startGate(state, shift, container.hasNoCPU());
        start.enabled = !error && report != null && gate == ECOCraftConfirmRules.StartGate.READY;
        CraftingCPUStatus selected = container.cpuTable.getSelectedCPU();
        boolean merge = selected != null && container.cpuCraftingSameItem(selected);
        start.displayString = force ? I18n.format("gui.neoecoae.force_start")
            : merge ? GuiText.Merge.getLocal() : GuiText.Start.getLocal();
        // A big order goes through the ECO submission, which has no completion subscription.
        startWithFollow.enabled = start.enabled && !ECOCraftConfirmRules.usesBigOrderSubmission(state, force);
        cancel.displayString = shift && report != null && report.hasMissing() ? GuiText.AddToBookmark.getLocal()
            : GuiText.Cancel.getLocal();
        selectCpu.enabled = !error && (ECOCraftConfirmRules.isStartable(state, shift) || force);
        selectCpu.displayString = cpuButtonLabel();
        start.visible = startWithFollow.visible = !error;
        selectCpu.visible = !error && list;
        retry.visible = back.visible = error;
        for (GuiButton button : cpuSortButtons) button.visible = list;
        sortByButton.visible = sortDirButton.visible = list;
        int materials = visibleMaterials().size();
        tableScroll.setRange(0, Math.max(0, (materials + COLUMNS - 1) / COLUMNS - rows), 1);
        int cycles = report == null ? 0
            : report.cycles.items()
                .size();
        cycleScroll.setRange(0, Math.max(0, cycles - rows), 1);
        if (selectedCycleComponent != null && (report == null || report.cycles.items()
            .stream()
            .noneMatch(entry -> entry.componentId() == selectedCycleComponent))) selectedCycleComponent = null;
        // The graph is fetched on demand for the plan of the current report version.
        boolean graphReady = state.reportReady && report != null;
        graph.enabled = viewButton.enabled = treeMode || graphReady;
        graph.displayString = I18n
            .format(treeMode ? "gui.neoecoae.crafting_graph.close" : "gui.neoecoae.crafting_graph.open");
        if (treeMode && !graphReady) treeMode = false;
        if (treeMode) planTree.poll(state.reportVersion);
        treeFitButton.visible = treeDepthButton.visible = treeMissingButton.visible = treeMode;
        treeFitButton.enabled = treeDepthButton.enabled = treeMissingButton.enabled = planTree.hasTree();
        treeFitButton.displayString = planTree.fitLabel();
        treeDepthButton.displayString = planTree.depthLabel();
        treeMissingButton.displayString = planTree.missingLabel();
        String query = search.getText()
            .trim();
        if (treeMode && !query.equals(treeQuery)) {
            treeQuery = query;
            planTree.search(query);
        }
    }

    /** The search filter and GTNH's sort order over the report rows; missing rows always lead, as on GTNH. */
    private List<ECOCraftConfirmReport.Row> visibleMaterials() {
        String query = search.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        if (report == filteredReport && query.equals(filteredQuery)
            && sortMode == filteredSort
            && sortDir == filteredDir) return visibleMaterials;
        filteredReport = report;
        filteredQuery = query;
        filteredSort = sortMode;
        filteredDir = sortDir;
        if (report == null) {
            visibleMaterials = Collections.emptyList();
            return visibleMaterials;
        }
        Map<ECOCraftConfirmReport.Row, String> names = new HashMap<>();
        List<ECOCraftConfirmReport.Row> matches = new ArrayList<>();
        for (ECOCraftConfirmReport.Row row : report.materials) {
            String name = row.key.getDisplayName();
            names.put(row, name);
            if (query.isEmpty() || name.toLowerCase(Locale.ROOT)
                .contains(query)) matches.add(row);
        }
        Comparator<ECOCraftConfirmReport.Row> byName = (a, b) -> names.get(a)
            .compareToIgnoreCase(names.get(b));
        Comparator<ECOCraftConfirmReport.Row> order;
        switch (sortMode) {
            case CRAFTS:
                order = Comparator.comparing(row -> row.rounds);
                break;
            case AMOUNT:
                order = Comparator.comparing(
                    row -> row.stored.add(row.craft)
                        .add(row.missing));
                break;
            case MOD: {
                Map<ECOCraftConfirmReport.Row, String> mods = new HashMap<>();
                for (ECOCraftConfirmReport.Row row : matches) mods.put(row, modId(row.key));
                order = Comparator.<ECOCraftConfirmReport.Row, String>comparing(
                    mods::get,
                    String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byName);
                break;
            }
            case PERCENT:
                order = Comparator.comparingDouble(ECOCraftConfirmGui::usedPercent);
                break;
            default:
                order = byName;
        }
        Comparator<ECOCraftConfirmReport.Row> directed = sortDir.sortHint < 0 ? order.reversed() : order;
        matches.sort(
            Comparator.<ECOCraftConfirmReport.Row>comparingInt(row -> row.missing.signum() > 0 ? 0 : 1)
                .thenComparing(directed));
        visibleMaterials = matches;
        return visibleMaterials;
    }

    private static String modId(AEKey key) {
        try {
            IAEStack<?> stack = key.toNative(1);
            String mod = stack == null ? null : stack.getModId();
            return mod == null ? "" : mod;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /** GTNH's usage share for rows taken purely from storage; {@code -1} for every other row. */
    private static float usedPercent(ECOCraftConfirmReport.Row row) {
        if (row.stored.signum() <= 0 || row.craft.signum() > 0
            || row.missing.signum() > 0
            || row.available.signum() <= 0) return -1F;
        double percent = new BigDecimal(row.stored).multiply(BigDecimal.valueOf(100))
            .divide(new BigDecimal(row.available), 6, BigDecimal.ROUND_HALF_UP)
            .doubleValue();
        return (float) Math.max(0, Math.min(100, percent));
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
        this.partialTicks = partialTicks;
        refresh();
        cpuTable.drawScreen();
        tooltip = null;
        hoveredStack = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tooltip == null && treeMode && state.error == null) {
            List<String> lines = planTree.tooltip();
            if (!lines.isEmpty()) tooltip = lines;
            hoveredStack = planTree.hoveredStack();
        }
        if (tooltip == null && selectCpu.visible && over(selectCpu, mouseX, mouseY)) tooltip = diagnosticTooltip();
        boolean overStart = start.visible && over(start, mouseX, mouseY)
            || startWithFollow.visible && over(startWithFollow, mouseX, mouseY);
        if (tooltip == null && overStart
            && ECOCraftConfirmRules
                .usesBigOrderSubmission(state, ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown()))
            && !state.bigOrderCpuAvailable)
            tooltip = Collections.singletonList(I18n.format("tooltip.neoecoae.big_order.requires_cpu"));
        if (tooltip == null && overStart && ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown()))
            tooltip = Collections.singletonList(I18n.format("tooltip.neoecoae.force_start"));
        if (tooltip == null && overStart && isBlockedByNonPlayerMerge()) {
            List<String> lines = new ArrayList<>();
            Collections.addAll(
                lines,
                ButtonToolTips.MergeOnlyPlayerRequestedCrafts.getLocal()
                    .split("\n"));
            tooltip = lines;
        }
        if (tooltip != null && !tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    private static boolean over(GuiButton button, int mouseX, int mouseY) {
        return mouseX >= button.xPosition && mouseY >= button.yPosition
            && mouseX < button.xPosition + button.width
            && mouseY < button.yPosition + button.height;
    }

    /** GTNH: a CPU running a machine-requested craft of the same item does not accept a player's merge. */
    private boolean isBlockedByNonPlayerMerge() {
        CraftingCPUStatus selected = container.cpuTable.getSelectedCPU();
        return selected != null && selected.isBusy()
            && container.cpuCraftingSameItem(selected)
            && !selected.isCraftingLinkStandalone();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1);
        int left = offsetX - CPU_PANEL_WIDTH;
        if (treeMode) {
            mc.getTextureManager()
                .bindTexture(TREE_BACKGROUND);
            // Top, one 23px strip of the graph area per row beyond six, then the bottom.
            blit(left, offsetY, 0, 100, TREE_TEXTURE_WIDTH, TREE_TEXTURE_HEIGHT);
            int y = offsetY + 100;
            for (int i = 0; i < rows - 6; i++, y += ROW_PITCH)
                blit(left, y, 100, ROW_PITCH, TREE_TEXTURE_WIDTH, TREE_TEXTURE_HEIGHT);
            blit(left, y, 123, HEIGHT - 123, TREE_TEXTURE_WIDTH, TREE_TEXTURE_HEIGHT);
            drawSearch();
            return;
        }
        mc.getTextureManager()
            .bindTexture(BACKGROUND);
        // Top with the first table row, the middle row strip once per further row, then the last row and below.
        blit(left, offsetY, 0, 50, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        int y = offsetY + 50;
        for (int i = 0; i < rows - 2; i++, y += ROW_PITCH) blit(left, y, 50, ROW_PITCH, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        blit(left, y, 165, HEIGHT - 165, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        drawSearch();
    }

    /** Vanilla's black box with a grey border, plus a hint while it is empty, so it stands out on the panel. */
    private void drawSearch() {
        search.drawTextBox();
        if (search.getText()
            .isEmpty() && !search.isFocused())
            fontRendererObj.drawString(
                fontRendererObj
                    .trimStringToWidth(I18n.format("gui.neoecoae.crafting_graph.search_hint"), search.width - 6),
                search.xPosition + 4,
                search.yPosition + (search.height - 8) / 2,
                SEARCH_HINT);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void blit(int x, int y, int v, int h, int textureWidth, int textureHeight) {
        Gui.func_146110_a(x, y, 0, v, DRAWN_WIDTH, h, textureWidth, textureHeight);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        int localX = mouseX - offsetX;
        int localY = mouseY - offsetY;
        // The tree texture has no CPU column, so its ECO logo and header sit one column further left.
        int headerShift = treeMode ? TREE_HEADER_SHIFT : 0;
        GL11.glPushMatrix();
        GL11.glTranslatef(headerShift, 0, 0);
        drawCpuTierGlyph();
        drawHeader(localX - headerShift, localY);
        GL11.glPopMatrix();
        if (!treeMode) drawCycleStatus();
        drawComplexTreeHint();
        if (report != null && !treeMode) {
            drawMaterials(localX, localY);
            drawCycleItems(localX, localY);
        }
        drawCenteredScaled(cpuStatusLine(), 109, 218 + extra(), 212, cpuStatusColor(), false);
        GL11.glColor4f(1, 1, 1, 1);
        if (!treeMode) {
            tableScroll.draw(this);
            cycleScroll.draw(this);
        }
        if (treeMode && state.error == null) {
            planTree.draw(localX, localY);
            GL11.glColor4f(1, 1, 1, 1);
        }
        if (state.error != null) drawError();
        if (!treeMode) {
            // Last, so the CPU card tooltip is not painted over by the report.
            GL11.glPushMatrix();
            GL11.glTranslatef(0, CPU_LIST_SHIFT, 0);
            cpuTable.drawFG(offsetX, offsetY, mouseX, mouseY - CPU_LIST_SHIFT, guiLeft, guiTop);
            GL11.glPopMatrix();
        }
        GL11.glColor4f(1, 1, 1, 1);
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

    /** The cycle planning state, centred under the cycle column. */
    private void drawCycleStatus() {
        boolean detected = state.cycleDetected;
        boolean enabled = state.cyclePlanningEnabled;
        String key = !detected
            ? state.status == PlanningStatus.CYCLE_UNRESOLVED
                ? "gui.neoecoae.crafting_report.cycle_not_detected_preview"
                : "gui.neoecoae.crafting_report.cycle_not_detected"
            : enabled ? "gui.neoecoae.crafting_report.cycle_planning_enabled"
                : "gui.neoecoae.crafting_report.cycle_planning_disabled";
        drawCenteredScaled(
            I18n.format(key),
            CYCLE_X + CELL_WIDTH / 2,
            CYCLE_STATUS_Y + extra(),
            CYCLE_STATUS_WIDTH,
            TEXT_DARK,
            false);
    }

    private void drawComplexTreeHint() {
        if (state.status != PlanningStatus.CYCLE_UNRESOLVED) return;
        String key = report != null && report.anySeedShortfallEstimate()
            ? "gui.neoecoae.crafting_report.seed_requirement_unknown"
            : "gui.neoecoae.crafting_report.complex_tree_hint";
        drawCenteredScaled(I18n.format(key), WIDTH / 2, -11, WIDTH, 0xFFCC66, true);
    }

    private void drawMaterials(int localX, int localY) {
        List<ECOCraftConfirmReport.Row> visible = visibleMaterials;
        int first = tableScroll.getCurrentScroll() * COLUMNS;
        for (int i = 0; i < rows * COLUMNS; i++) {
            int index = first + i;
            if (index >= visible.size()) break;
            ECOCraftConfirmReport.Row row = visible.get(index);
            int cellX = TABLE_X + (i % COLUMNS) * (CELL_WIDTH + 1);
            int cellY = TABLE_Y + (i / COLUMNS) * ROW_PITCH;
            int overlay = row.missing.signum() > 0 ? MISSING_OVERLAY
                : report.isCycleParticipant(row.key) ? CYCLE_OVERLAY : 0;
            if (overlay != 0) Gui.drawRect(cellX, cellY, cellX + CELL_WIDTH, cellY + CELL_HEIGHT, overlay);
            List<String> lines = new ArrayList<>(4);
            List<Integer> colors = new ArrayList<>(4);
            if (row.stored.signum() > 0)
                add(lines, colors, GuiText.FromStorage.getLocal() + ": " + compact(row.key, row.stored), TEXT_LIGHT);
            float percent = usedPercent(row);
            if (percent >= 0) add(lines, colors, percentLine(percent), percentColor(percent));
            if (row.missing.signum() > 0)
                add(lines, colors, missingLine(row.key, compact(row.key, row.missing)), TEXT_LIGHT);
            if (row.craft.signum() > 0)
                add(lines, colors, GuiText.ToCraft.getLocal() + ": " + compact(row.key, row.craft), TEXT_LIGHT);
            if (row.rounds.signum() > 0) add(
                lines,
                colors,
                GuiText.ToCraftRequests.getLocal() + ": " + BigNumberFormatter.format(row.rounds, 1, false),
                TEXT_LIGHT);
            if (lines.isEmpty())
                add(lines, colors, I18n.format("gui.neoecoae.crafting_report.quantity_unknown"), TEXT_LIGHT);
            drawCell(row.key, lines, colors, cellX, cellY);
            if (localX >= cellX && localX < cellX + CELL_WIDTH && localY >= cellY && localY < cellY + CELL_HEIGHT) {
                hover(row.key);
                tooltip = materialTooltip(row);
            }
        }
    }

    private static void add(List<String> lines, List<Integer> colors, String line, int color) {
        lines.add(line);
        colors.add(color);
    }

    private static String percentLine(float percent) {
        return GuiText.FromStoragePercent.getLocal() + ": "
            + (percent > 0 && percent < 0.01F ? "<0.01" : RoundHelper.toRoundedFormattedForm(percent, 2))
            + "%";
    }

    private static int percentColor(float percent) {
        try {
            return ColorPickHelper.selectColorFromThreshold(percent)
                .getColor() & 0xFFFFFF;
        } catch (RuntimeException | LinkageError ignored) {
            return TEXT_LIGHT;
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
        float percent = usedPercent(row);
        if (percent >= 0) lines.add(percentLine(percent));
        if (row.missing.signum() > 0) {
            lines.add(missingLine(row.key, full(row.key, row.missing)));
            if (report.isEstimatedStartupSeed(row.key))
                lines.add(I18n.format("gui.neoecoae.crafting_report.seed_shortfall_estimate_hint"));
        }
        if (row.craft.signum() > 0) lines.add(GuiText.ToCraft.getLocal() + ": " + full(row.key, row.craft));
        if (row.rounds.signum() > 0)
            lines.add(GuiText.ToCraftRequests.getLocal() + ": " + BigNumberFormatter.format(row.rounds, 1, true));
        if (row.requested.signum() > 0) lines.add(
            I18n.format(
                "gui.neoecoae.crafting_report.requested_exact",
                BigNumberFormatter.format(row.requested, 1, true)));
        return lines;
    }

    private void drawCycleItems(int localX, int localY) {
        List<ECOCycleItemList.Entry> items = report.cycles.items();
        int first = cycleScroll.getCurrentScroll();
        for (int row = 0; row < rows; row++) {
            int index = first + row;
            if (index >= items.size()) break;
            ECOCycleItemList.Entry entry = items.get(index);
            int cellY = TABLE_Y + row * ROW_PITCH;
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
                CPU_PANEL_WIDTH,
                hovered || selected ? 287 : 265,
                CELL_WIDTH,
                CELL_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
            drawCell(
                entry.what(),
                Collections.singletonList(cycleTotalLine(entry, false)),
                Collections.singletonList(TEXT_LIGHT),
                CYCLE_X,
                cellY);
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

    /** AE2's table cell: up to four half-size lines right-aligned against the stack icon. */
    private void drawCell(AEKey key, List<String> lines, List<Integer> colors, int cellX, int cellY) {
        int itemX = cellX + CELL_WIDTH - 19;
        float lineHeight = fontRendererObj.FONT_HEIGHT * 0.5F + 1;
        float textY = cellY + (CELL_HEIGHT - lines.size() * lineHeight) / 2F + 1;
        GL11.glPushMatrix();
        GL11.glScalef(0.5F, 0.5F, 1);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int width = fontRendererObj.getStringWidth(line);
            fontRendererObj.drawString(line, (int) ((itemX - 2 - width * 0.5F) * 2), (int) (textY * 2), colors.get(i));
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
        Gui.drawRect(TABLE_X, TABLE_Y, TABLE_X + 3 * (CELL_WIDTH + 1) - 1, 188 + extra(), 0xF0202028);
        int y = TABLE_Y + 10;
        y = drawWrapped(EnumChatFormatting.BOLD + I18n.format("gui.neoecoae.crafting_report.error.title"), y, 0xFF6A75);
        y = drawWrapped(I18n.format(state.error.translationKey()), y + 6, TEXT_LIGHT);
        if (!state.errorDetail.isEmpty()) drawWrapped(state.errorDetail, y + 6, 0xAAA4B2);
    }

    private int drawWrapped(String text, int y, int color) {
        @SuppressWarnings("unchecked")
        List<String> lines = fontRendererObj.listFormattedStringToWidth(text, 3 * (CELL_WIDTH + 1) - 12);
        for (String line : lines) {
            if (y > 150 + extra()) break;
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

    @Override
    protected void actionPerformed(GuiButton button) {
        super.actionPerformed(button);
        boolean backwards = Mouse.isButtonDown(1);
        if (cpuTable.actionPerformed(button, backwards)) return;
        if (button == styleButton) {
            // Local to this page like GTNH's: the terminals keep their own style.
            style = (style + (backwards ? STYLE_ICONS.length - 1 : 1)) % STYLE_ICONS.length;
            setWorldAndResolution(mc, width, height);
            return;
        }
        if (button == sortByButton || button == sortDirButton) {
            GuiImgButton setting = (GuiImgButton) button;
            Enum<?> next = Platform.rotateEnum(
                setting.getCurrentValue(),
                backwards,
                setting.getSetting()
                    .getPossibleValues());
            setting.set(next);
            AEConfig.instance.settings.putSetting(setting.getSetting(), next);
            if (button == sortByButton) sortMode = (CraftingSortOrder) next;
            else sortDir = (SortDir) next;
            return;
        }
        boolean force = ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown());
        if (button == start) submit(force, false);
        else if (button == startWithFollow) submit(force, true);
        else if (button == selectCpu) cpuTable.cycleCPU(backwards);
        else if (button == cancel) {
            addMissingToBookmarks();
            goBack();
        } else if (button == retry) submit(lastSubmissionForced, lastSubmissionFollow);
        else if (button == back) ECOCraftConfirmPackets.clearError(container.windowId);
        else if (button == graph || button == viewButton) toggleView();
        else if (button == saveButton) saveAsImage();
        else if (button == treeFitButton) planTree.fit();
        else if (button == treeDepthButton) planTree.cycleDepth();
        else if (button == treeMissingButton) planTree.toggleMissingOnly();
    }

    private void submit(boolean forced, boolean follow) {
        if (state.error == null && !(follow ? startWithFollow : start).enabled) return;
        lastSubmissionForced = forced;
        lastSubmissionFollow = follow;
        // An extended amount or a forced cycle shortage goes to an ECO CPU as a big order (1.21).
        if (ECOCraftConfirmRules.usesBigOrderSubmission(state, forced))
            ECOCraftConfirmPackets.startBigOrder(container.windowId, forced);
        else ECOCraftConfirmPackets.start(container.windowId, forced, follow);
    }

    /** Switches between the report tables and the plan tree of the plan on screen. */
    private void toggleView() {
        if (!treeMode && (report == null || !state.reportReady)) return;
        treeMode = !treeMode;
        treeQuery = null;
    }

    /** GTNH: Shift+Cancel bookmarks the missing materials in NEI under the requested item. */
    private void addMissingToBookmarks() {
        if (!isShiftKeyDown() || report == null || !report.hasMissing()) return;
        List<ItemStack> missing = new ArrayList<>();
        for (ECOCraftConfirmReport.Row row : report.materials) {
            if (row.missing.signum() <= 0) continue;
            IAEStack<?> stack = row.key.toNative(1);
            ItemStack display = stack == null ? null : stack.getItemStackForNEI();
            if (display != null) missing.add(display);
        }
        IAEStack<?> output = container.getItemToCraft();
        if (output == null || missing.isEmpty()) return;
        NeiBookmarks.add(output.getItemStackForNEI(), missing);
    }

    /** GTNH's "Save as image" over the rows on screen, with the same amounts, rounds and usage shares. */
    private void saveAsImage() {
        List<IAEStack<?>> visual = new ArrayList<>();
        IAEStackList storage = new IAEStackList();
        IAEStackList pending = new IAEStackList();
        IAEStackList missing = new IAEStackList();
        for (ECOCraftConfirmReport.Row row : visibleMaterials()) {
            IAEStack<?> shown = row.key.toNative(1);
            if (shown == null) continue;
            visual.add(shown);
            if (row.stored.signum() > 0) {
                IAEStack<?> stack = row.key.toNative(clamp(row.stored));
                float percent = usedPercent(row);
                if (stack != null) {
                    if (percent > 0) stack.setUsedPercent(percent);
                    storage.add(stack);
                }
            }
            if (row.craft.signum() > 0) {
                IAEStack<?> stack = row.key.toNative(clamp(row.craft));
                if (stack != null) {
                    stack.setCountRequestableCrafts(clamp(row.rounds));
                    pending.add(stack);
                }
            }
            if (row.missing.signum() > 0) {
                IAEStack<?> stack = row.key.toNative(clamp(row.missing));
                if (stack != null) missing.add(stack);
            }
        }
        if (!visual.isEmpty()) GuiCraftingList.saveScreenShot(this, visual, storage, pending, missing);
    }

    private static long clamp(BigInteger value) {
        return value.bitLength() < 63 ? value.longValue() : Long.MAX_VALUE;
    }

    private void goBack() {
        try {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis());
        } catch (Exception failure) {
            mc.thePlayer.closeScreen();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        search.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int key) {
        boolean enter = key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER;
        if (search.isFocused() && key != Keyboard.KEY_ESCAPE) {
            // Enter in the tree view jumps to the next match, as in GTNH's tree search.
            if (enter && treeMode) {
                planTree.findNext();
                return;
            }
            if (!enter && search.textboxKeyTyped(character, key)) return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (state.error == null && start.enabled)
                submit(ECOCraftConfirmRules.isForceStart(state, isShiftKeyDown()), false);
            return;
        }
        super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int localX = mouseX - guiLeft;
        int localY = mouseY - guiTop;
        boolean overSearch = mouseX >= search.xPosition && mouseX < search.xPosition + search.width
            && mouseY >= search.yPosition
            && mouseY < search.yPosition + search.height;
        search.mouseClicked(mouseX, mouseY, button);
        // GTNH: right-clicking the search box clears it.
        if (overSearch && button == 1) search.setText("");
        if (treeMode) {
            if (state.error == null && planTree.click(localX, localY, button)) return;
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        tableScroll.click(this, localX, localY);
        cycleScroll.click(this, localX, localY);
        cpuTable.mouseClicked(localX, localY - CPU_LIST_SHIFT, button);
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
        if (treeMode) {
            if (button == 0) planTree.drag(mouseX - guiLeft, mouseY - guiTop);
        } else {
            tableScroll.clickMove(mouseY - guiTop);
            cycleScroll.clickMove(mouseY - guiTop);
            cpuTable.mouseClickMove(mouseX - guiLeft, mouseY - guiTop - CPU_LIST_SHIFT);
        }
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state != -1) planTree.release();
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        if (treeMode) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                int x = Mouse.getEventX() * width / mc.displayWidth;
                int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
                if (planTree.wheel(x - guiLeft, y - guiTop, wheel)) return;
            }
        } else if (cpuTable.handleMouseInput(guiLeft, guiTop + CPU_LIST_SHIFT)) return;
        super.handleMouseInput();
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        int localX = x - guiLeft;
        int localY = y - guiTop;
        boolean cpuColumn = localX + w > -CPU_PANEL_WIDTH && localX < 0
            && localY + h > 0
            && localY < CPU_PANEL_HEIGHT + extra();
        boolean leftButtons = localX + w > -36 && localX < 0 && localY + h > ySize - 18 && localY < ySize;
        boolean sideButtons = localX + w > WIDTH && localX < SIDE_X + 18 && localY + h > 0 && localY < 66;
        return cpuColumn || leftButtons || sideButtons;
    }

    @Override
    protected boolean mouseWheelEvent(int mouseX, int mouseY, int wheel) {
        if (treeMode) return true;
        int localX = mouseX - guiLeft;
        if (localX >= CYCLE_X) cycleScroll.wheel(wheel);
        else tableScroll.wheel(wheel);
        return true;
    }

    private @Nullable ECOCycleItemList.Entry cycleEntryAt(int localX, int localY) {
        if (localX < CYCLE_X || localX >= CYCLE_X + CELL_WIDTH || localY < TABLE_Y) return null;
        int row = (localY - TABLE_Y) / ROW_PITCH;
        if (row >= rows || localY >= TABLE_Y + row * ROW_PITCH + CELL_HEIGHT) return null;
        int index = cycleScroll.getCurrentScroll() + row;
        List<ECOCycleItemList.Entry> items = report.cycles.items();
        return index < items.size() ? items.get(index) : null;
    }

    @Override
    public ItemStack getHoveredStack() {
        return hoveredStack;
    }

    /** A button whose label shrinks to its width, so the four bottom buttons fit the narrow bottom panel. */
    private static final class FitButton extends GuiButton {

        FitButton(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            String label = displayString;
            boolean hovered = over(this, mouseX, mouseY);
            field_146123_n = hovered;
            // Vanilla stretches only horizontally; draw the four corners so a button lower than 20px keeps its
            // bottom edge.
            minecraft.getTextureManager()
                .bindTexture(buttonTextures);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int v = 46 + getHoverState(hovered) * 20;
            int left = width / 2;
            int right = width - left;
            int top = height / 2;
            int bottom = height - top;
            drawTexturedModalRect(xPosition, yPosition, 0, v, left, top);
            drawTexturedModalRect(xPosition + left, yPosition, 200 - right, v, right, top);
            drawTexturedModalRect(xPosition, yPosition + top, 0, v + 20 - bottom, left, bottom);
            drawTexturedModalRect(xPosition + left, yPosition + top, 200 - right, v + 20 - bottom, right, bottom);
            mouseDragged(minecraft, mouseX, mouseY);
            int color = !enabled ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0;
            FontRenderer font = minecraft.fontRenderer;
            int textWidth = font.getStringWidth(label);
            float scale = Math.min(1F, (width - 6) / (float) Math.max(1, textWidth));
            GL11.glPushMatrix();
            GL11.glTranslatef(
                xPosition + width / 2F - textWidth * scale / 2F,
                yPosition + (height - 8 * scale) / 2F,
                0);
            GL11.glScalef(scale, scale, 1);
            font.drawStringWithShadow(label, 0, 0, color);
            GL11.glPopMatrix();
        }
    }

    /** NEI is optional at link time; its classes are touched only when a bookmark is actually made. */
    private static final class NeiBookmarks {

        static void add(ItemStack output, List<ItemStack> missing) {
            try {
                appeng.integration.modules.NEI nei = appeng.integration.modules.NEI.instance;
                if (nei != null) nei.addToBookmark(output, missing);
            } catch (RuntimeException | LinkageError ignored) {
                // Without NEI there is no bookmark panel to add to.
            }
        }
    }
}
