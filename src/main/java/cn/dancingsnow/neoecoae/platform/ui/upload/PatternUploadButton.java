package cn.dancingsnow.neoecoae.platform.ui.upload;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.texture.ExtraBlockTextures;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * AE2-styled 16x16 terminal button with its own glyph, so it cannot be mistaken for AE2's encode button: an up arrow
 * onto a tray for "upload to...", the same arrow with a lightning bolt for automatic upload. The auto button also
 * shows the server's current auto-upload decision.
 */
@SideOnly(Side.CLIENT)
final class PatternUploadButton extends GuiImgButton {

    private static final String[] UPLOAD = { "................", "................", ".......oo.......",
        "......obbo......", ".....obbbbo.....", "....obbbbbbo....", "...obbbbbbbbo...", "...oooobboooo...",
        "......obbo......", "......obbo......", "......obbo......", "......oooo......", "................",
        "..oo........oo..", "..oooooooooooo..", "................" };
    private static final String[] AUTO = { "................", "............ooo.", ".....oo....oyyo.",
        "....obbo...oyo..", "...obbbbo.oyyyo.", "..obbbbbbooyyo..", ".obbbbbbbbooyo..", ".oooobboooooo...",
        "....obbo........", "....obbo........", "....obbo........", "....oooo........", "................",
        "oo........oo....", "oooooooooooo....", "................" };

    final boolean automatic;

    PatternUploadButton(int x, int y, boolean automatic) {
        super(x, y, Settings.ACTIONS, automatic ? ActionItems.MOLECULAR_ASSEMBLEERS_ON : ActionItems.ENCODE);
        this.automatic = automatic;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!visible) return;
        field_146123_n = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        float shade = enabled ? 1F : 0.5F;
        GL11.glColor4f(shade, shade, shade, 1F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        minecraft.renderEngine.bindTexture(ExtraBlockTextures.GuiTexture("guis/states.png"));
        // AE2's own button background (GuiImgButton draws the same 16x16 cell before its icon).
        drawTexturedModalRect(xPosition, yPosition, 240, 240, 16, 16);
        GL11.glColor4f(1, 1, 1, 1);
        int outline = field_146123_n ? 0xFF101820 : 0xFF2A3140;
        if (automatic) UploadDraw.glyph(xPosition, yPosition, AUTO, 'o', outline, 'b', 0xFF4CAF50, 'y', 0xFFFFC107);
        else UploadDraw.glyph(xPosition, yPosition, UPLOAD, 'o', outline, 'b', 0xFF4A90E2);
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public String getMessage() {
        String prefix = "gui.neoecoae.pattern_upload.button." + (automatic ? "auto" : "open") + ".tooltip.";
        StringBuilder message = new StringBuilder(StatCollector.translateToLocal(prefix + "title")).append('\n')
            .append(StatCollector.translateToLocal(prefix + "description"))
            .append('\n')
            .append("§7")
            .append(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.button.tooltip.encode_right_click"));
        if (!automatic) return message.toString();
        PatternUploadPackets.AutoTarget target = PatternUploadClient.currentAutoTarget();
        if (target == null) return message.toString();
        if (target.name.isEmpty()) {
            if (!target.reason.isEmpty()) message.append('\n')
                .append("§7")
                .append(PatternUploadScreen.text(target.reason));
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
                    PatternUploadScreen.text(target.name),
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
