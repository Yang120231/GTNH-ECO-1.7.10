package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector.Evaluation;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;

/** Player route preferences. Pattern items carry no provenance tags or binding tags. */
public final class PatternUploadMappings {

    private static final String TAG = "neoecoaePatternUploadMappings";
    private static final int MAX_MAPPINGS = 512;

    private PatternUploadMappings() {}

    public static final class Scope {

        public final String categoryId, selectionKey, category;

        private Scope(String categoryId, String selectionKey, String category) {
            this.categoryId = categoryId;
            this.selectionKey = selectionKey == null || selectionKey.isEmpty() ? "?" : selectionKey;
            this.category = category == null ? "" : category;
        }

        String key() {
            return "2:" + categoryId.length() + ":" + categoryId + ":" + selectionKey;
        }
    }

    public static final class Mapping {

        public final Scope scope;
        public final String positionKey, signature, kind, name, nameSuffix;
        public final boolean enabled, legacy, conflict;
        private final String legacyGroup;
        private final boolean programming;

        private Mapping(Scope scope, NBTTagCompound tag) {
            this.scope = scope;
            positionKey = tag.getString("position");
            signature = tag.getString("signature");
            kind = tag.getString("kind");
            name = tag.getString("name");
            nameSuffix = tag.getString("suffix");
            enabled = tag.getBoolean("enabled");
            programming = tag.getBoolean("programming");
            legacyGroup = tag.getString("group");
            legacy = positionKey.isEmpty() && !legacyGroup.isEmpty();
            conflict = false;
        }

        private Mapping() {
            scope = null;
            positionKey = signature = kind = name = nameSuffix = legacyGroup = "";
            enabled = conflict = true;
            legacy = programming = false;
        }

        public boolean owns(Evaluation evaluation) {
            return legacy ? legacyGroup.equals(PatternTargetGroups.legacyMappingKey(evaluation))
                : positionKey.equals(evaluation.positionKey);
        }

        public boolean matches(Evaluation evaluation) {
            if (conflict || !owns(evaluation)) return false;
            if (legacy) return true;
            return kind.equals(
                evaluation.target.kind()
                    .name())
                && signature.equals(evaluation.match.routeSignature)
                && programming == evaluation.match.programmingCover
                && scope.categoryId.equals(evaluation.match.categoryId);
        }
    }

    /** A bound NEI category takes precedence; source-free patterns require a unique inferred category. */
    public static Scope scope(PatternContext context, List<Evaluation> evaluations) {
        Scope source = sourceScope(context);
        if (source != null) return source;
        Map<String, Scope> inferred = inferredScopes(evaluations);
        return inferred.size() == 1 ? inferred.values()
            .iterator()
            .next() : null;
    }

    /** The player may declare an inferred category/unknown selector by binding one concrete receiver. */
    public static Scope scope(PatternContext context, Evaluation evaluation) {
        Scope source = sourceScope(context);
        if (source != null) return source;
        MatchResult match = evaluation.match;
        return match.categoryId.isEmpty() || !match.allowsMapping() ? null
            : new Scope(match.categoryId, match.selectionKey, category(match));
    }

    private static Scope sourceScope(PatternContext context) {
        if (context == null) return null;
        if (!context.processing) return new Scope("ae2:crafting", "-", "gui.neoecoae.pattern_upload.crafting");
        if (context.hint == null || context.hint.getCategoryId()
            .isEmpty()) return null;
        String selection = NativeGregTech.isAvailable()
            ? cn.dancingsnow.neoecoae.platform.gt.upload.GtPatternRoutes.selectionKey(context)
            : context.hint.selectionKey();
        return new Scope(context.hint.getCategoryId(), selection, context.hint.getCategory());
    }

    private static Map<String, Scope> inferredScopes(List<Evaluation> evaluations) {
        Map<String, Scope> scopes = new LinkedHashMap<>();
        for (Evaluation evaluation : evaluations) {
            MatchResult match = evaluation.match;
            if (match.categoryId.isEmpty()
                || !match.allowsMapping() && (match.level != MatchResult.Level.CIRCUIT_CONFLICT
                    || "gui.neoecoae.pattern_upload.reason.circuits_multiple".equals(match.reason)))
                continue;
            Scope scope = new Scope(match.categoryId, match.selectionKey, category(match));
            scopes.put(scope.key(), scope);
        }
        return scopes;
    }

    private static String category(MatchResult match) {
        return match.categoryId.startsWith("gt:") ? match.categoryId.substring(3) : match.routeName;
    }

    public static Mapping get(EntityPlayer player, Scope scope) {
        if (scope == null || scope.categoryId.isEmpty()) return null;
        NBTTagCompound data = data(player, false);
        NBTTagCompound tag = data.getCompoundTag(scope.key());
        if (data.hasKey(scope.key(), 10)) return new Mapping(scope, tag);
        // An old category-only preference is never silently converted into a choice between parallel machines.
        tag = data.getCompoundTag(scope.categoryId);
        String group = tag.getString("group");
        if (group.isEmpty()) return null;
        String selection = PatternTargetGroups.legacySelection(group);
        if (selection != null && !"?".equals(scope.selectionKey) && !scope.selectionKey.equals(selection)) return null;
        return new Mapping(scope, tag);
    }

    public static Mapping get(EntityPlayer player, PatternContext context, List<Evaluation> evaluations) {
        Scope source = sourceScope(context);
        if (source != null) return get(player, source);
        List<Mapping> enabled = new ArrayList<>(), disabled = new ArrayList<>();
        for (Scope scope : inferredScopes(evaluations).values()) {
            Mapping mapping = get(player, scope);
            // A fully inferred selector can reuse its existing NEI binding. Never borrow a receiver's circuit to
            // resolve an ambiguous pattern; only ROUTED evaluations supply this independent proof.
            if (mapping == null && "?".equals(scope.selectionKey)) {
                String proven = null;
                boolean ambiguous = false;
                for (Evaluation evaluation : evaluations)
                    if (scope.categoryId.equals(evaluation.match.categoryId) && evaluation.match.allowsAuto()) {
                        String selection = evaluation.match.circuit == null ? "-"
                            : Integer.toString(evaluation.match.circuit.getItemDamage() & 0xFF);
                        if (proven != null && !proven.equals(selection)) ambiguous = true;
                        proven = selection;
                    }
                if (!ambiguous && proven != null)
                    mapping = get(player, new Scope(scope.categoryId, proven, scope.category));
            }
            if (mapping == null) continue;
            (mapping.enabled ? enabled : disabled).add(mapping);
        }
        if (enabled.size() > 1) return new Mapping();
        if (enabled.size() == 1) return enabled.get(0);
        return disabled.size() == 1 ? disabled.get(0) : null;
    }

    /** Call only after re-reading a receiver selected by its server-issued list token. */
    public static boolean put(EntityPlayer player, Scope scope, Evaluation evaluation) {
        if (scope == null || scope.categoryId.isEmpty()
            || !evaluation.match.allowsMapping()
            || !scope.categoryId.equals(evaluation.match.categoryId)
            || evaluation.state == PatternTargetSelector.SlotState.OFFLINE) return false;
        String position = evaluation.positionKey;
        String signature = evaluation.match.routeSignature;
        NBTTagCompound current = data(player, false);
        if (position.length() > 1024 || signature.length() > 8192
            || scope.key()
                .length() > 1024
            || !current.hasKey(scope.key(), 10) && current.func_150296_c()
                .size() >= MAX_MAPPINGS)
            return false;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("category", scope.categoryId);
        tag.setString("selection", scope.selectionKey);
        tag.setString("position", position);
        tag.setString(
            "kind",
            evaluation.target.kind()
                .name());
        tag.setString("signature", signature);
        tag.setBoolean("programming", evaluation.match.programmingCover);
        tag.setString("name", display(evaluation.target.name()));
        tag.setString(
            "suffix",
            display(evaluation.target.nameSuffix()) + " ("
                + evaluation.target.dimension()
                + ": "
                + evaluation.target.x()
                + ", "
                + evaluation.target.y()
                + ", "
                + evaluation.target.z()
                + ")");
        tag.setBoolean("enabled", true);
        data(player, true).setTag(scope.key(), tag);
        return true;
    }

    public static boolean toggle(EntityPlayer player, Mapping mapping) {
        if (mapping == null || mapping.conflict) return false;
        String key = mapping.legacy ? mapping.scope.categoryId : mapping.scope.key();
        NBTTagCompound tag = data(player, false).getCompoundTag(key);
        tag.setBoolean("enabled", !tag.getBoolean("enabled"));
        return true;
    }

    public static boolean remove(EntityPlayer player, Mapping mapping) {
        if (mapping == null || mapping.conflict) return false;
        data(player, false).removeTag(mapping.legacy ? mapping.scope.categoryId : mapping.scope.key());
        return true;
    }

    private static NBTTagCompound data(EntityPlayer player, boolean create) {
        NBTTagCompound entity = player.getEntityData();
        NBTTagCompound persisted = entity.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound mappings = persisted.getCompoundTag(TAG);
        if (create) {
            persisted.setTag(TAG, mappings);
            entity.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        return mappings;
    }

    private static String display(String value) {
        if (value == null) return "";
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
