package cn.dancingsnow.neoecoae.platform.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Small native drawing primitives shared by the source-defined machine pages. */
public final class NativeMachineWidgets {

    private NativeMachineWidgets() {}

    public static ButtonWidget<?> button(Supplier<Component> label, Runnable action) {
        return new ButtonWidget<>().onMousePressed(mouse -> {
            if (mouse != 0) return false;
            action.run();
            return true;
        })
            .child(new NativeText(label).sizeRel(1, 1));
    }

    public static Widget<?> item(Supplier<ItemStack> stack) {
        return new Item(stack);
    }

    private static final class Item extends Widget<Item> {

        private final Supplier<ItemStack> stack;

        private Item(Supplier<ItemStack> stack) {
            this.stack = stack;
            tooltipDynamic(tooltip -> {
                ItemStack current = stack.get();
                if (!ItemStacks.isEmpty(current)) tooltip.addFromItem(current);
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            ItemStack current = stack.get();
            if (!ItemStacks.isEmpty(current)) GuiDraw.drawItem(current, 0, 0, 16, 16, context.getCurrentDrawingZ());
        }
    }

    public static Widget<?> toggle(String atlas, int y, BooleanSupplier value, Runnable action,
        Supplier<Component> tooltip) {
        return new Toggle(atlas, y, value, action, tooltip);
    }

    private static final class Toggle extends ButtonWidget<Toggle> {

        private final String atlas;
        private final int y;
        private final BooleanSupplier value;

        private Toggle(String atlas, int y, BooleanSupplier value, Runnable action, Supplier<Component> tooltip) {
            this.atlas = atlas;
            this.y = y;
            this.value = value;
            onMousePressed(mouse -> {
                if (mouse != 0) return false;
                action.run();
                return true;
            });
            tooltipDynamic(lines -> {
                String[] parts = tooltip.get()
                    .getString()
                    .split("\n");
                for (int index = 0; index < parts.length; index++) {
                    if (index > 0) lines.newLine();
                    lines.add(parts[index]);
                }
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            NativePageLayout.texture(atlas, (value.getAsBoolean() ? 0 : 30) + (isHovering() ? 15 : 0), y, 15, 26)
                .draw(0, 0, getArea().width, getArea().height);
        }
    }
}
