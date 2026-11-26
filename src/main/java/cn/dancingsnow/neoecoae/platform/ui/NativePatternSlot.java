package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;

import appeng.items.misc.ItemEncodedPattern;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pattern slot with AE2's empty watermark and primary-output item preview. */
public class NativePatternSlot extends NativeItemSlot {

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        super.draw(context, theme);
        if (!getSlot().getHasStack()) {
            NativePageLayout.texture(NativeHostSideBar.ATLAS, 240, 128, 16, 16)
                .draw(1, 1, 16, 16);
        }
    }

    /** Encoded patterns show their primary output, like AE2's own pattern slots with Shift held. */
    @Override
    @SideOnly(Side.CLIENT)
    protected ItemStack getItemStackForRendering(ItemStack stack, boolean dragging) {
        if (stack != null && stack.getItem() instanceof ItemEncodedPattern pattern) {
            ItemStack output = pattern.getOutput(stack);
            if (output != null) return output;
        }
        return super.getItemStackForRendering(stack, dragging);
    }
}
