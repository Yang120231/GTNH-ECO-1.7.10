package cn.dancingsnow.neoecoae.crafting.planner.ported.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.dancingsnow.neoecoae.crafting.planner.ported.ECOCancellation;

/** Iterative Tarjan SCC analysis. Runtime and memory are O(V + E), without Java recursion. */
public final class TarjanSccAnalyzer {

    private static final class Frame {

        final Object node;
        final Object parent;
        final List<CraftingGraphEdge> edges;
        int nextEdge;

        Frame(Object node, Object parent, List<CraftingGraphEdge> edges) {
            this.node = node;
            this.parent = parent;
            this.edges = edges;
        }
    }

    public List<SccComponent> analyze(CraftingDependencyGraph graph, ECOCancellation cancellation)
        throws InterruptedException {
        Map<Object, Integer> index = new HashMap<>();
        Map<Object, Integer> lowlink = new HashMap<>();
        ArrayDeque<Object> tarjanStack = new ArrayDeque<>();
        Set<Object> onStack = new HashSet<>();
        List<List<Object>> memberSets = new ArrayList<>();
        int nextIndex = 0;

        for (Object root : graph.nodes()
            .keySet()) {
            cancellation.checkpoint();
            if (index.containsKey(root)) continue;
            ArrayDeque<Frame> dfs = new ArrayDeque<>();
            index.put(root, nextIndex);
            lowlink.put(root, nextIndex++);
            tarjanStack.push(root);
            onStack.add(root);
            dfs.push(new Frame(root, null, graph.outgoing(root)));

            while (!dfs.isEmpty()) {
                cancellation.checkpoint();
                Frame frame = dfs.peek();
                if (frame.nextEdge < frame.edges.size()) {
                    Object target = frame.edges.get(frame.nextEdge++)
                        .requiredInput();
                    Integer targetIndex = index.get(target);
                    if (targetIndex == null) {
                        index.put(target, nextIndex);
                        lowlink.put(target, nextIndex++);
                        tarjanStack.push(target);
                        onStack.add(target);
                        dfs.push(new Frame(target, frame.node, graph.outgoing(target)));
                    } else if (onStack.contains(target)) {
                        lowlink.put(frame.node, Math.min(lowlink.get(frame.node), targetIndex));
                    }
                    continue;
                }

                dfs.pop();
                if (frame.parent != null) {
                    lowlink.put(frame.parent, Math.min(lowlink.get(frame.parent), lowlink.get(frame.node)));
                }
                if (lowlink.get(frame.node)
                    .equals(index.get(frame.node))) {
                    List<Object> members = new ArrayList<>();
                    Object member;
                    do {
                        member = tarjanStack.pop();
                        onStack.remove(member);
                        members.add(member);
                    } while (!member.equals(frame.node));
                    memberSets.add(com.google.common.collect.ImmutableList.copyOf(members));
                }
            }
        }

        List<SccComponent> result = new ArrayList<>(memberSets.size());
        int id = 0;
        for (List<Object> members : memberSets) {
            cancellation.checkpoint();
            Set<Object> memberLookup = new LinkedHashSet<>(members);
            List<CraftingGraphEdge> internal = new ArrayList<>();
            for (Object member : members) {
                for (CraftingGraphEdge edge : graph.outgoing(member)) {
                    if (memberLookup.contains(edge.requiredInput())) internal.add(edge);
                }
            }
            boolean cyclic = members.size() > 1 || internal.stream()
                .anyMatch(
                    edge -> edge.producer()
                        .equals(edge.requiredInput()));
            result.add(new SccComponent(id++, members, internal, cyclic));
        }
        return com.google.common.collect.ImmutableList.copyOf(result);
    }
}
