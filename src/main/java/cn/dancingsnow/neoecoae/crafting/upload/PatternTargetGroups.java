package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.Evaluation;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.SlotState;

/**
 * One row per concrete upload inventory. Only aliases of the same inventory and route share a row and token;
 * independent parallel machines remain separate binding choices.
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
                empty = Math.max(empty, member.emptySlots);
                total = Math.max(total, member.totalSlots);
                manual |= member.canUploadManually();
            }
            this.freeMembers = free;
            this.duplicates = duplicate;
            this.emptySlots = empty;
            this.totalSlots = total;
            this.manual = manual;
            // Members are alternate views of the same inventory.
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

    static final Comparator<Evaluation> UPLOAD_ORDER = Comparator
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
        Map<Object, Map<String, String>> aliases = new IdentityHashMap<>();
        for (Evaluation evaluation : evaluations) {
            String key = key(evaluation);
            Object inventory = evaluation.storageIdentity;
            if (inventory != null) {
                String route = evaluation.target.kind()
                    .name() + '\u0001'
                    + evaluation.match.routeSignature
                    + '\u0001'
                    + evaluation.match.level.name()
                    + '\u0001'
                    + evaluation.match.categoryId
                    + '\u0001'
                    + evaluation.match.selectionKey
                    + '\u0001'
                    + evaluation.match.programmingCover;
                Map<String, String> views = aliases.computeIfAbsent(inventory, ignored -> new HashMap<>());
                String previous = views.putIfAbsent(route, key);
                if (previous != null) key = previous;
            }
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(evaluation);
        }
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
        return mappingKey(evaluation) + '\u0001' + evaluation.match.level.name();
    }

    /** Concrete row identity, independent of names, free slots and match verdicts. */
    public static String mappingKey(Evaluation evaluation) {
        StringBuilder key = new StringBuilder("2:");
        append(key, evaluation.positionKey);
        append(
            key,
            evaluation.target.kind()
                .name());
        append(key, evaluation.match.routeSignature);
        return key.append(':')
            .append(evaluation.match.programmingCover)
            .toString();
    }

    /** Old group keys are read only to prove whether they still name exactly one concrete inventory. */
    public static String legacyMappingKey(Evaluation evaluation) {
        MatchResult match = evaluation.match;
        PatternUploadTarget target = evaluation.target;
        StringBuilder key = new StringBuilder("1:");
        append(
            key,
            target.kind()
                .name());
        append(key, target.name());
        append(key, target.nameSuffix());
        append(key, match.routeName);
        String signature = match.routeSignature;
        int role = signature.indexOf('#', signature.indexOf('#') + 1);
        append(key, role < 0 ? signature : signature.substring(0, role));
        return key.append(circuitNumber(match))
            .append(':')
            .append(match.programmingCover)
            .toString();
    }

    /** Selector encoded at the end of the old five-field length-prefixed group key. */
    public static String legacySelection(String key) {
        if (!key.startsWith("1:")) return null;
        try {
            int cursor = 2;
            for (int field = 0; field < 5; field++) {
                int colon = key.indexOf(':', cursor);
                if (colon < 0) return null;
                int length = Integer.parseInt(key.substring(cursor, colon));
                if (length < 0 || length > key.length() - colon - 1) return null;
                cursor = colon + 1 + length;
            }
            int end = key.indexOf(':', cursor);
            if (end < 0) return null;
            int number = Integer.parseInt(key.substring(cursor, end));
            return number < 0 ? "-" : Integer.toString(number);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Virtual circuit number shown for a route, or -1. */
    public static int circuitNumber(MatchResult match) {
        return match.circuit == null ? -1 : match.circuit.getItemDamage() & 0xFF;
    }

    private static void append(StringBuilder key, String value) {
        String text = value == null ? "" : value;
        key.append(text.length())
            .append(':')
            .append(text);
    }
}
