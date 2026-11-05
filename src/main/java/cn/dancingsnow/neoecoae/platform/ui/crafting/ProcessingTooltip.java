package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.21.1's crafting list cards in the vanilla tooltip frame. Machine times remain in ticks.
 */
@SideOnly(Side.CLIENT)
public final class ProcessingTooltip {

    private static final String KEY = "gui.neoecoae.crafting_status.processing.";
    private static final int GAP = 12;
    private static final int LINE_HEIGHT = 11;
    public static final int PRIMARY = 0xFFF2EEF8, MUTED = 0xFF9C94AC, ACCENT = 0xFFC9B8FF;
    public static final int OK = 0xFF8EE6A0, CYAN = 0xFF5BB8D0, WARN = 0xFFF0A848;
    private static final float SCALE = .75F;

    private ProcessingTooltip() {}

    enum Kind {
        TEXT,
        SEPARATOR,
        PROGRESS,
        HEADER,
        ICON_HEADER,
        SECTION,
        LARGE,
        HINT
    }

    public static final class Row {

        final Kind kind;
        final String left;
        final String right;
        final float progress;
        int color = PRIMARY, rightColor = PRIMARY;
        IAEStack<?> icon;
        String[] glyph;

        private Row(Kind kind, String left, String right, float progress) {
            this.kind = kind;
            this.left = left;
            this.right = right;
            this.progress = progress;
        }

        public static Row text(String left) {
            return new Row(Kind.TEXT, left, "", 0);
        }

        public static Row text(String left, String right) {
            return new Row(Kind.TEXT, left, right, 0);
        }

        public static Row separator() {
            return new Row(Kind.SEPARATOR, "", "", 0);
        }

        public static Row progress(float value) {
            return progress(value, 0xFF60CD64, 0xFF1F4A24);
        }

        public static Row progress(float value, int fill, int track) {
            Row row = new Row(Kind.PROGRESS, "", "", value);
            row.color = fill;
            row.rightColor = track;
            return row;
        }

        public static Row text(String left, String right, int rightColor) {
            Row row = text(left, right);
            row.rightColor = rightColor;
            return row;
        }

        public static Row header(String title, String status, int color) {
            Row row = new Row(Kind.HEADER, EnumChatFormatting.BOLD + title, status.toUpperCase(Locale.ROOT), 0);
            row.color = ACCENT;
            row.rightColor = color;
            row.glyph = new String[] { ".#.#.#.", "#######", "##...##", "##.#.##", "##...##", "#######", ".#.#.#." };
            return row;
        }

        public static Row iconHeader(IAEStack<?> icon, String title, String subtitle) {
            Row row = new Row(Kind.ICON_HEADER, title, subtitle, 0);
            row.icon = icon;
            return row;
        }

        public static Row section(String label, String state, int color) {
            Row row = new Row(Kind.SECTION, label, state.toUpperCase(Locale.ROOT), 0);
            row.color = MUTED;
            row.rightColor = color;
            return row;
        }

        public static Row value(String label, String value, int color) {
            Row row = new Row(Kind.LARGE, label, value, 0);
            row.rightColor = color;
            return row;
        }

        public static Row hint(String text) {
            Row row = new Row(Kind.HINT, text, "", 0);
            row.color = MUTED;
            row.glyph = new String[] { ".#.....", ".##....", ".###...", ".####..", ".#####.", ".##....", "...#..." };
            return row;
        }
    }

    /** Machine rows of an output being crafted, or the reason no machine cycle can be shown yet. */
    public static List<Row> rows(boolean active, boolean pending, boolean monitored, ECOProcessingProgress progress) {
        if (!active) return pending && monitored
            ? Collections.singletonList(Row.text(EnumChatFormatting.GRAY + I18n.format(KEY + "awaiting_dispatch")))
            : Collections.emptyList();
        List<Row> rows = machines(progress);
        if (rows.isEmpty() && monitored)
            rows.add(Row.text(EnumChatFormatting.GRAY + I18n.format(KEY + "progress_unavailable")));
        return rows;
    }

    private static List<Row> machines(ECOProcessingProgress progress) {
        List<Row> rows = new ArrayList<>();
        if (progress.machineCount() == 0 && !progress.refreshing()) return rows;
        if (progress.machineCount() > 0) rows.add(Row.text(I18n.format(KEY + "count", progress.machineCount())));
        if (progress.refreshing()) rows.add(Row.text(EnumChatFormatting.GRAY + I18n.format(KEY + "refreshing")));
        if (progress.machineCount() > 0 && progress.machines()
            .isEmpty()) rows.add(Row.text(EnumChatFormatting.GRAY + I18n.format(KEY + "loading_details")));
        int shown = Math.min(
            ECOProcessingProgress.DETAIL_PAGE_SIZE,
            progress.machines()
                .size());
        for (int i = 0; i < shown; i++) {
            var machine = progress.machines()
                .get(i);
            rows.add(Row.separator());
            EnumChatFormatting color = machine.state() == State.WAITING ? EnumChatFormatting.GOLD
                : machine.state() == State.RUNNING ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY;
            rows.add(
                Row.text(
                    machine.name()
                        .getString(),
                    color + I18n.format(
                        KEY + "state."
                            + machine.state()
                                .name()
                                .toLowerCase(Locale.ROOT))));
            String reason = machine.reason()
                .getString();
            if (!reason.isEmpty()) rows.add(Row.text(EnumChatFormatting.GOLD + I18n.format(KEY + "reason", reason)));
            String parallel = machine.parallel() > 0 ? I18n.format(KEY + "parallel", machine.parallel()) : "";
            String cycle = machine.durationTicks() > 0
                ? I18n.format(KEY + "cycle", (int) (100L * machine.progressTicks() / machine.durationTicks()))
                : "";
            if (!parallel.isEmpty() || !cycle.isEmpty()) rows.add(Row.text(parallel, cycle));
            if (machine.durationTicks() > 0) {
                rows.add(Row.progress((float) machine.progressTicks() / machine.durationTicks()));
                rows.add(
                    Row.text(
                        I18n.format(KEY + "time", machine.progressTicks(), machine.durationTicks()),
                        machine.state() == State.RUNNING
                            ? I18n.format(KEY + "remaining", machine.durationTicks() - machine.progressTicks())
                            : ""));
            }
            if (!machine.recipe().isEmpty()) rows.add(Row.text(EnumChatFormatting.GRAY + machine.recipe()));
            rows.add(Row.text(EnumChatFormatting.GRAY + location(machine.location())));
        }
        if (progress.pages() > 1) {
            rows.add(Row.separator());
            rows.add(
                Row.text(
                    EnumChatFormatting.GRAY
                        + I18n.format(KEY + "page", Math.max(0, progress.detailPage()) + 1, progress.pages())));
        }
        return rows;
    }

    /** "dim [x, y, z]" as the dimension's name and its coordinates. */
    private static String location(String location) {
        int coordinates = location.indexOf(" [");
        if (coordinates < 0) return location;
        String dimension = location.substring(0, coordinates);
        String label;
        switch (dimension) {
            case "0":
                label = I18n.format(KEY + "dimension.overworld");
                break;
            case "-1":
                label = I18n.format(KEY + "dimension.nether");
                break;
            case "1":
                label = I18n.format(KEY + "dimension.end");
                break;
            default:
                label = I18n.format(KEY + "dimension.other", dimension);
        }
        return label + "  (" + location.substring(coordinates + 2, location.length() - 1) + ")";
    }

    /** All card content uses the same .75 scale, palette and spacing as CraftingListCard in 1.21.1. */
    public static void draw(FontRenderer font, List<Row> rows, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (rows.isEmpty()) return;
        int width = 150;
        for (Row row : rows) {
            int left = font.getStringWidth(row.left), right = font.getStringWidth(row.right);
            int needed = left + (right == 0 ? 0 : GAP + right);
            if (row.kind == Kind.LARGE) needed = left + GAP + (int) Math.ceil(right * 1.5F);
            if (row.kind == Kind.HEADER || row.kind == Kind.HINT) needed += 12;
            if (row.kind == Kind.ICON_HEADER) needed = 25 + Math.max(left, right);
            width = Math.max(width, needed);
        }
        width = Math.min(width, Math.max(1, (int) ((screenWidth - 16) / SCALE)));
        List<List<String>> lefts = new ArrayList<>();
        int[] heights = new int[rows.size()];
        int height = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int indent = row.kind == Kind.HEADER || row.kind == Kind.HINT ? 12 : 0;
            int right = font.getStringWidth(row.right);
            int room = Math.max(1, width - indent - (right == 0 ? 0 : GAP + right));
            List<String> lines = row.kind == Kind.TEXT || row.kind == Kind.HINT
                ? split(font, row.left, Math.min(220, room)) : Collections.singletonList(row.left);
            lefts.add(lines);
            heights[i] = switch (row.kind) {
                case SEPARATOR -> 9;
                case PROGRESS -> 7;
                case ICON_HEADER -> 22;
                case LARGE -> 15;
                case HEADER -> 10;
                default -> Math.max(1, lines.size()) * LINE_HEIGHT;
            };
            height += heights[i];
        }
        int cardWidth = (int) Math.ceil(width * SCALE);
        int cardHeight = (int) Math.ceil(Math.max(0, height - 2) * SCALE);
        float scale = SCALE;
        // Keep a complete card on small GUI viewports as well as on a stretched status page.
        if (cardHeight > screenHeight - 12) {
            scale *= (screenHeight - 12F) / cardHeight;
            cardHeight = (int) Math.ceil((height - 2) * scale);
            cardWidth = (int) Math.ceil(width * scale);
        }
        int x = mouseX + 12, y = mouseY - 12;
        if (x + cardWidth + 4 > screenWidth) x = Math.max(4, mouseX - 16 - cardWidth);
        y = Math.max(4, Math.min(y, screenHeight - cardHeight - 6));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 400);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        int background = 0xF0100010, borderTop = 0x505000FF, borderBottom = 0x5028007F;
        gradient(x - 3, y - 4, x + cardWidth + 3, y - 3, background, background);
        gradient(x - 3, y + cardHeight + 3, x + cardWidth + 3, y + cardHeight + 4, background, background);
        gradient(x - 3, y - 3, x + cardWidth + 3, y + cardHeight + 3, background, background);
        gradient(x - 4, y - 3, x - 3, y + cardHeight + 3, background, background);
        gradient(x + cardWidth + 3, y - 3, x + cardWidth + 4, y + cardHeight + 3, background, background);
        gradient(x - 3, y - 2, x - 2, y + cardHeight + 2, borderTop, borderBottom);
        gradient(x + cardWidth + 2, y - 2, x + cardWidth + 3, y + cardHeight + 2, borderTop, borderBottom);
        gradient(x - 3, y - 3, x + cardWidth + 3, y - 2, borderTop, borderTop);
        gradient(x - 3, y + cardHeight + 2, x + cardWidth + 3, y + cardHeight + 3, borderBottom, borderBottom);
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(scale, scale, 1);
        int top = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            switch (row.kind) {
                case SEPARATOR -> gradient(0, top + 3, width, top + 4, 0xFF37303F, 0xFF37303F);
                case PROGRESS -> {
                    gradient(0, top + 1, width, top + 4, row.rightColor, row.rightColor);
                    int filled = Math.round(width * Math.max(0, Math.min(1, row.progress)));
                    if (filled > 0) gradient(0, top + 1, filled, top + 4, row.color, row.color);
                }
                case ICON_HEADER -> {
                    gradient(0, top, 20, top + 20, 0xFF2E2640, 0xFF2E2640);
                    if (row.icon != null) {
                        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
                        RenderHelper.enableGUIStandardItemLighting();
                        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                        row.icon.drawInGui(net.minecraft.client.Minecraft.getMinecraft(), 2, top + 2);
                        GL11.glPopAttrib();
                    }
                    font.drawStringWithShadow(row.left, 25, top + (row.right.isEmpty() ? 6 : 1), PRIMARY);
                    if (!row.right.isEmpty()) font.drawStringWithShadow(row.right, 25, top + 11, MUTED);
                }
                case LARGE -> {
                    font.drawStringWithShadow(row.left, 0, top + 3, PRIMARY);
                    GL11.glPushMatrix();
                    GL11.glTranslatef(width - (int) Math.ceil(font.getStringWidth(row.right) * 1.5F), top, 0);
                    GL11.glScalef(1.5F, 1.5F, 1);
                    font.drawStringWithShadow(row.right, 0, 0, row.rightColor);
                    GL11.glPopMatrix();
                }
                default -> {
                    int indent = row.glyph == null ? 0 : 12;
                    if (row.glyph != null) glyph(row.glyph, 0, top, row.color);
                    List<String> lines = lefts.get(i);
                    for (int line = 0; line < lines.size(); line++)
                        font.drawStringWithShadow(lines.get(line), indent, top + line * LINE_HEIGHT, row.color);
                    font.drawStringWithShadow(row.right, width - font.getStringWidth(row.right), top, row.rightColor);
                }
            }
            top += heights[i];
        }
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static void glyph(String[] pixels, int x, int y, int color) {
        for (int row = 0; row < pixels.length; row++) for (int col = 0; col < pixels[row].length(); col++)
            if (pixels[row].charAt(col) == '#') gradient(x + col, y + row, x + col + 1, y + row + 1, color, color);
    }

    @SuppressWarnings("unchecked")
    private static List<String> split(FontRenderer font, String text, int width) {
        if (text.isEmpty()) return Collections.singletonList("");
        return font.listFormattedStringToWidth(text, width);
    }

    /** Gui.drawGradientRect without its zLevel; depth testing is off while the tooltip draws. */
    private static void gradient(int left, int top, int right, int bottom, int from, int to) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(from & 0xFFFFFF, from >>> 24);
        tessellator.addVertex(right, top, 300);
        tessellator.addVertex(left, top, 300);
        tessellator.setColorRGBA_I(to & 0xFFFFFF, to >>> 24);
        tessellator.addVertex(left, bottom, 300);
        tessellator.addVertex(right, bottom, 300);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
