package cn.dancingsnow.neoecoae.platform.ui.workstation;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeButtonWidget;
import cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage;
import cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeSlotGroups;
import cn.dancingsnow.neoecoae.platform.ui.NativeSnapshotSession;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;
import cn.dancingsnow.neoecoae.platform.ui.NativeUpgradeSlot;
import cn.dancingsnow.neoecoae.platform.ui.storage.NativeStoragePriorityPages;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The 1.21.1 provider artwork and geometry over physically owned native container slots. */
public final class NativeLargeWorkstationInterfacePages {

    private static final String BACKGROUND = "ae2:textures/guis/large_integrated_working_station_interface.png";
    private static final String ATLAS = NativeHostSideBar.ATLAS;
    private static final int COLUMNS = 9, CELL = 18, VISIBLE_ROWS = 7, VIEWPORT_HEIGHT = CELL * VISIBLE_ROWS;

    private NativeLargeWorkstationInterfacePages() {}

    public static ModularPanel create(ECOLargeIntegratedWorkingStationInterfaceBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        var provider = host.getWorkstationProvider();
        provider.returnLegacyUpgrade(player);
        Session session = new Session(host);
        sync.syncValue("workstation.provider", session);
        var priority = sync.syncedPanel(
            "workstation.provider.priority",
            true,
            (panelSync, handler) -> NativeStoragePriorityPages.create(
                () -> session.state()
                    .getInt("priority"),
                text -> {
                    try {
                        session.action(3, Integer.parseInt(text));
                    } catch (NumberFormatException ignored) {}
                },
                delta -> session.action(4, delta),
                () -> Component.translatable("gui.appliedenergistics2.Priority"),
                () -> true,
                handler));
        var guide = sync.syncedPanel(
            "workstation.provider.guide",
            true,
            (panelSync, handler) -> NativeGuidePage.create("large_integrated_working_station", handler));
        ModularPanel panel = NativeMachinePanel.create("neoecoae_large_workstation_interface", 188, 245)
            .background(NativePageLayout.texture(BACKGROUND, 0, 0, 188, 245));
        panel.child(
            new NativeText(
                () -> Component.translatable("block.neoecoae.large_integrated_working_station_interface"),
                1,
                0x3f3d52).pos(8, 6)
                    .size(154, 9));
        panel.child(
            new NativeText(() -> Component.translatable("container.inventory"), 1, 0x3f3d52).pos(8, 150)
                .size(162, 9));
        panel.child(
            NativePageLayout.playerInventory()
                .pos(7, 160));

        var data = new PatternScrollData(session);
        var viewport = new ScrollWidget<>(data).pos(7, 22)
            .size(162, VIEWPORT_HEIGHT)
            .showScrollShadows(false)
            .background(IDrawable.EMPTY)
            .disableHoverBackground();
        panel.child(viewport);
        InvWrapper patterns = new InvWrapper(provider.getPatternInv());
        String patternGroup = NativeSlotGroups
            .register(sync, "workstation.provider.patterns", COLUMNS, NativeSlotGroups.STORAGE_PRIORITY);
        // Locked rows remain visible placeholders, while the menu keeps all physical slot indices stable.
        for (int row = 0; row < LargeWorkstationPatternProvider.MAX_PATTERN_SLOTS / COLUMNS; row++) viewport.child(
            new Widget<>().pos(0, row * CELL)
                .size(162, CELL)
                .background(NativePageLayout.texture(BACKGROUND, 7, 178, 162, CELL)));
        for (int index = 0; index < LargeWorkstationPatternProvider.MAX_PATTERN_SLOTS; index++) {
            final int slot = index;
            viewport.child(
                new NativeItemSlot().slot(new ModularSlot(patterns, index).slotGroup(patternGroup))
                    .background(IDrawable.EMPTY)
                    .disableHoverBackground()
                    .pos(index % COLUMNS * CELL, index / COLUMNS * CELL)
                    .setEnabledIf(
                        widget -> slot < session.state()
                            .getInt("patternRows") * COLUMNS));
        }
        panel.child(
            new PatternScrollbar(viewport, data).pos(174, 23)
                .size(9, 124));

        String upgradeGroup = NativeSlotGroups
            .register(sync, "workstation.provider.upgrades", 1, NativeSlotGroups.UPGRADE_PRIORITY);
        var upgrades = new InvWrapper(provider.getUpgrades());
        // Native AE2 UpgradesPanel: five-pixel top padding, eighteen-pixel cells, seven-pixel bottom cap.
        var upgradePanel = new ParentWidget<>().pos(186, 0)
            .size(28, 84)
            .excludeAreaInRecipeViewer()
            .background(
                NativePageLayout
                    .texture("neoecoae:textures/gui/vendor/ae2/extra_panels.png", 0, 0, 28, 30, 5, 5, 5, 7));
        for (int slot = 0; slot < LargeWorkstationPatternProvider.UPGRADE_SLOTS; slot++) upgradePanel.child(
            new NativeUpgradeSlot().slot(new ModularSlot(upgrades, slot).slotGroup(upgradeGroup))
                .background(NativePageLayout.texture(ATLAS, 192, 192, 18, 18))
                .pos(0, 5 + slot * CELL));
        upgradePanel.child(
            new UpgradeFrame().pos(0, 5)
                .size(18, 72));
        panel.child(upgradePanel);

        panel.child(
            new Widget<>().pos(-19, 0)
                .size(21, 94)
                .excludeAreaInRecipeViewer()
                .background(
                    NativePageLayout.texture(
                        "neoecoae:textures/gui/vendor/ae2/sprites/vertical_buttons_bg.png",
                        0,
                        0,
                        21,
                        26,
                        0,
                        2,
                        0,
                        4)));
        panel.child(
            new SettingButton(session, -1).pos(-15, 3)
                .size(16)
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    NativeGuidePage.open("large_integrated_working_station", player, guide::openPanel);
                    return true;
                }));
        for (int action = 0; action < 3; action++) {
            final int setting = action;
            panel.child(
                new SettingButton(session, action).pos(-15, 25 + action * 22)
                    .size(16)
                    .onMousePressed(button -> {
                        if (button != 0 && button != 1) return false;
                        data.animateTo(viewport.getScrollArea(), viewport.getScrollY());
                        session.action(setting, button == 0 ? 1 : -1);
                        return true;
                    }));
        }
        panel.child(
            new ButtonWidget<>().pos(164, -5)
                .size(20)
                .background(NativePageLayout.texture(ATLAS, 160, 192, 20, 20))
                .disableHoverBackground()
                .overlay(NativePageLayout.texture(ATLAS, 144, 64, 16, 16))
                .tooltipDynamic(
                    lines -> lines.add(
                        Component.translatable("gui.neoecoae.storage_priority.open")
                            .getString()))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    priority.openPanel();
                    return true;
                }));
        return panel;
    }

    private static final class UpgradeFrame extends Widget<UpgradeFrame> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            com.cleanroommc.modularui.drawable.GuiDraw.drawRect(1, 0, 16, 1, 0xfff2f2f2);
            com.cleanroommc.modularui.drawable.GuiDraw.drawRect(1, 71, 16, 1, 0xfff2f2f2);
            com.cleanroommc.modularui.drawable.GuiDraw.drawRect(0, 0, 1, 72, 0xfff2f2f2);
            com.cleanroommc.modularui.drawable.GuiDraw.drawRect(17, 0, 1, 72, 0xfff2f2f2);
        }
    }

    /** Let MUI own slot clipping and hit testing; its scroll data also drives the external AE2 thumb. */
    private static final class PatternScrollData extends VerticalScrollData {

        private final Session session;

        PatternScrollData(Session session) {
            this.session = session;
            setScrollSpeed(CELL);
        }

        @Override
        public int getThickness() {
            return 0;
        }

        @Override
        public void setScrollSize(int size) {
            super.setScrollSize(
                Math.max(
                    VIEWPORT_HEIGHT,
                    session.state()
                        .getInt("patternRows") * CELL));
        }

        @Override
        public boolean isInsideScrollbarArea(ScrollArea area, int x, int y) {
            return false;
        }

        @Override
        public boolean onMouseClicked(ScrollArea area, int main, int cross, int button) {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawScrollbar(ScrollArea area, ModularGuiContext context, WidgetTheme theme, IDrawable texture) {}
    }

    private static final class PatternScrollbar extends ButtonWidget<PatternScrollbar> {

        private final ScrollWidget<?> viewport;
        private final PatternScrollData data;
        private float grab;

        PatternScrollbar(ScrollWidget<?> viewport, PatternScrollData data) {
            this.viewport = viewport;
            this.data = data;
            NativeUiStyle.plainHover(this);
            onMouseScrolled((direction, amount) -> {
                data.animateTo(
                    viewport.getScrollArea(),
                    viewport.getScrollY() + (direction.modifier > 0 ? -1 : 1) * CELL * amount);
                return true;
            });
            onUpdateListener(widget -> {
                data.setScrollSize(0);
                data.clamp(viewport.getScrollArea());
            });
        }

        private int maximum() {
            return Math.max(0, data.getScrollSize() - VIEWPORT_HEIGHT);
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0 || maximum() == 0) return Result.IGNORE;
            float y = getContext().getMouseY(), top = 109f * data.getScroll() / maximum();
            grab = y >= top && y < top + 15 ? y - top : 7.5f;
            drag();
            return Result.SUCCESS;
        }

        private void drag() {
            int pixels = (int) ((getContext().getMouseY() - grab) / 109 * maximum());
            data.animateTo(viewport.getScrollArea(), pixels);
            data.scrollTo(viewport.getScrollArea(), pixels);
        }

        @Override
        public void onMouseDrag(int button, long elapsed) {
            if (button == 0) drag();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int y = maximum() == 0 ? 0 : Math.round(109f * data.getScroll() / maximum());
            NativePageLayout
                .texture(
                    "neoecoae:textures/gui/vendor/ae2/sprites/small_scroller" + (maximum() == 0 ? "_disabled" : "")
                        + ".png",
                    0,
                    0,
                    8,
                    15)
                .draw(0, y, 9, 15);
        }
    }

    private static final class SettingButton extends NativeButtonWidget<SettingButton> {

        private final Session session;
        private final int action;

        SettingButton(Session session, int action) {
            this.session = session;
            this.action = action;
            NativeUiStyle.plainHover(this);
            tooltipDynamic(lines -> lines.add(label().getString()));
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        private Component label() {
            var state = session.state();
            if (action == -1) return Component.translatable("gui.neoecoae.guide");
            if (action == 1)
                return Component.translatable("gui.neoecoae.workstation.provider.lock_mode." + state.getString("lock"));
            return Component.translatable(
                "gui.neoecoae.workstation.provider." + (action == 0 ? "blocking" : "terminal"),
                Component
                    .translatable(
                        state.getBoolean(action == 0 ? "blocking" : "terminal") ? "gui.neoecoae.enabled"
                            : "gui.neoecoae.disabled")
                    .getString());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int offset = isHovering() ? 1 : 0;
            NativeUiDraw.sprite(ATLAS, isHovering() ? 212 : 176, 128, 18, 20, -1, offset, 18, 20);
            int u, v;
            if (action == -1) {
                u = 176;
                v = 0;
            } else if (action == 0) {
                u = session.state()
                    .getBoolean("blocking") ? 80 : 64;
                v = 16;
            } else if (action == 2) {
                u = session.state()
                    .getBoolean("terminal") ? 64 : 80;
                v = 80;
            } else {
                v = 0;
                switch (session.state()
                    .getString("lock")) {
                    case "LOCK_UNTIL_PULSE":
                        u = 32;
                        break;
                    case "LOCK_WHILE_HIGH":
                        u = 16;
                        break;
                    case "LOCK_WHILE_LOW":
                        u = 0;
                        break;
                    case "LOCK_UNTIL_RESULT":
                        u = 112;
                        break;
                    default:
                        u = 160;
                        break;
                }
            }
            NativeUiDraw.sprite(ATLAS, u, v, 16, 16, 0, 1 + offset, 16, 16);
        }
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
            result.putInt("priority", provider.getPriority());
            result.putInt("patternRows", provider.getRequiredPatternRows());
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
            if (action == 3 || action == 4) {
                var provider = host.getWorkstationProvider();
                provider.setPriority(
                    action == 3 ? argument
                        : cn.dancingsnow.neoecoae.gui.storage.StoragePriority.adjust(provider.getPriority(), argument));
                return;
            }
            if (argument != 1 && argument != -1) return;
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
                            .ordinal() + argument
                            + modes.length) % modes.length]);
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
