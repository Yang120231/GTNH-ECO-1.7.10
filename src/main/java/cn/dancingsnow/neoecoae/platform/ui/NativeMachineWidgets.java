package cn.dancingsnow.neoecoae.platform.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

/** Small native drawing primitives shared by the source-defined machine pages. */
public final class NativeMachineWidgets {

    private NativeMachineWidgets() {}

    public static Widget<?> item(Supplier<ItemStack> stack) {
        return new ItemDisplayWidget().item(new ObjectValue.Dynamic<>(ItemStack.class, stack, ignored -> {}))
            .displayAmount(false)
            .tooltipDynamic(tooltip -> {
                ItemStack current = stack.get();
                if (!ItemStacks.isEmpty(current)) tooltip.addFromItem(current);
            })
            .onUpdateListener(widget -> widget.markTooltipDirty());
    }

    /** A source 15x26 switch: the atlas row holds on, on-hovered, off and off-hovered crops left to right. */
    public static Widget<?> toggle(String atlas, int y, BooleanSupplier value, Runnable action,
        Supplier<Component> tooltip) {
        return new ToggleButton().value(new BoolValue.Dynamic(value, ignored -> action.run()))
            .background(true, NativePageLayout.texture(atlas, 0, y, 15, 26))
            .hoverBackground(true, NativePageLayout.texture(atlas, 15, y, 15, 26))
            .background(false, NativePageLayout.texture(atlas, 30, y, 15, 26))
            .hoverBackground(false, NativePageLayout.texture(atlas, 45, y, 15, 26))
            .tooltipDynamic(lines -> {
                String[] parts = tooltip.get()
                    .getString()
                    .split("\n");
                for (int index = 0; index < parts.length; index++) {
                    if (index > 0) lines.newLine();
                    lines.add(parts[index]);
                }
            })
            .onUpdateListener(widget -> widget.markTooltipDirty());
    }
}
