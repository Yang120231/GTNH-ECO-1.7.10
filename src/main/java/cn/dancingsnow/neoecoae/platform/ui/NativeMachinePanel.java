package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.screen.ModularPanel;

/** Keep source pixel coordinates while fitting the complete page into small legacy game windows. */
public final class NativeMachinePanel extends ModularPanel {
    private NativeMachinePanel(String name) { super(name); }

    public static ModularPanel create(String name, int width, int height) {
        return new NativeMachinePanel(name).size(width, height);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public float getScale() {
        float animation = super.getScale();
        if (getScreen() == null || getArea().width <= 0 || getArea().height <= 0) return animation;
        var screen = getScreen().getScreenArea();
        if (getArea().height + 4 <= screen.height && getArea().width + 4 <= screen.width) return animation;
        return animation * Math.min(1f, Math.min((screen.width - 8f) / getArea().width,
            (screen.height - 8f) / (getArea().height + 10f)));
    }
}
