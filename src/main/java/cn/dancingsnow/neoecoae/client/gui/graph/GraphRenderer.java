package cn.dancingsnow.neoecoae.client.gui.graph;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CycleCluster;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.graph.InterCycleFlow;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.ExactKeyAmount;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Live rendering for sparse views; bounded progressive raster tiles for dense overviews. */
@SideOnly(Side.CLIENT)
public final class GraphRenderer implements AutoCloseable {

    static final int TEXT = 0xffe8edf2;
    static final int MUTED = 0xffaab3bd;
    /** Without framebuffers a dense view only draws plain node boxes, bounded by this many queried nodes. */
    private static final int FALLBACK_BOX_LIMIT = 20_000;

    @com.github.bsideup.jabel.Desugar
    public record Frame(@Nullable ClientCraftingGraph.Node hovered, List<String> tooltip) {}

    private final CompactTreeRenderer compactTreeRenderer = new CompactTreeRenderer();
    private final GraphVisibilityCache visibility = new GraphVisibilityCache();
    private final GraphViewportCache canvasCache = new GraphViewportCache();
    private boolean cachedView;
    private ClientCraftingGraph tooltipGraph;
    private ClientCraftingGraph.Node tooltipNode;
    private List<String> tooltipLines = JdkCompat.listOf();

    public Frame render(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout, float cameraX,
        float cameraY, float zoom, int screenWidth, int screenHeight, int guiScale, int mouseX, int mouseY,
        @Nullable Integer selectedId, GraphProfiler profiler) {
        long started = System.nanoTime();
        var area = GraphViewTransform.viewport(cameraX, cameraY, zoom, screenWidth, screenHeight);
        GraphVisibilityCache.VisiblePlan visible = visibility.get(layout, area.padded(24 / zoom));
        cachedView = GraphViewTransform.useCache(zoom, visible.nodeCount(), cachedView);
        Frame frame;
        if (!cachedView) {
            frame = renderImmediate(
                canvas,
                graph,
                layout,
                cameraX,
                cameraY,
                zoom,
                screenWidth,
                screenHeight,
                mouseX,
                mouseY,
                selectedId,
                profiler,
                visible);
        } else {
            float worldMouseX = (mouseX - cameraX) / zoom;
            float worldMouseY = (mouseY - cameraY) / zoom;
            var hit = mouseY < GraphViewTransform.TOOLBAR_HEIGHT ? JdkCompat.<GraphLayoutSnapshot.Box>listOf()
                : layout.query(new GraphLayoutSnapshot.Bounds(worldMouseX, worldMouseY, worldMouseX, worldMouseY), 1);
            var hovered = hit.isEmpty() ? null
                : graph.nodes()
                    .get(
                        hit.get(0)
                            .nodeId());
            boolean raster = GraphViewportCache.supported();
            if (raster) canvasCache.prepare(
                canvas,
                layout,
                area,
                layout.bounds(),
                zoom,
                guiScale,
                (tile, pixelsPerUnit) -> new TilePainter(graph, layout, tile, pixelsPerUnit));
            canvas.push();
            try {
                canvas.translate(cameraX, cameraY);
                canvas.scale(zoom, zoom);
                if (raster) canvasCache.draw(canvas);
                else drawPlainBoxes(canvas, graph, layout, area);
                drawCachedOverlay(canvas, graph, layout, area, selectedId, hovered, zoom);
            } finally {
                canvas.pop();
            }
            frame = new Frame(hovered, JdkCompat.listOf());
        }
        // Hover text contains exact amount formatting; keep it off the stationary-frame hot path.
        if (tooltipGraph != graph || tooltipNode != frame.hovered()) {
            tooltipGraph = graph;
            tooltipNode = frame.hovered();
            tooltipLines = graph.isCompactTree() ? CompactTreeRenderer.tooltip(graph, tooltipNode)
                : tooltip(graph, tooltipNode);
        }
        profiler.update(visible.nodeCount(), visible.edgeCount(), System.nanoTime() - started);
        return new Frame(frame.hovered(), tooltipLines);
    }

    @Override
    public void close() {
        canvasCache.close();
        visibility.clear();
        tooltipGraph = null;
        tooltipNode = null;
        tooltipLines = JdkCompat.listOf();
        cachedView = false;
    }

    private Frame renderImmediate(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
        float cameraX, float cameraY, float zoom, int screenWidth, int screenHeight, int mouseX, int mouseY,
        @Nullable Integer selectedId, GraphProfiler profiler, GraphVisibilityCache.VisiblePlan visible) {
        if (graph.isCompactTree()) {
            return compactTreeRenderer.render(
                canvas,
                graph,
                layout,
                cameraX,
                cameraY,
                zoom,
                screenWidth,
                screenHeight,
                mouseX,
                mouseY,
                selectedId,
                profiler,
                visible);
        }
        long started = System.nanoTime();
        List<GraphLayoutSnapshot.Box> boxes = visible.nodes();
        List<ClientCraftingGraph.Link> links = visible.edges();
        Set<Integer> neighborhood = selectedId == null ? JdkCompat.setOf() : graph.neighborhood(selectedId);

        for (var link : links) {
            drawLink(canvas, graph, layout, link, selectedId, neighborhood, cameraX, cameraY, zoom);
            if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER && zoom >= 0.55f) {
                drawEdgeAmount(canvas, graph, link, layout.edgePoints(link), cameraX, cameraY, zoom);
            }
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
            drawCycleOutputArrow(canvas, graph, layout, cameraX, cameraY, zoom);
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
            drawClusterLabels(canvas, graph, layout, cameraX, cameraY, zoom);
        }
        ClientCraftingGraph.Node hovered = null;
        float worldMouseX = (mouseX - cameraX) / zoom;
        float worldMouseY = (mouseY - cameraY) / zoom;
        for (var box : boxes) {
            ClientCraftingGraph.Node node = graph.nodes()
                .get(box.nodeId());
            if (node == null) continue;
            boolean selected = selectedId != null && selectedId == node.id();
            boolean adjacent = neighborhood.contains(node.id());
            drawNode(canvas, graph, node, box, selected, adjacent, cameraX, cameraY, zoom);
            if (box.contains(worldMouseX, worldMouseY)) hovered = node;
        }
        canvas.flush();
        profiler.update(boxes.size(), links.size(), System.nanoTime() - started);
        return new Frame(hovered, JdkCompat.listOf());
    }

    /** Framebuffer-less fallback for dense views: plain colored boxes, no text, icons or edges. */
    private static void drawPlainBoxes(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
        GraphLayoutSnapshot.Bounds area) {
        for (var box : layout.query(area, FALLBACK_BOX_LIMIT)) {
            var node = graph.nodes()
                .get(box.nodeId());
            if (node == null) continue;
            int color = graph.isCompactTree() ? CompactTreeRenderer.nodeBorder(graph, node) : border(graph, node);
            canvas.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), color);
        }
    }

    private static void drawCachedOverlay(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
        GraphLayoutSnapshot.Bounds area, @Nullable Integer selectedId, @Nullable ClientCraftingGraph.Node hovered,
        float zoom) {
        if (selectedId != null && zoom >= 0.5f) {
            for (var link : layout.linksAt(selectedId)) {
                if (layout.linkBounds(link)
                    .intersects(area)) {
                    drawCachedLink(canvas, graph, layout, link, zoom, 0xfff6c453);
                    int neighbor = link.fromId() == selectedId ? link.toId() : link.fromId();
                    drawOutline(canvas, layout.box(neighbor), area, 0xff83c5be, 1 / zoom);
                }
            }
        }
        if (selectedId != null) drawOutline(canvas, layout.box(selectedId), area, 0xfff6c453, 2 / zoom);
        if (hovered != null && (selectedId == null || selectedId != hovered.id())) {
            drawOutline(canvas, layout.box(hovered.id()), area, 0xff83c5be, 1 / zoom);
        }
        canvas.flush();
    }

    private static void drawOutline(GraphCanvas canvas, @Nullable GraphLayoutSnapshot.Box box,
        GraphLayoutSnapshot.Bounds area, int color, float thickness) {
        if (box == null || !box.intersects(area.left(), area.top(), area.right(), area.bottom())) return;
        GraphDraw.outline(canvas, box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), color, thickness);
    }

    private static void drawCachedLink(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
        ClientCraftingGraph.Link link, double pixelsPerUnit, int color) {
        var route = layout.edgePoints(link);
        if (route.size() < 2) {
            var from = layout.box(link.fromId());
            var to = layout.box(link.toId());
            if (from == null || to == null) return;
            if (graph.isCompactTree()) {
                float middle = (from.y() + from.height() + to.y()) / 2;
                route = JdkCompat.listOf(
                    new GraphLayoutSnapshot.Point(from.centerX(), from.y() + from.height()),
                    new GraphLayoutSnapshot.Point(from.centerX(), middle),
                    new GraphLayoutSnapshot.Point(to.centerX(), middle),
                    new GraphLayoutSnapshot.Point(to.centerX(), to.y()));
            } else {
                float middle = (from.x() + from.width() + to.x()) / 2;
                route = JdkCompat.listOf(
                    new GraphLayoutSnapshot.Point(from.x() + from.width(), from.centerY()),
                    new GraphLayoutSnapshot.Point(middle, from.centerY()),
                    new GraphLayoutSnapshot.Point(middle, to.centerY()),
                    new GraphLayoutSnapshot.Point(to.x(), to.centerY()));
            }
        }
        float half = (float) Math.max(0.35, 0.5 / pixelsPerUnit);
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            canvas.line(from.x(), from.y(), to.x(), to.y(), color, half);
        }
        if (graph.isCompactTree()) {
            var end = route.get(route.size() - 1);
            canvas.fill(end.x() - 3, end.y() - 2, end.x() + 1, end.y() + 3, color);
        } else {
            drawArrowHead(canvas, route, color, 0, 0, 1);
            if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
                drawEdgeAmount(canvas, graph, link, route, 0, 0, 1);
            }
        }
    }

    private static final class TilePainter implements GraphViewportCache.Painter {

        private final ClientCraftingGraph graph;
        private final GraphLayoutSnapshot layout;
        private final double pixelsPerUnit;
        private final List<ClientCraftingGraph.Link> links;
        private final List<GraphLayoutSnapshot.Box> nodes;
        private final List<GraphLayoutSnapshot.Bounds> rows;
        private int linkCursor, nodeCursor, rowCursor, missingCursor;
        private boolean finished;

        TilePainter(ClientCraftingGraph graph, GraphLayoutSnapshot layout, GraphLayoutSnapshot.Bounds area,
            double pixelsPerUnit) {
            this.graph = graph;
            this.layout = layout;
            this.pixelsPerUnit = pixelsPerUnit;
            links = layout.queryLinks(area);
            nodes = layout.query(area, Integer.MAX_VALUE);
            rows = graph.isCompactTree() ? layout.queryRowFrames(area) : JdkCompat.listOf();
        }

        @Override
        public boolean advance(GraphCanvas canvas, long deadline) {
            int work = 0;
            do {
                if (linkCursor < links.size()) {
                    var link = links.get(linkCursor++);
                    drawCachedLink(
                        canvas,
                        graph,
                        layout,
                        link,
                        pixelsPerUnit,
                        link.selected() ? 0xff8293a4 : 0xff66505a);
                } else if (nodeCursor < nodes.size()) {
                    var box = nodes.get(nodeCursor++);
                    var node = graph.nodes()
                        .get(box.nodeId());
                    if (node != null) {
                        if (graph.isCompactTree()) {
                            CompactTreeRenderer.drawNode(canvas, graph, node, box, false, false, 0, 0, 1);
                        } else drawNode(canvas, graph, node, box, false, false, 0, 0, 1);
                    }
                } else if (rowCursor < rows.size()) {
                    var row = rows.get(rowCursor++);
                    GraphDraw.outline(
                        canvas,
                        row.left(),
                        row.top(),
                        row.right(),
                        row.bottom(),
                        0xff8293a4,
                        (float) (1 / pixelsPerUnit));
                } else if (graph.isCompactTree() && missingCursor < nodes.size()) {
                    var box = nodes.get(missingCursor++);
                    var node = graph.nodes()
                        .get(box.nodeId());
                    if (node != null && node.material() != null
                        && node.material()
                            .status() == CraftingGraphSnapshot.MaterialStatus.MISSING) {
                        CompactTreeRenderer.drawNodeFrame(canvas, box, 0xffcf5e5e, 0, 0, 1);
                    }
                } else if (!finished) {
                    if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS) {
                        drawCycleOutputArrow(canvas, graph, layout, 0, 0, 1);
                    }
                    if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER)
                        drawClusterLabels(canvas, graph, layout, 0, 0, 1);
                    finished = true;
                    return true;
                } else return true;
            } while (++work < 128 && System.nanoTime() < deadline);
            return false;
        }
    }

    private static void drawLink(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
        ClientCraftingGraph.Link link, @Nullable Integer selected, Set<Integer> neighborhood, float cameraX,
        float cameraY, float zoom) {
        var from = layout.box(link.fromId());
        var to = layout.box(link.toId());
        if (from == null || to == null) return;
        boolean highlighted = selected != null && (link.fromId() == selected || link.toId() == selected)
            && (neighborhood.contains(link.fromId()) || neighborhood.contains(link.toId()));
        int color = highlighted ? 0xfff6c453 : link.selected() ? 0xff8293a4 : 0xff66505a;
        List<GraphLayoutSnapshot.Point> route = layout.edgePoints(link);
        if (route.size() >= 2) {
            drawRoutedLink(canvas, route, color, cameraX, cameraY, zoom);
            drawArrowHead(canvas, route, color, cameraX, cameraY, zoom);
            return;
        }
        int x1 = screen(cameraX, from.x() + from.width(), zoom);
        int y1 = screen(cameraY, from.centerY(), zoom);
        int x2 = screen(cameraX, to.x(), zoom);
        int y2 = screen(cameraY, to.centerY(), zoom);
        int middle = (x1 + x2) / 2;
        GraphDraw.hLine(canvas, Math.min(x1, middle), Math.max(x1, middle), y1, color);
        GraphDraw.vLine(canvas, middle, Math.min(y1, y2), Math.max(y1, y2), color);
        GraphDraw.hLine(canvas, Math.min(middle, x2), Math.max(middle, x2), y2, color);
        canvas.fill(x2 - 4, y2 - 2, x2, y2 + 3, color);
    }

    private static void drawCycleOutputArrow(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
        float cameraX, float cameraY, float zoom) {
        var cycle = focusedCycle(graph);
        // Unsolved cycles are diagnostic structure, not executable output flow.
        if (cycle == null || (cycle.patternTimes()
            .stream()
            .noneMatch(value -> value.amount() > 0)
            && cycle.executionWitness()
                .isEmpty()))
            return;
        Integer outputId = cycleOutputNodeId(graph);
        var output = outputId == null ? null : layout.box(outputId);
        if (output == null) return;
        double sumX = 0, sumY = 0;
        int count = 0;
        for (var box : layout.boxes()
            .values()) {
            if (graph.isBoundaryMaterial(box.nodeId())) continue;
            sumX += box.centerX();
            sumY += box.centerY();
            count++;
        }
        float centerX = count == 0 ? output.centerX() : (float) (sumX / count);
        float centerY = count == 0 ? output.centerY() : (float) (sumY / count);
        float dx = output.centerX() - centerX;
        float dy = output.centerY() - centerY;
        float length = Math.max(0.001f, (float) Math.hypot(dx, dy));
        dx /= length;
        dy /= length;
        var start = new GraphLayoutSnapshot.Point(
            output.centerX() + dx * output.width() / 2,
            output.centerY() + dy * output.height() / 2);
        var end = new GraphLayoutSnapshot.Point(start.x() + dx * 30, start.y() + dy * 30);
        var route = JdkCompat.listOf(start, end);
        int color = 0xff75c48b;
        drawRoutedLink(canvas, route, color, cameraX, cameraY, zoom);
        drawArrowHead(canvas, route, color, cameraX, cameraY, zoom);
    }

    static @Nullable CraftingGraphSnapshot.CycleGroup focusedCycle(ClientCraftingGraph graph) {
        for (var cycle : graph.source()
            .cycleGroups()) if (cycle.componentId() == graph.focusedCycleId()) return cycle;
        return null;
    }

    static @Nullable Integer cycleOutputNodeId(ClientCraftingGraph graph) {
        var cycle = focusedCycle(graph);
        if (cycle == null) return null;
        for (var output : cycle.requiredOutputs()) {
            Integer id = materialId(graph, output.key());
            if (id != null && !graph.isBoundaryMaterial(id)) return id;
        }
        for (var output : cycle.singleNetOutputs()) {
            if (output.amount() <= 0) continue;
            Integer id = materialId(graph, output.key());
            if (id != null && !graph.isBoundaryMaterial(id)) return id;
        }
        var root = graph.nodes()
            .get(graph.rootId());
        return root != null && root.kind() == ClientCraftingGraph.Kind.MATERIAL && !graph.isBoundaryMaterial(root.id())
            ? root.id()
            : null;
    }

    private static @Nullable Integer materialId(ClientCraftingGraph graph, AEKey key) {
        for (var node : graph.nodes()
            .values()) if (node.kind() == ClientCraftingGraph.Kind.MATERIAL && key.equals(node.key())) return node.id();
        return null;
    }

    private static void drawEdgeAmount(GraphCanvas canvas, ClientCraftingGraph graph, ClientCraftingGraph.Link link,
        List<GraphLayoutSnapshot.Point> route, float cameraX, float cameraY, float zoom) {
        String text = edgeAmount(graph, link);
        if (text.isEmpty() || route.size() < 2) return;
        var point = route.get(route.size() / 2);
        int x = screen(cameraX, point.x(), zoom);
        int y = screen(cameraY, point.y(), zoom);
        int width = canvas.width(text) + 6;
        canvas.fill(x - width / 2, y - 8, x + (width + 1) / 2, y + 3, 0xcc171b20);
        canvas.centered(text, x, y - 7, MUTED);
    }

    private static String edgeAmount(ClientCraftingGraph graph, ClientCraftingGraph.Link link) {
        if (link.amount() == 0) return "";
        ClientCraftingGraph.Node material = graph.nodes()
            .get(link.fromId());
        if (material == null || material.kind() != ClientCraftingGraph.Kind.MATERIAL) {
            material = graph.nodes()
                .get(link.toId());
        }
        AEKey key = material == null || material.material() == null ? null
            : material.material()
                .key();
        if (key == null && link.materialNodeId() >= 0) {
            for (var node : graph.source()
                .nodes()) {
                if (node.nodeId() == link.materialNodeId()) {
                    key = node.key();
                    break;
                }
            }
        }
        if (key == null) return "×" + link.amount();
        long magnitude = link.amount() == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(link.amount());
        return "×" + BigNumberFormatter.format(BigInteger.valueOf(magnitude), perUnit(key), false);
    }

    private static void drawClusterLabels(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout,
        float cameraX, float cameraY, float zoom) {
        CycleCluster cluster = graph.focusedCluster();
        if (cluster == null) return;
        for (int componentId : cluster.componentIds()) {
            Set<Integer> ids = new HashSet<>();
            for (var cycle : graph.source()
                .cycleGroups()) {
                if (cycle.componentId() == componentId) {
                    ids.addAll(cycle.memberNodeIds());
                    break;
                }
            }
            for (var node : graph.nodes()
                .values())
                if (node.pattern() != null && node.pattern()
                    .componentId() == componentId) ids.add(node.id());
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            for (int id : ids) {
                var box = layout.box(id);
                if (box == null) continue;
                minX = Math.min(minX, box.x());
                minY = Math.min(minY, box.y());
            }
            if (minX == Float.MAX_VALUE) continue;
            canvas
                .text("循环 #" + componentId, screen(cameraX, minX, zoom), screen(cameraY, minY - 14, zoom), 0xffe2b766);
        }
    }

    private static void drawRoutedLink(GraphCanvas canvas, List<GraphLayoutSnapshot.Point> route, int color,
        float cameraX, float cameraY, float zoom) {
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            int x1 = screen(cameraX, from.x(), zoom);
            int y1 = screen(cameraY, from.y(), zoom);
            int x2 = screen(cameraX, to.x(), zoom);
            int y2 = screen(cameraY, to.y(), zoom);
            if (x1 == x2) {
                GraphDraw.vLine(canvas, x1, Math.min(y1, y2), Math.max(y1, y2), color);
            } else if (y1 == y2) {
                GraphDraw.hLine(canvas, Math.min(x1, x2), Math.max(x1, x2), y1, color);
            } else {
                canvas.line(x1, y1, x2, y2, color, 0.5f);
            }
        }
    }

    private static void drawNode(GraphCanvas canvas, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
        GraphLayoutSnapshot.Box box, boolean selected, boolean adjacent, float cameraX, float cameraY, float zoom) {
        int x = screen(cameraX, box.x(), zoom);
        int y = screen(cameraY, box.y(), zoom);
        int right = screen(cameraX, box.x() + box.width(), zoom);
        int bottom = screen(cameraY, box.y() + box.height(), zoom);
        int pixelWidth = right - x;
        int pixelHeight = bottom - y;
        int border = selected ? 0xfff6c453 : adjacent ? 0xff83c5be : border(graph, node);
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS
            || graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER) {
            drawCompactCycleNode(
                canvas,
                graph,
                node,
                box,
                border,
                selected,
                x,
                y,
                right,
                bottom,
                pixelWidth,
                pixelHeight,
                cameraY,
                zoom);
            return;
        }
        canvas.fill(x, y, right, bottom, 0xee171b20);
        int borderWidth = selected ? 3 : 2;
        canvas.fill(x, y, right, Math.min(bottom, y + borderWidth), border);
        canvas.fill(x, Math.max(y, bottom - 2), right, bottom, border);
        canvas.fill(x, y, Math.min(right, x + 2), bottom, border);
        canvas.fill(Math.max(x, right - 2), y, right, bottom, border);
        if (pixelWidth < 18 || pixelHeight < 12) return;

        switch (node.kind()) {
            case MATERIAL -> {
                var material = node.material();
                if (material == null) return;
                if (zoom >= 0.7f && pixelWidth >= 82 && pixelHeight >= 48) {
                    canvas.item(material.key(), x + 7, y + 8);
                    drawFitted(canvas, node.label(), x + 29, y + 7, pixelWidth - 35, TEXT);
                    drawFitted(
                        canvas,
                        statusSymbol(material.status()) + " "
                            + material.status()
                                .name(),
                        x + 29,
                        y + 18,
                        pixelWidth - 35,
                        border);
                    drawFitted(
                        canvas,
                        "需求 " + compactAmount(material.exactRequested())
                            + "  库存 "
                            + compactAmount(material.exactFromInventory()),
                        x + 7,
                        y + 38,
                        pixelWidth - 14,
                        MUTED);
                    if (pixelHeight >= 64) {
                        drawFitted(canvas, "变化 " + signedTaskChange(material), x + 7, y + 53, pixelWidth - 14, MUTED);
                    }
                } else if (zoom >= 0.42f && pixelWidth >= 48 && pixelHeight >= 22) {
                    canvas.item(material.key(), x + 4, y + 4);
                    drawFitted(canvas, node.label(), x + 24, y + 4, pixelWidth - 28, TEXT);
                    if (pixelHeight >= 31) {
                        drawFitted(
                            canvas,
                            statusSymbol(material.status()) + " "
                                + material.status()
                                    .name(),
                            x + 24,
                            y + 15,
                            pixelWidth - 28,
                            border);
                    }
                } else {
                    canvas.centered(
                        statusSymbol(material.status()),
                        (x + right) / 2,
                        y + Math.max(2, (pixelHeight - canvas.lineHeight()) / 2),
                        border);
                }
            }
            case PATTERN -> {
                var pattern = node.pattern();
                if (pattern == null) return;
                String symbol = pattern.status() == CraftingGraphSnapshot.CandidateStatus.SELECTED ? "⚒" : "×";
                canvas.centered(symbol, (x + right) / 2, y + Math.max(3, pixelHeight >= 28 ? 6 : 2), border);
                if (pixelHeight >= 28 && pixelWidth >= 32) {
                    canvas.centered(
                        canvas.fit("× " + compactAmount(Long.toString(pattern.firingCount())), pixelWidth - 6),
                        (x + right) / 2,
                        y + 18,
                        TEXT);
                }
            }
            case CYCLE_GROUP -> {
                var cycle = node.cycle();
                if (cycle == null) return;
                drawFitted(canvas, "↻  循环 #" + cycle.componentId(), x + 7, y + 7, pixelWidth - 14, border);
                if (zoom >= 0.6f && pixelHeight >= 38) {
                    drawFitted(
                        canvas,
                        cycle.memberNodeIds()
                            .size() + " 个成员 · "
                            + cycle.status(),
                        x + 7,
                        y + 23,
                        pixelWidth - 14,
                        TEXT);
                    String required = cycle.requiredOutputs()
                        .isEmpty() ? "变化：-"
                            : "所需产物：" + cycle.requiredOutputs()
                                .size();
                    if (pixelHeight >= 55) drawFitted(canvas, required, x + 7, y + 40, pixelWidth - 14, MUTED);
                    if (pixelHeight >= 68) drawFitted(canvas, "双击查看内部图", x + 7, y + 54, pixelWidth - 14, MUTED);
                }
            }
            case CYCLE_CLUSTER -> {
                var cluster = node.cluster();
                if (cluster == null) return;
                drawFitted(canvas, "◎  循环簇 #" + cluster.clusterId(), x + 7, y + 7, pixelWidth - 14, border);
                if (zoom >= 0.6f && pixelHeight >= 38) {
                    drawFitted(
                        canvas,
                        cluster.componentIds()
                            .size() + " 个独立循环 · "
                            + cluster.flows()
                                .size()
                            + " 条跨环流",
                        x + 7,
                        y + 23,
                        pixelWidth - 14,
                        TEXT);
                    if (pixelHeight >= 55) drawFitted(canvas, "双击查看循环簇", x + 7, y + 40, pixelWidth - 14, MUTED);
                }
            }
            case FOLDER, REFERENCE -> {}
        }
    }

    private static void drawCompactCycleNode(GraphCanvas canvas, ClientCraftingGraph graph,
        ClientCraftingGraph.Node node, GraphLayoutSnapshot.Box box, int border, boolean selected, int x, int y,
        int right, int bottom, int pixelWidth, int pixelHeight, float cameraY, float zoom) {
        canvas.fill(x, y, right, bottom, 0xee171b20);
        int borderWidth = selected ? 2 : 1;
        canvas.fill(x, y, right, Math.min(bottom, y + borderWidth), border);
        canvas.fill(x, Math.max(y, bottom - borderWidth), right, bottom, border);
        canvas.fill(x, y, Math.min(right, x + borderWidth), bottom, border);
        canvas.fill(Math.max(x, right - borderWidth), y, right, bottom, border);
        if (pixelWidth < 12 || pixelHeight < 18) return;

        int numberTop = screen(cameraY, box.y() + 20, zoom);
        int iconBottom = Math.min(bottom, numberTop);
        if (node.kind() == ClientCraftingGraph.Kind.MATERIAL && node.material() != null) {
            int iconX = x + Math.max(0, (pixelWidth - 16) / 2);
            int iconY = y + Math.max(0, (iconBottom - y - 16) / 2);
            canvas.item(
                node.material()
                    .key(),
                iconX,
                iconY);
        } else if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            canvas.centered("⚒", (x + right) / 2, y + Math.max(1, (iconBottom - y - canvas.lineHeight()) / 2), border);
        }

        String text;
        int color;
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN && node.pattern() != null) {
            text = compactAmount(
                Long.toString(
                    node.pattern()
                        .firingCount()));
            color = TEXT;
        } else if (node.material() == null || !inDisplayedCycle(graph, node.material())) {
            text = "-";
            color = MUTED;
        } else {
            BigInteger delta = node.material()
                .producedBigInteger()
                .subtract(
                    node.material()
                        .consumedBigInteger());
            text = signedTaskChange(node.material());
            color = delta.signum() > 0 ? 0xff75c48b : delta.signum() < 0 ? 0xffe07a7a : MUTED;
        }
        drawScaledAmount(canvas, text, x, numberTop, right, bottom, color);
    }

    private static boolean inDisplayedCycle(ClientCraftingGraph graph, CraftingGraphSnapshot.MaterialNode material) {
        for (var cycle : graph.source()
            .cycleGroups()) {
            if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS ? cycle.componentId() == graph.focusedCycleId()
                : cycle.memberNodeIds()
                    .contains(material.nodeId()))
                return true;
        }
        return false;
    }

    static void drawScaledAmount(GraphCanvas canvas, String amount, int left, int top, int right, int bottom,
        int color) {
        int availableWidth = Math.max(1, right - left - 2);
        int availableHeight = Math.max(1, bottom - top);
        float scale = Math.min(
            0.5f,
            Math.min(
                (float) availableWidth / Math.max(1, canvas.width(amount)),
                (float) availableHeight / canvas.lineHeight()));
        float centerX = (left + right) / 2.0f;
        float textY = top + (availableHeight - canvas.lineHeight() * scale) / 2.0f;
        canvas.push();
        try {
            canvas.translate(centerX, textY);
            canvas.scale(scale, scale);
            canvas.text(amount, -canvas.width(amount) / 2, 0, color);
        } finally {
            canvas.pop();
        }
    }

    static String signedTaskChange(CraftingGraphSnapshot.MaterialNode material) {
        BigInteger delta = material.producedBigInteger()
            .subtract(material.consumedBigInteger());
        String magnitude = compactAmount(
            delta.abs()
                .toString());
        return delta.signum() > 0 ? "+" + magnitude : delta.signum() < 0 ? "-" + magnitude : "0";
    }

    static int screen(float camera, float world, float zoom) {
        return Math.round(camera + world * zoom);
    }

    private static void drawFitted(GraphCanvas canvas, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth > 0) canvas.text(canvas.fit(text, maxWidth), x, y, color);
    }

    private static List<String> tooltip(ClientCraftingGraph graph, @Nullable ClientCraftingGraph.Node node) {
        if (node == null) return JdkCompat.listOf();
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN && node.pattern() != null) {
            var pattern = node.pattern();
            List<String> lines = new ArrayList<>();
            lines.add(pattern.displayIdentity());
            lines.add("执行次数：" + compactAmount(Long.toString(pattern.firingCount())));
            lines.add(
                "状态：" + pattern.status()
                    .name());
            lines.add("输入：" + relationshipText(graph, pattern.inputs()));
            lines.add("输出：" + relationshipText(graph, pattern.outputs()));
            if (pattern.rejectionReason() != null) lines.add(pattern.rejectionReason());
            return lines;
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP && node.cycle() != null) {
            var cycle = node.cycle();
            return JdkCompat.listOf(
                "循环 #" + cycle.componentId(),
                cycle.status(),
                "成员：" + cycle.memberNodeIds()
                    .size(),
                "所需产物：" + cycle.requiredOutputs()
                    .size(),
                "外部输入：" + cycle.externalInputs()
                    .size(),
                "所需种子：" + cycle.requiredSeed()
                    .size());
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER && node.cluster() != null) {
            var cluster = node.cluster();
            return JdkCompat.listOf(
                "循环簇 #" + cluster.clusterId(),
                "独立 SCC：" + cluster.componentIds()
                    .size(),
                "跨环物料流：" + cluster.flows()
                    .size(),
                "循环：" + cluster.componentIds());
        }
        if (node.key() == null) return JdkCompat.listOf();
        List<String> lines = new ArrayList<>(keyTooltip(node.key()));
        lines.add(
            "registry id: " + node.key()
                .getId());
        if (graph.view() == ClientCraftingGraph.View.CYCLE_CLUSTER && graph.focusedCluster() != null
            && node.material() != null) {
            for (InterCycleFlow flow : graph.focusedCluster()
                .flows()) {
                if (flow.materialNodeId() != node.material()
                    .nodeId()) continue;
                lines.add(
                    "跨环流：循环 #" + flow.fromComponentId()
                        + " → #"
                        + flow.toComponentId()
                        + "（输出 ×"
                        + compactAmount(Long.toString(flow.outputAmount()))
                        + " / 输入 ×"
                        + compactAmount(Long.toString(flow.inputAmount()))
                        + "）");
            }
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && node.material() != null) {
            var cycle = focusedCycle(graph);
            if (cycle != null) {
                lines.add(
                    "单次变化：" + signedExact(
                        exactAmountFor(cycle.exactSingleNetOutputs(), cycle.singleNetOutputs(), node.key())));
                lines.add(
                    "总变化：" + signedExact(
                        exactAmountFor(cycle.exactTotalNetOutputs(), cycle.totalNetOutputs(), node.key())));
                lines.add("所需种子：" + amountText(cycle.requiredSeed(), node.key()));
                lines.add("所需产物：" + amountText(cycle.requiredOutputs(), node.key()));
                lines.add("外部输入：" + amountText(cycle.externalInputs(), node.key()));
                lines.add(
                    "任务消耗：" + compactAmount(
                        node.material()
                            .exactConsumed()));
                lines.add(
                    "任务产出：" + compactAmount(
                        node.material()
                            .exactProduced()));
            }
        }
        return lines;
    }

    /** The key's native item tooltip, or its display name when the item cannot describe itself. */
    static List<String> keyTooltip(AEKey key) {
        List<String> lines = new ArrayList<>();
        var mc = net.minecraft.client.Minecraft.getMinecraft();
        try {
            IAEStack<?> stack = key.toNative(1);
            ItemStack display = stack == null ? null : stack.getItemStackForNEI();
            if (display != null && display.getItem() != null && mc.thePlayer != null) {
                @SuppressWarnings("unchecked")
                List<String> native_ = display.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
                lines.addAll(native_);
            }
        } catch (RuntimeException ignored) {
            lines.clear();
        }
        if (lines.isEmpty()) lines.add(key.getDisplayName());
        return lines;
    }

    static int border(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return 0xffd59b45;
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) return 0xffcf8f45;
        if (node.kind() == ClientCraftingGraph.Kind.PATTERN) {
            return node.pattern() != null && node.pattern()
                .status() == CraftingGraphSnapshot.CandidateStatus.SELECTED ? 0xff66a182 : 0xff9a5964;
        }
        if (graph.view() == ClientCraftingGraph.View.CYCLE_FOCUS && graph.isBoundaryMaterial(node.id())) {
            return 0xff71808a;
        }
        if (node.material() == null) return 0xff8293a4;
        return statusColor(
            node.material()
                .status());
    }

    static int statusColor(CraftingGraphSnapshot.MaterialStatus status) {
        return switch (status) {
            case SATISFIED -> 0xff68a878;
            case CRAFTING -> 0xff5f9fc5;
            case MISSING -> 0xffcf5e5e;
            case UNSUPPORTED -> 0xffa36c9a;
            case CYCLE -> 0xffd59b45;
        };
    }

    static String statusSymbol(CraftingGraphSnapshot.MaterialStatus status) {
        return switch (status) {
            case SATISFIED -> "✓";
            case CRAFTING -> "⚒";
            case MISSING -> "!";
            case UNSUPPORTED -> "?";
            case CYCLE -> "↻";
        };
    }

    /** AE-style compact amount of an exact decimal string (1.21's HostText.ae2Amount). */
    static String compactAmount(String value) {
        try {
            BigInteger amount = new BigInteger(value);
            return BigNumberFormatter.format(amount.signum() < 0 ? BigInteger.ZERO : amount, 1, false);
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    /** Tangent-aligned triangle so curved cycle arrows do not snap to four directions. */
    private static void drawArrowHead(GraphCanvas canvas, List<GraphLayoutSnapshot.Point> route, int color,
        float cameraX, float cameraY, float zoom) {
        var end = JdkCompat.last(route);
        GraphLayoutSnapshot.Point previous = route.get(route.size() - 2);
        for (int i = route.size() - 2; i >= 0 && previous.equals(end); i--) previous = route.get(i);
        float tipX = cameraX + end.x() * zoom;
        float tipY = cameraY + end.y() * zoom;
        float dx = (end.x() - previous.x()) * zoom;
        float dy = (end.y() - previous.y()) * zoom;
        float length = Math.max(0.001f, (float) Math.hypot(dx, dy));
        float unitX = dx / length;
        float unitY = dy / length;
        float baseX = tipX - unitX * 6f;
        float baseY = tipY - unitY * 6f;
        float normalX = -unitY * 3.5f;
        float normalY = unitX * 3.5f;
        GraphDraw
            .triangle(canvas, tipX, tipY, baseX + normalX, baseY + normalY, baseX - normalX, baseY - normalY, color);
    }

    private static long amountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        for (var value : values) if (value.key()
            .equals(key)) return value.amount();
        return 0L;
    }

    private static String amountText(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        for (var value : values) if (value.key()
            .equals(key)) return formatMagnitude(key, value.amount());
        return "-";
    }

    private static String relationshipText(ClientCraftingGraph graph,
        List<CraftingGraphSnapshot.Relationship> relationships) {
        if (relationships.isEmpty()) return "-";
        StringBuilder text = new StringBuilder();
        for (var relationship : relationships) {
            if (text.length() > 0) text.append(", ");
            var node = graph.nodes()
                .get(relationship.materialNodeId());
            String label = node == null || node.key() == null ? "#" + relationship.materialNodeId()
                : node.key()
                    .getDisplayName();
            text.append(label)
                .append(" ×")
                .append(compactAmount(Long.toString(relationship.amount())));
        }
        return text.toString();
    }

    private static BigInteger exactAmountFor(List<ExactKeyAmount> exactValues,
        List<CraftingGraphSnapshot.KeyAmount> fallback, AEKey key) {
        for (var value : exactValues) if (value.key()
            .equals(key))
            return value.amount()
                .value();
        return BigInteger.valueOf(amountFor(fallback, key));
    }

    private static String signedExact(BigInteger amount) {
        String magnitude = HostText.expandedStorageBytes(amount.abs());
        return amount.signum() > 0 ? "+" + magnitude : amount.signum() < 0 ? "-" + magnitude : "0";
    }

    private static String formatMagnitude(AEKey key, long amount) {
        long magnitude = amount == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(amount);
        return BigNumberFormatter.format(BigInteger.valueOf(magnitude), perUnit(key), true);
    }

    private static int perUnit(AEKey key) {
        return Math.max(1, key.getAmountPerUnit());
    }
}
