package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;

import org.jetbrains.annotations.Nullable;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.container.implementations.ContainerCraftConfirm;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmReport;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmState;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client half of the ECO crafting report: holds the state the server mirrored for the open confirmation window and
 * replaces AE2's confirmation page with the ECO report once the server reports that ECO planned the request.
 */
@SideOnly(Side.CLIENT)
public final class ECOCraftConfirmClient {

    /** An entry never seen with an open confirmation window is dropped after this many ticks. */
    private static final int UNBOUND_TICKS = 200;

    private static volatile Entry latest;

    private static final class Entry {

        final int windowId;
        final ECOCraftConfirmState state;
        final ECOCraftConfirmReport report;
        /** The confirmation container this entry was matched with; a different container invalidates it. */
        Container bound;
        int unboundTicks;

        Entry(int windowId, ECOCraftConfirmState state, ECOCraftConfirmReport report, Container bound) {
            this.windowId = windowId;
            this.state = state;
            this.report = report;
            this.bound = bound;
        }
    }

    /** Network thread: store the newest state; a header-only update keeps the report already held. */
    public static void accept(int windowId, ECOCraftConfirmState state, @Nullable ECOCraftConfirmReport report) {
        Entry previous = latest;
        boolean sameWindow = previous != null && previous.windowId == windowId;
        ECOCraftConfirmReport kept = report != null ? report
            : sameWindow ? previous.report : ECOCraftConfirmReport.EMPTY;
        latest = new Entry(windowId, state, kept, sameWindow ? previous.bound : null);
    }

    public static ECOCraftConfirmState state(Container container) {
        Entry entry = entryFor(container);
        return entry == null ? ECOCraftConfirmState.NONE : entry.state;
    }

    /** The report matching the current header, or {@code null} while it is still on its way. */
    public static @Nullable ECOCraftConfirmReport report(Container container) {
        Entry entry = entryFor(container);
        if (entry == null || !entry.state.reportReady || entry.report.version != entry.state.reportVersion) return null;
        return entry.report;
    }

    private static @Nullable Entry entryFor(Container container) {
        Entry entry = latest;
        if (entry == null || container == null || entry.windowId != container.windowId) return null;
        return entry.bound == null || entry.bound == container ? entry : null;
    }

    public static void clear() {
        latest = null;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entry entry = latest;
        if (entry == null) return;
        if (mc.thePlayer == null) {
            latest = null;
            return;
        }
        Container open = mc.thePlayer.openContainer;
        if (entry.bound == null) {
            if (open instanceof ContainerCraftConfirm && open.windowId == entry.windowId) entry.bound = open;
            else if (++entry.unboundTicks > UNBOUND_TICKS) {
                latest = null;
                return;
            }
        } else if (open != entry.bound) {
            // The confirmation closed; window ids are reused, so the state must not outlive its container.
            latest = null;
            return;
        }
        route(mc, entry);
    }

    private static void route(Minecraft mc, Entry entry) {
        GuiScreen screen = mc.currentScreen;
        // Only AE2's own page is replaced; the ECO report and every other screen keep their lifecycle.
        if (!(screen instanceof GuiCraftConfirm) || !entry.state.plannerAvailable || entry.bound == null) return;
        Container container = ((GuiCraftConfirm) screen).inventorySlots;
        if (container != entry.bound || !(container instanceof ContainerCraftConfirm confirm)) return;
        mc.displayGuiScreen(new ECOCraftConfirmGui(confirm));
    }
}
