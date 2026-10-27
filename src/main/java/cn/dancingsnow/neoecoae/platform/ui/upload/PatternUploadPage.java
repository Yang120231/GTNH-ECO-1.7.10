package cn.dancingsnow.neoecoae.platform.ui.upload;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Target list with paging, reasons, locate and undo. Rows address targets only through server tokens. */
final class PatternUploadPage {

    private static final int WIDTH = 256, ROW_Y = 50, ROW_HEIGHT = 22;
    private static final int HEIGHT = ROW_Y + PatternUploadSyncHandler.PAGE_SIZE * ROW_HEIGHT + 34;
    private static final int TEXT = 0x3f3d52;

    private PatternUploadPage() {}

    static ModularPanel create(PanelSyncManager sync) {
        PatternUploadSyncHandler handler = new PatternUploadSyncHandler();
        sync.syncValue("pattern_upload", handler);
        ModularPanel panel = NativeMachinePanel.create("neoecoae_pattern_upload", WIDTH, HEIGHT)
            .background(NativeUiStyle.panel());
        panel.child(
            new NativeText(() -> Component.translatable("gui.neoecoae.pattern_upload.title"), 1, TEXT).pos(8, 6)
                .size(120, 10));
        panel.child(
            new PatternIcon(handler).pos(8, 18)
                .size(18, 18));
        panel.child(
            new NativeText(() -> summary(handler), .8f, TEXT).pos(30, 18)
                .size(WIDTH - 38, 9));
        panel.child(
            new NativeText(() -> autoLine(handler), .8f, TEXT).pos(30, 28)
                .size(WIDTH - 38, 9));
        panel.child(new NativeText(() -> {
            String message = text(
                handler.state()
                    .getString("message"));
            if (message.isEmpty()) return Component.empty();
            return Component.literal(
                (handler.state()
                    .getBoolean("error") ? "§c" : "§2") + message);
        }, .8f, TEXT).pos(8, 39)
            .size(WIDTH - 16, 9));
        for (int index = 0; index < PatternUploadSyncHandler.PAGE_SIZE; index++) {
            int row = index, y = ROW_Y + index * ROW_HEIGHT;
            panel.child(
                new Row(handler, row).pos(6, y)
                    .size(WIDTH - 82, ROW_HEIGHT - 2));
            panel.child(button("gui.neoecoae.pattern_upload.upload", () -> {
                int token = handler.row(row)
                    .getInt("token");
                if (token != 0) handler.action(PatternUploadSyncHandler.ACTION_UPLOAD, token);
            }).pos(WIDTH - 74, y + 1)
                .size(34, 18)
                .setEnabledIf(
                    widget -> handler.row(row)
                        .getBoolean("manual")));
            panel.child(button("gui.neoecoae.pattern_upload.locate", () -> {
                int token = handler.row(row)
                    .getInt("token");
                if (token != 0) handler.action(PatternUploadSyncHandler.ACTION_LOCATE, token);
            }).pos(WIDTH - 38, y + 1)
                .size(32, 18)
                .setEnabledIf(
                    widget -> handler.row(row)
                        .getInt("token") != 0));
        }
        int footer = HEIGHT - 26;
        panel.child(
            button(
                "<",
                () -> handler.action(
                    PatternUploadSyncHandler.ACTION_PAGE,
                    handler.state()
                        .getInt("page") - 1)).pos(6, footer)
                            .size(18, 18));
        panel.child(new NativeText(() -> {
            CompoundTag state = handler.state();
            return Component.translatable(
                "gui.neoecoae.pattern_upload.page",
                state.getInt("page") + 1,
                Math.max(1, state.getInt("pages")),
                state.getInt("total"));
        }, .8f, TEXT).pos(26, footer + 5)
            .size(52, 9));
        panel.child(
            button(
                ">",
                () -> handler.action(
                    PatternUploadSyncHandler.ACTION_PAGE,
                    handler.state()
                        .getInt("page") + 1)).pos(80, footer)
                            .size(18, 18));
        panel.child(
            button(
                "gui.neoecoae.pattern_upload.auto_upload",
                () -> handler.action(PatternUploadSyncHandler.ACTION_AUTO, 0)).pos(104, footer)
                    .size(52, 18)
                    .setEnabledIf(
                        widget -> handler.state()
                            .getInt("auto") != 0));
        panel.child(
            button("gui.neoecoae.pattern_upload.undo", () -> handler.action(PatternUploadSyncHandler.ACTION_UNDO, 0))
                .pos(160, footer)
                .size(52, 18)
                .setEnabledIf(
                    widget -> handler.state()
                        .getBoolean("receipt")));
        panel.child(
            button(
                "gui.neoecoae.pattern_upload.refresh",
                () -> handler.action(PatternUploadSyncHandler.ACTION_REFRESH, 0)).pos(216, footer)
                    .size(34, 18));
        return panel;
    }

    private static ButtonWidget<?> button(String key, Runnable click) {
        return NativeUiStyle.button()
            .overlay(key.length() == 1 ? IKey.str(key) : IKey.lang(key))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                click.run();
                return true;
            });
    }

    private static Component summary(PatternUploadSyncHandler handler) {
        CompoundTag state = handler.state();
        var pattern = ItemStacks.parseOptional(RegistryAccess.EMPTY, state.getCompound("pattern"));
        if (ItemStacks.isEmpty(pattern)) return Component.translatable("gui.neoecoae.pattern_upload.no_pattern");
        String kind = StatCollector.translateToLocal(
            state.getBoolean("processing") ? "gui.neoecoae.pattern_upload.processing"
                : "gui.neoecoae.pattern_upload.crafting");
        String hint = state.getBoolean("hinted")
            ? " · " + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.hinted")
            : "";
        return Component.literal(pattern.getDisplayName() + " · " + kind + hint);
    }

    private static Component autoLine(PatternUploadSyncHandler handler) {
        CompoundTag state = handler.state();
        if (state.getInt("auto") != 0) return Component.translatable("gui.neoecoae.pattern_upload.auto_ready");
        String reason = state.getString("autoReason");
        return reason.isEmpty() ? Component.empty() : Component.translatable(reason);
    }

    static String text(String key) {
        if (key == null || key.isEmpty()) return "";
        if (StatCollector.canTranslate(key)) return StatCollector.translateToLocal(key);
        if (StatCollector.canTranslate(key + ".name")) return StatCollector.translateToLocal(key + ".name");
        return key;
    }

    private static final class PatternIcon extends Widget<PatternIcon> {

        private final PatternUploadSyncHandler handler;

        PatternIcon(PatternUploadSyncHandler handler) {
            this.handler = handler;
            background(NativeUiStyle.slot());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var stack = ItemStacks.parseOptional(
                RegistryAccess.EMPTY,
                handler.state()
                    .getCompound("pattern"));
            NativeUiDraw.item(stack, 1, 1, context.getCurrentDrawingZ());
        }
    }

    private static final class Row extends Widget<Row> {

        private final PatternUploadSyncHandler handler;
        private final int index;

        Row(PatternUploadSyncHandler handler, int index) {
            this.handler = handler;
            this.index = index;
            tooltipDynamic(lines -> {
                CompoundTag row = handler.row(index);
                if (row.getInt("token") == 0) return;
                lines.add(title(row));
                lines.add(text(row.getString("level")));
                if (!row.getString("reason")
                    .isEmpty()) lines.add(text(row.getString("reason")));
                lines.add(text(row.getString("state")));
                if (row.getInt("circuit") >= 0) lines.add(
                    StatCollector.translateToLocalFormatted(
                        "gui.neoecoae.pattern_upload.button.auto.tooltip.circuit",
                        row.getInt("circuit")));
                if (row.getBoolean("cover"))
                    lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.programming_cover"));
                lines.add(
                    StatCollector.translateToLocalFormatted(
                        "gui.neoecoae.pattern_upload.location",
                        row.getInt("dim"),
                        row.getInt("x"),
                        row.getInt("y"),
                        row.getInt("z")));
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        private static String title(CompoundTag row) {
            String name = text(row.getString("name"));
            String route = row.getString("route");
            return route.isEmpty() ? name : name + " → " + text(route);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            CompoundTag row = handler.row(index);
            if (row.getInt("token") == 0) return;
            int width = getArea().width, height = getArea().height;
            int level = row.getInt("levelOrdinal"), state = row.getInt("stateOrdinal");
            int accent = state != PatternTargetSelector.SlotState.FREE.ordinal() ? 0xff9a9a9a
                : level == MatchResult.Level.EXACT.ordinal() || level == MatchResult.Level.ACCEPTED.ordinal()
                    ? 0xff3fa03f
                    : level == MatchResult.Level.INCOMPATIBLE.ordinal() ? 0xffb03030 : 0xffd0a020;
            GuiDraw.drawRect(0, 0, width, height, 0x22000000);
            GuiDraw.drawRect(0, 0, 2, height, accent);
            var icon = ItemStacks.parseOptional(RegistryAccess.EMPTY, row.getCompound("icon"));
            NativeUiDraw.item(icon, 4, (height - 16) / 2, context.getCurrentDrawingZ());
            int kind = row.getInt("kind");
            String kindName = kind >= 0 && kind < PatternUploadTarget.Kind.values().length
                ? StatCollector.translateToLocal(PatternUploadTarget.Kind.values()[kind].translationKey())
                : "";
            NativeUiDraw.text(Component.literal(title(row)), 24, 1, width - 26, .75f, TEXT, false, false);
            String detail = text(row.getString("level")) + " · "
                + text(row.getString("state"))
                + " · "
                + StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.pattern_upload.empty_slots",
                    row.getInt("empty"),
                    row.getInt("slots"))
                + " · "
                + kindName;
            NativeUiDraw.text(Component.literal(detail), 24, 10, width - 26, .65f, 0x555566, false, false);
        }
    }
}
