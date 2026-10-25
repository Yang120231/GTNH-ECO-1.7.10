package cn.dancingsnow.neoecoae.crafting.planner.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

class TarjanSccAnalyzerTest {

    @Test
    void zeroDfsIndexIsAVisitedNodeInACycle() throws InterruptedException {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class);
        var graph = graph(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b, c),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(edge(a, b), edge(b, c), edge(c, a)));
        var components = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
        assertEquals(1, components.size());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(a, b, c),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(components)
                    .members()));
        assertTrue(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(components)
                .cyclic());
    }

    @Test
    void separatesSelfLoopAndAcyclicAndDisconnectedNodes() throws InterruptedException {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class), d = mock(AEKey.class);
        var graph = graph(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b, c, d),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(edge(a, b), edge(b, b), edge(c, b)));
        var components = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
        assertEquals(4, components.size());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(b),
            components.stream()
                .filter(SccComponent::cyclic)
                .flatMap(
                    component -> component.members()
                        .stream())
                .collect(Collectors.toSet()));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(a, b, c, d),
            components.stream()
                .flatMap(
                    component -> component.members()
                        .stream())
                .collect(Collectors.toSet()));
    }

    private static CraftingGraphEdge edge(AEKey from, AEKey to) {
        return new CraftingGraphEdge(from, to, null, null);
    }

    private static CraftingDependencyGraph graph(List<AEKey> keys, List<CraftingGraphEdge> edges) {
        var nodes = new LinkedHashMap<AEKey, CraftingGraphNode>();
        for (AEKey key : keys)
            nodes.put(key, new CraftingGraphNode(key, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        return new CraftingDependencyGraph(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(keys), nodes, edges);
    }
}
