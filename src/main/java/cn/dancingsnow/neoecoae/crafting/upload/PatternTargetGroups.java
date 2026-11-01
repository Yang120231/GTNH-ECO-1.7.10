package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.Evaluation;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.SlotState;

/**
 * Folds target evaluations into list rows. Hosts of the same kind, name, route, match level, route signature, circuit
 * and programming cover are interchangeable for the player (typically parallel machines), and pattern hosts of one
 * machine in one mode feed the same machine, so each set shares one row and one server-issued token; the server picks
 * the member to write at submit time.
 *
 * <p>
 * Tokens are kept for a group key that is still present after a refresh, so a click sent against the previous list
 * still names the same group. Only the groups of the latest refresh are addressable.
 * </p>
 */
public final class PatternTargetGroups {

    public static final class Group {

        public final String key;
        /** Members in {@link PatternTargetSelector#ORDER}; the first one is the group's best member. */
        public final List<Evaluation> members;
        public final SlotState state;
        public final int freeMembers, duplicates, emptySlots, totalSlots;
        /** Some member accepts a manual upload right now. */
        public final boolean manual;
        int token;

        Group(String key, List<Evaluation> members) {
            this.key = key;
            this.members = Collections.unmodifiableList(members);
            int free = 0, duplicate = 0, empty = 0, total = 0;
            boolean manual = false, full = false;
            for (Evaluation member : members) {
                if (member.state == SlotState.FREE) free++;
                else if (member.state == SlotState.DUPLICATE) duplicate++;
                else if (member.state == SlotState.FULL) full = true;
                empty += member.emptySlots;
                total += member.totalSlots;
                manual |= member.canUploadManually();
            }
            this.freeMembers = free;
            this.duplicates = duplicate;
            this.emptySlots = empty;
            this.totalSlots = total;
            this.manual = manual;
            // A free parallel machine wins over copies that already hold the pattern.
            this.state = free > 0 ? SlotState.FREE
                : duplicate > 0 ? SlotState.DUPLICATE : full ? SlotState.FULL : SlotState.OFFLINE;
        }

        public int token() {
            return token;
        }

        public Evaluation best() {
            return members.get(0);
        }

        /** Members a manual upload may write to, in the order they are tried: most empty slots first. */
        public List<Evaluation> uploadOrder() {
            List<Evaluation> order = new ArrayList<>();
            for (Evaluation member : members) if (member.canUploadManually()) order.add(member);
            order.sort(UPLOAD_ORDER);
            return order;
        }

        /** The member the next upload would choose, or the best member when none can take the pattern. */
        public Evaluation position() {
            List<Evaluation> order = uploadOrder();
            return order.isEmpty() ? best() : order.get(0);
        }

        public boolean contains(PatternUploadTarget target) {
            for (Evaluation member : members) if (member.target == target) return true;
            return false;
        }
    }

    private static final Comparator<Evaluation> UPLOAD_ORDER = Comparator
        .<Evaluation>comparingInt(evaluation -> -evaluation.emptySlots)
        .thenComparing(evaluation -> evaluation.target.positionKey());

    private static final Comparator<Group> GROUP_ORDER = (left, right) -> PatternTargetSelector.ORDER
        .compare(left.best(), right.best());

    private final Map<String, Integer> tokenByKey = new HashMap<>();
    private final Map<Integer, Group> byToken = new HashMap<>();
    private List<Group> groups = Collections.emptyList();
    /**
     * Shared by every session (server thread only), so a token from a closed session can never name a group of a new
     * one.
     */
    private static int serial;

    /** Regroups {@code evaluations}, keeping the token of every group key that is still present. */
    public List<Group> update(List<Evaluation> evaluations) {
        List<Group> next = group(evaluations);
        Map<String, Integer> previous = new HashMap<>(tokenByKey);
        tokenByKey.clear();
        byToken.clear();
        for (Group group : next) {
            Integer token = previous.get(group.key);
            if (token == null) token = nextToken();
            group.token = token;
            tokenByKey.put(group.key, token);
            byToken.put(token, group);
        }
        groups = Collections.unmodifiableList(next);
        return groups;
    }

    private static int nextToken() {
        if (serial == Integer.MAX_VALUE) serial = 0;
        return ++serial;
    }

    public List<Group> groups() {
        return groups;
    }

    /** Null when the token does not name a group of the latest refresh. */
    public Group group(int token) {
        return byToken.get(token);
    }

    public Group groupOf(PatternUploadTarget target) {
        if (target == null) return null;
        for (Group group : groups) if (group.contains(target)) return group;
        return null;
    }

    public void clear() {
        tokenByKey.clear();
        byToken.clear();
        groups = Collections.emptyList();
    }

    /**
     * Pure grouping, sorted best group first. Hosts join one group when they share a {@link #key} (interchangeable
     * parallel machines) or feed the same machine in the same mode ({@link #machineKey}), e.g. several pattern hatches
     * of one multiblock whose own names or ghost circuits differ.
     */
    public static List<Group> group(List<Evaluation> evaluations) {
        int size = evaluations.size();
        int[] parent = new int[size];
        for (int index = 0; index < size; index++) parent[index] = index;
        Map<String, Integer> firstByKey = new HashMap<>();
        Map<String, Integer> firstByMachine = new HashMap<>();
        String[] keys = new String[size];
        for (int index = 0; index < size; index++) {
            Evaluation evaluation = evaluations.get(index);
            keys[index] = key(evaluation);
            Integer same = firstByKey.putIfAbsent(keys[index], index);
            if (same != null) union(parent, same, index);
            String machine = machineKey(evaluation);
            if (machine == null) continue;
            same = firstByMachine.putIfAbsent(machine, index);
            if (same != null) union(parent, same, index);
        }
        Map<Integer, List<Evaluation>> byRoot = new LinkedHashMap<>();
        Map<Integer, String> keyByRoot = new HashMap<>();
        for (int index = 0; index < size; index++) {
            int root = find(parent, index);
            byRoot.computeIfAbsent(root, ignored -> new ArrayList<>())
                .add(evaluations.get(index));
            // The smallest member key names the group, so its token survives a refresh in any discovery order.
            String current = keyByRoot.get(root);
            if (current == null || keys[index].compareTo(current) < 0) keyByRoot.put(root, keys[index]);
        }
        List<Group> result = new ArrayList<>(byRoot.size());
        for (Map.Entry<Integer, List<Evaluation>> entry : byRoot.entrySet()) {
            List<Evaluation> members = entry.getValue();
            members.sort(PatternTargetSelector.ORDER);
            result.add(new Group(keyByRoot.get(entry.getKey()), members));
        }
        result.sort(GROUP_ORDER);
        return result;
    }

    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static void union(int[] parent, int left, int right) {
        int a = find(parent, left), b = find(parent, right);
        if (a != b) parent[Math.max(a, b)] = Math.min(a, b);
    }

    /**
     * Same machine and mode, host kind, match level and programming cover; null when the route names no machine. The
     * host's own name and circuit are left out on purpose: a match level that stays equal already proves each hatch
     * can run the pattern.
     */
    public static String machineKey(Evaluation evaluation) {
        MatchResult match = evaluation.match;
        if (match.machine == null) return null;
        return evaluation.target.kind()
            .name() + '' + match.machine + '' + match.level.name() + '' + match.programmingCover;
    }

    public static String key(Evaluation evaluation) {
        MatchResult match = evaluation.match;
        PatternUploadTarget target = evaluation.target;
        return target.kind()
            .name() + '\u0001'
            + nullToEmpty(target.name())
            + nullToEmpty(target.nameSuffix())
            + '\u0001'
            + nullToEmpty(match.routeName)
            + '\u0001'
            + match.level.name()
            + '\u0001'
            + match.routeSignature
            + '\u0001'
            + circuitNumber(match)
            + '\u0001'
            + match.programmingCover;
    }

    /** Virtual circuit number shown for a route, or -1. */
    public static int circuitNumber(MatchResult match) {
        return match.circuit == null ? -1 : match.circuit.getItemDamage() & 0xFF;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
