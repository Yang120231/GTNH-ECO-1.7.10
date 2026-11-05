package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.GuiContext;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Uses the same status card renderer from a native ModularUI widget's tooltip pass. */
public final class NativeStatusCardTooltip extends RichTooltip {

    private final Supplier<List<ProcessingTooltip.Row>> rows;

    public NativeStatusCardTooltip(Supplier<List<ProcessingTooltip.Row>> rows) {
        this.rows = rows;
    }

    @Override
    public boolean isEmpty() {
        // Evaluate the supplier only while drawing on the client, never during server widget construction.
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiContext context, ItemStack stack) {
        ProcessingTooltip.draw(
            Minecraft.getMinecraft().fontRenderer,
            rows.get(),
            context.getAbsMouseX(),
            context.getAbsMouseY(),
            context.getScreenArea().width,
            context.getScreenArea().height);
    }
}
