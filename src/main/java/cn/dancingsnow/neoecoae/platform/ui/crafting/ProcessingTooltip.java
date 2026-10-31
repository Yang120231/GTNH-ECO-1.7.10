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

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.20.1's processing tooltip (ComputationProcessingTooltips) for the vanilla 1.7.10 tooltip: text rows with an
 * optional right-aligned column, separators between machines and a cycle progress bar. Times are machine ticks.
 */
@SideOnly(Side.CLIENT)
final class ProcessingTooltip {

    private static final String KEY = "gui.neoecoae.crafting_status.processing.";
    private static final int GAP = 12;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xEEEAF2;

    private ProcessingTooltip() {}

    enum Kind {
        TEXT,
        SEPARATOR,
        PROGRESS
    }

    static final class Row {

        final Kind kind;
        final String left;
        final String right;
        final float progress;

        private Row(Kind kind, String left, String right, float progress) {
            this.kind = kind;
            this.left = left;
            this.right = right;
            this.progress = progress;
        }

        static Row text(String left) {
            return new Row(Kind.TEXT, left, "", 0);
        }

        static Row text(String left, String right) {
            return new Row(Kind.TEXT, left, right, 0);
        }

        static Row separator() {
            return new Row(Kind.SEPARATOR, "", "", 0);
        }

        static Row progress(float value) {
            return new Row(Kind.PROGRESS, "", "", value);
        }
    }

    /** Machine rows of an output being crafted, or the reason no machine cycle can be shown yet. */
    static List<Row> rows(boolean active, boolean pending, boolean monitored, ECOProcessingProgress progress) {
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

    /** Vanilla's tooltip frame around the rows; the first row is the title, as in vanilla tooltips. */
    static void draw(FontRenderer font, List<Row> rows, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (rows.isEmpty()) return;
        int maximum = Math.max(80, Math.min(260, screenWidth - 20));
        boolean hasProgress = false;
        int width = 0;
        for (Row row : rows) {
            if (row.kind == Kind.PROGRESS) hasProgress = true;
            if (row.kind != Kind.TEXT) continue;
            int right = font.getStringWidth(row.right);
            width = Math.max(width, font.getStringWidth(row.left) + (right == 0 ? 0 : GAP + right));
        }
        if (hasProgress) width = Math.max(width, 140);
        width = Math.min(maximum, width);
        List<List<String>> lefts = new ArrayList<>();
        List<List<String>> rights = new ArrayList<>();
        int[] heights = new int[rows.size()];
        int height = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            List<String> left = Collections.emptyList(), right = Collections.emptyList();
            int rowHeight = 6;
            if (row.kind == Kind.TEXT) {
                int rightWidth = Math.min(font.getStringWidth(row.right), width / 2);
                left = split(font, row.left, Math.max(1, width - (rightWidth == 0 ? 0 : GAP + rightWidth)));
                right = rightWidth == 0 ? Collections.emptyList() : split(font, row.right, rightWidth);
                rowHeight = Math.max(1, Math.max(left.size(), right.size())) * LINE_HEIGHT;
                // Vanilla leaves a gap under the title line.
                if (i == 0) rowHeight += 2;
            }
            lefts.add(left);
            rights.add(right);
            heights[i] = rowHeight;
            height += rowHeight;
        }
        height -= 2;
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width + 4 > screenWidth) x = Math.max(4, mouseX - 16 - width);
        if (y + height + 6 > screenHeight) y = screenHeight - height - 6;
        y = Math.max(4, y);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        int background = 0xF0100010;
        gradient(x - 3, y - 4, x + width + 3, y - 3, background, background);
        gradient(x - 3, y + height + 3, x + width + 3, y + height + 4, background, background);
        gradient(x - 3, y - 3, x + width + 3, y + height + 3, background, background);
        gradient(x - 4, y - 3, x - 3, y + height + 3, background, background);
        gradient(x + width + 3, y - 3, x + width + 4, y + height + 3, background, background);
        int borderTop = 0x505000FF;
        int borderBottom = (borderTop & 0xFEFEFE) >> 1 | borderTop & 0xFF000000;
        gradient(x - 3, y - 3 + 1, x - 3 + 1, y + height + 3 - 1, borderTop, borderBottom);
        gradient(x + width + 2, y - 3 + 1, x + width + 3, y + height + 3 - 1, borderTop, borderBottom);
        gradient(x - 3, y - 3, x + width + 3, y - 3 + 1, borderTop, borderTop);
        gradient(x - 3, y + height + 2, x + width + 3, y + height + 3, borderBottom, borderBottom);

        int top = y;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind == Kind.SEPARATOR) {
                gradient(x, top + 2, x + width, top + 3, 0xFF37303F, 0xFF37303F);
            } else if (row.kind == Kind.PROGRESS) {
                gradient(x, top + 1, x + width, top + 3, 0xFF332E3B, 0xFF332E3B);
                int filled = Math.round(width * Math.max(0, Math.min(1, row.progress)));
                if (filled > 0) gradient(x, top + 1, x + filled, top + 3, 0xFF60CD64, 0xFF60CD64);
            } else {
                int color = i == 0 ? 0xFFFFFF : TEXT_COLOR;
                List<String> left = lefts.get(i);
                for (int line = 0; line < left.size(); line++)
                    font.drawStringWithShadow(left.get(line), x, top + line * LINE_HEIGHT, color);
                List<String> right = rights.get(i);
                for (int line = 0; line < right.size(); line++) font.drawStringWithShadow(
                    right.get(line),
                    x + width - font.getStringWidth(right.get(line)),
                    top + line * LINE_HEIGHT,
                    TEXT_COLOR);
            }
            top += heights[i];
        }
        GL11.glPopAttrib();
        GL11.glColor4f(1, 1, 1, 1);
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
