package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

/**
 * MUI2 Shift-clicks only reach slots inside a registered group, and NEI's wheel and Shift mass transfers are built on
 * those Shift-clicks. Upgrades sort before storage so cards never fall into a general inventory first.
 */
public final class NativeSlotGroups {

    public static final int UPGRADE_PRIORITY = 50, TOOLBOX_PRIORITY = 60,
        STORAGE_PRIORITY = SlotGroup.STORAGE_SLOT_PRIO;

    private NativeSlotGroups() {}

    /** Registers a Shift-click target group and returns its name for {@code ModularSlot.slotGroup(String)}. */
    public static String register(PanelSyncManager sync, String name, int rowSize, int priority) {
        sync.registerSlotGroup(new SlotGroup(name, rowSize, priority, true));
        return name;
    }
}
