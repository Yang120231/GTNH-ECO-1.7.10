package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.inventory.Container;
import net.minecraftforge.client.event.GuiOpenEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiCraftingStatus;
import appeng.container.implementations.ContainerCraftingStatus;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client half of the ECO crafting status page. AE2's own status screen is swapped for {@link ECOCraftingStatusGui}
 * when it opens; that subclass stays visually native until the server reports that the network has an ECO computation
 * system with the fast planner on, and holds AE2's container, CPU list and entry sync either way.
 */
@SideOnly(Side.CLIENT)
public final class ECOCraftingStatusClient {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");

    /** What the server reported for one status window. */
    public static final class State {

        public static final State NONE = new State(-1, false, 0, ECOCycleItemList.EMPTY);

        final int windowId;
        public final boolean eco;
        public final int cpuTier;
        public final ECOCycleItemList cycles;

        State(int windowId, boolean eco, int cpuTier, ECOCycleItemList cycles) {
            this.windowId = windowId;
            this.eco = eco;
            this.cpuTier = cpuTier;
            this.cycles = cycles;
        }
    }

    private static volatile State latest = State.NONE;

    /** The newest live machine status of one status window, as the server sent it. */
    static final class Processing {

        static final Processing NONE = new Processing(-1, new CompoundTag());

        final int windowId;
        final CompoundTag tag;

        Processing(int windowId, CompoundTag tag) {
            this.windowId = windowId;
            this.tag = tag;
        }
    }

    private static volatile Processing processing = Processing.NONE;

    /** Network thread: the payload is replaced whole; the screen parses it when it changes. */
    public static void acceptProcessing(int windowId, CompoundTag tag) {
        processing = new Processing(windowId, tag);
    }

    static Processing processing(Container container) {
        Processing value = processing;
        return container != null && value.windowId == container.windowId ? value : Processing.NONE;
    }

    private static volatile Processing itemAction = Processing.NONE;

    /** Network thread: the newest answer to the status cell menu; the open screen takes it once. */
    public static void acceptItemAction(int windowId, CompoundTag result) {
        itemAction = new Processing(windowId, result);
    }

    /** The pending menu answer for this window, consumed by the call. */
    @Nullable
    static CompoundTag takeItemAction(Container container) {
        Processing value = itemAction;
        if (container == null || value.windowId != container.windowId) return null;
        itemAction = Processing.NONE;
        return value.tag;
    }

    /** Network thread: store the newest state; an update without cycles keeps the list already held. */
    public static void accept(int windowId, boolean eco, int cpuTier, @Nullable ECOCycleItemList cycles) {
        State previous = latest;
        ECOCycleItemList kept = cycles != null ? cycles
            : previous.windowId == windowId ? previous.cycles : ECOCycleItemList.EMPTY;
        latest = new State(windowId, eco, cpuTier, kept);
    }

    public static State state(Container container) {
        State state = latest;
        return container != null && state.windowId == container.windowId ? state : State.NONE;
    }

    /** Drops the state of a closed window; a newer window's state is kept. */
    static void release(Container container) {
        State state = latest;
        if (container != null && state.windowId == container.windowId) latest = State.NONE;
        Processing value = processing;
        if (container != null && value.windowId == container.windowId) processing = Processing.NONE;
    }

    @SubscribeEvent
    public void open(GuiOpenEvent event) {
        // Only AE2's own page; subclasses from other mods keep their screens.
        if (event.gui == null || event.gui.getClass() != GuiCraftingStatus.class) return;
        var native_ = (GuiCraftingStatus) event.gui;
        if (!(native_.inventorySlots instanceof ContainerCraftingStatus container)
            || !(container.getTarget() instanceof ITerminalHost host)) return;
        try {
            // FML assigns the window id to the open container after the screen is shown, so ours receives it.
            event.gui = new ECOCraftingStatusGui(container.getPlayerInv(), host);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[craft-status] Could not create the ECO crafting status page; keeping AE2's", failure);
        }
    }
}
