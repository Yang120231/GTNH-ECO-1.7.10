package cn.dancingsnow.neoecoae.platform.ae2.upload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import appeng.api.networking.IGrid;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternStacks;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetGroups;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTransfer;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadMappings;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSession;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.crafting.upload.RouteHint;
import cn.dancingsnow.neoecoae.crafting.upload.UploadReceipt;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server side of the terminal buttons. Runs only on the server tick, for the exact terminal window that asked. */
public final class PatternUploadService {

    private static final int HINT_REFRESH_TICKS = 20, HINT_FORCE_TICKS = 100;
    private static final Map<UUID, Integer> SEQUENCES = new HashMap<>();
    private static final Map<EntityPlayerMP, TooltipState> TOOLTIPS = new WeakHashMap<>();
    private static int ticks;

    private PatternUploadService() {}

    private static ContainerPatternTerm terminal(EntityPlayerMP player, int windowId) {
        return player.openContainer instanceof ContainerPatternTerm && player.openContainer.windowId == windowId
            ? (ContainerPatternTerm) player.openContainer
            : null;
    }

    /** Shared replay check for requests and actions: a repeated or late packet never moves a second pattern. */
    private static boolean acceptSequence(EntityPlayerMP player, int sequence) {
        Integer last = SEQUENCES.get(player.getUniqueID());
        if (last != null && sequence <= last && sequence > last - 4096) return false;
        SEQUENCES.put(player.getUniqueID(), sequence);
        return true;
    }

    /** The session of exactly this terminal window, reused while the window stays open so tokens stay stable. */
    private static PatternUploadSession session(EntityPlayerMP player, ContainerPatternTerm container,
        Ae2TerminalSource source) {
        PatternUploadSession session = PatternUploadSessions.active(player, container);
        if (session != null) return session;
        return PatternUploadSessions
            .open(new PatternUploadSession(player, source, container, Ae2UploadDiscovery::discover));
    }

    public static void request(EntityPlayerMP player, int windowId, int mode, int sequence) {
        if (!acceptSequence(player, sequence)) return;
        ContainerPatternTerm container = terminal(player, windowId);
        if (container == null) return;
        Ae2TerminalSource source = Ae2TerminalSource.of(container);
        if (source == null) return;
        if (!source.canEdit(player)) {
            notify(player, "gui.neoecoae.pattern_upload.reason.no_permission", true);
            return;
        }
        // The upload buttons also work directly from an unencoded terminal draft.
        if (mode == PatternUploadPackets.ENCODE_AND_OPEN || source.slots()
            .get(source.slot()) == null) {
            ItemStack before = copy(
                source.slots()
                    .get(source.slot()));
            try {
                ((PatternTermEncoder) container).neoecoae$encode();
            } catch (RuntimeException failure) {
                NeoECOAE.LOG.warn("Pattern terminal encode failed", failure);
                return;
            } finally {
                afterEncoded(player);
            }
            ItemStack after = source.slots()
                .get(source.slot());
            // A failed encode keeps the terminal state and never opens the list for an older pattern.
            if (mode == PatternUploadPackets.ENCODE_AND_OPEN
                && (after == null || PatternStacks.sameStack(before, after))) return;
        }
        PatternUploadSession session = session(player, container, source);
        session.message("", false);
        session.refresh();
        if (mode == PatternUploadPackets.AUTO) {
            if (session.context() == null) {
                notify(player, "gui.neoecoae.pattern_upload.no_pattern", true);
                return;
            }
            PatternTargetSelector.AutoChoice choice = session.autoChoice();
            if (choice.selected != null) {
                PatternUploadTarget target = choice.selected.target;
                PatternTransfer.Result result = session.upload(target, true);
                if (result.succeeded()) {
                    // The player stays in the terminal; chat is the only feedback.
                    uploaded(player, target, session);
                    return;
                }
                notify(player, result.reason, true);
            } else notify(player, choice.reason, true);
            return;
        }
        if (session.context() == null) session.message("gui.neoecoae.pattern_upload.no_pattern", true);
        PatternUploadPackets.targetList(player, list(session, container.windowId, PatternUploadPackets.EVENT_NONE));
    }

    /** One action from the client-side target screen that sits on top of the still open terminal window. */
    public static void action(EntityPlayerMP player, int windowId, int action, int argument, int sequence) {
        if (!acceptSequence(player, sequence)) return;
        ContainerPatternTerm container = terminal(player, windowId);
        if (container == null) return;
        Ae2TerminalSource source = Ae2TerminalSource.of(container);
        if (source == null) return;
        PatternUploadSession session = session(player, container, source);
        session.message("", false);
        int event = PatternUploadPackets.EVENT_NONE;
        if (!source.canEdit(player)) {
            session.message("gui.neoecoae.pattern_upload.reason.no_permission", true);
            notify(player, "gui.neoecoae.pattern_upload.reason.no_permission", true);
            PatternUploadPackets.targetList(player, list(session, windowId, event));
            return;
        }
        switch (action) {
            case PatternUploadPackets.ACTION_UPLOAD: {
                // Uses the groups of the list the player clicked; every member is re-verified at submit time.
                if (session.groups()
                    .isEmpty()) session.refresh();
                PatternUploadSession.GroupUpload upload = session.uploadGroup(argument);
                if (upload.succeeded()) {
                    uploaded(player, upload.target, session);
                    event = PatternUploadPackets.EVENT_UPLOADED;
                } else if (upload.result.status == PatternTransfer.Status.INDETERMINATE)
                    notify(player, upload.result.reason, true);
                session.refresh();
                break;
            }
            case PatternUploadPackets.ACTION_AUTO: {
                session.refresh();
                PatternTargetSelector.AutoChoice choice = session.autoChoice();
                if (session.context() == null) session.message("gui.neoecoae.pattern_upload.no_pattern", true);
                else if (choice.selected == null) session.message(choice.reason, true);
                else {
                    PatternUploadTarget target = choice.selected.target;
                    PatternTransfer.Result result = session.upload(target, true);
                    if (result.succeeded()) {
                        uploaded(player, target, session);
                        event = PatternUploadPackets.EVENT_UPLOADED;
                    } else
                        if (result.status == PatternTransfer.Status.INDETERMINATE) notify(player, result.reason, true);
                    session.refresh();
                }
                break;
            }
            case PatternUploadPackets.ACTION_UNDO: {
                if (session.undo()
                    .succeeded()) {
                    event = PatternUploadPackets.EVENT_UNDONE;
                    TOOLTIPS.remove(player);
                }
                session.refresh();
                break;
            }
            case PatternUploadPackets.ACTION_REFRESH:
                session.refresh();
                break;
            case PatternUploadPackets.ACTION_SAVE_MAPPING: {
                if (session.saveMapping(argument)) event = PatternUploadPackets.EVENT_MAPPING;
                TOOLTIPS.remove(player);
                session.refresh();
                break;
            }
            case PatternUploadPackets.ACTION_TOGGLE_MAPPING:
            case PatternUploadPackets.ACTION_REMOVE_MAPPING: {
                PatternUploadMappings.Mapping mapping = session.mapping();
                boolean changed = action == PatternUploadPackets.ACTION_TOGGLE_MAPPING
                    ? PatternUploadMappings.toggle(player, mapping)
                    : PatternUploadMappings.remove(player, mapping);
                session.message(
                    changed ? "gui.neoecoae.pattern_upload.mapping.updated"
                        : "gui.neoecoae.pattern_upload.mapping.no_category",
                    !changed);
                if (changed) event = PatternUploadPackets.EVENT_MAPPING;
                TOOLTIPS.remove(player);
                session.refresh();
                break;
            }
            case PatternUploadPackets.ACTION_LOCATE: {
                // Locating writes nothing; the last list stays valid, so no re-evaluation is needed.
                if (session.groups()
                    .isEmpty()) session.refresh();
                PatternTargetGroups.Group group = session.group(argument);
                if (group == null) session.message("gui.neoecoae.pattern_upload.reason.target_missing", true);
                else {
                    highlight(player, group.position().target);
                    session.message("gui.neoecoae.pattern_upload.located", false);
                }
                break;
            }
            default:
                return;
        }
        PatternUploadPackets.targetList(player, list(session, windowId, event));
    }

    private static void uploaded(EntityPlayerMP player, PatternUploadTarget target, PatternUploadSession session) {
        player.addChatMessage(
            new ChatComponentTranslation(
                "gui.neoecoae.pattern_upload.success_to",
                displayName(target),
                target.x(),
                target.y(),
                target.z()));
        if ("gui.neoecoae.pattern_upload.success_unverified".equals(session.message())) {
            ChatComponentTranslation warning = new ChatComponentTranslation(
                "gui.neoecoae.pattern_upload.warning.unverified");
            warning.getChatStyle()
                .setColor(EnumChatFormatting.YELLOW);
            player.addChatMessage(warning);
        }
        TOOLTIPS.remove(player);
    }

    /** Builds the complete grouped list from the session's latest refresh. */
    private static PatternUploadPackets.TargetList list(PatternUploadSession session, int windowId, int event) {
        PatternUploadPackets.TargetList list = new PatternUploadPackets.TargetList();
        list.windowId = windowId;
        PatternContext context = session.context();
        if (context != null) {
            list.pattern = context.primaryOutput();
            list.processing = context.processing;
            list.hinted = context.hint != null;
        }
        PatternUploadMappings.Scope scope = session.mappingScope();
        list.category = scope == null ? "" : scope.category;
        list.categoryId = scope == null ? "" : scope.categoryId;
        list.canMap = context != null && scope != null;
        if (context != null) for (var evaluation : session.evaluations())
            if (!evaluation.match.categoryId.isEmpty() && evaluation.match.allowsMapping()) list.canMap = true;
        PatternUploadMappings.Mapping mapping = session.mapping();
        if (mapping != null && !mapping.conflict) {
            list.hasMapping = true;
            list.mappingEnabled = mapping.enabled;
            list.mappingName = mapping.name;
            list.mappingNameSuffix = mapping.nameSuffix;
            for (PatternTargetGroups.Group group : session.groups()) {
                for (var member : group.members) if (mapping.matches(member)) list.mappingToken = group.token();
                if (list.mappingToken != 0) break;
            }
        }
        list.message = session.message();
        list.error = session.messageError();
        list.event = event;
        UploadReceipt receipt = session.receipt();
        list.receipt = receipt != null;
        list.receiptName = receipt == null || receipt.targetName == null ? "" : receipt.targetName;
        list.receiptNameSuffix = receipt == null || receipt.targetNameSuffix == null ? "" : receipt.targetNameSuffix;
        PatternTargetSelector.AutoChoice choice = session.autoChoice();
        list.autoMapped = choice.mapped;
        if (choice.selected != null) {
            PatternTargetGroups.Group group = session.groupOf(choice.selected.target);
            list.autoToken = group == null ? 0 : group.token();
        }
        list.autoReason = choice.reason == null ? "" : choice.reason;
        List<PatternUploadPackets.Row> rows = new ArrayList<>();
        for (PatternTargetGroups.Group group : session.groups()) rows.add(row(group));
        list.rows = rows;
        return list;
    }

    private static PatternUploadPackets.Row row(PatternTargetGroups.Group group) {
        PatternTargetSelector.Evaluation position = group.position();
        PatternUploadTarget target = position.target;
        MatchResult match = position.match;
        PatternUploadPackets.Row row = new PatternUploadPackets.Row();
        row.token = group.token();
        row.name = target.name() == null ? "" : target.name();
        row.nameSuffix = target.nameSuffix() == null ? "" : target.nameSuffix();
        row.route = match.routeName == null ? "" : match.routeName;
        row.kind = target.kind()
            .ordinal();
        row.level = match.level.ordinal();
        row.state = group.state.ordinal();
        row.reason = match.reason == null ? "" : match.reason;
        row.icon = match.routeIcon != null ? match.routeIcon : target.icon();
        row.members = group.members.size();
        row.freeMembers = group.freeMembers;
        row.duplicates = group.duplicates;
        row.emptySlots = group.emptySlots;
        row.totalSlots = group.totalSlots;
        row.circuit = PatternTargetGroups.circuitNumber(match);
        row.programmingCover = match.programmingCover;
        row.manual = group.manual;
        row.dimension = target.dimension();
        row.x = target.x();
        row.y = target.y();
        row.z = target.z();
        return row;
    }

    public static void hint(EntityPlayerMP player, int windowId, String recipeMapId, ItemStack circuit, String category,
        String categoryId, List<ItemStack> machineTypes) {
        ContainerPatternTerm container = terminal(player, windowId);
        if (container == null) return;
        bindEditor(container, player);
        PatternUploadSessions
            .setHint(player, new RouteHint(recipeMapId, circuit, category, categoryId, windowId, machineTypes));
    }

    private static void bindEditor(ContainerPatternTerm container, EntityPlayer player) {
        Ae2TerminalSource source = Ae2TerminalSource.of(container);
        if (source == null) return;
        var terminal = container.getPatternTerminal();
        appeng.api.parts.IPatternTerminal.PatternEncodeListener listener = (host,
            pattern) -> onEncoded(container, player, pattern);
        if (PatternUploadSessions
            .bindEditor(player, container, source.identity(), () -> terminal.removePatternEncodeListeners(listener)))
            terminal.addPatternEncodeListeners(listener);
    }

    public static void beforeEncoded(ContainerPatternTerm container, EntityPlayer player) {
        bindEditor(container, player);
        PatternUploadSessions.beginEncoding(player);
    }

    public static void afterEncoded(EntityPlayer player) {
        PatternUploadSessions.endEncoding(player);
    }

    public static void draftCleared(EntityPlayer player) {
        PatternUploadSessions.draftCleared(player);
    }

    private static void onEncoded(ContainerPatternTerm container, EntityPlayer player, ItemStack pattern) {
        if (player.openContainer != container || !PatternUploadSessions.isEncoding(player)) return;
        Ae2TerminalSource source = Ae2TerminalSource.of(container);
        if (source == null) return;
        PatternContext context = PatternContext.of(pattern, null, player.worldObj, source.grid());
        RouteHint draft = PatternUploadSessions.draft(player);
        if (context == null || !context.processing || context.details.isInputOnly())
            PatternUploadSessions.setHint(player, null);
        else if (draft != null && cn.dancingsnow.neoecoae.platform.gt.NativeGregTech.isAvailable())
            cn.dancingsnow.neoecoae.platform.gt.upload.GtPatternRoutes.updateSelection(draft, context.recipeInputs);
        PatternUploadSessions.onEncoded(player, container.windowId, pattern);
        if (player instanceof EntityPlayerMP) TOOLTIPS.remove(player);
    }

    /** Sends the target that auto-upload would pick, refreshing only when the pattern changed or periodically. */
    private static void refreshTooltip(EntityPlayerMP player, ContainerPatternTerm container) {
        Ae2TerminalSource source = Ae2TerminalSource.of(container);
        if (source == null) return;
        ItemStack pattern = source.slots()
            .get(source.slot());
        IGrid grid = source.grid();
        TooltipState state = TOOLTIPS.get(player);
        cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache tracking = grid == null ? null
            : grid.getCache(cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache.class);
        long revision = tracking == null ? -1L : tracking.revision();
        long now = player.worldObj.getTotalWorldTime();
        if (state != null && state.windowId == container.windowId
            && state.grid == grid
            && state.revision == revision
            && (state.pattern == null ? pattern == null : PatternStacks.sameItem(state.pattern, pattern))
            && now - state.at < HINT_FORCE_TICKS) return;
        PatternUploadPackets.AutoTarget message;
        PatternContext context = grid == null ? null
            : PatternContext
                .of(pattern, PatternUploadSessions.hintFor(player, container.windowId, pattern), player.worldObj, grid);
        if (context == null) message = new PatternUploadPackets.AutoTarget(container.windowId, "", -1, null, false, "");
        else {
            var evaluations = PatternTargetSelector.evaluateAll(Ae2UploadDiscovery.discover(grid), context, grid);
            PatternTargetSelector.AutoChoice choice = PatternTargetSelector
                .choose(evaluations, PatternUploadMappings.get(player, context, evaluations));
            if (choice.selected == null)
                message = new PatternUploadPackets.AutoTarget(container.windowId, "", -1, null, false, choice.reason);
            else {
                var match = choice.selected.match;
                var target = choice.selected.target;
                message = new PatternUploadPackets.AutoTarget(
                    container.windowId,
                    match.routeName != null ? match.routeName : target.name(),
                    target.kind()
                        .ordinal(),
                    match.circuit,
                    match.programmingCover,
                    choice.reason);
                message.nameSuffix = (match.routeName == null && target.nameSuffix() != null ? target.nameSuffix()
                    : "") + " (" + target.dimension() + ": " + target.x() + ", " + target.y() + ", " + target.z() + ")";
                message.icon = copy(match.routeIcon != null ? match.routeIcon : target.icon());
                message.mapped = choice.mapped;
            }
        }
        String key = message.key();
        if (state == null || !key.equals(state.key) || state.windowId != container.windowId)
            PatternUploadPackets.autoTarget(player, message);
        TOOLTIPS.put(player, new TooltipState(container.windowId, grid, copy(pattern), now, revision, key));
    }

    public static void highlight(EntityPlayerMP player, PatternUploadTarget target) {
        PatternUploadPackets.highlight(player, target.dimension(), target.x(), target.y(), target.z());
    }

    private static void notify(EntityPlayerMP player, String key, boolean error) {
        ChatComponentTranslation message = new ChatComponentTranslation(key);
        if (error) message.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        player.addChatMessage(message);
    }

    /** Host name for chat: a key translated on the client (block/item keys need ".name"), then the literal suffix. */
    private static IChatComponent displayName(PatternUploadTarget target) {
        String name = target.name();
        IChatComponent component;
        if (name == null) component = new ChatComponentText("");
        else if (name.indexOf('.') <= 0 || name.indexOf(' ') >= 0) component = new ChatComponentText(name);
        else if (!StatCollector.canTranslate(name) && StatCollector.canTranslate(name + ".name"))
            component = new ChatComponentTranslation(name + ".name");
        else if (!StatCollector.canTranslate(name) && target.icon() != null
            && name.equals(
                target.icon()
                    .getUnlocalizedName()))
            // Untranslatable raw block name (the mod's ItemBlock names itself): use the host item's own name.
            component = new ChatComponentText(
                target.icon()
                    .getDisplayName());
        else component = new ChatComponentTranslation(name);
        String suffix = target.nameSuffix();
        if (suffix != null && !suffix.isEmpty()) component.appendText(suffix);
        return component;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    public static void stopped() {
        PatternUploadSessions.clear();
        SEQUENCES.clear();
        TOOLTIPS.clear();
    }

    public static final class Events {

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ++ticks % HINT_REFRESH_TICKS != 0) return;
            PatternUploadSessions.sweep();
            for (Object entry : net.minecraft.server.MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                if (!(entry instanceof EntityPlayerMP)) continue;
                EntityPlayerMP player = (EntityPlayerMP) entry;
                if (!(player.openContainer instanceof ContainerPatternTerm)) {
                    TOOLTIPS.remove(player);
                    continue;
                }
                try {
                    refreshTooltip(player, (ContainerPatternTerm) player.openContainer);
                } catch (RuntimeException failure) {
                    NeoECOAE.LOG.debug("Pattern upload hint refresh failed", failure);
                }
            }
        }

        @SubscribeEvent
        public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
            PatternUploadSessions.logout(event.player);
            SEQUENCES.remove(event.player.getUniqueID());
            if (event.player instanceof EntityPlayerMP) TOOLTIPS.remove(event.player);
        }
    }

    private static final class TooltipState {

        final int windowId;
        final IGrid grid;
        final ItemStack pattern;
        final long at;
        final long revision;
        final String key;

        TooltipState(int windowId, IGrid grid, ItemStack pattern, long at, long revision, String key) {
            this.windowId = windowId;
            this.grid = grid;
            this.pattern = pattern;
            this.at = at;
            this.revision = revision;
            this.key = key;
        }
    }
}
