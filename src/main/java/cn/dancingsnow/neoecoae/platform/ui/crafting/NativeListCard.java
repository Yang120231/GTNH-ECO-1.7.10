package cn.dancingsnow.neoecoae.platform.ui.crafting;

import static cn.dancingsnow.neoecoae.platform.ui.crafting.ProcessingTooltip.ACCENT;
import static cn.dancingsnow.neoecoae.platform.ui.crafting.ProcessingTooltip.MUTED;
import static cn.dancingsnow.neoecoae.platform.ui.crafting.ProcessingTooltip.PRIMARY;
import static cn.dancingsnow.neoecoae.platform.ui.crafting.ProcessingTooltip.WARN;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.GuiContext;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The 1.21.1 workstation card layout, rendered in the native MUI2 tooltip pass. */
public final class NativeListCard extends RichTooltip {

    public static final int DANGER = 0xFFFF7A7A, DANGER_BACKGROUND = 0xFF3A2026, WARN_BACKGROUND = 0xFF3A2C1E;
    private static final int TRACK = 0xFF332E3B, DIVIDER = 0xFF37303F, LINE = 9, LARGE_HEIGHT = 15;
    private static final int MIN_WIDTH = 150, GAP = 12, ICON_SPACE = 12, GLYPH_SPACE = 9, WRAP = 220;
    private static final int BANNER_PAD = 4, TILE_PAD = 4, TILE_GAP = 5, TILE_BORDER = 0xFF4A4058;
    private static final int TILE_BACKGROUND = 0xFF241D30, TILE_ACTIVE = 0xFF33284A;
    private static final float METRIC_SCALE = 2, LARGE_SCALE = 1.5f, CONTENT_SCALE = .75f;
    private final Supplier<Builder> content;

    public NativeListCard(Supplier<Builder> content) {
        this.content = content;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Pixel glyphs, one string per row, '#' marks a filled pixel. */
    public enum Glyph {

        SPARKLE("...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."),
        DOTS(".##..##.", "########", "########", ".##..##."),
        STACK(".#######.", ".#######.", ".........", "#########", ".........", ".#######.", ".#######."),
        BOLT("....##", "...##.", "..##..", ".#####", "...##.", "..##..", ".##..."),
        SNOW("...#...", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "...#..."),
        DROP("...#...", "..###..", ".#####.", "#######", "#######", ".#####.", "..###.."),
        NODES("..###..", "..###..", "...#...", ".#####.", ".#...#.", "###.###", "###.###"),
        CROSS("#...#", ".#.#.", "..#..", ".#.#.", "#...#"),
        CHECK("......#", ".....#.", "#...#..", ".#.#...", "..#...."),
        CHIP(".#.#.#.", "#######", "##...##", "##.#.##", "##...##", "#######", ".#.#.#."),
        CUBE("...#...", ".##.##.", "#.....#", "##...##", "#.#.#.#", "#..#..#", ".#.#.#.", "...#..."),
        STEPS("###....", "#.#....", "###....", ".#.....", ".#.###.", ".##.#.#", "....###"),
        INFO("..###..", ".#...#.", "#..#..#", "#.....#", "#..#..#", ".#.#.#.", "..###.."),
        POINTER(".#.....", ".##....", ".###...", ".####..", ".#####.", ".##....", "...#..."),
        BOOK("##.##..", "#.#.#..", "#.#.#..", "#.#.#..", "#.#.#..", "##.##..", "..#...."),
        THERMOMETER(".#.", "#.#", "#.#", "#.#", "#.#", "###", "###", ".#."),
        PAUSE(".###.", "#####", "#.#.#", "#.#.#", "#####", ".###."),
        WARNING("...#...", "..#.#..", "..###..", ".##.##.", ".#####.", "##.#.##", "#######"),
        BATTERY("#########.", "#.......##", "#.##.##.##", "#.##.##.##", "#.......##", "#########."),
        PUZZLE("..##...", "..##...", "######.", "#....##", "#....##", "#.....#", "#######");

        private final String[] rows;

        Glyph(String... rows) {
            this.rows = rows;
        }

        int width() {
            int width = 0;
            for (String row : rows) width = Math.max(width, row.length());
            return width;
        }

        @SideOnly(Side.CLIENT)
        void draw(int x, int y, int color) {
            int top = y + (LINE - 1 - rows.length) / 2;
            for (int row = 0; row < rows.length; row++) {
                String pixels = rows[row];
                int start = -1;
                for (int column = 0; column <= pixels.length(); column++) {
                    boolean filled = column < pixels.length() && pixels.charAt(column) == '#';
                    if (filled && start < 0) start = column;
                    if (!filled && start >= 0) {
                        fill(x + start, top + row, x + column, top + row + 1, color);
                        start = -1;
                    }
                }
            }
        }
    }

    private interface Entry {

        @SideOnly(Side.CLIENT)
        int width(FontRenderer font);

        int height();

        @SideOnly(Side.CLIENT)
        void draw(FontRenderer font, int x, int y, int width);
    }

    @com.github.bsideup.jabel.Desugar
    private record Header(Glyph glyph, Component title, Component right, int rightColor, Glyph rightGlyph)
        implements Entry {

        Component line() {
            Component line = Component.empty()
                .append(
                    title.copy()
                        .withColor(ACCENT));
            if (!right.getString()
                .isEmpty())
                line.append("  ")
                    .append(
                        right.copy()
                            .withColor(rightColor));
            return line;
        }

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            return (glyph == null ? 0 : ICON_SPACE) + NativeListCard.width(font, title)
                + gap(font, right)
                + NativeListCard.width(font, right)
                + (rightGlyph == null ? 0 : GAP + GLYPH_SPACE);
        }

        public int height() {
            return LINE + 1;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            int textX = x;
            if (glyph != null) {
                glyph.draw(
                    x,
                    y,
                    title.getStyle()
                        .getColor() == null ? ACCENT
                            : title.getStyle()
                                .getColor()
                                .getValue() | 0xff000000);
                textX += ICON_SPACE;
            }
            drawString(font, title, textX, y, ACCENT, true);
            drawRight(font, right, x + width, y, rightColor);
            if (rightGlyph != null)
                rightGlyph.draw(x + width - NativeListCard.width(font, right) - GLYPH_SPACE, y, rightColor);
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record Divider(int color) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            return 0;
        }

        public int height() {
            return 9;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            fill(x, y + 3, x + width, y + 4, color);
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record Section(Component label, Component right, int rightColor) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            return NativeListCard.width(font, label) + gap(font, right) + NativeListCard.width(font, right);
        }

        public int height() {
            return LINE + 2;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            drawString(font, label, x, y, MUTED, true);
            drawRight(font, right, x + width, y, rightColor);
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record Row(Glyph glyph, int glyphColor, Component left, int leftColor, Glyph rightGlyph, Component right,
        Component suffix, int rightColor, boolean large) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            int rightWidth = (large ? (int) Math.ceil(NativeListCard.width(font, right) * LARGE_SCALE)
                : NativeListCard.width(font, right)) + NativeListCard.width(font, suffix)
                + (rightGlyph == null ? 0 : GLYPH_SPACE);
            return (glyph == null ? 0 : ICON_SPACE) + NativeListCard.width(font, left)
                + (rightWidth == 0 ? 0 : GAP)
                + rightWidth;
        }

        public int height() {
            return large ? LARGE_HEIGHT : LINE + 2;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            int textY = large ? y + 3 : y;
            int textX = x;
            if (glyph != null) {
                glyph.draw(x, textY, glyphColor);
                textX += ICON_SPACE;
            }
            drawString(font, left, textX, textY, leftColor, true);
            int end = x + width;
            int suffixWidth = NativeListCard.width(font, suffix);
            if (suffixWidth > 0) {
                drawString(font, suffix, end - suffixWidth, textY + (large ? 1 : 0), MUTED, true);
                end -= suffixWidth;
            }
            int rightWidth = NativeListCard.width(font, right);
            if (rightWidth > 0) {
                if (large) {
                    int start = end - (int) Math.ceil(rightWidth * LARGE_SCALE);

                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(start, y, 0);
                        GL11.glScalef(LARGE_SCALE, LARGE_SCALE, 1.0F);
                        drawString(font, right, 0, 0, rightColor, true);
                    } finally {
                        GL11.glPopMatrix();
                    }
                    end = start;
                } else {
                    drawString(font, right, end - rightWidth, textY, rightColor, true);
                    end -= rightWidth;
                }
            }
            if (rightGlyph != null) {
                int glyphX = end - (rightWidth > 0 ? 3 : 0) - rightGlyph.width();
                rightGlyph.draw(glyphX, textY, rightColor);
            }
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record Bar(double fraction, int color, int track) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            return 0;
        }

        public int height() {
            return 7;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            fill(x, y + 1, x + width, y + 4, track);
            int length = (int) Math
                .round(width * cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.clamp(fraction, 0.0D, 1.0D));
            if (length > 0) fill(x, y + 1, x + length, y + 4, color);
        }
    }

    /** Free text with an optional leading glyph and right column; long left text wraps at {@link #WRAP}. */
    @com.github.bsideup.jabel.Desugar
    private record Text(Glyph glyph, Component left, Component right, int color) implements Entry {

        private int indent() {
            return glyph == null ? 0 : ICON_SPACE;
        }

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            int rightWidth = NativeListCard.width(font, right);
            return Math
                .min(WRAP, indent() + NativeListCard.width(font, left) + (rightWidth == 0 ? 0 : GAP + rightWidth));
        }

        public int height() {
            return LINE + 2;
        }

        @SideOnly(Side.CLIENT)
        private List<String> lines(FontRenderer font, int width) {
            int rightWidth = Math.min(NativeListCard.width(font, right), width / 2);
            List<String> lines = font.listFormattedStringToWidth(
                left.nativeText()
                    .getFormattedText(),
                Math.max(1, width - indent() - (rightWidth == 0 ? 0 : GAP + rightWidth)));
            return lines == null ? java.util.Collections.emptyList() : lines;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            var lines = lines(font, width);
            if (glyph != null) glyph.draw(x, y, color);
            for (int i = 0; i < lines.size(); i++) {
                drawString(font, lines.get(i), x + indent(), y + i * (LINE + 2), color, true);
            }
            drawRight(font, right, x + width, y, color);
        }
    }

    /** Tinted callout box with a leading glyph, a title line and an optional muted detail line. */
    @com.github.bsideup.jabel.Desugar
    private record Banner(Glyph glyph, Component title, Component detail, int color, int background) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            return BANNER_PAD * 2
                + Math.max(ICON_SPACE + NativeListCard.width(font, title), NativeListCard.width(font, detail));
        }

        public int height() {
            return (hasDetail() ? 2 * (LINE + 2) : LINE + 1) + BANNER_PAD * 2 + 3;
        }

        private boolean hasDetail() {
            return !detail.getString()
                .isEmpty();
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            int bottom = y + height() - 3;
            fill(x, y, x + width, bottom, background);
            int textY = y + BANNER_PAD + 1;
            glyph.draw(x + BANNER_PAD, textY, color);
            drawString(font, title, x + BANNER_PAD + ICON_SPACE, textY, color, true);
            if (hasDetail()) {
                drawString(font, detail, x + BANNER_PAD, textY + LINE + 2, MUTED, true);
            }
        }
    }

    /**
     * A muted label above a double-size headline value, with an optional right column that is either another
     * label/headline pair or a small note on the headline line.
     */
    @com.github.bsideup.jabel.Desugar
    private record Metric(Component label, Component value, int valueColor, Component unit, int unitColor,
        Component rightLabel, Component rightValue, int rightColor, boolean rightLarge, boolean equalColumns)
        implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            int unitWidth = NativeListCard.width(font, unit);
            int left = Math
                .max(NativeListCard.width(font, label), scaled(font, value) + (unitWidth == 0 ? 0 : 3 + unitWidth));
            int right = Math.max(
                NativeListCard.width(font, rightLabel),
                rightLarge ? scaled(font, rightValue) : NativeListCard.width(font, rightValue));
            return equalColumns ? Math.max(left, right) * 2 + GAP : left + (right == 0 ? 0 : GAP) + right;
        }

        public int height() {
            return LINE + 2 + Math.round(LINE * METRIC_SCALE) + 2;
        }

        @SideOnly(Side.CLIENT)
        private static int scaled(FontRenderer font, Component text) {
            return (int) Math.ceil(NativeListCard.width(font, text) * METRIC_SCALE);
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            drawString(font, label, x, y, MUTED, true);
            int rightX = x + (width + GAP) / 2;
            if (equalColumns) drawString(font, rightLabel, rightX, y, MUTED, true);
            else drawRight(font, rightLabel, x + width, y, MUTED);
            int valueY = y + LINE + 2;
            drawScaled(font, value, x, valueY, valueColor);
            if (NativeListCard.width(font, unit) > 0) {
                drawString(
                    font,
                    unit,
                    x + scaled(font, value) + 3,
                    valueY + Math.round(LINE * METRIC_SCALE) - LINE,
                    unitColor,
                    true);
            }
            if (rightLarge) {
                drawScaled(
                    font,
                    rightValue,
                    equalColumns ? rightX : x + width - scaled(font, rightValue),
                    valueY,
                    rightColor);
            } else {
                drawRight(font, rightValue, x + width, valueY + 1, rightColor);
            }
        }

        @SideOnly(Side.CLIENT)
        private static void drawScaled(FontRenderer font, Component text, int x, int y, int color) {
            if (NativeListCard.width(font, text) == 0) return;

            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, 0);
                GL11.glScalef(METRIC_SCALE, METRIC_SCALE, 1.0F);
                drawString(font, text, 0, 0, color, true);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    /** One line of a panel: an optional glyph, a left text and an optional right-aligned value. */
    @com.github.bsideup.jabel.Desugar
    public record PanelLine(Glyph glyph, Component left, int leftColor, Component right, int rightColor) {}

    /** Inset box with an optional glyph title and status, followed by compact lines. */
    @com.github.bsideup.jabel.Desugar
    private record Panel(Glyph glyph, Component title, Component right, int rightColor, List<PanelLine> lines)
        implements Entry {

        private boolean titled() {
            return !title.getString()
                .isEmpty();
        }

        private int inner() {
            return (titled() ? LINE + 3 : 0) + lines.size() * (LINE + 2) - 2;
        }

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            int widest = titled()
                ? (glyph == null ? 0 : ICON_SPACE) + NativeListCard.width(font, title)
                    + gap(font, right)
                    + NativeListCard.width(font, right)
                : 0;
            for (PanelLine line : lines) {
                widest = Math.max(
                    widest,
                    (line.glyph() == null ? 0 : ICON_SPACE) + NativeListCard.width(font, line.left())
                        + gap(font, line.right())
                        + NativeListCard.width(font, line.right()));
            }
            return widest + BANNER_PAD * 2;
        }

        public int height() {
            return inner() + BANNER_PAD * 2 + 3;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            fill(x, y, x + width, y + inner() + BANNER_PAD * 2, TILE_BACKGROUND);
            int left = x + BANNER_PAD, end = x + width - BANNER_PAD, textY = y + BANNER_PAD + 1;
            if (titled()) {
                int titleX = left;
                if (glyph != null) {
                    glyph.draw(left, textY, WARN);
                    titleX += ICON_SPACE;
                }
                drawString(font, title, titleX, textY, PRIMARY, true);
                drawRight(font, right, end, textY, rightColor);
                textY += LINE + 3;
            }
            for (PanelLine line : lines) {
                int lineX = left;
                if (line.glyph() != null) {
                    line.glyph()
                        .draw(left, textY, line.leftColor());
                    lineX += ICON_SPACE;
                }
                drawString(font, line.left(), lineX, textY, line.leftColor(), true);
                drawRight(font, line.right(), end, textY, line.rightColor());
                textY += LINE + 2;
            }
        }
    }

    /** One coloured range of a scale bar, ending at {@code end} (0..1). */
    @com.github.bsideup.jabel.Desugar
    public record Band(double end, int color) {}

    /**
     * Bar split into bands with small gaps and a tick label under every band boundary. The part of each band
     * below {@code value} is drawn at full colour, the remainder dimmed.
     */
    @com.github.bsideup.jabel.Desugar
    private record Scale(List<Band> bands, double value, List<Component> ticks) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            int total = 0;
            for (Component tick : ticks) total += NativeListCard.width(font, tick) + 6;
            return total;
        }

        public int height() {
            return 4 + 3 + LINE + 2;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            double fill = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.clamp(value, 0.0D, 1.0D);
            int filled = x + (int) Math.round(width * fill);
            double start = 0.0D;
            for (int i = 0; i < bands.size(); i++) {
                Band band = bands.get(i);
                int left = x + (int) Math.round(width * start) + (i == 0 ? 0 : 1);
                int right = x
                    + (int) Math
                        .round(width * cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.clamp(band.end(), 0.0D, 1.0D))
                    - (i == bands.size() - 1 ? 0 : 1);
                if (right > left) {
                    fill(left, y, right, y + 4, dim(band.color()));
                    int lit = Math.min(right, filled);
                    if (lit > left) fill(left, y, lit, y + 4, band.color());
                }
                start = band.end();
            }
            if (fill > 0.0D) {
                int marker = Math.min(x + width - 1, filled);
                fill(marker, y - 1, marker + 1, y + 5, PRIMARY);
            }
            int tickY = y + 4 + 3;
            for (int i = 0; i < ticks.size(); i++) {
                Component tick = ticks.get(i);
                double at = i == 0 || bands.isEmpty() ? 0.0D
                    : bands.get(Math.min(i, bands.size()) - 1)
                        .end();
                int tickWidth = NativeListCard.width(font, tick);
                int tickX = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .clamp(x + (int) Math.round(width * at) - tickWidth / 2, x, x + width - tickWidth);
                boolean reached = at > 0.0D && fill >= at;
                drawString(font, tick, tickX, tickY, reached ? PRIMARY : MUTED, true);
            }
        }

        private static int dim(int color) {
            int r = (color >> 16 & 0xFF) * 2 / 5, g = (color >> 8 & 0xFF) * 2 / 5, b = (color & 0xFF) * 2 / 5;
            return 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    /** Three short lines in a bordered box: accent heading, primary value and muted caption. */
    @com.github.bsideup.jabel.Desugar
    public record Tile(Component heading, Component value, Component caption, boolean active) {}

    @com.github.bsideup.jabel.Desugar
    private record Tiles(List<Tile> tiles) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            int widest = 0;
            for (Tile tile : tiles) {
                widest = Math.max(
                    widest,
                    Math.max(
                        NativeListCard.width(font, tile.heading()),
                        Math.max(
                            NativeListCard.width(font, tile.value()),
                            NativeListCard.width(font, tile.caption()))));
            }
            return tiles.size() * (widest + TILE_PAD * 2) + Math.max(0, tiles.size() - 1) * TILE_GAP;
        }

        public int height() {
            return 3 * (LINE + 2) + TILE_PAD * 2 + 3;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            int count = tiles.size();
            if (count == 0) return;
            int tileWidth = (width - (count - 1) * TILE_GAP) / count;
            int bottom = y + height() - 3;
            for (int i = 0; i < count; i++) {
                Tile tile = tiles.get(i);
                int left = x + i * (tileWidth + TILE_GAP);
                int right = i == count - 1 ? x + width : left + tileWidth;
                fill(left, y, right, bottom, tile.active() ? ACCENT : TILE_BORDER);
                fill(left + 1, y + 1, right - 1, bottom - 1, tile.active() ? TILE_ACTIVE : TILE_BACKGROUND);
                int textX = left + TILE_PAD, textY = y + TILE_PAD;
                drawString(font, tile.heading(), textX, textY, ACCENT, true);
                drawString(font, tile.value(), textX, textY + LINE + 2, PRIMARY, true);
                drawString(font, tile.caption(), textX, textY + 2 * (LINE + 2), MUTED, true);
            }
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record Statistic(Component value, Component suffix, double fraction, int color) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            return (int) Math.ceil(NativeListCard.width(font, value) * LARGE_SCALE) + NativeListCard.width(font, suffix)
                + 1;
        }

        public int height() {
            return 26;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(x, y + 2, 0);
                GL11.glScalef(LARGE_SCALE, LARGE_SCALE, 1);
                drawString(font, value, 0, 0, color, true);
            } finally {
                GL11.glPopMatrix();
            }
            drawString(
                font,
                suffix,
                x + (int) Math.ceil(NativeListCard.width(font, value) * LARGE_SCALE) + 1,
                y + 6,
                MUTED,
                true);
            fill(x, y + 18, x + width, y + 21, TRACK);
            int length = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
            if (length > 0) fill(x, y + 18, x + length, y + 21, color);
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record StatRow(Component left, Component right, int rightColor, boolean divided) implements Entry {

        @SideOnly(Side.CLIENT)
        public int width(FontRenderer font) {
            return NativeListCard.width(font, left) + gap(font, right) + NativeListCard.width(font, right);
        }

        public int height() {
            return divided ? 18 : 10;
        }

        @SideOnly(Side.CLIENT)
        public void draw(FontRenderer font, int x, int y, int width) {
            if (divided) {
                fill(x, y + 3, x + width, y + 4, DIVIDER);
                y += 8;
            }
            drawString(font, left, x, y, MUTED, true);
            drawRight(font, right, x + width, y, rightColor);
        }
    }

    public static final class Builder {

        private final List<Entry> entries = new ArrayList<>();

        private Builder() {}

        public Builder statistic(String value, String total, double fraction, int color) {
            entries.add(
                new Statistic(
                    Component.literal(value),
                    Component.literal(total == null ? "" : " / " + total),
                    fraction,
                    color));
            return this;
        }

        public Builder statRow(Component left, Component right, int color, boolean divided) {
            entries.add(new StatRow(left, right, color, divided));
            return this;
        }

        public Builder hint(Glyph glyph, Component text) {
            entries.add(new Row(glyph, MUTED, text, MUTED, null, Component.empty(), Component.empty(), MUTED, false));
            return this;
        }

        public Builder header(Glyph glyph, Component title, Component right, int rightColor) {
            return header(glyph, title, right, rightColor, null);
        }

        public Builder header(Glyph glyph, Component title, Component right, int rightColor, Glyph rightGlyph) {
            entries.add(
                new Header(
                    glyph,
                    title.copy()
                        .withStyle(EnumChatFormatting.BOLD),
                    right,
                    rightColor,
                    rightGlyph));
            return this;
        }

        public Builder divider() {
            return divider(DIVIDER);
        }

        public Builder divider(int color) {
            entries.add(new Divider(color));
            return this;
        }

        public Builder section(Component label) {
            entries.add(new Section(label, Component.empty(), PRIMARY));
            return this;
        }

        public Builder section(Component label, Component right, int rightColor) {
            entries.add(new Section(label, right, rightColor));
            return this;
        }

        /** A labelled row with a right-aligned value. */
        public Builder row(Glyph glyph, int glyphColor, Component left, Component right, int rightColor) {
            entries.add(new Row(glyph, glyphColor, left, PRIMARY, null, right, Component.empty(), rightColor, false));
            return this;
        }

        /** A tinted callout box; {@code detail} may be empty. */
        public Builder banner(Glyph glyph, Component title, Component detail, int color, int background) {
            entries.add(new Banner(glyph, title, detail, color, background));
            return this;
        }

        /** A muted label over a large value, with a small right-aligned note on the value line. */
        public Builder metric(Component label, Component value, int valueColor, Component note, int noteColor) {
            entries.add(
                new Metric(
                    label,
                    value,
                    valueColor,
                    Component.empty(),
                    0,
                    Component.empty(),
                    note,
                    noteColor,
                    false,
                    false));
            return this;
        }

        /** Two label-over-large-value columns, the second right-aligned. */
        public Builder metrics(Component label, Component value, int valueColor, Component rightLabel,
            Component rightValue, int rightColor) {
            entries.add(
                new Metric(
                    label,
                    value,
                    valueColor,
                    Component.empty(),
                    0,
                    rightLabel,
                    rightValue,
                    rightColor,
                    true,
                    false));
            return this;
        }

        /** A muted label over a large value followed by a small unit, such as {@code 0.0 AE/t}. */
        public Builder metricUnit(Component label, Component value, int valueColor, Component unit, int unitColor) {
            entries.add(
                new Metric(
                    label,
                    value,
                    valueColor,
                    unit,
                    unitColor,
                    Component.empty(),
                    Component.empty(),
                    0,
                    false,
                    false));
            return this;
        }

        /** An inset box; an empty {@code title} leaves only the lines. */
        public Builder panel(Glyph glyph, Component title, Component right, int rightColor, List<PanelLine> lines) {
            entries.add(new Panel(glyph, title, right, rightColor, new ArrayList<>(lines)));
            return this;
        }

        /** A banded bar; {@code ticks} holds one label for 0 and one per band end. */
        public Builder scale(List<Band> bands, double value, List<Component> ticks) {
            entries.add(new Scale(new ArrayList<>(bands), value, new ArrayList<>(ticks)));
            return this;
        }

        public Builder tiles(List<Tile> tiles) {
            entries.add(new Tiles(new ArrayList<>(tiles)));
            return this;
        }

        public Builder text(Component left, Component right, int color) {
            entries.add(new Text(null, left, right, color));
            return this;
        }

        /** Wrapped text behind a leading glyph, used for longer hints and notes. */
        public Builder wrapped(Glyph glyph, Component text, int color) {
            entries.add(new Text(glyph, text, Component.empty(), color));
            return this;
        }

        /** A row with a double-size coloured right value and no glyph. */
        public Builder largeValue(Component left, String value, int valueColor) {
            entries.add(
                new Row(
                    null,
                    MUTED,
                    left,
                    PRIMARY,
                    null,
                    Component.literal(value),
                    Component.empty(),
                    valueColor,
                    true));
            return this;
        }

        public Builder bar(double fraction, int color) {
            return bar(fraction, color, TRACK);
        }

        public Builder bar(double fraction, int color, int track) {
            entries.add(new Bar(fraction, color, track));
            return this;
        }

    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiContext context, ItemStack stack) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        List<Entry> entries = content.get().entries;
        if (entries.isEmpty()) return;
        // 1.21.1 draws the leading header as a vanilla title line and scales only the body.
        Header header = entries.get(0) instanceof Header ? (Header) entries.get(0) : null;
        Component title = header == null ? Component.empty() : header.line();
        int first = header == null ? 0 : 1;
        boolean statistic = entries.size() > first && entries.get(first) instanceof Statistic;
        int width = Math
            .max(statistic ? 140 : MIN_WIDTH, (int) Math.ceil(NativeListCard.width(font, title) / CONTENT_SCALE));
        for (int i = first; i < entries.size(); i++) width = Math.max(
            width,
            entries.get(i)
                .width(font));
        int[] heights = new int[entries.size()];
        int height = 0;
        for (int i = first; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            heights[i] = entry instanceof Text ? Math.max(
                1,
                ((Text) entry).lines(font, width)
                    .size())
                * (LINE + 2) : entry.height();
            height += heights[i];
        }
        int titleHeight = header == null ? 0 : 12;
        int cardWidth = (int) Math.ceil(width * CONTENT_SCALE);
        int cardHeight = titleHeight + (int) Math.ceil(Math.max(0, height - (statistic ? 1 : 2)) * CONTENT_SCALE);
        float fit = Math.min(
            1,
            Math.min(
                (context.getScreenArea().width - 8f) / Math.max(1, cardWidth),
                (context.getScreenArea().height - 12f) / Math.max(1, cardHeight)));
        int scaledWidth = (int) Math.ceil(cardWidth * fit), scaledHeight = (int) Math.ceil(cardHeight * fit);
        int x = context.getAbsMouseX() + 12, y = context.getAbsMouseY() - 12;
        if (x + scaledWidth + 4 > context.getScreenArea().width)
            x = Math.max(4, context.getAbsMouseX() - 16 - scaledWidth);
        y = Math.max(4, Math.min(y, context.getScreenArea().height - scaledHeight - 6));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0, 0, 400);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GuiDraw
                .drawTooltipBackground(stack, java.util.Collections.emptyList(), x, y, scaledWidth, scaledHeight, this);
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(fit, fit, 1);
            if (header != null) {
                drawString(font, header.title(), 0, 0, ACCENT, true);
                drawString(
                    font,
                    header.right(),
                    NativeListCard.width(font, header.title()) + font.getStringWidth("  "),
                    0,
                    header.rightColor(),
                    true);
            }
            GL11.glTranslatef(0, titleHeight, 0);
            GL11.glScalef(CONTENT_SCALE, CONTENT_SCALE, 1);
            int top = 0;
            for (int i = first; i < entries.size(); i++) {
                entries.get(i)
                    .draw(font, 0, top, width);
                top += heights[i];
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @SideOnly(Side.CLIENT)
    private static int width(FontRenderer font, Component text) {
        return font.getStringWidth(
            text.nativeText()
                .getFormattedText());
    }

    @SideOnly(Side.CLIENT)
    private static void drawString(FontRenderer font, Component text, int x, int y, int color, boolean shadow) {
        int actual = text.getStyle()
            .getColor() == null ? color
                : text.getStyle()
                    .getColor()
                    .getValue();
        font.drawString(
            text.nativeText()
                .getFormattedText(),
            x,
            y,
            actual,
            shadow);
    }

    @SideOnly(Side.CLIENT)
    private static void drawString(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        font.drawString(text, x, y, color, shadow);
    }

    @SideOnly(Side.CLIENT)
    private static void fill(int left, int top, int right, int bottom, int color) {
        GuiDraw.drawRect(left, top, right - left, bottom - top, color);
    }

    @SideOnly(Side.CLIENT)
    private static int gap(FontRenderer font, Component right) {
        return NativeListCard.width(font, right) == 0 ? 0 : GAP;
    }

    @SideOnly(Side.CLIENT)
    private static void drawRight(FontRenderer font, Component text, int end, int y, int color) {
        int length = NativeListCard.width(font, text);
        if (length > 0) drawString(font, text, end - length, y, color, true);
    }
}
