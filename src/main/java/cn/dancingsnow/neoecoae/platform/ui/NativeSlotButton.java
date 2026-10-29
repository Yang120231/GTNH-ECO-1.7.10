package cn.dancingsnow.neoecoae.platform.ui;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Display and filter slots share the real slots' outline without joining the container's slot map. */
@SuppressWarnings("unchecked")
public class NativeSlotButton<W extends NativeSlotButton<W>> extends ButtonWidget<W> {

    private int inset = 1;
    private BooleanSupplier highlight = () -> true;

    public NativeSlotButton() {
        background(IDrawable.EMPTY);
        hoverBackground(IDrawable.EMPTY);
    }

    public W slotInset(int inset) {
        this.inset = inset;
        return (W) this;
    }

    public W highlightWhen(BooleanSupplier highlight) {
        this.highlight = highlight;
        return (W) this;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawForeground(ModularGuiContext context) {
        // An item display child owns the hover while the enclosing slot still owns its outline.
        if ((isHovering() || getChildren().stream()
            .anyMatch(widget -> widget.isHovering())) && highlight.getAsBoolean())
            NativeSlotHighlight.draw(inset, inset, getArea().width - 2 * inset, getArea().height - 2 * inset);
        super.drawForeground(context);
    }
}
