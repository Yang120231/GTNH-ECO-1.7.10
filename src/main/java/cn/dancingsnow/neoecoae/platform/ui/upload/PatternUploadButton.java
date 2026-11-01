package cn.dancingsnow.neoecoae.platform.ui.upload;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Native AE2 terminal button icons, as in 7.3.0-beta1, with ECO's upload tooltips and current auto-upload target. */
@SideOnly(Side.CLIENT)
final class PatternUploadButton extends GuiImgButton {

    final boolean automatic;

    PatternUploadButton(int x, int y, boolean automatic) {
        super(x, y, Settings.ACTIONS, automatic ? ActionItems.MOLECULAR_ASSEMBLEERS_ON : ActionItems.ENCODE);
        this.automatic = automatic;
    }

    /** AE2 draws no text tooltip; {@link PatternUploadClient} draws {@link UploadButtonTooltip} instead. */
    @Override
    public String getMessage() {
        return null;
    }

    boolean hovered(int mouseX, int mouseY) {
        return visible && mouseX >= xPosition
            && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
    }
}
