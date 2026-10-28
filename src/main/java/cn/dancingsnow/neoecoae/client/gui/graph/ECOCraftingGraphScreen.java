package cn.dancingsnow.neoecoae.client.gui.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CompactTreeNode;
import cn.dancingsnow.neoecoae.crafting.graph.CompactTreeProjection;
import cn.dancingsnow.neoecoae.crafting.graph.CycleCluster;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayout;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutCache;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmState;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftingGraphStream;
import cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftConfirmClient;
import cn.dancingsnow.neoecoae.platform.ui.crafting.ECOCraftingGraphClient;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Full-screen immediate-mode ECO crafting DAG and SCC viewer (1.21's ECOCraftingGraphScreen). It is opened over the
 * ECO crafting report: the confirmation container stays open underneath, the graph of the report's plan is fetched
 * on demand, and closing the graph returns to the same report screen.
 */
@SideOnly(Side.CLIENT)
public final class ECOCraftingGraphScreen extends GuiScreen {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");
    private static final int TOOLBAR_HEIGHT = GraphViewTransform.TOOLBAR_HEIGHT;
    /** Larger snapshots are refused before layout rather than stalling the client thread. */
    static final int CLIENT_NODE_LIMIT = 250_000;
    private static final int SEARCH_HEIGHT = 18;

    private final GuiScreen previous;
    private final Container container;
    private final @Nullable Integer requestedCycleComponentId;
    private final List<ECOCycleItemList.Entry> cycleItems;
    private long version;
    private @Nullable ECOCraftingGraphStream.Status status;
    private @Nullable CraftingGraphSnapshot snapshot;
    private @Nullable Integer initialCycleComponentId;
    private GraphLayoutCache layoutCache;
    private final GraphRenderer renderer = new GraphRenderer();
    private final GraphProfiler profiler = new GraphProfiler();
    private final Set<Integer> collapsed = new HashSet<>();
    private final Set<String> expandedTreePaths = new HashSet<>();
    private final Set<String> fullyExpandedTreePaths = new HashSet<>();
    private final Set<String> collapsedTreePaths = new HashSet<>();
    private @Nullable ClientCraftingGraph baseGraph;
    private @Nullable ClientCraftingGraph graph;
    private @Nullable GraphLayoutSnapshot layout;
    private GraphCanvas canvas;
    private GuiTextField search;
    private GuiButton backButton;
    private GuiButton fitButton;
    private GuiButton depthButton;
    private GuiButton missingButton;
    private final List<ClientCraftingGraph.Node> searchSuggestions = new ArrayList<>();
    private @Nullable Integer selectedId;
    private @Nullable ClientCraftingGraph.Node hovered;
    private float cameraX;
    private float cameraY = TOOLBAR_HEIGHT;
    private float zoom = 1;
    private int depth = CompactTreeProjection.DEFAULT_DEPTH;
    private boolean missingOnly;
    private final GraphLayout.Mode layoutMode;
    private final boolean advanced = false;
    private boolean panning;
    private int dragX;
    private int dragY;
    private long lastClickMillis;
    private int lastClickNode = Integer.MAX_VALUE;
    private boolean lastClickWasFolder;
    private boolean fitted;
    private @Nullable CycleCluster parentCluster;

    /**
     * @param previous   the report screen to return to
     * @param container  the confirmation container whose plan is shown
     * @param version    report version of the plan on screen
     * @param cycle      cycle component the report has selected, or {@code null}
     * @param cycleItems the report's cycle rows, used to focus the selected cycle's material
     */
    public ECOCraftingGraphScreen(GuiScreen previous, Container container, long version, @Nullable Integer cycle,
        List<ECOCycleItemList.Entry> cycleItems) {
        this.previous = previous;
        this.container = container;
        this.version = version;
        this.requestedCycleComponentId = cycle;
        this.cycleItems = cycleItems;
        this.layoutMode = GraphLayout.Mode.fromSystemProperty();
        this.layoutCache = new GraphLayoutCache(layoutMode);
    }

    @Override
    public void initGui() {
        super.initGui();
        renderer.close();
        canvas = new GraphCanvas(mc);
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        String query = search == null ? "" : search.getText();
        boolean focused = search != null && search.isFocused();
        int x = 5;
        backButton = addButton(0, x, 40, I18n.format("gui.neoecoae.crafting_graph.toolbar.back"));
        x += 43;
        fitButton = addButton(1, x, 48, tr("toolbar.fit_all"));
        x += 51;
        depthButton = addButton(2, x, 66, depthLabel());
        x += 69;
        missingButton = addButton(3, x, 74, tr(missingOnly ? "toolbar.missing_on" : "toolbar.missing_off"));
        x += 77;
        search = new GuiTextField(
            fontRendererObj,
            x + 4,
            6,
            Math.min(180, Math.max(80, width - x - 250)),
            SEARCH_HEIGHT);
        search.setMaxStringLength(64);
        search.setText(query);
        search.setFocused(focused);
        updateSearchSuggestions();
        if (graph != null) rebuildGraph(!fitted);
    }

    private GuiButton addButton(int id, int x, int width, String label) {
        GuiButton button = new GuiButton(id, x, 5, width, 20, label);
        buttonList.add(button);
        return button;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (search != null) search.updateCursorCounter();
        if (mc.thePlayer != null && mc.thePlayer.openContainer == mc.thePlayer.inventoryContainer) {
            // The server closed the confirmation underneath the graph; there is no report left to return to.
            mc.displayGuiScreen(null);
            return;
        }
        poll();
    }

    /** Follows the report's plan version and picks up the graph once the server delivered it. */
    private void poll() {
        ECOCraftConfirmState state = ECOCraftConfirmClient.state(container);
        if (state.reportReady && state.reportVersion != version) {
            version = state.reportVersion;
            resetGraph();
        }
        if (status != null) return;
        ECOCraftingGraphClient.Result result = ECOCraftingGraphClient.get(container, version);
        if (result != null) accept(result);
    }

    private void resetGraph() {
        status = null;
        snapshot = null;
        baseGraph = null;
        graph = null;
        layout = null;
        selectedId = null;
        hovered = null;
        fitted = false;
        collapsed.clear();
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        searchSuggestions.clear();
        parentCluster = null;
        renderer.close();
    }

    private void accept(ECOCraftingGraphClient.Result result) {
        status = result.status;
        if (result.status != ECOCraftingGraphStream.Status.READY || result.snapshot == null) return;
        CraftingGraphSnapshot received = result.snapshot;
        if (ECOCraftingGraphStream.isEmpty(received)) {
            status = ECOCraftingGraphStream.Status.EMPTY;
            return;
        }
        if ((long) received.nodes()
            .size() + received.patterns()
                .size()
            > CLIENT_NODE_LIMIT) {
            status = ECOCraftingGraphStream.Status.TOO_LARGE;
            return;
        }
        try {
            snapshot = received;
            initialCycleComponentId = resolveInitialCycle(received, requestedCycleComponentId);
            AEKey focusedMaterial = null;
            if (initialCycleComponentId != null) for (ECOCycleItemList.Entry entry : cycleItems) {
                if (entry.componentId() == initialCycleComponentId) {
                    focusedMaterial = entry.what();
                    break;
                }
            }
            baseGraph = initialCycleComponentId == null ? ClientCraftingGraph.main(received, advanced)
                : ClientCraftingGraph.cycle(received, initialCycleComponentId, advanced, focusedMaterial);
            selectedId = baseGraph.focusedMaterialId() != null ? baseGraph.focusedMaterialId() : baseGraph.rootId();
            rebuildGraph(true);
        } catch (RuntimeException | StackOverflowError failure) {
            LOGGER.warn("[craft-graph] Laying out the crafting graph failed", failure);
            resetGraph();
            status = ECOCraftingGraphStream.Status.FAILED;
        }
    }

    /** The report's selected cycle when the snapshot knows it, else the only cycle (1.21's openGraph). */
    static @Nullable Integer resolveInitialCycle(CraftingGraphSnapshot snapshot, @Nullable Integer requested) {
        if (requested != null) {
            for (var cycle : snapshot.cycleGroups()) if (cycle.componentId() == requested) return requested;
        }
        return snapshot.cycleGroups()
            .size() == 1 ? JdkCompat.first(snapshot.cycleGroups())
                .componentId() : null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (status == null) poll();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawRect(0, 0, width, height, 0xff0e1115);
        drawRect(0, 0, width, TOOLBAR_HEIGHT, 0xff242a31);
        List<String> tooltip = JdkCompat.listOf();
        hovered = null;
        if (graph != null && layout != null) {
            int graphMouseX = mouseY >= TOOLBAR_HEIGHT ? mouseX : -1;
            int graphMouseY = mouseY >= TOOLBAR_HEIGHT ? mouseY : -1;
            enableScissor(0, TOOLBAR_HEIGHT, width, height - TOOLBAR_HEIGHT);
            try {
                var frame = renderer.render(
                    canvas,
                    graph,
                    layout,
                    cameraX,
                    cameraY,
                    zoom,
                    width,
                    height,
                    guiScale(),
                    graphMouseX,
                    graphMouseY,
                    selectedId,
                    profiler);
                hovered = frame.hovered();
                tooltip = frame.tooltip();
            } finally {
                canvas.flush();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        } else drawStatus();
        GL11.glColor4f(1, 1, 1, 1);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawSearch();
        drawSearchSuggestions(mouseX, mouseY);
        if (graph != null && layout != null) {
            drawBreadcrumb();
            drawStats();
            if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) drawCycleDetails();
            else if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) drawClusterDetails();
            else if (graph.isCompactTree()) drawDetailsPanel();
        }
        canvas.flush();
        if (!tooltip.isEmpty() && mouseY >= TOOLBAR_HEIGHT) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            var tooltipCycle = cycleForTooltipNode(hovered);
            if (tooltipCycle != null) drawCycleTooltipRing(tooltipCycle, mouseX, mouseY);
            canvas.flush();
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void drawStatus() {
        String key;
        if (status == null) key = "gui.neoecoae.crafting_graph.loading";
        else key = switch (status) {
            case EMPTY -> "gui.neoecoae.crafting_graph.empty";
            case TOO_LARGE -> "gui.neoecoae.crafting_graph.too_large";
            case FAILED -> "gui.neoecoae.crafting_graph.failed";
            case READY -> "gui.neoecoae.crafting_graph.loading";
        };
        @SuppressWarnings("unchecked")
        List<String> lines = fontRendererObj.listFormattedStringToWidth(I18n.format(key), Math.max(40, width - 40));
        int y = TOOLBAR_HEIGHT + (height - TOOLBAR_HEIGHT - lines.size() * 10) / 2;
        for (String line : lines) {
            drawCenteredString(fontRendererObj, line, width / 2, y, 0xffaab3bd);
            y += 10;
        }
    }

    private void drawSearch() {
        search.drawTextBox();
        if (search.getText()
            .isEmpty() && !search.isFocused())
            fontRendererObj.drawString(
                canvas.fit(tr("search_hint"), search.getWidth() - 8),
                search.xPosition + 4,
                search.yPosition + (SEARCH_HEIGHT - 8) / 2,
                0xff707a84);
    }

    private void enableScissor(int x, int y, int w, int h) {
        int scale = guiScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + h) * scale, Math.max(0, w * scale), Math.max(0, h * scale));
    }

    private int guiScale() {
        return new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
    }

    private @Nullable CraftingGraphSnapshot.CycleGroup cycleForTooltipNode(@Nullable ClientCraftingGraph.Node node) {
        if (node == null || snapshot == null) return null;
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return node.cycle();
        if (node.kind() != ClientCraftingGraph.Kind.MATERIAL || node.material() == null) return null;
        int sourceId = node.material()
            .nodeId();
        for (var cycle : snapshot.cycleGroups()) if (cycle.memberNodeIds()
            .contains(sourceId)) return cycle;
        return null;
    }

    /** Draws a compact cycle summary over the tooltip without changing vanilla tooltip layout. */
    private void drawCycleTooltipRing(CraftingGraphSnapshot.CycleGroup cycle, int mouseX, int mouseY) {
        int radius = 18;
        int centerX = Math.min(width - radius - 4, mouseX + 112);
        int centerY = Math.max(radius + TOOLBAR_HEIGHT + 2, mouseY - 24);
        canvas.fill(centerX - radius - 5, centerY - radius - 5, centerX + radius + 6, centerY + radius + 6, 0xee171b20);
        int activeColor = cycleStatusColor(cycle.status());
        int segments = 12;
        int activeSegments = Math.max(
            1,
            Math.min(
                segments,
                cycle.memberNodeIds()
                    .size()));
        for (int i = 0; i < segments; i++) {
            double angle = -Math.PI / 2 + i * (Math.PI * 2 / segments);
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            canvas.fill(x - 2, y - 2, x + 3, y + 3, i < activeSegments ? activeColor : 0xff56616c);
        }
        canvas.centered(
            HostText.expandedNumber(
                cycle.memberNodeIds()
                    .size()),
            centerX,
            centerY - canvas.lineHeight() / 2,
            0xffe8edf2);
    }

    private static int cycleStatusColor(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SOLVED", "PLANNED", "READY" -> 0xff68b58a;
            case "UNSUPPORTED", "DISABLED" -> 0xffa36c9a;
            case "FAILED", "MISSING" -> 0xffcf5e5e;
            default -> 0xffd59b45;
        };
    }

    private void drawBreadcrumb() {
        String breadcrumb = switch (graph.view()) {
            case MAIN -> tr("breadcrumb.plan");
            case CYCLE_FOCUS -> tr("breadcrumb.cycle", graph.focusedCycleId());
            case CYCLE_CLUSTER -> tr(
                "breadcrumb.cluster",
                graph.focusedCluster() == null ? -1
                    : graph.focusedCluster()
                        .clusterId());
        };
        fontRendererObj.drawString(breadcrumb, 7, height - 14, 0xffc6d0da);
    }

    private void drawStats() {
        String stats = String.format(
            Locale.ROOT,
            "nodes %,d/%,d  edges %,d/%,d  zoom %.0f%%  layout %,.2f ms  index %,.2f ms  render %,.2f ms  v%d",
            profiler.visibleNodes(),
            snapshot.summary()
                .materialNodes()
                + snapshot.summary()
                    .patternNodes(),
            profiler.visibleEdges(),
            snapshot.summary()
                .edges(),
            zoom * 100,
            layout.layoutNanos() / 1_000_000.0,
            layout.spatialIndexNanos() / 1_000_000.0,
            profiler.renderNanos() / 1_000_000.0,
            layout.version());
        fontRendererObj
            .drawString(stats, Math.max(7, width - fontRendererObj.getStringWidth(stats) - 7), height - 14, 0xff8e9aa6);
    }

    private void drawCycleDetails() {
        CraftingGraphSnapshot.CycleGroup cycle = null;
        for (var value : snapshot.cycleGroups()) if (value.componentId() == graph.focusedCycleId()) cycle = value;
        if (cycle == null) return;
        int panelWidth = 250;
        int left = width - panelWidth - 8;
        int top = 38;
        int lines = 7;
        drawRect(left, top, width - 8, top + 10 + lines * 12, 0xdd171b20);
        fontRendererObj.drawString(tr("details.cycle_title", cycle.componentId()), left + 7, top + 6, 0xffe2b766);
        int y = top + 19;
        y = textLine(left, panelWidth, y, "status", statusText(cycle.status()));
        y = textLine(left, panelWidth, y, "seed", summarizeAmounts(cycle.requiredSeed()));
        y = textLine(left, panelWidth, y, "external", summarizeAmounts(cycle.externalInputs()));
        y = textLine(left, panelWidth, y, "execute", summarizePatterns(cycle));
        y = line(
            left,
            y,
            "witness_steps",
            cycle.executionWitness()
                .size());
        y = line(
            left,
            y,
            "members",
            cycle.memberNodeIds()
                .size());
        line(
            left,
            y,
            "required_outputs",
            cycle.requiredOutputs()
                .size());
    }

    private int line(int left, int y, String keySuffix, long value) {
        fontRendererObj.drawString(tr("details." + keySuffix, value), left + 7, y, 0xffaeb8c2);
        return y + 12;
    }

    private void drawClusterDetails() {
        CycleCluster cluster = graph.focusedCluster();
        if (cluster == null) return;
        int panelWidth = 250;
        int left = width - panelWidth - 8;
        int top = 38;
        drawRect(left, top, width - 8, top + 70, 0xdd171b20);
        fontRendererObj.drawString(tr("details.cluster_title", cluster.clusterId()), left + 7, top + 6, 0xffe2b766);
        int y = top + 20;
        y = line(
            left,
            y,
            "cluster_cycles",
            cluster.componentIds()
                .size());
        y = line(
            left,
            y,
            "cluster_flows",
            cluster.flows()
                .size());
        fontRendererObj.drawString(tr("details.cluster_hint"), left + 7, y, 0xffaeb8c2);
    }

    private int textLine(int left, int panelWidth, int y, String keySuffix, String value) {
        String label = tr("details." + keySuffix + "_label");
        int valueX = left + 7 + fontRendererObj.getStringWidth(label);
        fontRendererObj.drawString(label, left + 7, y, 0xffaeb8c2);
        fontRendererObj.drawString(
            canvas.fit(value, panelWidth - 14 - fontRendererObj.getStringWidth(label)),
            valueX,
            y,
            0xffe2b766);
        return y + 12;
    }

    private String summarizeAmounts(List<CraftingGraphSnapshot.KeyAmount> values) {
        if (values.isEmpty()) return "-";
        StringBuilder text = new StringBuilder();
        int shown = Math.min(2, values.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append(", ");
            var value = values.get(i);
            text.append(
                value.key() == null ? "?"
                    : value.key()
                        .getDisplayName())
                .append(" ×")
                .append(GraphRenderer.compactAmount(Long.toString(value.amount())));
        }
        if (values.size() > shown) text.append(tr("summary.more", values.size() - shown));
        return text.toString();
    }

    private String summarizePatterns(CraftingGraphSnapshot.CycleGroup cycle) {
        if (cycle.patternTimes()
            .isEmpty()) return "-";
        StringBuilder text = new StringBuilder();
        int shown = Math.min(
            2,
            cycle.patternTimes()
                .size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append(", ");
            var value = cycle.patternTimes()
                .get(i);
            CraftingGraphSnapshot.PatternNode pattern = null;
            for (var candidate : snapshot.patterns()) if (candidate.patternNodeId() == value.patternNodeId()) {
                pattern = candidate;
                break;
            }
            text.append(pattern == null ? "Pattern #" + value.patternNodeId() : pattern.displayIdentity())
                .append(" ×")
                .append(GraphRenderer.compactAmount(Long.toString(value.amount())));
        }
        if (cycle.patternTimes()
            .size() > shown)
            text.append(" +")
                .append(
                    cycle.patternTimes()
                        .size() - shown)
                .append(tr("summary.more_suffix"));
        return text.toString();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || graph == null || layout == null) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (mouseY < TOOLBAR_HEIGHT) return;
        int steps = wheel > 0 ? 1 : -1;
        if (graph.isCompactTree() && !isCtrlKeyDown()) {
            // The 1.7.10 wheel has one axis; shift turns it sideways like a horizontal wheel.
            if (isShiftKeyDown()) cameraX += steps * 28;
            else cameraY += steps * 28;
            return;
        }
        float oldZoom = zoom;
        zoom = GraphViewTransform.scrollZoom(zoom, steps);
        float worldX = (mouseX - cameraX) / oldZoom;
        float worldY = (mouseY - cameraY) / oldZoom;
        cameraX = mouseX - worldX * zoom;
        cameraY = mouseY - worldY * zoom;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && selectSearchSuggestion(mouseX, mouseY)) return;
        search.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && overButton(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (search.isFocused() || graph == null || layout == null) return;
        if (button == 0 && mouseY >= height - 20 && graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            goRoot();
            return;
        }
        if (mouseY < TOOLBAR_HEIGHT) return;
        ClientCraftingGraph.Node hit = hit(mouseX, mouseY);
        if (button == 1 && hit != null && graph.isCompactTree()) {
            selectedId = hit.id();
            collapseSelected();
            return;
        }
        if (button == 0 && hit != null) {
            selectedId = hit.id();
            long now = Minecraft.getSystemTime();
            boolean doubleClick = lastClickNode == hit.id() && now - lastClickMillis < 350;
            // A one-level expansion replaces a folder with its source node at the same path/id. Keep the
            // previous folder gesture only while the second click is on that same projected occurrence.
            boolean sameFolderOccurrence = lastClickWasFolder && lastClickNode == hit.id();
            if (graph.isCompactTree() && (hit.kind() == ClientCraftingGraph.Kind.FOLDER || sameFolderOccurrence)) {
                if (doubleClick && sameFolderOccurrence) expandAllSelected();
                else expandSelected();
            } else if (doubleClick) activate(hit);
            lastClickWasFolder = hit.kind() == ClientCraftingGraph.Kind.FOLDER;
            lastClickNode = hit.id();
            lastClickMillis = now;
            return;
        }
        if (button == 2 || button == 0) {
            panning = true;
            dragX = mouseX;
            dragY = mouseY;
        }
    }

    private boolean overButton(int mouseX, int mouseY) {
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (button.visible && mouseX >= button.xPosition
                && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height) return true;
        }
        return false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceClick) {
        if (panning) {
            cameraX += mouseX - dragX;
            cameraY += mouseY - dragY;
            dragX = mouseX;
            dragY = mouseY;
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state != -1) panning = false;
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == backButton) goBack();
        else if (button == fitButton) fitAll();
        else if (button == depthButton) cycleDepth();
        else if (button == missingButton) toggleMissingOnly();
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            goBack();
            return;
        }
        if (search.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) findNext();
            else if (search.textboxKeyTyped(character, key)) updateSearchSuggestions();
            return;
        }
        if (graph == null) return;
        if (key == Keyboard.KEY_F) fitAll();
        else if (key == Keyboard.KEY_HOME) goRoot();
    }

    /** Back to the report over the same, still open confirmation container. */
    private void goBack() {
        if (mc.thePlayer != null && mc.thePlayer.openContainer == container) mc.displayGuiScreen(previous);
        else mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        renderer.close();
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private void activate(ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER && node.cluster() != null) {
            parentCluster = null;
            baseGraph = ClientCraftingGraph.cluster(snapshot, node.cluster(), advanced);
            selectedId = baseGraph.rootId();
            collapsed.clear();
            rebuildGraph(true);
        } else if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP && node.cycle() != null) {
            parentCluster = null;
            baseGraph = ClientCraftingGraph.cycle(
                snapshot,
                node.cycle()
                    .componentId(),
                advanced);
            selectedId = baseGraph.rootId();
            collapsed.clear();
            rebuildGraph(true);
        } else if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            expandAllSelected();
        } else if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER
            && (node.kind() == ClientCraftingGraph.Kind.MATERIAL || node.kind() == ClientCraftingGraph.Kind.PATTERN)) {
                Integer componentId = componentForClusterNode(node);
                if (componentId != null) {
                    parentCluster = graph.focusedCluster();
                    baseGraph = ClientCraftingGraph.cycle(
                        snapshot,
                        componentId,
                        advanced,
                        node.kind() == ClientCraftingGraph.Kind.MATERIAL ? node.key() : null);
                    selectedId = baseGraph.rootId();
                    rebuildGraph(true);
                }
            } else if (node.kind() == ClientCraftingGraph.Kind.MATERIAL) {
                selectedId = node.id();
                rebuildGraph(true);
            }
    }

    private void goRoot() {
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && parentCluster != null) {
            baseGraph = ClientCraftingGraph.cluster(snapshot, parentCluster, advanced);
            parentCluster = null;
        } else if (graph.view() != ClientCraftingGraph.View.MAIN) {
            baseGraph = ClientCraftingGraph.main(snapshot, advanced);
            parentCluster = null;
        }
        selectedId = baseGraph.rootId();
        collapsed.clear();
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        rebuildGraph(true);
    }

    private void expandSelected() {
        if (selectedId == null) return;
        if (graph.isCompactTree()) {
            CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
            if (treeNode == null) return;
            collapsedTreePaths.remove(treeNode.path());
            fullyExpandedTreePaths.remove(treeNode.path());
            expandedTreePaths.add(treeNode.path());
        } else collapsed.remove(selectedId);
        rebuildGraph(false);
    }

    private void collapseSelected() {
        if (selectedId == null) return;
        if (graph.isCompactTree()) {
            CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
            if (treeNode == null) return;
            collapsedTreePaths.add(treeNode.path());
            expandedTreePaths.remove(treeNode.path());
            fullyExpandedTreePaths.remove(treeNode.path());
        } else if (!collapsed.add(selectedId)) collapsed.remove(selectedId);
        rebuildGraph(false);
    }

    private void expandAllSelected() {
        if (selectedId == null || !graph.isCompactTree()) return;
        CompactTreeNode treeNode = graph.compactTreeNode(selectedId);
        if (treeNode == null) return;
        collapsedTreePaths.remove(treeNode.path());
        expandedTreePaths.remove(treeNode.path());
        fullyExpandedTreePaths.add(treeNode.path());
        rebuildGraph(false);
    }

    private void cycleDepth() {
        depth = depth == 99 ? 2 : depth == 2 ? 4 : depth == 4 ? 8 : 99;
        depthButton.displayString = depthLabel();
        if (graph != null) rebuildGraph(initialCycleComponentId != null);
    }

    private void toggleMissingOnly() {
        missingOnly = !missingOnly;
        missingButton.displayString = tr(missingOnly ? "toolbar.missing_on" : "toolbar.missing_off");
        expandedTreePaths.clear();
        fullyExpandedTreePaths.clear();
        collapsedTreePaths.clear();
        if (graph != null) rebuildGraph(true);
    }

    private void rebuildGraph(boolean fit) {
        if (baseGraph == null) return;
        if (baseGraph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
            || baseGraph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
            // A cycle focus is a complete SCC explanation. Depth/collapse controls belong to MAIN projection and
            // must never silently remove a boundary or an internal pattern from this view.
            graph = baseGraph;
        } else if (layoutMode == GraphLayout.Mode.COMPACT_TREE && baseGraph.view() == ClientCraftingGraph.View.MAIN) {
            ClientCraftingGraph projectedBase = missingOnly ? baseGraph.missingPaths() : baseGraph;
            graph = CompactTreeProjection.project(
                projectedBase,
                missingOnly ? 99 : depth,
                expandedTreePaths,
                fullyExpandedTreePaths,
                collapsedTreePaths);
        } else {
            ClientCraftingGraph projectedBase = missingOnly && baseGraph.view() == ClientCraftingGraph.View.MAIN
                ? baseGraph.missingPaths()
                : baseGraph;
            int focus = selectedId != null && projectedBase.nodes()
                .containsKey(selectedId) ? selectedId : projectedBase.rootId();
            graph = projectedBase.limited(focus, missingOnly ? 99 : depth, collapsed);
        }
        layoutCache.invalidate();
        layout = layoutCache.get(graph);
        if (fit) fitAll();
    }

    private void fitAll() {
        if (layout == null || layout.boxes()
            .isEmpty()) return;
        var bounds = layout.bounds();
        float detailsReserve = graph.isCompactTree() || graph.view() != ClientCraftingGraph.View.MAIN ? 258 : 0;
        float availableWidth = Math.max(1, width - 40 - detailsReserve);
        float availableHeight = Math.max(1, height - TOOLBAR_HEIGHT - 24);
        zoom = GraphViewTransform.fitZoom(bounds, availableWidth, availableHeight);
        cameraX = 20 - bounds.left() * zoom + (availableWidth - bounds.width() * zoom) / 2;
        cameraY = TOOLBAR_HEIGHT + 12 - bounds.top() * zoom + (availableHeight - bounds.height() * zoom) / 2;
        fitted = true;
    }

    private void findNext() {
        if (baseGraph == null) return;
        String query = search.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        for (var node : baseGraph.nodes()
            .values()) {
            if (matchesItemSearch(node, query)) {
                focusSearchMatch(node);
                return;
            }
        }
    }

    private void center(int id) {
        var box = layout.box(id);
        if (box == null) return;
        float detailsReserve = graph.isCompactTree() || graph.view() != ClientCraftingGraph.View.MAIN ? 258 : 0;
        float usableWidth = Math.max(1, width - detailsReserve);
        float usableHeight = Math.max(1, height - TOOLBAR_HEIGHT);
        cameraX = usableWidth / 2.0f - box.centerX() * zoom;
        cameraY = TOOLBAR_HEIGHT + usableHeight / 2.0f - box.centerY() * zoom;
    }

    private @Nullable ClientCraftingGraph.Node hit(int mouseX, int mouseY) {
        float worldX = (mouseX - cameraX) / zoom;
        float worldY = (mouseY - cameraY) / zoom;
        for (var box : layout.query(worldX, worldY, worldX, worldY, 0)) {
            if (box.contains(worldX, worldY)) return graph.nodes()
                .get(box.nodeId());
        }
        return null;
    }

    /** The material key under the given screen coordinates, for recipe lookups (1.21's getECOStackAt). */
    public @Nullable AEKey getECOStackAt(int mouseX, int mouseY) {
        if (mouseY < TOOLBAR_HEIGHT || layout == null || graph == null) return null;
        ClientCraftingGraph.Node node = hit(mouseX, mouseY);
        return node != null && node.kind() == ClientCraftingGraph.Kind.MATERIAL ? node.key() : null;
    }

    private void drawDetailsPanel() {
        if (selectedId == null) return;
        var node = graph.nodes()
            .get(selectedId);
        if (node == null) return;
        int panelWidth = 220;
        int left = width - panelWidth - 8;
        int top = 38;
        int lines = 9;
        drawRect(left, top, width - 8, top + 12 + lines * 12, 0xdd171b20);
        boolean folderNode = node.kind() == ClientCraftingGraph.Kind.FOLDER;
        fontRendererObj.drawString(
            canvas.fit(folderNode ? tr("details.folder") : node.label(), panelWidth - 14),
            left + 7,
            top + 6,
            folderNode ? 0xff9fb3c2 : 0xffe2b766);
        int y = top + 20;
        if (folderNode) {
            var folder = graph.compactTreeNode(node.id());
            if (folder != null) {
                y = line(left, y, "hidden_nodes", folder.hiddenNodeCount());
                y = line(left, y, "hidden_layers", folder.hiddenDepth());
                y = line(left, y, "depth", folder.depth());
                y = detailTextLine(left, y, "missing", yesNo(folder.containsMissing()));
                y = detailTextLine(left, y, "unsupported", yesNo(folder.containsUnsupported()));
                y = detailTextLine(left, y, "cycle", yesNo(folder.containsCycle()));
                detailTextLine(left, y, "action", tr("action.expand"));
            }
        } else if (node.material() != null) {
            var material = node.material();
            y = detailTextLine(left, y, "requested", GraphRenderer.compactAmount(material.exactRequested()));
            y = detailTextLine(left, y, "inventory", GraphRenderer.compactAmount(material.exactFromInventory()));
            y = detailTextLine(left, y, "to_craft", GraphRenderer.compactAmount(material.exactToCraft()));
            y = detailTextLine(left, y, "missing", GraphRenderer.compactAmount(material.exactMissing()));
            y = detailTextLine(left, y, "consumed", GraphRenderer.compactAmount(material.exactConsumed()));
            y = detailTextLine(left, y, "produced", GraphRenderer.compactAmount(material.exactProduced()));
            y = detailTextLine(
                left,
                y,
                "status",
                statusText(
                    material.status()
                        .name()));
            line(
                left,
                y,
                "source_patterns",
                baseGraph.source()
                    .patterns()
                    .size());
        } else if (node.cluster() != null) {
            var cluster = node.cluster();
            y = line(
                left,
                y,
                "cluster_cycles",
                cluster.componentIds()
                    .size());
            line(
                left,
                y,
                "cluster_flows",
                cluster.flows()
                    .size());
        } else if (node.cycle() != null) {
            var cycle = node.cycle();
            y = line(left, y, "cycle", cycle.componentId());
            y = line(
                left,
                y,
                "members",
                cycle.memberNodeIds()
                    .size());
            detailTextLine(left, y, "status", statusText(cycle.status()));
        }
    }

    private int detailTextLine(int left, int y, String keySuffix, String value) {
        fontRendererObj.drawString(tr("details." + keySuffix, value), left + 7, y, 0xffaeb8c2);
        return y + 12;
    }

    private static String yesNo(boolean value) {
        return I18n.format(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }

    private static String statusText(String status) {
        return I18n.format("gui.neoecoae.crafting_graph.status." + status.toLowerCase(Locale.ROOT));
    }

    private String depthLabel() {
        return depth == 99 ? tr("toolbar.depth_all") : tr("toolbar.depth", depth);
    }

    private void updateSearchSuggestions() {
        searchSuggestions.clear();
        if (search == null || baseGraph == null) return;
        String query = search.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        List<ClientCraftingGraph.Node> matches = new ArrayList<>();
        for (var node : baseGraph.nodes()
            .values()) if (matchesItemSearch(node, query)) matches.add(node);
        matches.sort(
            (a, b) -> a.label()
                .compareToIgnoreCase(b.label()));
        searchSuggestions.addAll(matches.subList(0, Math.min(6, matches.size())));
    }

    /** The graph search locates material occurrences; recipe/pattern nodes are not item search results. */
    static boolean matchesItemSearch(ClientCraftingGraph.Node node, String query) {
        if (node.kind() != ClientCraftingGraph.Kind.MATERIAL || node.material() == null || node.key() == null) {
            return false;
        }
        return node.label()
            .toLowerCase(Locale.ROOT)
            .contains(query)
            || node.key()
                .toString()
                .toLowerCase(Locale.ROOT)
                .contains(query);
    }

    private void drawSearchSuggestions(int mouseX, int mouseY) {
        if (search == null || searchSuggestions.isEmpty() || !search.isFocused()) return;
        int left = search.xPosition;
        int top = search.yPosition + SEARCH_HEIGHT;
        int right = left + search.getWidth();
        drawRect(left, top, right, top + searchSuggestions.size() * 18 + 2, 0xf020252b);
        for (int i = 0; i < searchSuggestions.size(); i++) {
            ClientCraftingGraph.Node node = searchSuggestions.get(i);
            int rowTop = top + 1 + i * 18;
            if (mouseX >= left && mouseX < right && mouseY >= rowTop && mouseY < rowTop + 18)
                drawRect(left, rowTop, right, rowTop + 18, 0xff3a4650);
            fontRendererObj
                .drawString(canvas.fit(node.label(), search.getWidth() - 8), left + 4, rowTop + 5, 0xffe8edf2);
        }
    }

    private boolean selectSearchSuggestion(int mouseX, int mouseY) {
        if (search == null || searchSuggestions.isEmpty() || !search.isFocused()) return false;
        int top = search.yPosition + SEARCH_HEIGHT;
        if (mouseX < search.xPosition || mouseX >= search.xPosition + search.getWidth()
            || mouseY < top
            || mouseY >= top + searchSuggestions.size() * 18 + 2) return false;
        int index = (mouseY - top - 1) / 18;
        if (index < 0 || index >= searchSuggestions.size()) return false;
        ClientCraftingGraph.Node match = searchSuggestions.get(index);
        search.setText(match.label());
        focusSearchMatch(match);
        searchSuggestions.clear();
        return true;
    }

    private void focusSearchMatch(ClientCraftingGraph.Node match) {
        if (graph == null) return;
        if (layoutMode == GraphLayout.Mode.COMPACT_TREE && baseGraph.view() == ClientCraftingGraph.View.MAIN) {
            List<String> paths = CompactTreeProjection.pathTo(baseGraph, match.id());
            expandedTreePaths.addAll(paths);
            collapsedTreePaths.removeAll(paths);
            rebuildGraph(false);
            graph.compactTreeNodes()
                .values()
                .stream()
                .filter(node -> node.sourceId() == match.id() && !node.folder())
                .sorted(Comparator.comparing(CompactTreeNode::reference))
                .findFirst()
                .ifPresent(node -> selectedId = node.id());
        } else {
            selectedId = match.id();
            if (!graph.nodes()
                .containsKey(match.id())) rebuildGraph(false);
        }
        if (selectedId != null && layout.box(selectedId) != null) {
            zoom = 1.0f;
            center(selectedId);
        }
    }

    private @Nullable Integer componentForClusterNode(ClientCraftingGraph.Node node) {
        if (node.pattern() != null) return node.pattern()
            .componentId();
        if (node.material() == null) return null;
        int materialId = node.material()
            .nodeId();
        for (var cycle : snapshot.cycleGroups()) if (cycle.memberNodeIds()
            .contains(materialId)) return cycle.componentId();
        return null;
    }

    private static String tr(String suffix, Object... args) {
        return I18n.format("gui.neoecoae.crafting_graph." + suffix, args);
    }
}
