package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.AEBaseGui;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.util.ReadableNumberConverter;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftingGraphStream;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The plan tree of the crafting report, drawn and operated like GTNH's crafting tree ({@code GuiCraftingTree}): a
 * top-down tree of requested materials, each with the step that supplies it underneath (a pattern and its executions,
 * the storage it is taken from, or the shortage), connected by light elbow lines. It reads the plan graph the server
 * sends for the confirmation, so ECO-planned and natively planned requests show the same way. Depth, missing-only and
 * fit-all are the ECO view controls on top of GTNH's drag, wheel, Shift/Ctrl wheel and collapse.
 *
 * <p>
 * The graph is a DAG with shared materials; the tree gives every request its share of the material's plan amounts,
 * so the counts along one branch add up the way GTNH's per-request counts do. Branches beyond the depth limit stay
 * collapsed and are only built when opened, which keeps very large plans cheap.
 */
@SideOnly(Side.CLIENT)
final class PlanTreeView {

    private static final int X_SPACING = 24;
    private static final int REQUEST_TASK_Y_SPACING = 10;
    private static final int TASK_CHILD_Y_SPACING = 12;
    private static final int LINE_COLOR = 0xFFDDDDDD;
    private static final int REQUEST_COLOR = 0xAAAAAA;
    private static final int REQUEST_MISSING_COLOR = 0xCCAAAA;
    private static final int REQUEST_CYCLE_COLOR = 0xB89BE0;
    private static final int TASK_COLOR = 0x777777;
    private static final int MISSING_TEXT = 0xFF5555;
    /** Nodes built before further branches are left collapsed; opening one builds it. */
    private static final int BUILD_BUDGET = 4000;
    private static final int[] DEPTHS = { 2, 4, 8, Integer.MAX_VALUE };
    /** Below this zoom an icon is a few pixels wide; the tinted frames alone show the shape of the tree. */
    private static final float ICON_MIN_ZOOM = 0.2F;
    /** Below this zoom the half-size counts are unreadable. */
    private static final float COUNT_MIN_ZOOM = 0.45F;

    private enum Kind {
        REQUEST,
        CRAFT,
        STORAGE,
        MISSING,
        CYCLE
    }

    private static final class Node {

        final Kind kind;
        @Nullable
        final Node parent;
        final List<Node> children = new ArrayList<>(2);
        final int materialId;
        @Nullable
        final CraftingGraphSnapshot.PatternNode pattern;
        final BigInteger amount;
        final int depth;
        /** Materials on the branch from the root, so a cycle ends instead of repeating. */
        final Set<Integer> path;
        boolean built;
        boolean collapsed;
        int x;
        int y;
        /** The formatted count, made once instead of every frame. */
        @Nullable
        String countText;

        Node(Kind kind, @Nullable Node parent, int materialId, @Nullable CraftingGraphSnapshot.PatternNode pattern,
            BigInteger amount, int depth, Set<Integer> path) {
            this.kind = kind;
            this.parent = parent;
            this.materialId = materialId;
            this.pattern = pattern;
            this.amount = amount;
            this.depth = depth;
            this.path = path;
        }
    }

    private final AEBaseGui parent;
    private final Container container;
    private int widgetX;
    private int widgetY;
    private int widgetW;
    private int widgetH;

    private long version = Long.MIN_VALUE;
    @Nullable
    private ECOCraftingGraphStream.Status status;
    @Nullable
    private CraftingGraphSnapshot snapshot;
    private final Map<Integer, CraftingGraphSnapshot.MaterialNode> materials = new HashMap<>();
    private final Map<Integer, List<CraftingGraphSnapshot.PatternNode>> producers = new HashMap<>();
    private final Map<Integer, Integer> cycleOf = new HashMap<>();
    private final Set<Integer> leadsToMissing = new HashSet<>();
    /** One native stack per material for its icon; drawing ignores the amount, so it is never copied per frame. */
    private final Map<Integer, IAEStack<?>> icons = new HashMap<>();

    private int depthIndex = 1;
    private boolean missingOnly;
    @Nullable
    private Node root;
    /** Laid-out nodes in placement order, which is also ascending x: the layout cursor only moves right. */
    private final List<Node> visible = new ArrayList<>();
    /** Connector rectangles of the current layout as {@code left, top, right, bottom} quadruples. */
    private int[] lineRects = new int[0];
    private int lineRectCount;
    private int builtNodes;
    private int treeWidth;
    private int treeHeight;

    private float zoom = 1F;
    private float scrollX = -8;
    private float scrollY = -8;
    private boolean dragging;
    private int lastDragX;
    private int lastDragY;

    private String query = "";
    private final List<Node> matches = new ArrayList<>();
    private final Set<Node> matchSet = Collections.newSetFromMap(new IdentityHashMap<>());
    private int matchIndex = -1;
    @Nullable
    private Node highlighted;
    @Nullable
    private Node hovered;

    PlanTreeView(AEBaseGui parent, Container container) {
        this.parent = parent;
        this.container = container;
    }

    void place(int x, int y, int width, int height) {
        widgetX = x;
        widgetY = y;
        widgetW = width;
        widgetH = height;
    }

    // ---- data -----------------------------------------------------------------------------------------------------

    /** Follows the report's plan version and takes the plan graph once the server delivered it. */
    void poll(long reportVersion) {
        if (reportVersion != version) {
            version = reportVersion;
            status = null;
            snapshot = null;
            root = null;
            visible.clear();
        }
        if (status != null) return;
        ECOCraftingGraphClient.Result result = ECOCraftingGraphClient.get(container, version);
        if (result == null) return;
        status = result.status;
        if (status == ECOCraftingGraphStream.Status.READY && result.snapshot != null
            && !ECOCraftingGraphStream.isEmpty(result.snapshot)) {
            snapshot = result.snapshot;
            index(snapshot);
            rebuild();
            fit();
        } else if (status == ECOCraftingGraphStream.Status.READY) status = ECOCraftingGraphStream.Status.EMPTY;
    }

    private void index(CraftingGraphSnapshot graph) {
        materials.clear();
        producers.clear();
        cycleOf.clear();
        leadsToMissing.clear();
        icons.clear();
        for (CraftingGraphSnapshot.MaterialNode material : graph.nodes()) materials.put(material.nodeId(), material);
        Map<Integer, CraftingGraphSnapshot.PatternNode> patterns = new HashMap<>();
        for (CraftingGraphSnapshot.PatternNode pattern : graph.patterns())
            patterns.put(pattern.patternNodeId(), pattern);
        for (CraftingGraphSnapshot.Edge edge : graph.edges()) {
            if (edge.kind() != CraftingGraphSnapshot.EdgeKind.PATTERN_OUTPUT) continue;
            CraftingGraphSnapshot.PatternNode pattern = patterns.get(edge.toId());
            if (pattern == null || pattern.firingCount() <= 0) continue;
            List<CraftingGraphSnapshot.PatternNode> list = producers
                .computeIfAbsent(edge.fromId(), id -> new ArrayList<>());
            if (!list.contains(pattern)) list.add(pattern);
        }
        // Cycle members are produced inside their component; their internal patterns produce them as well.
        for (CraftingGraphSnapshot.PatternNode pattern : graph.patterns()) {
            if (pattern.firingCount() <= 0 || pattern.componentId() < 0) continue;
            for (CraftingGraphSnapshot.Relationship output : pattern.outputs()) {
                List<CraftingGraphSnapshot.PatternNode> list = producers
                    .computeIfAbsent(output.materialNodeId(), id -> new ArrayList<>());
                if (!list.contains(pattern)) list.add(pattern);
            }
        }
        for (CraftingGraphSnapshot.CycleGroup cycle : graph.cycleGroups())
            for (int member : cycle.memberNodeIds()) cycleOf.put(member, cycle.componentId());
        markLeadsToMissing();
    }

    /**
     * A material leads to a shortage when it is short itself or one of its producing patterns takes an input that
     * does. Spreading backwards from the short materials over "input of a producer of" visits every link once, where a
     * search per material repeated the whole subgraph for every material that does not lead to a shortage.
     */
    private void markLeadsToMissing() {
        Map<Integer, List<Integer>> consumers = new HashMap<>();
        for (Map.Entry<Integer, List<CraftingGraphSnapshot.PatternNode>> entry : producers.entrySet())
            for (CraftingGraphSnapshot.PatternNode pattern : entry.getValue())
                for (CraftingGraphSnapshot.Relationship input : pattern.inputs())
                    consumers.computeIfAbsent(input.materialNodeId(), id -> new ArrayList<>())
                        .add(entry.getKey());
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (CraftingGraphSnapshot.MaterialNode material : materials.values()) if (material.missingBigInteger()
            .signum() > 0 && leadsToMissing.add(material.nodeId())) queue.add(material.nodeId());
        while (!queue.isEmpty()) {
            for (int consumer : consumers.getOrDefault(queue.poll(), Collections.emptyList()))
                if (leadsToMissing.add(consumer)) queue.add(consumer);
        }
    }

    private void rebuild() {
        root = null;
        visible.clear();
        builtNodes = 0;
        if (snapshot == null) return;
        CraftingGraphSnapshot.MaterialNode top = materials.get(snapshot.rootNodeId());
        if (top == null) return;
        BigInteger amount = top.requestedBigInteger()
            .signum() > 0 ? top.requestedBigInteger() : top.toCraftBigInteger();
        root = new Node(Kind.REQUEST, null, top.nodeId(), null, amount, 0, Collections.singleton(top.nodeId()));
        build(root);
        layout();
        search(query);
    }

    private int maxDepth() {
        return DEPTHS[depthIndex];
    }

    /** Builds a request's supplying steps and their input requests, collapsing past the depth or the budget. */
    private void build(Node request) {
        request.built = true;
        CraftingGraphSnapshot.MaterialNode material = materials.get(request.materialId);
        if (material == null) return;
        BigInteger total = demand(material);
        BigInteger stored = share(material.fromInventoryBigInteger(), request.amount, total);
        BigInteger missing = share(material.missingBigInteger(), request.amount, total);
        if (!missingOnly && stored.signum() > 0) add(
            request,
            new Node(Kind.STORAGE, request, request.materialId, null, stored, request.depth, request.path));
        for (CraftingGraphSnapshot.PatternNode pattern : producers
            .getOrDefault(request.materialId, Collections.emptyList())) {
            BigInteger executions = shareCeil(BigInteger.valueOf(pattern.firingCount()), request.amount, total);
            if (executions.signum() <= 0) continue;
            if (missingOnly && !patternReachesMissing(pattern)) continue;
            Node craft = new Node(
                Kind.CRAFT,
                request,
                request.materialId,
                pattern,
                executions,
                request.depth,
                request.path);
            add(request, craft);
            for (CraftingGraphSnapshot.Relationship input : pattern.inputs()) {
                if (missingOnly && !leadsToMissing.contains(input.materialNodeId())) continue;
                BigInteger needed = executions.multiply(BigInteger.valueOf(input.amount()));
                if (needed.signum() <= 0) continue;
                if (request.path.contains(input.materialNodeId())) {
                    add(
                        craft,
                        new Node(
                            Kind.CYCLE,
                            craft,
                            input.materialNodeId(),
                            null,
                            needed,
                            request.depth + 1,
                            request.path));
                    continue;
                }
                Set<Integer> path = new HashSet<>(request.path);
                path.add(input.materialNodeId());
                Node child = new Node(
                    Kind.REQUEST,
                    craft,
                    input.materialNodeId(),
                    null,
                    needed,
                    request.depth + 1,
                    path);
                add(craft, child);
                if (child.depth >= maxDepth() || builtNodes >= BUILD_BUDGET) child.collapsed = true;
                else build(child);
            }
        }
        if (missing.signum() > 0) add(
            request,
            new Node(Kind.MISSING, request, request.materialId, null, missing, request.depth, request.path));
    }

    private boolean patternReachesMissing(CraftingGraphSnapshot.PatternNode pattern) {
        for (CraftingGraphSnapshot.Relationship input : pattern.inputs())
            if (leadsToMissing.contains(input.materialNodeId())) return true;
        return false;
    }

    private void add(Node parentNode, Node child) {
        parentNode.children.add(child);
        builtNodes++;
    }

    private static BigInteger demand(CraftingGraphSnapshot.MaterialNode material) {
        BigInteger requested = material.requestedBigInteger();
        BigInteger supplied = material.fromInventoryBigInteger()
            .add(material.toCraftBigInteger())
            .add(material.missingBigInteger());
        return requested.max(supplied)
            .max(BigInteger.ONE);
    }

    /** This request's part of a material-wide amount. */
    private static BigInteger share(BigInteger value, BigInteger requested, BigInteger total) {
        if (value.signum() <= 0) return BigInteger.ZERO;
        if (requested.compareTo(total) >= 0) return value;
        return new BigDecimal(value.multiply(requested)).divide(new BigDecimal(total), 0, RoundingMode.HALF_UP)
            .toBigInteger()
            .max(BigInteger.ONE)
            .min(value);
    }

    private static BigInteger shareCeil(BigInteger value, BigInteger requested, BigInteger total) {
        if (value.signum() <= 0) return BigInteger.ZERO;
        if (requested.compareTo(total) >= 0) return value;
        BigInteger[] division = value.multiply(requested)
            .divideAndRemainder(total);
        BigInteger result = division[1].signum() > 0 ? division[0].add(BigInteger.ONE) : division[0];
        return result.max(BigInteger.ONE)
            .min(value);
    }

    /** GTNH's layout: siblings 24 apart left to right, the first child under its parent. */
    private void layout() {
        visible.clear();
        treeWidth = 0;
        treeHeight = 0;
        if (root == null) return;
        int[] cursor = { 0 };
        place(root, 0, cursor);
        lineRectCount = 0;
        for (Node node : visible) {
            treeWidth = Math.max(treeWidth, node.x + 16);
            treeHeight = Math.max(treeHeight, node.y + 16);
            if (node.parent != null) addLine(node.x + 8, node.y - 3, node.parent.x + 8, node.parent.y + 18);
        }
    }

    /** GTNH's elbow connector from a child's top up to its parent's bottom, kept as up to three rectangles. */
    private void addLine(int x0, int y0, int x1, int y1) {
        if (x0 == x1) {
            addRect(x0, Math.min(y0, y1), x0 + 1, Math.max(y0, y1));
            return;
        }
        int midY = (y0 + y1) / 2;
        addRect(x0, Math.min(y0, midY), x0 + 1, Math.max(y0, midY));
        addRect(Math.min(x0, x1), midY, Math.max(x0, x1) + 1, midY + 1);
        addRect(x1, Math.min(midY, y1), x1 + 1, Math.max(midY, y1));
    }

    private void addRect(int left, int top, int right, int bottom) {
        if (left >= right || top >= bottom) return;
        int at = lineRectCount * 4;
        if (at + 4 > lineRects.length) lineRects = Arrays.copyOf(lineRects, Math.max(64, lineRects.length * 2));
        lineRects[at] = left;
        lineRects[at + 1] = top;
        lineRects[at + 2] = right;
        lineRects[at + 3] = bottom;
        lineRectCount++;
    }

    private void place(Node node, int y, int[] cursor) {
        node.x = cursor[0];
        node.y = y;
        visible.add(node);
        if (node.collapsed) return;
        int childY = y + 16 + (node.kind == Kind.REQUEST ? REQUEST_TASK_Y_SPACING : TASK_CHILD_Y_SPACING);
        for (int i = 0; i < node.children.size(); i++) {
            if (i > 0) cursor[0] += X_SPACING;
            place(node.children.get(i), childY, cursor);
        }
    }

    // ---- controls -------------------------------------------------------------------------------------------------

    boolean hasTree() {
        return root != null;
    }

    String fitLabel() {
        return I18n.format("gui.neoecoae.crafting_graph.toolbar.fit_all");
    }

    String depthLabel() {
        return maxDepth() == Integer.MAX_VALUE ? I18n.format("gui.neoecoae.crafting_graph.toolbar.depth_all")
            : I18n.format("gui.neoecoae.crafting_graph.toolbar.depth", maxDepth());
    }

    String missingLabel() {
        return I18n.format(
            missingOnly ? "gui.neoecoae.crafting_graph.toolbar.missing_on"
                : "gui.neoecoae.crafting_graph.toolbar.missing_off");
    }

    void cycleDepth() {
        depthIndex = (depthIndex + 1) % DEPTHS.length;
        rebuild();
    }

    void toggleMissingOnly() {
        missingOnly = !missingOnly;
        rebuild();
        fit();
    }

    /** Fits the whole visible tree into the area, like the ECO graph's "fit all". */
    void fit() {
        if (treeWidth <= 0 || treeHeight <= 0) return;
        zoom = MathHelper.clamp_float(Math.min((widgetW - 16F) / treeWidth, (widgetH - 16F) / treeHeight), 0.1F, 1F);
        scrollX = -Math.max(8F, (widgetW / zoom - treeWidth) / 2F);
        scrollY = -8F;
    }

    void search(String text) {
        query = text == null ? ""
            : text.trim()
                .toLowerCase(Locale.ROOT);
        matches.clear();
        matchSet.clear();
        matchIndex = -1;
        highlighted = null;
        if (query.isEmpty()) return;
        Map<Integer, Boolean> named = new HashMap<>();
        for (Node node : visible) if (node.kind == Kind.REQUEST && named.computeIfAbsent(
            node.materialId,
            id -> name(id).toLowerCase(Locale.ROOT)
                .contains(query)))
            matches.add(node);
        matchSet.addAll(matches);
        findNext();
    }

    /** GTNH's search go-to: the next match, scrolled into the top-left of the area. */
    void findNext() {
        if (matches.isEmpty()) return;
        matchIndex = (matchIndex + 1) % matches.size();
        highlighted = matches.get(matchIndex);
        scrollX = highlighted.x - 16;
        scrollY = highlighted.y - 16;
    }

    boolean contains(int localX, int localY) {
        return localX >= widgetX && localY >= widgetY && localX < widgetX + widgetW && localY < widgetY + widgetH;
    }

    /** A click on a request with steps under it opens or closes that branch; any other click starts a drag. */
    boolean click(int localX, int localY, int button) {
        if (!contains(localX, localY)) return false;
        Node node = nodeAt(localX, localY);
        if (button == 0 && node != null && node.kind == Kind.REQUEST && hasBranch(node)) {
            if (node.collapsed) {
                node.collapsed = false;
                if (!node.built) build(node);
            } else node.collapsed = true;
            layout();
            return true;
        }
        if (button == 0) {
            dragging = true;
            lastDragX = localX;
            lastDragY = localY;
        }
        return true;
    }

    void drag(int localX, int localY) {
        if (!dragging) return;
        scrollX -= (localX - lastDragX) / zoom;
        scrollY -= (localY - lastDragY) / zoom;
        lastDragX = localX;
        lastDragY = localY;
        clampScroll();
    }

    void release() {
        dragging = false;
    }

    /** GTNH's wheel: vertical scroll, Shift sideways, Ctrl zoom around the cursor. */
    boolean wheel(int localX, int localY, int wheel) {
        if (!contains(localX, localY)) return false;
        int steps = wheel > 0 ? 1 : -1;
        if (GuiScreen.isShiftKeyDown()) scrollX -= steps * widgetW / (5F * zoom);
        else if (GuiScreen.isCtrlKeyDown()) {
            float mouseX = localX - widgetX;
            float mouseY = localY - widgetY;
            float oldZoom = zoom;
            zoom = MathHelper.clamp_float(zoom * (float) Math.pow(1.4, steps), 0.1F, 8F);
            scrollX += mouseX / oldZoom - mouseX / zoom;
            scrollY += mouseY / oldZoom - mouseY / zoom;
        } else scrollY -= steps * widgetH / (5F * zoom);
        clampScroll();
        return true;
    }

    private void clampScroll() {
        scrollX = MathHelper.clamp_float(scrollX, -widgetW / zoom + 4, Math.max(4, treeWidth - 4));
        scrollY = MathHelper.clamp_float(scrollY, -widgetH / zoom + 4, Math.max(4, treeHeight - 4));
    }

    private boolean hasBranch(Node node) {
        if (node.built) return !node.children.isEmpty();
        return true;
    }

    private @Nullable Node nodeAt(int localX, int localY) {
        float treeX = (localX - widgetX) / zoom + scrollX;
        float treeY = (localY - widgetY) / zoom + scrollY;
        for (int i = firstAtOrRightOf(treeX - 16); i < visible.size(); i++) {
            Node node = visible.get(i);
            if (node.x > treeX) break;
            if (treeX >= node.x && treeY >= node.y && treeX < node.x + 16 && treeY < node.y + 16) return node;
        }
        return null;
    }

    /** Index of the first laid-out node whose x is at least {@code x}; {@link #visible} is sorted by x. */
    private int firstAtOrRightOf(float x) {
        int low = 0, high = visible.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (visible.get(middle).x < x) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    /**
     * Draws in the host's foreground layer (origin at guiLeft/guiTop); {@code mouse*} are host-relative. The tree sits
     * on the panel texture's own graph area, so only the nodes and connectors are drawn.
     *
     * <p>
     * Like the 1.21 graph renderer, a frame costs a few batched passes instead of several GL state changes per node:
     * every connector is one untextured batch, every slot frame and step icon one textured batch, the item icons share
     * one lighting setup and the counts one scaled matrix. Only nodes inside the area are visited, found by binary
     * search over the x-sorted layout, and icons and counts are left out once they are too small to read.
     */
    void draw(int localMouseX, int localMouseY) {
        if (root == null) {
            drawStatus();
            return;
        }
        hovered = contains(localMouseX, localMouseY) ? nodeAt(localMouseX, localMouseY) : null;
        Minecraft mc = Minecraft.getMinecraft();
        float scale = (float) mc.displayWidth / parent.width;
        int screenLeft = parent.getGuiLeft() + widgetX;
        int screenBottom = parent.height - (parent.getGuiTop() + widgetY + widgetH);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glScissor(
            (int) (screenLeft * scale),
            (int) (screenBottom * scale),
            (int) (widgetW * scale),
            (int) (widgetH * scale));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPushMatrix();
        GL11.glTranslatef(widgetX - scrollX * zoom, widgetY - scrollY * zoom, 0);
        GL11.glScalef(zoom, zoom, 1);
        float left = scrollX - 32;
        float right = scrollX + widgetW / zoom + 32;
        float top = scrollY - 32;
        float bottom = scrollY + widgetH / zoom + 32;
        int from = firstAtOrRightOf(left - 16);
        int to = from;
        while (to < visible.size() && visible.get(to).x <= right) to++;
        try {
            drawLines(left, right, top, bottom);
            drawFrames(from, to, top, bottom);
            if (zoom >= ICON_MIN_ZOOM) drawStacks(mc, from, to, top, bottom);
            drawMarkers(from, to, top, bottom);
            if (zoom >= COUNT_MIN_ZOOM) drawCounts(from, to, top, bottom);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        // GTNH's scroll position marks along the bottom and right edges.
        float percentX = treeWidth <= 0 ? 0 : MathHelper.clamp_float(scrollX / treeWidth, 0, 1);
        float percentY = treeHeight <= 0 ? 0 : MathHelper.clamp_float(scrollY / treeHeight, 0, 1);
        int markX = (int) (widgetX + percentX * (widgetW - 24));
        int markY = (int) (widgetY + percentY * (widgetH - 24));
        Gui.drawRect(markX, widgetY + widgetH - 2, markX + 24, widgetY + widgetH - 1, LINE_COLOR);
        Gui.drawRect(widgetX + widgetW - 2, markY, widgetX + widgetW - 1, markY + 24, LINE_COLOR);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static boolean outsideRows(Node node, float top, float bottom) {
        return node.y + 16 < top || node.y > bottom;
    }

    /** Every connector in the area as one untextured batch. */
    private void drawLines(float left, float right, float top, float bottom) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(LINE_COLOR & 0xFFFFFF, LINE_COLOR >>> 24);
        int[] rects = lineRects;
        for (int i = 0, end = lineRectCount * 4; i < end; i += 4) {
            int x0 = rects[i], y0 = rects[i + 1], x1 = rects[i + 2], y1 = rects[i + 3];
            if (x1 < left || x0 > right || y1 < top || y0 > bottom) continue;
            tessellator.addVertex(x0, y1, 0);
            tessellator.addVertex(x1, y1, 0);
            tessellator.addVertex(x1, y0, 0);
            tessellator.addVertex(x0, y0, 0);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** GTNH's tinted slot frames with the step icon of storage, craft and shortage steps, as one textured batch. */
    private void drawFrames(int from, int to, float top, float bottom) {
        int searchColor = ColorUtils.searchHighlight.getColor();
        int goToColor = ColorUtils.searchGoToHighlight.getColor();
        beginStates();
        Tessellator tessellator = Tessellator.instance;
        for (int i = from; i < to; i++) {
            Node node = visible.get(i);
            if (outsideRows(node, top, bottom)) continue;
            switch (node.kind) {
                case REQUEST: {
                    int color = matchSet.contains(node) ? node == highlighted ? goToColor : searchColor
                        : cycleOf.containsKey(node.materialId) ? REQUEST_CYCLE_COLOR
                            : showsMissing(node) ? REQUEST_MISSING_COLOR : REQUEST_COLOR;
                    frame(tessellator, node, color, false);
                    break;
                }
                case CRAFT:
                    frame(tessellator, node, TASK_COLOR, true);
                    icon(tessellator, node.x, node.y, 16, 16 + 3);
                    break;
                case STORAGE:
                    frame(tessellator, node, TASK_COLOR, true);
                    icon(tessellator, node.x, node.y, 16, 16 + 2);
                    break;
                case MISSING:
                    frame(tessellator, node, TASK_COLOR, true);
                    icon(tessellator, node.x, node.y, 16, 8 * 16);
                    break;
                case CYCLE:
                    frame(tessellator, node, REQUEST_CYCLE_COLOR, false);
                    break;
            }
        }
        tessellator.draw();
    }

    /** The materials' item and fluid icons under one item lighting setup. */
    private void drawStacks(Minecraft mc, int from, int to, float top, float bottom) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glColor4f(1, 1, 1, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        try {
            for (int i = from; i < to; i++) {
                Node node = visible.get(i);
                if (node.kind != Kind.REQUEST && node.kind != Kind.CYCLE || outsideRows(node, top, bottom)) continue;
                IAEStack<?> stack = iconStack(node.materialId);
                if (stack == null) continue;
                try {
                    stack.drawInGui(mc, node.x, node.y);
                } catch (RuntimeException ignored) {
                    // A broken item renderer must not take the whole tree down.
                }
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glPopAttrib();
        }
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    /** GTNH's open/closed marker under a request with steps and the shortage mark on its icon, over the icons. */
    private void drawMarkers(int from, int to, float top, float bottom) {
        beginStates();
        Tessellator tessellator = Tessellator.instance;
        for (int i = from; i < to; i++) {
            Node node = visible.get(i);
            if (node.kind != Kind.REQUEST || outsideRows(node, top, bottom)) continue;
            if (hasBranch(node)) icon(tessellator, node.x + 3, node.y + 19, 4, 14 * 16 + (node.collapsed ? 14 : 13));
            if (showsMissing(node)) icon(tessellator, node.x + 8, node.y, 8, 8 * 16);
        }
        tessellator.draw();
    }

    /** GTNH's half-size counts in the slots' bottom-right corners, under one scaled matrix. */
    private void drawCounts(int from, int to, float top, float bottom) {
        FontRenderer font = parent.getFontRenderer();
        int textColor = ColorUtils.searchboxText.getColor();
        GL11.glPushMatrix();
        GL11.glScalef(0.5F, 0.5F, 1);
        GL11.glTranslatef(0, 0, 200);
        for (int i = from; i < to; i++) {
            Node node = visible.get(i);
            if (outsideRows(node, top, bottom)) continue;
            String text = node.countText;
            if (text == null)
                node.countText = text = ReadableNumberConverter.INSTANCE.toWideReadableForm(clamp(node.amount));
            font.drawString(
                text,
                node.x * 2 + 32 - font.getStringWidth(text),
                node.y * 2 + 22,
                node.kind == Kind.MISSING ? MISSING_TEXT : textColor,
                true);
        }
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private boolean showsMissing(Node node) {
        if (!leadsToMissing.contains(node.materialId)) return false;
        CraftingGraphSnapshot.MaterialNode material = materials.get(node.materialId);
        return material != null && material.missingBigInteger()
            .signum() > 0;
    }

    private void beginStates() {
        parent.bindTexture("guis/states.png");
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1, 1, 1, 1);
        Tessellator.instance.startDrawingQuads();
    }

    /** The 24px slot frame of {@code states.png} around a node, tinted like GTNH's tree. */
    private static void frame(Tessellator tessellator, Node node, int rgb, boolean operation) {
        quad(tessellator, node.x - 3, node.y - 3, 24, 192, operation ? 32 : 56, 24, rgb);
    }

    /** One 16px cell of {@code states.png} drawn {@code size} wide. */
    private static void icon(Tessellator tessellator, int x, int y, int size, int index) {
        quad(tessellator, x, y, size, (index % 16) * 16, (index / 16) * 16, 16, 0xFFFFFF);
    }

    private static void quad(Tessellator tessellator, float x, float y, float size, int u, int v, int span, int rgb) {
        float unit = 1F / 256F;
        float u0 = u * unit, v0 = v * unit, u1 = (u + span) * unit, v1 = (v + span) * unit;
        tessellator.setColorOpaque_I(rgb);
        tessellator.addVertexWithUV(x, y + size, 0, u0, v1);
        tessellator.addVertexWithUV(x + size, y + size, 0, u1, v1);
        tessellator.addVertexWithUV(x + size, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
    }

    private @Nullable IAEStack<?> iconStack(int materialId) {
        if (icons.containsKey(materialId)) return icons.get(materialId);
        CraftingGraphSnapshot.MaterialNode material = materials.get(materialId);
        IAEStack<?> stack = null;
        if (material != null && material.key() != null) {
            try {
                stack = material.key()
                    .toNative(1);
            } catch (RuntimeException ignored) {
                // An unreadable key just shows no icon.
            }
        }
        icons.put(materialId, stack);
        return stack;
    }

    private void drawStatus() {
        String key;
        if (status == null) key = "gui.neoecoae.crafting_graph.loading";
        else switch (status) {
            case TOO_LARGE:
                key = "gui.neoecoae.crafting_graph.too_large";
                break;
            case FAILED:
                key = "gui.neoecoae.crafting_graph.failed";
                break;
            case READY:
                key = "gui.neoecoae.crafting_graph.loading";
                break;
            default:
                key = "gui.neoecoae.crafting_graph.empty";
        }
        String text = I18n.format(key);
        int width = parent.getFontRenderer()
            .getStringWidth(text);
        parent.getFontRenderer()
            .drawString(text, widgetX + (widgetW - width) / 2, widgetY + widgetH / 2 - 4, 0xAAB3BD);
    }

    // ---- hover ----------------------------------------------------------------------------------------------------

    /** The tooltip of the node under the cursor, for the host to draw. */
    List<String> tooltip() {
        Node node = hovered;
        if (node == null) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        CraftingGraphSnapshot.MaterialNode material = materials.get(node.materialId);
        String amount = ReadableNumberConverter.INSTANCE.toWideReadableForm(clamp(node.amount));
        switch (node.kind) {
            case REQUEST:
            case CYCLE:
                lines.add(name(node.materialId));
                lines.add(EnumChatFormatting.GRAY + GuiText.ToCraftRequests.getLocal() + ": " + exact(node.amount));
                if (material != null) {
                    if (material.fromInventoryBigInteger()
                        .signum() > 0)
                        lines.add(GuiText.FromStorage.getLocal() + ": " + exact(material.fromInventoryBigInteger()));
                    if (material.toCraftBigInteger()
                        .signum() > 0)
                        lines.add(GuiText.ToCraft.getLocal() + ": " + exact(material.toCraftBigInteger()));
                    if (material.missingBigInteger()
                        .signum() > 0)
                        lines.add(
                            EnumChatFormatting.RED + GuiText.Missing.getLocal()
                                + ": "
                                + exact(material.missingBigInteger()));
                }
                Integer cycle = cycleOf.get(node.materialId);
                if (cycle != null) lines.add(EnumChatFormatting.LIGHT_PURPLE + "Cycle #" + cycle);
                break;
            case CRAFT:
                lines.add(GuiText.Crafting.getLocal() + ": " + name(node.materialId));
                lines.add(GuiText.ToCraftRequests.getLocal() + ": " + exact(node.amount));
                break;
            case STORAGE:
                lines.add(GuiText.StoredItems.getLocal() + ": " + amount);
                break;
            case MISSING:
                lines.add(EnumChatFormatting.RED + GuiText.Missing.getLocal() + ": " + amount);
                break;
        }
        return lines;
    }

    /** The hovered material, for NEI recipe lookups. */
    @Nullable
    ItemStack hoveredStack() {
        Node node = hovered;
        if (node == null) return null;
        CraftingGraphSnapshot.MaterialNode material = materials.get(node.materialId);
        IAEStack<?> stack = material == null ? null
            : material.key()
                .toNative(1);
        return stack == null ? null : stack.getItemStackForNEI();
    }

    private String name(int materialId) {
        CraftingGraphSnapshot.MaterialNode material = materials.get(materialId);
        if (material == null) return "?";
        AEKey key = material.key();
        return key == null ? "?" : key.getDisplayName();
    }

    private static String exact(BigInteger amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    private static long clamp(BigInteger value) {
        return value.bitLength() < 63 ? value.longValue() : Long.MAX_VALUE;
    }
}
