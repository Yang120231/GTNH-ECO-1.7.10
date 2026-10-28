package cn.dancingsnow.neoecoae.client.gui.graph;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.I18n;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.CompactTreeNode;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Minecraft-style compact tree renderer. */
@SideOnly(Side.CLIENT)
public final class CompactTreeRenderer {

    private static final int TEXT = GraphRenderer.TEXT;
    private static final int MUTED = GraphRenderer.MUTED;

    GraphRenderer.Frame render(GraphCanvas canvas, ClientCraftingGraph graph, GraphLayoutSnapshot layout, float cameraX,
        float cameraY, float zoom, int screenWidth, int screenHeight, int mouseX, int mouseY,
        @Nullable Integer selectedId, GraphProfiler profiler, GraphVisibilityCache.VisiblePlan visible) {
        long started = System.nanoTime();
        Set<Integer> neighborhood = selectedId == null ? JdkCompat.setOf() : graph.neighborhood(selectedId);
        for (var link : visible.edges()) {
            drawLink(canvas, layout, link, selectedId, neighborhood, cameraX, cameraY, zoom);
        }
        ClientCraftingGraph.Node hovered = null;
        float worldMouseX = (mouseX - cameraX) / zoom;
        float worldMouseY = (mouseY - cameraY) / zoom;
        for (var box : visible.nodes()) {
            var node = graph.nodes()
                .get(box.nodeId());
            if (node == null) continue;
            boolean selected = selectedId != null && selectedId == node.id();
            drawNode(canvas, graph, node, box, selected, neighborhood.contains(node.id()), cameraX, cameraY, zoom);
            if (box.contains(worldMouseX, worldMouseY)) hovered = node;
        }
        for (var row : layout
            .queryRowFrames(GraphViewTransform.viewport(cameraX, cameraY, zoom, screenWidth, screenHeight))) {
            drawRowFrame(canvas, row.left(), row.top(), row.right(), row.bottom(), cameraX, cameraY, zoom);
        }
        for (var box : visible.nodes()) {
            var node = graph.nodes()
                .get(box.nodeId());
            if (node != null && node.material() != null
                && node.material()
                    .status() == CraftingGraphSnapshot.MaterialStatus.MISSING) {
                drawNodeFrame(canvas, box, 0xffcf5e5e, cameraX, cameraY, zoom);
            }
        }
        canvas.flush();
        profiler.update(
            visible.nodes()
                .size(),
            visible.edges()
                .size(),
            System.nanoTime() - started);
        return new GraphRenderer.Frame(hovered, JdkCompat.listOf());
    }

    private static void drawRowFrame(GraphCanvas canvas, float left, float top, float right, float bottom,
        float cameraX, float cameraY, float zoom) {
        int x = GraphRenderer.screen(cameraX, left, zoom);
        int y = GraphRenderer.screen(cameraY, top, zoom);
        int r = GraphRenderer.screen(cameraX, right, zoom);
        int b = GraphRenderer.screen(cameraY, bottom, zoom);
        if (r <= x || b <= y) return;
        int color = 0xff8293a4;
        canvas.fill(x, y, r, Math.min(b, y + 1), color);
        canvas.fill(x, Math.max(y, b - 1), r, b, color);
        canvas.fill(x, y, Math.min(r, x + 1), b, color);
        canvas.fill(Math.max(x, r - 1), y, r, b, color);
    }

    static void drawNodeFrame(GraphCanvas canvas, GraphLayoutSnapshot.Box box, int color, float cameraX, float cameraY,
        float zoom) {
        int x = GraphRenderer.screen(cameraX, box.x(), zoom);
        int y = GraphRenderer.screen(cameraY, box.y(), zoom);
        int right = GraphRenderer.screen(cameraX, box.x() + box.width(), zoom);
        int bottom = GraphRenderer.screen(cameraY, box.y() + box.height(), zoom);
        if (right <= x || bottom <= y) return;
        int thickness = Math.min(2, Math.min(right - x, bottom - y));
        canvas.fill(x, y, right, y + thickness, color);
        canvas.fill(x, bottom - thickness, right, bottom, color);
        canvas.fill(x, y, x + thickness, bottom, color);
        canvas.fill(right - thickness, y, right, bottom, color);
    }

    static void drawLink(GraphCanvas canvas, GraphLayoutSnapshot layout, ClientCraftingGraph.Link link,
        @Nullable Integer selected, Set<Integer> neighborhood, float cameraX, float cameraY, float zoom) {
        boolean highlighted = selected != null && (link.fromId() == selected || link.toId() == selected)
            && (neighborhood.contains(link.fromId()) || neighborhood.contains(link.toId()));
        int color = highlighted ? 0xfff6c453 : link.selected() ? 0xff8293a4 : 0xff66505a;
        List<GraphLayoutSnapshot.Point> route = layout.edgePoints(link);
        if (route.isEmpty()) {
            var from = layout.box(link.fromId());
            var to = layout.box(link.toId());
            if (from == null || to == null) return;
            route = verticalRoute(from, to);
        }
        drawRoute(canvas, route, color, cameraX, cameraY, zoom);
        var end = JdkCompat.last(route);
        int endX = GraphRenderer.screen(cameraX, end.x(), zoom);
        int endY = GraphRenderer.screen(cameraY, end.y(), zoom);
        canvas.fill(endX - 3, endY - 2, endX + 1, endY + 3, color);
    }

    private static void drawRoute(GraphCanvas canvas, List<GraphLayoutSnapshot.Point> route, int color, float cameraX,
        float cameraY, float zoom) {
        for (int i = 1; i < route.size(); i++) {
            var from = route.get(i - 1);
            var to = route.get(i);
            int x1 = GraphRenderer.screen(cameraX, from.x(), zoom);
            int y1 = GraphRenderer.screen(cameraY, from.y(), zoom);
            int x2 = GraphRenderer.screen(cameraX, to.x(), zoom);
            int y2 = GraphRenderer.screen(cameraY, to.y(), zoom);
            if (x1 == x2) GraphDraw.vLine(canvas, x1, Math.min(y1, y2), Math.max(y1, y2), color);
            else if (y1 == y2) GraphDraw.hLine(canvas, Math.min(x1, x2), Math.max(x1, x2), y1, color);
            else {
                int middle = (x1 + x2) / 2;
                GraphDraw.hLine(canvas, Math.min(x1, middle), Math.max(x1, middle), y1, color);
                GraphDraw.vLine(canvas, middle, Math.min(y1, y2), Math.max(y1, y2), color);
                GraphDraw.hLine(canvas, Math.min(middle, x2), Math.max(middle, x2), y2, color);
            }
        }
    }

    private static List<GraphLayoutSnapshot.Point> verticalRoute(GraphLayoutSnapshot.Box from,
        GraphLayoutSnapshot.Box to) {
        float startX = from.centerX();
        float startY = from.y() + from.height();
        float endX = to.centerX();
        float endY = to.y();
        float middle = (startY + endY) / 2;
        return JdkCompat.listOf(
            new GraphLayoutSnapshot.Point(startX, startY),
            new GraphLayoutSnapshot.Point(startX, middle),
            new GraphLayoutSnapshot.Point(endX, middle),
            new GraphLayoutSnapshot.Point(endX, endY));
    }

    static void drawNode(GraphCanvas canvas, ClientCraftingGraph graph, ClientCraftingGraph.Node node,
        GraphLayoutSnapshot.Box box, boolean selected, boolean adjacent, float cameraX, float cameraY, float zoom) {
        int x = GraphRenderer.screen(cameraX, box.x(), zoom);
        int y = GraphRenderer.screen(cameraY, box.y(), zoom);
        int right = GraphRenderer.screen(cameraX, box.x() + box.width(), zoom);
        int bottom = GraphRenderer.screen(cameraY, box.y() + box.height(), zoom);
        if (right <= x || bottom <= y) return;
        int accent = selected ? 0xfff6c453 : adjacent ? 0xff83c5be : nodeBorder(graph, node);
        int background = selected ? 0xee2b271c : adjacent ? 0xee182527 : 0xee171b20;
        canvas.fill(x, y, right, bottom, background);
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            drawFolder(canvas, graph, node, x, y, right, bottom, accent);
        } else {
            drawCompactContent(canvas, node, x, y, right, bottom, accent);
        }
    }

    private static void drawFolder(GraphCanvas canvas, ClientCraftingGraph graph, ClientCraftingGraph.Node node, int x,
        int y, int right, int bottom, int border) {
        CompactTreeNode metadata = graph.compactTreeNode(node.id());
        if (metadata == null || bottom - y < 14) return;
        String symbol = metadata.containsCycle() ? "↻"
            : metadata.containsMissing() ? "!" : metadata.containsUnsupported() ? "?" : "▱";
        canvas.centered(symbol, (x + right) / 2, y + 5, border);
        if (bottom - y >= 24)
            GraphRenderer.drawScaledAmount(canvas, "+" + metadata.hiddenNodeCount(), x, y + 20, right, bottom, TEXT);
    }

    private static void drawCompactContent(GraphCanvas canvas, ClientCraftingGraph.Node node, int x, int y, int right,
        int bottom, int border) {
        if (bottom - y < 14) return;
        if (node.kind() == ClientCraftingGraph.Kind.MATERIAL
            || node.kind() == ClientCraftingGraph.Kind.REFERENCE && node.material() != null) {
            var material = node.material();
            if (material != null && material.key() != null && right - x >= 16 && bottom - y >= 20) {
                int iconX = x + (right - x - 16) / 2;
                int iconY = y + (Math.min(20, bottom - y) - 16) / 2;
                canvas.item(material.key(), iconX, iconY);
            }
            if (material != null && material.key() != null
                && bottom - y >= 24
                && (material.consumedBigInteger()
                    .signum() > 0
                    || material.producedBigInteger()
                        .signum() > 0
                    || material.requestedBigInteger()
                        .signum() > 0)) {
                GraphRenderer.drawScaledAmount(canvas, flowText(material), x, y + 20, right, bottom, TEXT);
            }
        } else if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) {
            var cycle = node.cycle();
            String label = cycle == null ? node.label() : "↻ 循环 #" + cycle.componentId();
            canvas.text(canvas.fit(label, Math.max(20, right - x - 24)), x + 6, y + 5, TEXT);
            if (cycle != null) canvas.text(
                cycle.memberNodeIds()
                    .size() + " 个成员",
                x + 6,
                y + 19,
                MUTED);
            canvas.text("↻", right - 14, y + 9, border);
        } else if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) {
            var cluster = node.cluster();
            String label = cluster == null ? node.label() : "◎ 循环簇 #" + cluster.clusterId();
            canvas.text(canvas.fit(label, Math.max(20, right - x - 24)), x + 6, y + 5, TEXT);
            if (cluster != null) canvas.text(
                cluster.componentIds()
                    .size() + " 个循环",
                x + 6,
                y + 19,
                MUTED);
            canvas.text("◎", right - 14, y + 9, border);
        } else if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) {
            canvas.text(canvas.fit(node.label(), Math.max(20, right - x - 10)), x + 6, y + 9, MUTED);
        }
    }

    static List<String> tooltip(ClientCraftingGraph graph, @Nullable ClientCraftingGraph.Node node) {
        if (node == null) return JdkCompat.listOf();
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var folder = graph.compactTreeNode(node.id());
            if (folder == null) return JdkCompat.listOf(node.label());
            List<String> result = new ArrayList<>();
            result.add(node.label());
            result.add("hidden nodes: " + folder.hiddenNodeCount());
            result.add("hidden layers: " + folder.hiddenDepth());
            result.add(
                "missing: " + folder.containsMissing()
                    + ", unsupported: "
                    + folder.containsUnsupported()
                    + ", cycle: "
                    + folder.containsCycle());
            result.add("click: expand one level, right click: expand all");
            return result;
        }
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) {
            var treeNode = graph.compactTreeNode(node.id());
            return JdkCompat.listOf(
                node.label(),
                "shared dependency reference",
                treeNode == null ? "" : "source id: " + treeNode.sourceId());
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP && node.cycle() != null) {
            var cycle = node.cycle();
            return JdkCompat.listOf(
                "循环 #" + cycle.componentId(),
                "成员：" + cycle.memberNodeIds()
                    .size(),
                "状态：" + cycle.status(),
                "双击：打开循环详情");
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER && node.cluster() != null) {
            var cluster = node.cluster();
            return JdkCompat.listOf(
                "循环簇 #" + cluster.clusterId(),
                "独立循环：" + cluster.componentIds()
                    .size(),
                "跨环物料流：" + cluster.flows()
                    .size(),
                "双击：打开循环簇");
        }
        if (node.material() != null) {
            var material = node.material();
            return JdkCompat.listOf(
                node.label(),
                "requested: " + GraphRenderer.compactAmount(material.exactRequested()),
                "inventory: " + GraphRenderer.compactAmount(material.exactFromInventory()),
                "to craft: " + GraphRenderer.compactAmount(material.exactToCraft()),
                "missing: " + GraphRenderer.compactAmount(material.exactMissing()),
                "consumed: " + GraphRenderer.compactAmount(material.exactConsumed()),
                "produced: " + GraphRenderer.compactAmount(material.exactProduced()),
                "status: " + material.status());
        }
        return JdkCompat.listOf(node.label());
    }

    static int nodeBorder(ClientCraftingGraph graph, ClientCraftingGraph.Node node) {
        if (node.kind() == ClientCraftingGraph.Kind.FOLDER) {
            var metadata = graph.compactTreeNode(node.id());
            if (metadata != null && metadata.containsCycle()) return 0xffd59b45;
            if (metadata != null && metadata.containsMissing()) return 0xffcf5e5e;
            if (metadata != null && metadata.containsUnsupported()) return 0xffa36c9a;
            return 0xff788996;
        }
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP) return 0xffd59b45;
        if (node.kind() == ClientCraftingGraph.Kind.CYCLE_CLUSTER) return 0xffcf8f45;
        if (node.kind() == ClientCraftingGraph.Kind.REFERENCE) return 0xff8d98a3;
        if (node.material() == null) return 0xff8293a4;
        return GraphRenderer.statusColor(
            node.material()
                .status());
    }

    private static String flowText(CraftingGraphSnapshot.MaterialNode material) {
        if (showsPendingCraftLabel(material)) {
            return I18n.format("gui.neoecoae.crafting_graph.node.pending_craft");
        }
        if (material.consumedBigInteger()
            .signum() == 0
            && material.producedBigInteger()
                .signum() == 0) {
            return GraphRenderer.compactAmount(material.exactRequested());
        }
        BigInteger delta = material.producedBigInteger()
            .subtract(material.consumedBigInteger());
        String amount = GraphRenderer.compactAmount(
            delta.abs()
                .toString());
        return delta.signum() > 0 ? "+" + amount : delta.signum() < 0 ? "-" + amount : "0";
    }

    static boolean showsPendingCraftLabel(CraftingGraphSnapshot.MaterialNode material) {
        return material.status() == CraftingGraphSnapshot.MaterialStatus.CRAFTING && material.toCraftBigInteger()
            .signum() > 0
            && material.producedBigInteger()
                .equals(material.consumedBigInteger());
    }
}
