package cn.dancingsnow.neoecoae.platform.ui.upload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Card-style tooltip for one upload target: name and mode, match status, upload capacity, location and the mouse
 * actions. The screen fills in the already translated texts; this class only lays them out.
 */
@SideOnly(Side.CLIENT)
final class UploadRowTooltip {

    static final int CARD = 0xFF261C36, DIVIDER = 0x30FFFFFF;
    static final int TEXT = 0xFFF0ECF8, MUTED = 0xFFA79FB8, MODE = 0xFFB89CE6, BADGE = 0xFF3D2F57;
    static final int GOOD = 0xFF6EDB8F, WARN = 0xFFE6B84A, BAD = 0xFFE36A6A;
    private static final float SMALL = 0.75F;
    private static final int PAD = 7, MIN_WIDTH = 176, CARD_HEIGHT = 27;

    static final String[] CHECK = { "..ooooo..", ".o.....o.", "o.......o", "o.....o.o", "o.o..o..o", "o..oo...o",
        "o.......o", ".o.....o.", "..ooooo.." };
    private static final String[] ALERT = { "..ooooo..", ".o.....o.", "o...o...o", "o...o...o", "o...o...o",
        "o.......o", "o...o...o", ".o.....o.", "..ooooo.." };
    private static final String[] PIN = { ".ooo.", "o...o", "o.o.o", "o...o", ".o.o.", "..o.." };

    // ---- content, filled by the screen ------------------------------------------------------------------------

    String title = "", mode = "", subtitle = "";
    boolean star;
    String level = "", state = "";
    int levelColor = GOOD;
    boolean stateGood;
    int free, members, duplicates, emptySlots, totalSlots;
    final List<String> notes = new ArrayList<>();
    String location = "";
    boolean uploadable;
    boolean mappingAction;
    String disabledReason = "";

    private FontRenderer font;

    // ---- layout -----------------------------------------------------------------------------------------------

    void draw(FontRenderer font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.font = font;
        int width = Math.min(screenWidth - 8, width());
        int height = layout(0, 0, width, false);
        int x = mouseX + 12, y = mouseY - 12;
        if (x + width > screenWidth - 4) x = Math.max(4, mouseX - 16 - width);
        if (y + height > screenHeight - 4) y = screenHeight - 4 - height;
        y = Math.max(4, y);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 400);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        UploadDraw.vanillaTooltipFrame(x - 1, y - 1, x + width + 1, y + height + 1);
        layout(x, y, width, true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private int width() {
        int titleWidth = font.getStringWidth((star ? "★ " : "") + title + (mode.isEmpty() ? "" : " · " + mode));
        int cards = 2 * Math.max(
            smallWidth(tr("gui.neoecoae.pattern_upload.tooltip.can_upload")) + 12,
            Math.max(smallWidth(tr("gui.neoecoae.pattern_upload.tooltip.existing")) + 12, 60)) + 4;
        int hints = 0;
        for (String[] hint : hints())
            hints = Math.max(hints, smallWidth(hint[0]) + 6 + 10 + font.getStringWidth(hint[1]));
        int status = 12 + font.getStringWidth(level + " · " + state);
        int needed = Math.max(Math.max(titleWidth, cards), Math.max(hints, status));
        needed = Math.max(needed, smallWidth(subtitle));
        needed = Math.max(needed, 8 + smallWidth(location));
        return Math.max(MIN_WIDTH, needed + 2 * PAD);
    }

    /** Draws when {@code paint}, and returns the height either way. */
    private int layout(int left, int top, int width, boolean paint) {
        int x = left + PAD, right = left + width - PAD, y = top + PAD;

        // Title: machine · mode, then host kind.
        if (paint) {
            int cursor = x;
            if (star) cursor = text("★ ", cursor, y, WARN);
            String main = title, rest = mode.isEmpty() ? "" : " · " + mode;
            int room = right - cursor - font.getStringWidth(rest);
            if (font.getStringWidth(main) > room) main = font.trimStringToWidth(main, Math.max(0, room - 6)) + "…";
            cursor = text(main, cursor, y, TEXT);
            if (!mode.isEmpty()) {
                cursor = text(" · ", cursor, y, MUTED);
                text(font.trimStringToWidth(mode, Math.max(0, right - cursor)), cursor, y, MODE);
            }
        }
        y += 11;
        if (!subtitle.isEmpty()) {
            if (paint) small(trimSmall(subtitle, right - x), x, y, MUTED);
            y += 8;
        }
        y = divider(x, right, y + 3, paint) + 4;

        // Match status · slot state.
        if (paint) {
            UploadDraw.glyph(x, y - 1, levelColor == GOOD ? CHECK : ALERT, 'o', levelColor);
            int cursor = text(level, x + 12, y, levelColor);
            cursor = text(" · ", cursor, y, MUTED);
            text(font.trimStringToWidth(state, Math.max(0, right - cursor)), cursor, y, stateGood ? TEXT : MUTED);
        }
        y += 13;

        // Two stat cards.
        int cardWidth = (right - x - 4) / 2;
        if (paint) {
            card(
                x,
                y,
                cardWidth,
                tr("gui.neoecoae.pattern_upload.tooltip.can_upload"),
                String.valueOf(free),
                " / " + members,
                free > 0 ? GOOD : MUTED);
            card(
                x + cardWidth + 4,
                y,
                right - x - cardWidth - 4,
                tr("gui.neoecoae.pattern_upload.tooltip.existing"),
                String.valueOf(duplicates),
                "",
                duplicates > 0 ? WARN : TEXT);
        }
        y += CARD_HEIGHT + 5;

        // Free slots row.
        if (paint) {
            text(tr("gui.neoecoae.pattern_upload.tooltip.free_slots"), x, y, TEXT);
            String value = totalSlots > 0 ? emptySlots + " / " + totalSlots : "-";
            text(value, right - font.getStringWidth(value), y, TEXT);
        }
        y += 11;

        // Optional notes (mismatch reason, circuit, programming cover, auto choice).
        for (String note : notes) {
            if (paint) small(trimSmall(note, right - x), x, y, MUTED);
            y += 8;
        }

        // Location.
        y = divider(x, right, y + 2, paint) + 4;
        if (paint) {
            UploadDraw.glyph(x, y, PIN, 'o', MUTED);
            small(trimSmall(location, right - x - 9), x + 9, y + 1, MUTED);
        }
        y += 9;

        // Mouse actions.
        y = divider(x, right, y + 3, paint) + 4;
        if (!uploadable && !disabledReason.isEmpty()) {
            if (paint) small(trimSmall(disabledReason, right - x), x, y, BAD);
            y += 10;
        }
        for (String[] hint : hints()) {
            if (paint) {
                int badgeWidth = smallWidth(hint[0]) + 6;
                UploadDraw.rect(x, y, x + badgeWidth, y + 10, BADGE);
                small(hint[0], x + 3, y + 2, TEXT);
                String action = font.trimStringToWidth(hint[1], Math.max(0, right - x - badgeWidth - 6));
                text(action, right - font.getStringWidth(action), y + 1, TEXT);
            }
            y += 12;
        }
        return y - 2 + PAD - top;
    }

    private List<String[]> hints() {
        List<String[]> hints = new ArrayList<>();
        if (mappingAction) {
            hints.add(
                new String[] { tr("gui.neoecoae.pattern_upload.tooltip.key.left"),
                    tr("gui.neoecoae.pattern_upload.mapping.save_action") });
        } else if (uploadable) {
            hints.add(
                new String[] { tr("gui.neoecoae.pattern_upload.tooltip.key.left"),
                    tr("gui.neoecoae.pattern_upload.tooltip.action.return") });
            hints.add(
                new String[] { tr("gui.neoecoae.pattern_upload.tooltip.key.shift_left"),
                    tr("gui.neoecoae.pattern_upload.tooltip.action.stay") });
        }
        hints.add(
            new String[] { tr("gui.neoecoae.pattern_upload.tooltip.key.right"),
                tr("gui.neoecoae.pattern_upload.tooltip.action.locate") });
        return hints;
    }

    private void card(int x, int y, int width, String label, String value, String suffix, int valueColor) {
        UploadDraw.rect(x, y, x + width, y + CARD_HEIGHT, CARD);
        small(trimSmall(label, width - 8), x + 5, y + 4, MUTED);
        GL11.glPushMatrix();
        GL11.glTranslatef(x + 5, y + 13, 0);
        GL11.glScalef(1.25F, 1.25F, 1);
        font.drawString(value, 0, 0, valueColor, false);
        GL11.glPopMatrix();
        if (!suffix.isEmpty()) small(suffix, x + 5 + font.getStringWidth(value) * 1.25F + 1, y + 17, MUTED);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private int divider(int x, int right, int y, boolean paint) {
        if (paint) UploadDraw.rect(x, y, right, y + 1, DIVIDER);
        return y + 1;
    }

    private int text(String text, int x, int y, int color) {
        font.drawString(text, x, y, color, false);
        GL11.glColor4f(1, 1, 1, 1);
        return x + font.getStringWidth(text);
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
