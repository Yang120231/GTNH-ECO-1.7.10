package cn.dancingsnow.neoecoae.platform.ui.upload;

import net.minecraft.util.StatCollector;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** AE2-styled terminal button; the auto button also shows the server's current auto-upload decision. */
@SideOnly(Side.CLIENT)
final class PatternUploadButton extends GuiImgButton {

    final boolean automatic;

    PatternUploadButton(int x, int y, boolean automatic) {
        super(x, y, Settings.ACTIONS, automatic ? ActionItems.MOLECULAR_ASSEMBLEERS_ON : ActionItems.ENCODE);
        this.automatic = automatic;
    }

    @Override
    public String getMessage() {
        String prefix = "gui.neoecoae.pattern_upload.button." + (automatic ? "auto" : "open") + ".tooltip.";
        StringBuilder message = new StringBuilder(StatCollector.translateToLocal(prefix + "title")).append('\n')
            .append(StatCollector.translateToLocal(prefix + "description"));
        if (!automatic) return message.toString();
        PatternUploadPackets.AutoTarget target = PatternUploadClient.currentAutoTarget();
        if (target == null) return message.toString();
        if (target.name.isEmpty()) {
            if (!target.reason.isEmpty()) message.append('\n')
                .append("§7")
                .append(PatternUploadPage.text(target.reason));
            return message.toString();
        }
        PatternUploadTarget.Kind[] kinds = PatternUploadTarget.Kind.values();
        String kind = target.kind >= 0 && target.kind < kinds.length
            ? StatCollector.translateToLocal(kinds[target.kind].translationKey())
            : "";
        message.append('\n')
            .append(
                StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.pattern_upload.button.auto.tooltip.target",
                    PatternUploadPage.text(target.name),
                    kind));
        if (target.programmingCover) message.append('\n')
            .append(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.programming_cover"));
        else if (target.circuit != null) message.append('\n')
            .append(
                StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.pattern_upload.button.auto.tooltip.circuit",
                    target.circuit.getItemDamage() & 0xFF));
        return message.toString();
    }
}
