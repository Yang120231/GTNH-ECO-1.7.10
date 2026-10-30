package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;

/**
 * One player's upload interaction with one open pattern terminal window.
 *
 * <p>
 * The target screen is drawn client-side on top of the terminal, so the server-side container never changes while
 * the player picks a target. The session lives exactly as long as that terminal window stays open and is reused for
 * every request and action sent from it, which keeps the server-issued group tokens stable between lists.
 * </p>
 */
public final class PatternUploadSession {

    private static final long IDLE_MILLIS = 15L * 60L * 1000L;

    private static final String REASON_TARGET_MISSING = "gui.neoecoae.pattern_upload.reason.target_missing";
    private static final String REASON_NO_PERMISSION = "gui.neoecoae.pattern_upload.reason.no_permission";
    private static final String REASON_NO_PATTERN = "gui.neoecoae.pattern_upload.no_pattern";

    private final EntityPlayerMP player;
    private final PatternUploadSource source;
    private final Container sourceContainer;
    private final int sourceWindowId;
    private final Function<IGrid, List<PatternUploadTarget>> discovery;
    private final PatternTargetGroups groups = new PatternTargetGroups();
    private List<PatternTargetSelector.Evaluation> evaluations = Collections.emptyList();
    private boolean closed;
    private long touchedAt = System.currentTimeMillis();
    private String message = "";
    private boolean messageError;

    public PatternUploadSession(EntityPlayerMP player, PatternUploadSource source, Container sourceContainer,
        Function<IGrid, List<PatternUploadTarget>> discovery) {
        this.player = player;
        this.source = source;
        this.sourceContainer = sourceContainer;
        this.sourceWindowId = sourceContainer.windowId;
        this.discovery = discovery;
    }

    public EntityPlayerMP player() {
        return player;
    }

    public int sourceWindowId() {
        return sourceWindowId;
    }

    /** True when this session belongs to exactly {@code container}. */
    public boolean boundTo(Container container) {
        return container != null && container == sourceContainer && container.windowId == sourceWindowId;
    }

    public String message() {
        return message;
    }

    public boolean messageError() {
        return messageError;
    }

    public void close() {
        closed = true;
        groups.clear();
        evaluations = Collections.emptyList();
    }

    public boolean isOpen() {
        if (closed || player.playerNetServerHandler == null) return false;
        if (System.currentTimeMillis() - touchedAt > IDLE_MILLIS) return false;
        return player.openContainer == sourceContainer;
    }

    private void touch() {
        touchedAt = System.currentTimeMillis();
    }

    /** Any hint bound to the pattern currently in the terminal, including a category-only one. */
    public RouteHint hint() {
        return PatternUploadSessions.hintFor(player, sourceWindowId, currentPattern());
    }

    private ItemStack currentPattern() {
        return source.slots()
            .get(source.slot());
    }

    public PatternContext context() {
        ItemStack pattern = currentPattern();
        RouteHint hint = PatternUploadSessions.routeHintFor(player, sourceWindowId, pattern);
        return PatternContext.of(pattern, hint, player.worldObj);
    }

    /** Re-discovers targets, re-evaluates them against the live machines and regroups them. */
    public List<PatternTargetSelector.Evaluation> refresh() {
        touch();
        IGrid grid = source.grid();
        PatternContext context = context();
        if (grid == null || context == null) evaluations = Collections.emptyList();
        else evaluations = PatternTargetSelector.evaluateAll(discovery.apply(grid), context, grid);
        groups.update(evaluations);
        return evaluations;
    }

    public List<PatternTargetSelector.Evaluation> evaluations() {
        return evaluations;
    }

    /** Groups of the latest refresh, best first. */
    public List<PatternTargetGroups.Group> groups() {
        return groups.groups();
    }

    public PatternTargetGroups.Group group(int token) {
        return groups.group(token);
    }

    public PatternTargetGroups.Group groupOf(PatternUploadTarget target) {
        return groups.groupOf(target);
    }

    /**
     * Uploads into the group named by {@code token}. Members are tried most-empty first; each attempt re-verifies its
     * target, and a failure that belongs to that target alone (slot taken meanwhile, machine went offline) moves on
     * to the next member. A failure that would repeat for every member stops at once.
     */
    public GroupUpload uploadGroup(int token) {
        PatternTargetGroups.Group group = groups.group(token);
        if (group == null) return new GroupUpload(fail(REASON_TARGET_MISSING), null);
        List<PatternTargetSelector.Evaluation> order = group.uploadOrder();
        if (order.isEmpty()) {
            PatternTargetSelector.Evaluation best = group.best();
            String reason = best.state != PatternTargetSelector.SlotState.FREE ? best.state.translationKey()
                : best.match.level.translationKey();
            return new GroupUpload(fail(reason), null);
        }
        PatternTransfer.Result result = null;
        for (PatternTargetSelector.Evaluation member : order) {
            result = upload(member.target, false);
            if (result.succeeded()) return new GroupUpload(result, member.target);
            if (!targetSpecific(result)) break;
        }
        return new GroupUpload(result, null);
    }

    /** Whether another member of the same group could still succeed after {@code result}. */
    static boolean targetSpecific(PatternTransfer.Result result) {
        if (result.status != PatternTransfer.Status.REJECTED) return false;
        String reason = result.reason;
        return !REASON_NO_PERMISSION.equals(reason) && !REASON_NO_PATTERN.equals(reason)
            && !PatternTransfer.REASON_SOURCE_CHANGED.equals(reason);
    }

    public PatternTransfer.Result upload(PatternUploadTarget target, boolean automatic) {
        touch();
        if (!isOpen() || !source.canEdit(player)) return fail(REASON_NO_PERMISSION);
        PatternContext context = context();
        if (context == null) return fail(REASON_NO_PATTERN);
        IGrid grid = source.grid();
        // Re-read the destination and its machine route at submit time.
        PatternTargetSelector.Evaluation evaluation = PatternTargetSelector.evaluate(target, context, grid);
        if (automatic ? !evaluation.canUploadAutomatically() : !evaluation.canUploadManually()) {
            String reason = evaluation.state != PatternTargetSelector.SlotState.FREE ? evaluation.state.translationKey()
                : evaluation.match.level.translationKey();
            return fail(reason);
        }
        PatternSlotAccess.SlotRef physical = target.slots()
            .physical(evaluation.freeSlot);
        PatternTransfer.Result result = PatternTransfer
            .move(source.slots(), source.slot(), physical.access, physical.slot, context.pattern);
        if (result.succeeded()) {
            PatternUploadSessions
                .remember(player, new UploadReceipt(source.identity(), physical, context.pattern, target));
            message("gui.neoecoae.pattern_upload.success", false);
        } else message(result.reason, true);
        return result;
    }

    public PatternTransfer.Result undo() {
        touch();
        UploadReceipt receipt = PatternUploadSessions.receipt(player);
        if (receipt == null) return fail("gui.neoecoae.pattern_upload.reason.no_receipt");
        if (receipt.sourceIdentity != source.identity()) return fail("gui.neoecoae.pattern_upload.reason.other_source");
        if (!isOpen() || !source.canEdit(player)) return fail(REASON_NO_PERMISSION);
        PatternTransfer.Result result = PatternTransfer
            .move(receipt.target.access, receipt.target.slot, source.slots(), source.slot(), receipt.pattern);
        if (result.succeeded()) {
            PatternUploadSessions.forget(player, receipt);
            message("gui.neoecoae.pattern_upload.undo_success", false);
        } else message(result.reason, true);
        return result;
    }

    /** The receipt of the player's last upload when it can be undone into this terminal, else null. */
    public UploadReceipt receipt() {
        UploadReceipt receipt = PatternUploadSessions.receipt(player);
        return receipt != null && receipt.sourceIdentity == source.identity() ? receipt : null;
    }

    public boolean hasReceipt() {
        return receipt() != null;
    }

    private PatternTransfer.Result fail(String reason) {
        message(reason, true);
        return PatternTransfer.reject(reason);
    }

    public void message(String key, boolean error) {
        message = key == null ? "" : key;
        messageError = error;
    }

    /** Outcome of a group upload and the member that received the pattern, if any. */
    public static final class GroupUpload {

        public final PatternTransfer.Result result;
        public final PatternUploadTarget target;

        GroupUpload(PatternTransfer.Result result, PatternUploadTarget target) {
            this.result = result;
            this.target = target;
        }

        public boolean succeeded() {
            return result.succeeded();
        }
    }
}
