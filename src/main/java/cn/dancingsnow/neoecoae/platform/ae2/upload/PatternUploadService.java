package cn.dancingsnow.neoecoae.platform.ae2.upload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import appeng.api.networking.IGrid;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternStacks;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTransfer;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSession;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.crafting.upload.RouteHint;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadUiFactory;
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

    public static void request(EntityPlayerMP player, int windowId, int mode, int sequence) {
        Integer last = SEQUENCES.get(player.getUniqueID());
        if (last != null && sequence <= last && sequence > last - 4096) return;
        SEQUENCES.put(player.getUniqueID(), sequence);
        ContainerPatternTerm container = terminal(player, windowId);
        if (container == null) return;
        Ae2TerminalSource source = Ae2TerminalSource.of(container);
        if (source == null) return;
        if (mode == PatternUploadPackets.ENCODE_AND_OPEN) {
            ItemStack before = copy(
                source.slots()
                    .get(source.slot()));
            try {
                ((PatternTermEncoder) container).neoecoae$encode();
            } catch (RuntimeException failure) {
                NeoECOAE.LOG.warn("Pattern terminal encode failed", failure);
                return;
            }
            ItemStack after = source.slots()
                .get(source.slot());
            // A failed encode keeps AE2's own state and never opens the page for an older pattern.
            if (after == null || PatternStacks.sameStack(before, after)) return;
        }
        if (!source.canEdit(player)) {
            notify(player, "gui.neoecoae.pattern_upload.reason.no_permission", true);
            return;
        }
        PatternUploadSession session = PatternUploadSessions
            .open(new PatternUploadSession(player, source, container, Ae2UploadDiscovery::discover));
        List<PatternTargetSelector.Evaluation> evaluations = session.refresh();
        if (session.context() == null) session.message("gui.neoecoae.pattern_upload.no_pattern", true);
        else if (mode == PatternUploadPackets.AUTO) {
            PatternTargetSelector.AutoChoice choice = PatternTargetSelector.choose(evaluations);
            if (choice.selected != null) {
                PatternUploadTarget target = choice.selected.target;
                PatternTransfer.Result result = session.upload(target, true);
                if (result.succeeded()) {
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            "gui.neoecoae.pattern_upload.success_to",
                            translatable(target.name()),
                            target.x(),
                            target.y(),
                            target.z()));
                    TOOLTIPS.remove(player);
                    return;
                }
                if (result.status == PatternTransfer.Status.INDETERMINATE) {
                    notify(player, result.reason, true);
                    return;
                }
            } else session.message(choice.reason, true);
        }
        PatternUploadUiFactory.open(player);
    }

    public static void hint(EntityPlayerMP player, int windowId, String recipeMapId, ItemStack circuit) {
        if (terminal(player, windowId) == null) return;
        PatternUploadSessions.setHint(player, new RouteHint(recipeMapId, circuit, windowId));
    }

    public static void onEncoded(ContainerPatternTerm container, EntityPlayer player) {
        Ae2TerminalSource source = Ae2TerminalSource.of(container);
        if (source == null) return;
        PatternUploadSessions.onEncoded(
            player,
            container.windowId,
            source.slots()
                .get(source.slot()));
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
        long now = player.worldObj.getTotalWorldTime();
        if (state != null && state.windowId == container.windowId
            && state.grid == grid
            && (state.pattern == null ? pattern == null : PatternStacks.sameItem(state.pattern, pattern))
            && now - state.at < HINT_FORCE_TICKS) return;
        PatternUploadPackets.AutoTarget message;
        PatternContext context = grid == null ? null
            : PatternContext
                .of(pattern, PatternUploadSessions.hintFor(player, container.windowId, pattern), player.worldObj);
        if (context == null) message = new PatternUploadPackets.AutoTarget(container.windowId, "", -1, null, false, "");
        else {
            PatternTargetSelector.AutoChoice choice = PatternTargetSelector
                .choose(PatternTargetSelector.evaluateAll(Ae2UploadDiscovery.discover(grid), context, grid));
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
                    "");
            }
        }
        String key = message.key();
        if (state == null || !key.equals(state.key) || state.windowId != container.windowId)
            PatternUploadPackets.autoTarget(player, message);
        TOOLTIPS.put(player, new TooltipState(container.windowId, grid, copy(pattern), now, key));
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

    private static Object translatable(String name) {
        return name != null && name.indexOf('.') > 0 && name.indexOf(' ') < 0 ? new ChatComponentTranslation(name)
            : name;
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
        final String key;

        TooltipState(int windowId, IGrid grid, ItemStack pattern, long at, String key) {
            this.windowId = windowId;
            this.grid = grid;
            this.pattern = pattern;
            this.at = at;
            this.key = key;
        }
    }
}
