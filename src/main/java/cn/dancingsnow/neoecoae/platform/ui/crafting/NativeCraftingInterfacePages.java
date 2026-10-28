package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The original 195x237 pattern manager; the preview addresses the real network buses. */
public final class NativeCraftingInterfacePages {

    private static final int COLUMNS = 9, ROWS = 6, CELL = 18;
    private static final int PREVIEW_WIDTH = COLUMNS * CELL, PREVIEW_HEIGHT = ROWS * CELL;
    private static final int THUMB_HEIGHT = 15, THUMB_TRAVEL = PREVIEW_HEIGHT - 2 - THUMB_HEIGHT;

    private NativeCraftingInterfacePages() {}

    public static ModularPanel create(ECOMachineInterfaceBlockEntity<?> host, EntityPlayer player,
        PanelSyncManager sync) {
        var session = new NativePatternInterfaceSession(host);
        sync.syncValue("crafting.patterns", session);
        var layout = new NativePageLayout("crafting/crafting_interface");
        var panel = cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel
            .create("neoecoae_crafting_interface", layout.width(), layout.height())
            .background(NativePageLayout.texture("neoecoae:textures/guis/pattern_manager.png", 0, 0, 195, 237));
        layout.children(panel, node -> {
            switch (node.get("custom")
                .getAsString()) {
                case "title":
                    return new NativeText(
                        () -> Component.translatable("gui.neoecoae.crafting_interface.title"),
                        1,
                        0x3f3d52);
                case "operation_status":
                    return new Widget<>();
                case "operation_indicator":
                    return new OperationIndicator(session);
                case "input_search":
                    return search(session, false);
                case "output_search":
                    return search(session, true);
                case "pattern_preview":
                    var preview = new Preview().size(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                    for (int index = 0; index < COLUMNS * ROWS; index++) preview.child(
                        new Pattern(session, index).pos(index % COLUMNS * CELL, index / COLUMNS * CELL)
                            .size(CELL));
                    return preview;
                case "pattern_scrollbar":
                    return new Scrollbar(session).size(14, 108);
                case "side_controls":
                    return NativeHostSideBar.left(
                        true,
                        NativeHostSideBar.button(
                            32,
                            48,
                            () -> Component.translatable("gui.neoecoae.host.crafting.pattern_transfer"),
                            () -> session.action(4, 0)),
                        NativeHostSideBar.button(
                            32,
                            64,
                            () -> Component.translatable("gui.neoecoae.crafting_interface.preview.organize"),
                            () -> session.action(3, 0)));
                case "status_label":
                    return new NativeText(() -> {
                        int rows = session.state()
                            .getInt("rows"),
                            offset = session.state()
                                .getInt("offset");
                        return rows == 0 ? Component.translatable("gui.neoecoae.crafting_interface.preview.no_results")
                            : Component.translatable(
                                "gui.neoecoae.crafting_interface.preview.rows",
                                offset + 1,
                                Math.min(rows, offset + 6),
                                rows);
                    }, 1, 0x3f3d52);
                case "transfer_progress":
                    return new Widget<>();
                case "player_inventory":
                    return NativePageLayout.playerInventory();
                default:
                    throw new IllegalArgumentException("Unknown pattern manager node " + node);
            }
        });
        return panel;
    }

    private static TextFieldWidget search(NativePatternInterfaceSession session, boolean output) {
        return new TextFieldWidget().setMaxLength(128)
            .setTextColor(0xffffffff)
            .background(IDrawable.EMPTY)
            .disableHoverBackground()
            .padding(2, 0)
            .hintText(
                Component
                    .translatable("gui.neoecoae.crafting_interface.preview.search." + (output ? "output" : "inputs"))
                    .getString())
            .hintColor(0xffa0a0a0)
            .value(new StringSyncValue(() -> session.search(output), query -> session.search(output, query)))
            .tooltipDynamic(
                lines -> lines.add(
                    Component.translatable(
                        "gui.neoecoae.crafting_interface.preview.search." + (output ? "output" : "inputs") + ".tooltip")
                        .getString()));
    }

    /** Draw the continuous grid in the viewport pass, after MUI's sibling background pass. */
    private static final class Preview extends ParentWidget<Preview> implements IViewport {

        Preview() {
            background(IDrawable.EMPTY).disableHoverBackground();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void preDraw(ModularGuiContext context, boolean transformed) {
            if (transformed) return;
            // The fixed outline belongs to the panel artwork, not the scrolling contents.
            Stencil.apply(1, 1, PREVIEW_WIDTH - 2, PREVIEW_HEIGHT - 2, context);
            for (int row = 0; row < ROWS; row++) NativeUiDraw.sprite(
                "neoecoae:textures/guis/pattern_manager.png",
                7,
                170,
                PREVIEW_WIDTH,
                CELL,
                0,
                row * CELL,
                PREVIEW_WIDTH,
                CELL);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void postDraw(ModularGuiContext context, boolean transformed) {
            if (!transformed) Stencil.remove();
        }
    }

    private static final class OperationIndicator extends Widget<OperationIndicator> {

        OperationIndicator(NativePatternInterfaceSession session) {
            background(IDrawable.EMPTY).disableHoverBackground();
            tooltipDynamic(
                lines -> lines.add(
                    Component.translatable("gui.neoecoae.storage_interface.network")
                        .append(": ")
                        .append(
                            Component.translatable(
                                session.state()
                                    .getBoolean("online") ? "gui.neoecoae.storage_interface.connected"
                                        : "gui.neoecoae.storage_interface.disconnected"))
                        .getString()));
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            boolean hovered = isHovering();
            NativeUiDraw.sprite(
                "neoecoae:textures/guis/crafting_widgets.png",
                8,
                hovered ? 142 : 129,
                10,
                hovered ? 11 : 12,
                0,
                hovered ? 1 : 0,
                10,
                hovered ? 11 : 12);
        }
    }

    private static final class Pattern extends cn.dancingsnow.neoecoae.platform.ui.NativeSlotButton<Pattern> {

        private final NativePatternInterfaceSession session;
        private final int index;

        Pattern(NativePatternInterfaceSession session, int index) {
            this.session = session;
            this.index = index;
            background(IDrawable.EMPTY);
            hoverBackground(IDrawable.EMPTY);
            onMousePressed(button -> {
                int token = session.entry(index)
                    .getInt("token");
                if (token == 0 || button != 0 && button != 1) return false;
                session.action(Interactable.hasShiftDown() ? 2 : 1, token);
                return true;
            });
            onMouseScrolled((direction, amount) -> {
                session.action(
                    0,
                    session.state()
                        .getInt("offset") + (direction.modifier > 0 ? -1 : 1));
                return true;
            });
            tooltipDynamic(lines -> {
                var entry = session.entry(index);
                var stack = ItemStacks.parseOptional(RegistryAccess.EMPTY, entry.getCompound("stack"));
                if (!ItemStacks.isEmpty(stack)) lines.addFromItem(stack);
                if (entry.getInt("token") != 0) {
                    var source = BlockPos.of(entry.getLong("bus"));
                    lines.add(
                        Component
                            .translatable(
                                "gui.neoecoae.crafting_interface.preview.source",
                                source.getX(),
                                source.getY(),
                                source.getZ(),
                                entry.getInt("slot") + 1)
                            .getString());
                }
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var entry = session.entry(index);
            if (entry.getInt("token") == 0) return;
            var stack = ItemStacks.parseOptional(RegistryAccess.EMPTY, entry.getCompound("display"));
            if (!ItemStacks.isEmpty(stack)) NativeUiDraw.item(stack, 1, 1, context.getCurrentDrawingZ());
            if (!ItemStacks.isEmpty(stack) && (!session.search(false)
                .trim()
                .isEmpty()
                || !session.search(true)
                    .trim()
                    .isEmpty())) {
                GuiDraw.drawRect(0, 0, CELL, 1, 0xffffd95a);
                GuiDraw.drawRect(0, CELL - 1, CELL, 1, 0xffffd95a);
                GuiDraw.drawRect(0, 0, 1, CELL, 0xffffd95a);
                GuiDraw.drawRect(CELL - 1, 0, 1, CELL, 0xffffd95a);
            }
        }
    }

    private static final class Scrollbar extends ButtonWidget<Scrollbar> {

        private final NativePatternInterfaceSession session;
        private float dragStartY;
        private int dragStartOffset;

        Scrollbar(NativePatternInterfaceSession session) {
            this.session = session;
            background(NativePageLayout.texture("neoecoae:textures/guis/pattern_manager.png", 178, 31, 6, 108, 1, 1));
            NativeUiStyle.plainHover(this);
            onMouseScrolled((direction, amount) -> {
                session.action(
                    0,
                    session.state()
                        .getInt("offset") + (direction.modifier > 0 ? -1 : 1));
                return true;
            });
        }

        private void drag() {
            int max = Math.max(
                0,
                session.state()
                    .getInt("rows") - ROWS);
            if (max > 0) session.action(
                0,
                Math.max(
                    0,
                    Math.min(
                        max,
                        dragStartOffset + Math.round((getContext().getMouseY() - dragStartY) * max / THUMB_TRAVEL))));
        }

        @Override
        public Result onMousePressed(int button) {
            int max = Math.max(
                0,
                session.state()
                    .getInt("rows") - ROWS);
            if (button != 0 || max == 0) return Result.IGNORE;
            float mouseY = getContext().getMouseY() - 1;
            dragStartOffset = Math.min(
                max,
                session.state()
                    .getInt("offset"));
            int thumbY = dragStartOffset * THUMB_TRAVEL / max;
            if (mouseY < thumbY || mouseY >= thumbY + THUMB_HEIGHT) {
                dragStartOffset = Math
                    .max(0, Math.min(max, Math.round((mouseY - THUMB_HEIGHT / 2f) * max / THUMB_TRAVEL)));
                session.action(0, dragStartOffset);
            }
            // Use the initial row plus pointer displacement, avoiding rounding jumps on long lists.
            dragStartY = getContext().getMouseY();
            return Result.SUCCESS;
        }

        @Override
        public void onMouseDrag(int button, long elapsed) {
            if (button == 0) drag();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int max = Math.max(
                0,
                session.state()
                    .getInt("rows") - ROWS);
            int y = max == 0 ? 1
                : 1 + Math.min(
                    max,
                    session.state()
                        .getInt("offset"))
                    * THUMB_TRAVEL
                    / max;
            NativeUiDraw.sprite(
                "neoecoae:textures/gui/vendor/ae2/sprites/big_scroller" + (max == 0 ? "_disabled" : "") + ".png",
                0,
                0,
                12,
                15,
                1,
                y,
                12,
                15);
        }
    }
}
