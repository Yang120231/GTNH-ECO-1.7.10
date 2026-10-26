package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
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
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The original 195x237 pattern manager; the preview addresses the real network buses. */
public final class NativeCraftingInterfacePages {
    private NativeCraftingInterfacePages() {}

    public static ModularPanel create(ECOMachineInterfaceBlockEntity<?> host, EntityPlayer player, PanelSyncManager sync) {
        var session = new NativePatternInterfaceSession(host);
        sync.syncValue("crafting.patterns", session);
        var layout = new NativePageLayout("crafting/crafting_interface");
        var panel = cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel.create("neoecoae_crafting_interface", layout.width(), layout.height())
            .background(NativePageLayout.texture("neoecoae:textures/guis/pattern_manager.png", 0, 0, 195, 237));
        layout.children(panel, node -> {
            switch (node.get("custom").getAsString()) {
                case "title": return new NativeText(() -> Component.translatable("gui.neoecoae.crafting_interface.title"),
                    1, 0x3f3d52);
                case "operation_status": return new Widget<>();
                case "operation_indicator": return new Widget<>().background(NativePageLayout.texture(
                    "neoecoae:textures/guis/crafting_widgets.png", 8, 129, 10, 12));
                case "input_search": return search(session, false);
                case "output_search": return search(session, true);
                case "pattern_preview":
                    var preview = new ParentWidget<>().size(162, 108).background(IDrawable.EMPTY);
                    for (int row = 0; row < 6; row++) {
                        final int first = row * 9;
                        preview.child(new Widget<>().pos(0, row * 18).size(162, 18).background(NativePageLayout.texture(
                            "neoecoae:textures/guis/pattern_manager.png", 7, 170, 162, 18))
                            .setEnabledIf(widget -> session.entry(first).getInt("token") != 0));
                    }
                    for (int index = 0; index < 54; index++) preview.child(new Pattern(session, index)
                        .pos(index % 9 * 18, index / 9 * 18).size(18));
                    return preview;
                case "pattern_scrollbar": return new Scrollbar(session).size(14, 108);
                case "side_controls": return NativeHostSideBar.left(true,
                    NativeHostSideBar.button(32, 48, () -> Component.translatable("gui.neoecoae.host.crafting.pattern_transfer"),
                        () -> session.action(4, 0)),
                    NativeHostSideBar.button(32, 64, () -> Component.translatable("gui.neoecoae.crafting_interface.preview.organize"),
                        () -> session.action(3, 0)));
                case "status_label": return new NativeText(() -> {
                    int rows = session.state().getInt("rows"), offset = session.state().getInt("offset");
                    return rows == 0 ? Component.translatable("gui.neoecoae.crafting_interface.preview.no_results")
                        : Component.translatable("gui.neoecoae.crafting_interface.preview.rows", offset + 1, Math.min(rows, offset + 6), rows);
                }, 1, 0x3f3d52);
                case "transfer_progress": return new Widget<>();
                case "player_inventory": return NativePageLayout.playerInventory();
                default: throw new IllegalArgumentException("Unknown pattern manager node " + node);
            }
        });
        return panel;
    }

    private static TextFieldWidget search(NativePatternInterfaceSession session, boolean output) {
        return new TextFieldWidget().setMaxLength(128).setTextColor(0xffe4e4ee).background(IDrawable.EMPTY)
            .value(new StringSyncValue(() -> session.search(output), query -> session.search(output, query)))
            .tooltipDynamic(lines -> lines.add(Component.translatable("gui.neoecoae.crafting_interface.preview.search."
                + (output ? "output" : "inputs") + ".tooltip").getString()));
    }

    private static final class Pattern extends ButtonWidget<Pattern> {
        private final NativePatternInterfaceSession session;
        private final int index;
        Pattern(NativePatternInterfaceSession session, int index) {
            this.session = session; this.index = index;
            background(IDrawable.EMPTY);
            hoverBackground(IDrawable.EMPTY);
            onMousePressed(button -> {
                int token = session.entry(index).getInt("token");
                if (token == 0 || button != 0 && button != 1) return false;
                session.action(Interactable.hasShiftDown() ? 2 : 1, token);
                return true;
            });
            onMouseScrolled((direction, amount) -> {
                session.action(0, session.state().getInt("offset") + (direction.modifier > 0 ? -1 : 1));
                return true;
            });
            tooltipDynamic(lines -> {
                var entry = session.entry(index);
                var stack = ItemStacks.parseOptional(RegistryAccess.EMPTY, entry.getCompound("stack"));
                if (!ItemStacks.isEmpty(stack)) lines.addFromItem(stack);
                if (entry.getInt("token") != 0) lines.add(Component.translatable(
                    "gui.neoecoae.crafting_interface.preview.put_pattern").getString());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var entry = session.entry(index);
            if (entry.getInt("token") == 0) return;
            var stack = ItemStacks.parseOptional(RegistryAccess.EMPTY, entry.getCompound("stack"));
            if (ItemStacks.isEmpty(stack)) NativeUiDraw.sprite("neoecoae:textures/guis/crafting_widgets.png",
                38, 129, 16, 16, 1, 1, 16, 16);
            else NativeUiDraw.item(stack, 1, 1, context.getCurrentDrawingZ());
            if (isHovering()) com.cleanroommc.modularui.drawable.GuiDraw.drawRect(1, 1, 16, 16, 0x80ffffff);
        }
    }

    private static final class Scrollbar extends ButtonWidget<Scrollbar> {
        private final NativePatternInterfaceSession session;
        Scrollbar(NativePatternInterfaceSession session) {
            this.session = session;
            background(NativePageLayout.texture("neoecoae:textures/guis/pattern_manager.png", 178, 31, 6, 108, 1, 1));
            onMouseScrolled((direction, amount) -> {
                session.action(0, session.state().getInt("offset") + (direction.modifier > 0 ? -1 : 1));
                return true;
            });
        }
        private void drag() {
            int max = Math.max(0, session.state().getInt("rows") - 6);
            session.action(0, Math.max(0, Math.min(max, Math.round((getContext().getMouseY() - 8.5f) * max / 91))));
        }
        @Override
        public Result onMousePressed(int button) { if (button != 0) return Result.IGNORE; drag(); return Result.SUCCESS; }
        @Override
        public void onMouseDrag(int button, long elapsed) { if (button == 0) drag(); }
        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int max = Math.max(0, session.state().getInt("rows") - 6);
            int y = max == 0 ? 1 : 1 + Math.min(max, session.state().getInt("offset")) * 91 / max;
            NativeUiDraw.sprite("neoecoae:textures/gui/vendor/ae2/sprites/big_scroller"
                + (max == 0 ? "_disabled" : "") + ".png", 0, 0, 12, 15, 1, y, 12, 15);
        }
    }
}
