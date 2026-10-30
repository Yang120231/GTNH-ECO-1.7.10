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
 * and programming cover are interchangeable for the player (typically parallel machines), so they share one row and
 * one server-issued token; the server picks the member to write at submit time.
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

    /** Pure grouping, sorted best group first. */
    public static List<Group> group(List<Evaluation> evaluations) {
        Map<String, List<Evaluation>> byKey = new LinkedHashMap<>();
        for (Evaluation evaluation : evaluations) byKey.computeIfAbsent(key(evaluation), ignored -> new ArrayList<>())
            .add(evaluation);
        List<Group> result = new ArrayList<>(byKey.size());
        for (Map.Entry<String, List<Evaluation>> entry : byKey.entrySet()) {
            List<Evaluation> members = entry.getValue();
            members.sort(PatternTargetSelector.ORDER);
            result.add(new Group(entry.getKey(), members));
        }
        result.sort(GROUP_ORDER);
        return result;
    }

    public static String key(Evaluation evaluation) {
        MatchResult match = evaluation.match;
        PatternUploadTarget target = evaluation.target;
        return target.kind()
            .name() + '\u0001'
            + nullToEmpty(target.name())
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
