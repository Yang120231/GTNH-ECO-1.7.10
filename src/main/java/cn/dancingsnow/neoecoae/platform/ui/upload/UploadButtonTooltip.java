package cn.dancingsnow.neoecoae.platform.ui.upload;

import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.BACKGROUND;
import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.BORDER;
import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.CHECK;
import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.DIVIDER;
import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.GOOD;
import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.MUTED;
import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.TEXT;
import static cn.dancingsnow.neoecoae.platform.ui.upload.UploadRowTooltip.WARN;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Card-style tooltip for the two upload buttons beside AE2's encode button: title, the mouse actions, the current
 * auto-upload target or why there is none, and a note that the right-click belongs to AE2's encode button.
 */
@SideOnly(Side.CLIENT)
final class UploadButtonTooltip {

    private static final int LEFT_KEY = 0xFF7EE29A, RIGHT_KEY = 0xFF6FD3E8;
    private static final float SMALL = 0.75F;
    private static final int PAD = 7, MIN_WIDTH = 150, MAX_WIDTH = 260;

    private static final String[] WARNING = { "....o....", "...o.o...", "...o.o...", "..o.o.o..", "..o.o.o..",
        ".o.....o.", ".o..o..o.", "ooooooooo" };

    private final FontRenderer font;
    private final String title;
    /** label, colour (as string), action, optional second line. */
    private final List<Object[]> actions = new ArrayList<>();
    /** Status lines after the divider; empty when there is nothing to say. */
    private final List<String> status = new ArrayList<>();
    private boolean statusWarning;
    private final List<String> statusDetails = new ArrayList<>();
    private final String note;

    private UploadButtonTooltip(FontRenderer font, boolean automatic) {
        this.font = font;
        title = tr(
            automatic ? "gui.neoecoae.pattern_upload.button.auto.tooltip.title"
                : "gui.neoecoae.pattern_upload.button.open.tooltip.title");
        actions.add(
            new Object[] { tr("gui.neoecoae.pattern_upload.tooltip.key.left"), LEFT_KEY,
                tr(
                    automatic ? "gui.neoecoae.pattern_upload.button.auto.tooltip.left"
                        : "gui.neoecoae.pattern_upload.button.open.tooltip.left"),
                automatic ? tr("gui.neoecoae.pattern_upload.button.auto.tooltip.left_fallback") : null });
        actions.add(
            new Object[] { tr("gui.neoecoae.pattern_upload.tooltip.key.right"), RIGHT_KEY,
                tr("gui.neoecoae.pattern_upload.button.tooltip.right"), null });
        note = tr("gui.neoecoae.pattern_upload.button.tooltip.right_note");
        if (automatic) describeTarget(PatternUploadClient.currentAutoTarget());
    }

    private void describeTarget(PatternUploadPackets.AutoTarget target) {
        if (target == null) return;
        if (target.name.isEmpty()) {
            if (target.reason.isEmpty()) return;
            statusWarning = true;
            status.add(PatternUploadScreen.text(target.reason));
            return;
        }
        PatternUploadTarget.Kind[] kinds = PatternUploadTarget.Kind.values();
        String kind = target.kind >= 0 && target.kind < kinds.length ? tr(kinds[target.kind].translationKey()) : "";
        status.add(
            StatCollector.translateToLocalFormatted(
                "gui.neoecoae.pattern_upload.button.auto.tooltip.target",
                PatternUploadScreen.hostText(target.name, target.icon) + target.nameSuffix,
                kind));
        if (target.programmingCover) statusDetails.add(tr("gui.neoecoae.pattern_upload.programming_cover"));
        else if (target.circuit != null) statusDetails.add(
            StatCollector.translateToLocalFormatted(
                "gui.neoecoae.pattern_upload.button.auto.tooltip.circuit",
                target.circuit.getItemDamage() & 0xFF));
    }

    static void draw(FontRenderer font, boolean automatic, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        new UploadButtonTooltip(font, automatic).draw(mouseX, mouseY, screenWidth, screenHeight);
    }

    private void draw(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int width = Math.min(screenWidth - 8, Math.min(MAX_WIDTH, width()));
        int height = layout(0, 0, width, false);
        int x = mouseX + 12, y = mouseY - 12;
        if (x + width > screenWidth - 4) x = Math.max(4, mouseX - 16 - width);
        if (y + height > screenHeight - 4) y = screenHeight - 4 - height;
        y = Math.max(4, y);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 400);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        UploadDraw.rect(x - 1, y - 1, x + width + 1, y + height + 1, BORDER);
        UploadDraw.rect(x, y, x + width, y + height, BACKGROUND);
        layout(x, y, width, true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private int labelColumn() {
        int column = 0;
        for (Object[] action : actions) column = Math.max(column, font.getStringWidth((String) action[0]));
        return column + 6;
    }

    private int width() {
        int needed = font.getStringWidth(title);
        int column = labelColumn();
        for (Object[] action : actions) {
            needed = Math.max(needed, column + font.getStringWidth((String) action[2]));
            if (action[3] != null) needed = Math.max(needed, column + font.getStringWidth((String) action[3]));
        }
        for (String line : status) needed = Math.max(needed, 13 + font.getStringWidth(line));
        for (String line : statusDetails) needed = Math.max(needed, 13 + smallWidth(line));
        needed = Math.max(needed, smallWidth(note));
        return Math.max(MIN_WIDTH, needed + 2 * PAD);
    }

    /** Draws when {@code paint}, and returns the height either way. */
    private int layout(int left, int top, int width, boolean paint) {
        int x = left + PAD, right = left + width - PAD, y = top + PAD;
        if (paint) text(font.trimStringToWidth(title, right - x), x, y, TEXT);
        y += 14;

        int column = x + labelColumn();
        for (Object[] action : actions) {
            for (String line : wrap((String) action[2], right - column)) {
                if (paint) {
                    if (action[0] != null) text((String) action[0], x, y, (Integer) action[1]);
                    text(line, column, y, TEXT);
                }
                action = new Object[] { null, action[1], action[2], action[3] };
                y += 10;
            }
            if (action[3] != null) for (String line : wrap((String) action[3], right - column)) {
                if (paint) text(line, column, y, MUTED);
                y += 10;
            }
            y += 2;
        }

        if (!status.isEmpty()) {
            y = divider(x, right, y + 2, paint) + 6;
            int color = statusWarning ? WARN : GOOD;
            boolean first = true;
            for (String line : status) for (String wrapped : wrap(line, right - x - 13)) {
                if (paint) {
                    if (first) UploadDraw.glyph(x, y, statusWarning ? WARNING : CHECK, 'o', color);
                    text(wrapped, x + 13, y, statusWarning ? WARN : TEXT);
                }
                first = false;
                y += 10;
            }
            for (String line : statusDetails) {
                if (paint) small(line, x + 13, y, MUTED);
                y += 8;
            }
            y += 2;
        }

        if (paint) small(trimSmall(note, right - x), x, y, MUTED);
        y += 7;
        return y + PAD - top;
    }

    private List<String> wrap(String text, int width) {
        @SuppressWarnings("unchecked")
        List<String> lines = font.listFormattedStringToWidth(text, Math.max(20, width));
        return lines.isEmpty() ? java.util.Collections.singletonList("") : lines;
    }

    private int divider(int x, int right, int y, boolean paint) {
        if (paint) UploadDraw.rect(x, y, right, y + 1, DIVIDER);
        return y + 1;
    }

    private void text(String text, int x, int y, int color) {
        font.drawString(text, x, y, color, false);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void small(String text, float x, float y, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(SMALL, SMALL, 1);
        font.drawString(text, 0, 0, color, false);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private int smallWidth(String text) {
        return (int) Math.ceil(font.getStringWidth(text) * SMALL);
    }

    private String trimSmall(String text, int width) {
        return font.trimStringToWidth(text, Math.max(0, (int) (width / SMALL)));
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
