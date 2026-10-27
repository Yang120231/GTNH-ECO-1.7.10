package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;

/**
 * One player's upload interaction, from the terminal window through the upload page.
 *
 * <p>
 * The MUI2 page replaces (and closes) the AE2 terminal container, so the session moves through an explicit
 * {@code TERMINAL -> UPLOAD_PAGE} transition bound to the new window instead of treating the close as a cancel.
 * Clients only ever hold server-issued target tokens.
 * </p>
 */
public final class PatternUploadSession {

    public enum State {
        TERMINAL,
        UPLOAD_PAGE,
        CLOSED
    }

    private static final long IDLE_MILLIS = 15L * 60L * 1000L;

    private final EntityPlayerMP player;
    private final PatternUploadSource source;
    private final Container sourceContainer;
    private final int sourceWindowId;
    private final Function<IGrid, List<PatternUploadTarget>> discovery;
    private final Map<Integer, PatternUploadTarget> tokens = new HashMap<>();
    private final IdentityHashMap<PatternUploadTarget, Integer> tokenOf = new IdentityHashMap<>();
    private List<PatternTargetSelector.Evaluation> evaluations = Collections.emptyList();
    private Container uploadContainer;
    private State state = State.TERMINAL;
    private int serial, lastSequence = Integer.MIN_VALUE;
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

    public State state() {
        return state;
    }

    public int sourceWindowId() {
        return sourceWindowId;
    }

    public String message() {
        return message;
    }

    public boolean messageError() {
        return messageError;
    }

    public void bindUploadPage(Container container) {
        if (state == State.CLOSED) return;
        uploadContainer = container;
        state = State.UPLOAD_PAGE;
        touch();
    }

    public void close() {
        state = State.CLOSED;
        tokens.clear();
        tokenOf.clear();
        evaluations = Collections.emptyList();
        uploadContainer = null;
    }

    public boolean isOpen() {
        if (state == State.CLOSED || player.playerNetServerHandler == null) return false;
        if (System.currentTimeMillis() - touchedAt > IDLE_MILLIS) return false;
        Container open = player.openContainer;
        return state == State.TERMINAL ? open == sourceContainer : open == uploadContainer;
    }

    /** Request ordering: a repeated or late request never moves a second pattern. */
    public boolean acceptSequence(int sequence) {
        if (sequence <= lastSequence) return false;
        lastSequence = sequence;
        return true;
    }

    private void touch() {
        touchedAt = System.currentTimeMillis();
    }

    public PatternContext context() {
        ItemStack pattern = source.slots()
            .get(source.slot());
        RouteHint hint = PatternUploadSessions.hintFor(player, sourceWindowId, pattern);
        return PatternContext.of(pattern, hint, player.worldObj);
    }

    /** Re-discovers targets and re-evaluates them against the live machines. */
    public List<PatternTargetSelector.Evaluation> refresh() {
        touch();
        IGrid grid = source.grid();
        PatternContext context = context();
        if (grid == null || context == null) {
            evaluations = Collections.emptyList();
            return evaluations;
        }
        Map<String, Integer> previous = new HashMap<>();
        tokenOf.forEach((target, token) -> previous.put(target.positionKey(), token));
        tokens.clear();
        tokenOf.clear();
        List<PatternUploadTarget> targets = discovery.apply(grid);
        for (PatternUploadTarget target : targets) {
            // Keep the token of an unchanged position so an in-flight click still names the same host.
            Integer token = previous.get(target.positionKey());
            if (token == null) token = ++serial;
            tokens.put(token, target);
            tokenOf.put(target, token);
        }
        evaluations = PatternTargetSelector.evaluateAll(targets, context, grid);
        return evaluations;
    }

    public List<PatternTargetSelector.Evaluation> evaluations() {
        return evaluations;
    }

    public int token(PatternUploadTarget target) {
        Integer token = tokenOf.get(target);
        return token == null ? 0 : token;
    }

    public PatternUploadTarget target(int token) {
        return tokens.get(token);
    }

    public PatternTransfer.Result upload(int token, boolean automatic) {
        PatternUploadTarget target = tokens.get(token);
        if (target == null) return fail("gui.neoecoae.pattern_upload.reason.target_missing");
        return upload(target, automatic);
    }

    public PatternTransfer.Result upload(PatternUploadTarget target, boolean automatic) {
        touch();
        if (!isOpen() || !source.canEdit(player)) return fail("gui.neoecoae.pattern_upload.reason.no_permission");
        PatternContext context = context();
        if (context == null) return fail("gui.neoecoae.pattern_upload.no_pattern");
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
        if (!isOpen() || !source.canEdit(player)) return fail("gui.neoecoae.pattern_upload.reason.no_permission");
        PatternTransfer.Result result = PatternTransfer
            .move(receipt.target.access, receipt.target.slot, source.slots(), source.slot(), receipt.pattern);
        if (result.succeeded()) {
            PatternUploadSessions.forget(player, receipt);
            message("gui.neoecoae.pattern_upload.undo_success", false);
        } else message(result.reason, true);
        return result;
    }

    public boolean hasReceipt() {
        UploadReceipt receipt = PatternUploadSessions.receipt(player);
        return receipt != null && receipt.sourceIdentity == source.identity();
    }

    public List<PatternTargetSelector.Evaluation> page(int page, int size) {
        int from = Math.max(0, page * size);
        if (from >= evaluations.size()) return Collections.emptyList();
        return new ArrayList<>(evaluations.subList(from, Math.min(evaluations.size(), from + size)));
    }

    private PatternTransfer.Result fail(String reason) {
        message(reason, true);
        return PatternTransfer.reject(reason);
    }

    public void message(String key, boolean error) {
        message = key == null ? "" : key;
        messageError = error;
    }
}
