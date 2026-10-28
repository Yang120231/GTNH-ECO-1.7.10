package cn.dancingsnow.neoecoae.client.gui.graph;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/** Camera changes trigger queries; stationary frames reuse the exact same result. */
final class GraphVisibilityCache {

    static final int LIVE_LIMIT = 256;
    private GraphLayoutSnapshot layout;
    private GraphLayoutSnapshot.Bounds area;
    private VisiblePlan visible;

    /** Live-drawable nodes and edges of one camera area, plus the exact counts the stats line reports. */
    @com.github.bsideup.jabel.Desugar
    record VisiblePlan(List<GraphLayoutSnapshot.Box> nodes, List<ClientCraftingGraph.Link> edges, int nodeCount,
        int edgeCount) {}

    VisiblePlan get(GraphLayoutSnapshot next, GraphLayoutSnapshot.Bounds nextArea) {
        if (layout != next || !nextArea.equals(area)) {
            layout = next;
            area = nextArea;
            var nodes = next.query(nextArea, LIVE_LIMIT + 1);
            visible = new VisiblePlan(
                nodes,
                nodes.size() > LIVE_LIMIT ? JdkCompat.listOf() : next.queryLinks(nextArea),
                next.nodeCount(nextArea),
                next.edgeCount(nextArea));
        }
        return visible;
    }

    void clear() {
        layout = null;
        area = null;
        visible = null;
    }
}
