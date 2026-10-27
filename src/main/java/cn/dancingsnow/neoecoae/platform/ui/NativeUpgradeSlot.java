package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** AE2's upgrade placeholder is visible only while the real native slot is empty. */
public final class NativeUpgradeSlot extends NativeItemSlot {

    @Override
    @SideOnly(Side.CLIENT)
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (isSynced() && !getSlot().getHasStack())
            NativeUiDraw.sprite(NativeHostSideBar.ATLAS, 240, 208, 16, 16, 1, 1, 16, 16);
        super.drawOverlay(context, theme);
    }
}
