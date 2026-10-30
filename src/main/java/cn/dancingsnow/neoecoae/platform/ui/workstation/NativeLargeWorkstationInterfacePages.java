package cn.dancingsnow.neoecoae.platform.ui.workstation;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.NativeSlotGroups;
import cn.dancingsnow.neoecoae.platform.ui.NativeSnapshotSession;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;
import cn.dancingsnow.neoecoae.platform.ui.NativeUpgradeSlot;

/** Native, physically owned pattern and upgrade slots with server-owned provider settings. */
public final class NativeLargeWorkstationInterfacePages {

    private NativeLargeWorkstationInterfacePages() {}

    public static ModularPanel create(ECOLargeIntegratedWorkingStationInterfaceBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        Session session = new Session(host);
        sync.syncValue("workstation.provider", session);
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_large_workstation_interface", 228, 277)
            .background(NativeUiStyle.panel());
        panel.child(
            new NativeText(
                () -> Component.translatable("block.neoecoae.large_integrated_working_station_interface"),
                .8f,
                0x3f3d52).pos(28, 5)
                    .size(180, 10));
        panel.child(
            new NativeText(
                () -> Component.translatable(
                    "gui.neoecoae.workstation.provider.patterns",
                    session.state()
                        .getInt("advertised"),
                    session.state()
                        .getInt("unlockedRows") * 9),
                .75f,
                0x596177).pos(28, 15)
                    .size(162, 9));
        LargeWorkstationPatternProvider provider = host.getWorkstationProvider();
        InvWrapper patterns = new InvWrapper(provider.getPatternInv());
        String patternGroup = NativeSlotGroups.register(
            sync,
            "workstation.provider.patterns",
            LargeWorkstationPatternProvider.PATTERN_COLUMNS,
            NativeSlotGroups.STORAGE_PRIORITY);
        String upgradeGroup = NativeSlotGroups
            .register(sync, "workstation.provider.upgrades", 1, NativeSlotGroups.UPGRADE_PRIORITY);
        InvWrapper upgrades = new InvWrapper(provider.getUpgrades());
        for (int index = 0; index < LargeWorkstationPatternProvider.MAX_PATTERN_SLOTS; index++) {
            int slot = index;
            panel.child(
                new NativeItemSlot().slot(new ModularSlot(patterns, index).slotGroup(patternGroup))
                    .background(NativeUiStyle.slot())
                    .pos(28 + index % 9 * 18, 23 + index / 9 * 18)
                    .tooltipDynamic(lines -> {
                        if (slot >= session.state()
                            .getInt("unlockedRows") * 9)
                            lines.add(
                                Component.translatable("gui.neoecoae.workstation.provider.locked_slot")
                                    .getString());
                    }));
        }
        for (int slot = 0; slot < LargeWorkstationPatternProvider.UPGRADE_SLOTS; slot++) panel.child(
            new NativeUpgradeSlot().slot(new ModularSlot(upgrades, slot).slotGroup(upgradeGroup))
                .background(NativeUiStyle.slot())
                .pos(206, 23 + slot * 18));
        panel.child(
            NativeUiStyle.button()
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    session.action(0, 0);
                    return true;
                })
                .child(
                    new NativeText(
                        () -> Component.translatable(
                            "gui.neoecoae.workstation.provider.blocking",
                            Component.translatable(
                                session.state()
                                    .getBoolean("blocking") ? "gui.neoecoae.enabled" : "gui.neoecoae.disabled")
                                .getString()),
                        .6f,
                        0x3f3d52))
                .pos(28, 173)
                .size(58, 16));
        panel.child(
            NativeUiStyle.button()
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    session.action(1, 0);
                    return true;
                })
                .child(
                    new NativeText(
                        () -> Component.translatable(
                            "gui.neoecoae.workstation.provider.lock_mode." + session.state()
                                .getString("lock")),
                        .6f,
                        0x3f3d52))
                .pos(89, 173)
                .size(64, 16));
        panel.child(
            NativeUiStyle.button()
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    session.action(2, 0);
                    return true;
                })
                .child(
                    new NativeText(
                        () -> Component.translatable(
                            "gui.neoecoae.workstation.provider.terminal",
                            Component.translatable(
                                session.state()
                                    .getBoolean("terminal") ? "gui.neoecoae.enabled" : "gui.neoecoae.disabled")
                                .getString()),
                        .6f,
                        0x3f3d52))
                .pos(156, 173)
                .size(58, 16));
        NativeUiStyle.inventoryFrames(panel, 28, 195);
        panel.child(
            SlotGroupWidget.playerInventory((index, slot) -> new NativeItemSlot().background(NativeUiStyle.slot()))
                .pos(28, 195));
        return panel;
    }

    static final class Session extends NativeSnapshotSession {

        private final ECOLargeIntegratedWorkingStationInterfaceBlockEntity host;

        Session(ECOLargeIntegratedWorkingStationInterfaceBlockEntity host) {
            super(host);
            this.host = host;
        }

        @Override
        protected CompoundTag capture() {
            LargeWorkstationPatternProvider provider = host.getWorkstationProvider();
            CompoundTag result = new CompoundTag();
            result.putInt(
                "advertised",
                provider.getAvailablePatterns()
                    .size());
            result.putInt("unlockedRows", provider.getUnlockedPatternRows());
            result.putBoolean(
                "blocking",
                provider.getConfigManager()
                    .getSetting(Settings.BLOCK) == YesNo.YES);
            result.putBoolean(
                "terminal",
                provider.getConfigManager()
                    .getSetting(Settings.INTERFACE_TERMINAL) == YesNo.YES);
            result.putString(
                "lock",
                provider.getConfigManager()
                    .getSetting(Settings.LOCK_CRAFTING_MODE)
                    .name());
            return result;
        }

        @Override
        protected void apply(int action, int argument) {
            if (argument != 0) return;
            var config = host.getWorkstationProvider()
                .getConfigManager();
            switch (action) {
                case 0:
                    config.putSetting(
                        Settings.BLOCK,
                        config.getSetting(Settings.BLOCK) == YesNo.YES ? YesNo.NO : YesNo.YES);
                    break;
                case 1:
                    LockCraftingMode[] modes = LockCraftingMode.values();
                    config.putSetting(
                        Settings.LOCK_CRAFTING_MODE,
                        modes[(config.getSetting(Settings.LOCK_CRAFTING_MODE)
                            .ordinal() + 1) % modes.length]);
                    break;
                case 2:
                    config.putSetting(
                        Settings.INTERFACE_TERMINAL,
                        config.getSetting(Settings.INTERFACE_TERMINAL) == YesNo.YES ? YesNo.NO : YesNo.YES);
                    break;
                default:
                    break;
            }
        }
    }
}
