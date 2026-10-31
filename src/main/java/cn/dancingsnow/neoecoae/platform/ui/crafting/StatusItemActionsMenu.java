package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.util.NamedDimensionalCoord;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.core.localization.PlayerMessages;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Right-click menu of a crafting status cell (1.21.1 ComputationItemActionsPopup): locate and highlight the pattern
 * providers, open the recipe machine, or open the pattern provider. Several candidates open a paged chooser.
 */
@SideOnly(Side.CLIENT)
final class StatusItemActionsMenu {

    static final String INSPECT = "inspect", OPEN_PROVIDER = "open_provider", OPEN_MACHINE = "open_machine";
    private static final String KEY = "gui.neoecoae.processing.";
    private static final int MENU_WIDTH = 168, ROW = 14, HEADER = 16, PAGE_SIZE = 5, ENTRY = 22, FOOTER = 16;
    private static final int TEXT = 0xEEEAF2, MUTED = 0x8F8A9C, TITLE = 0xFFFFFF, ERROR = 0xFF7B7B;

    @FunctionalInterface
    interface Requester {

        void send(long sequence, String action, AEKey key, String id);
    }

    private enum Mode {
        MENU,
        CHOOSE_PROVIDER,
        CHOOSE_MACHINE,
        MESSAGE
    }

    private final Requester requester;
    private final List<CompoundTag> sources = new ArrayList<>();
    private final List<CompoundTag> machines = new ArrayList<>();
    private AEKey key;
    private String title = "";
    private long sequence;
    private boolean open, loaded;
    private Mode mode = Mode.MENU;
    private int page, x, y, width, height, screenWidth, screenHeight;
    private String message = "";

    StatusItemActionsMenu(Requester requester) {
        this.requester = requester;
    }

    boolean isOpen() {
        return open;
    }

    void open(AEKey key, String title, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.key = key;
        this.title = title;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        sequence++;
        loaded = false;
        sources.clear();
        machines.clear();
        mode = Mode.MENU;
        page = 0;
        place(mouseX, mouseY, MENU_WIDTH, HEADER + 3 * ROW + 4);
        open = true;
        requester.send(sequence, INSPECT, key, null);
    }

    void close() {
        open = false;
    }

    /** A server answer; stale sequences and other outputs are ignored. */
    void accept(CompoundTag response) {
        if (!open || response.getLong("sequence") != sequence) return;
        if (response.contains("error")) {
            message = I18n.format(response.getString("error"));
            mode = Mode.MESSAGE;
            dialog();
            return;
        }
        if (response.getBoolean("close")) {
            close();
            return;
        }
        if (!INSPECT.equals(response.getString("action"))) return;
        loaded = true;
        sources.clear();
        machines.clear();
        for (var raw : response.getList("sources", Tag.TAG_COMPOUND)) sources.add((CompoundTag) raw);
        for (var raw : response.getList("machines", Tag.TAG_COMPOUND)) machines.add((CompoundTag) raw);
    }

    private void place(int left, int top, int w, int h) {
        width = w;
        height = h;
        x = Math.max(4, Math.min(screenWidth - w - 4, left));
        y = Math.max(4, Math.min(screenHeight - h - 4, top));
    }

    private void dialog() {
        int rows = mode == Mode.MESSAGE ? 1 : Math.min(PAGE_SIZE, Math.max(1, candidates().size()));
        int w = 220;
        place(x, y, w, HEADER + rows * ENTRY + FOOTER + 4);
    }

    private List<CompoundTag> candidates() {
        return mode == Mode.CHOOSE_MACHINE ? machines : sources;
    }

    private int pages() {
        return Math.max(1, (candidates().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    // ---- Input ----

    /** Every click while open belongs to the menu; a click outside closes it. */
    boolean click(int mouseX, int mouseY, int button) {
        if (!open) return false;
        if (!inside(mouseX, mouseY)) {
            close();
            return true;
        }
        if (button != 0) return true;
        int localY = mouseY - y;
        if (mode == Mode.MENU) {
            int row = localY - HEADER < 0 ? -1 : (localY - HEADER) / ROW;
            if (row == 0 && enabled(0)) locate();
            else if (row == 1 && enabled(1)) choose(Mode.CHOOSE_MACHINE);
            else if (row == 2 && enabled(2)) choose(Mode.CHOOSE_PROVIDER);
            return true;
        }
        if (localY >= height - FOOTER) {
            int column = (mouseX - x) * 3 / width;
            if (column == 0) page = Math.max(0, page - 1);
            else if (column == 1) page = Math.min(pages() - 1, page + 1);
            else close();
            return true;
        }
        if (mode == Mode.MESSAGE) return true;
        int index = localY - HEADER < 0 ? -1 : (localY - HEADER) / ENTRY;
        int absolute = page * PAGE_SIZE + index;
        if (index >= 0 && index < PAGE_SIZE && absolute < candidates().size()) send(absolute);
        return true;
    }

    boolean keyTyped(int keyCode) {
        if (!open) return false;
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    boolean scroll(int delta) {
        if (!open) return false;
        if (mode == Mode.CHOOSE_MACHINE || mode == Mode.CHOOSE_PROVIDER)
            page = Math.max(0, Math.min(pages() - 1, page + (delta < 0 ? 1 : -1)));
        return true;
    }

    private boolean inside(int mouseX, int mouseY) {
        return mouseX >= x - 3 && mouseX < x + width + 3 && mouseY >= y - 3 && mouseY < y + height + 3;
    }

    private boolean enabled(int row) {
        return loaded && (row == 1 ? !machines.isEmpty() : !sources.isEmpty());
    }

    private void choose(Mode target) {
        mode = target;
        page = 0;
        if (candidates().size() == 1) {
            send(0);
            return;
        }
        dialog();
    }

    private void send(int index) {
        var candidate = candidates().get(index);
        requester
            .send(sequence, mode == Mode.CHOOSE_MACHINE ? OPEN_MACHINE : OPEN_PROVIDER, key, candidate.getString("id"));
    }

    /** Highlights every provider in the world (AE2's highlighter) and prints clickable coordinates. */
    private void locate() {
        var player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        Map<NamedDimensionalCoord, String[]> highlight = new HashMap<>();
        for (var source : sources) {
            int px = source.getInt("x"), py = source.getInt("y"), pz = source.getInt("z");
            int dimension = source.getInt("dimension");
            String name = source.getString("name");
            highlight.put(
                new NamedDimensionalCoord(px, py, pz, dimension, name),
                new String[] { PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                    PlayerMessages.MachineInOtherDimNamed.getUnlocalized() });
            var coordinates = new ChatComponentText(px + ", " + py + ", " + pz);
            coordinates.setChatStyle(
                new ChatStyle().setColor(EnumChatFormatting.AQUA)
                    .setUnderlined(true)
                    .setChatClickEvent(
                        new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + px + " " + py + " " + pz))
                    .setChatHoverEvent(
                        new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentTranslation(KEY + "click_teleport"))));
            var line = new ChatComponentTranslation(
                KEY + "pattern_location",
                title,
                name,
                String.valueOf(dimension),
                coordinates);
            if (source.contains("side")) line.appendText(" · " + source.getString("side"));
            player.addChatMessage(line);
        }
        BlockPosHighlighter.highlightNamedBlocks(player, highlight, title);
        close();
    }

    // ---- Drawing ----

    void draw(FontRenderer font, int mouseX, int mouseY) {
        if (!open) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 400);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        frame();
        String header = title;
        if (mode == Mode.CHOOSE_MACHINE || mode == Mode.CHOOSE_PROVIDER) header = I18n
            .format(KEY + (mode == Mode.CHOOSE_MACHINE ? "choose_machine" : "choose_provider"), page + 1, pages());
        text(font, header, x + 4, y + 4, TITLE);
        Gui.drawRect(x + 2, y + HEADER - 3, x + width - 2, y + HEADER - 2, 0xFF5B4A86);
        if (mode == Mode.MENU) {
            menuRow(font, 0, KEY + "action.locate", mouseX, mouseY);
            menuRow(font, 1, KEY + "action.open_machine", mouseX, mouseY);
            menuRow(font, 2, KEY + "action.open", mouseX, mouseY);
        } else if (mode == Mode.MESSAGE) {
            text(font, message, x + 4, y + HEADER + 6, ERROR);
            footer(font, mouseX, mouseY);
        } else {
            var list = candidates();
            for (int index = 0; index < PAGE_SIZE && page * PAGE_SIZE + index < list.size(); index++) {
                var entry = list.get(page * PAGE_SIZE + index);
                int top = y + HEADER + index * ENTRY;
                if (mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ENTRY)
                    Gui.drawRect(x + 1, top, x + width - 1, top + ENTRY, 0x605A4A8A);
                text(font, entry.getString("name"), x + 4, top + 2, TEXT);
                String where = I18n.format(
                    KEY + "place",
                    entry.getInt("dimension"),
                    entry.getInt("x"),
                    entry.getInt("y"),
                    entry.getInt("z")) + (entry.contains("side") ? " · " + entry.getString("side") : "");
                text(font, where, x + 4, top + 12, MUTED);
            }
            footer(font, mouseX, mouseY);
        }
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void menuRow(FontRenderer font, int row, String label, int mouseX, int mouseY) {
        int top = y + HEADER + row * ROW;
        boolean enabled = enabled(row);
        if (enabled && mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ROW)
            Gui.drawRect(x + 1, top, x + width - 1, top + ROW, 0x605A4A8A);
        String suffix = !loaded ? "  " + I18n.format(KEY + "loading")
            : enabled ? count(row) : "  " + I18n.format(KEY + "none");
        text(font, I18n.format(label) + EnumChatFormatting.GRAY + suffix, x + 6, top + 3, enabled ? TEXT : MUTED);
    }

    private String count(int row) {
        int size = row == 1 ? machines.size() : sources.size();
        return row == 0 || size <= 1 ? "" : "  (" + size + ")";
    }

    private void footer(FontRenderer font, int mouseX, int mouseY) {
        int top = y + height - FOOTER;
        Gui.drawRect(x + 2, top, x + width - 2, top + 1, 0xFF5B4A86);
        String[] labels = { I18n.format(KEY + "previous"), I18n.format(KEY + "next"), I18n.format(KEY + "close") };
        for (int i = 0; i < 3; i++) {
            int left = x + width * i / 3, right = x + width * (i + 1) / 3;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= top + 1 && mouseY < y + height;
            if (hovered) Gui.drawRect(left + 1, top + 1, right - 1, y + height, 0x605A4A8A);
            int w = font.getStringWidth(labels[i]);
            font.drawStringWithShadow(labels[i], (left + right - w) / 2, top + 5, hovered ? TITLE : TEXT);
        }
    }

    private void text(FontRenderer font, String value, int left, int top, int color) {
        int room = x + width - 4 - left;
        String shown = font.getStringWidth(value) <= room ? value : font.trimStringToWidth(value, room - 6) + "...";
        font.drawStringWithShadow(shown, left, top, color);
    }

    /** The same frame as the processing tooltip: dark fill and a purple gradient border. */
    private void frame() {
        int background = 0xF0100010, borderTop = 0x505000FF;
        int borderBottom = (borderTop & 0xFEFEFE) >> 1 | borderTop & 0xFF000000;
        Gui.drawRect(x - 3, y - 4, x + width + 3, y - 3, background);
        Gui.drawRect(x - 3, y + height + 3, x + width + 3, y + height + 4, background);
        Gui.drawRect(x - 3, y - 3, x + width + 3, y + height + 3, background);
        Gui.drawRect(x - 4, y - 3, x - 3, y + height + 3, background);
        Gui.drawRect(x + width + 3, y - 3, x + width + 4, y + height + 3, background);
        Gui.drawRect(x - 3, y - 2, x - 2, y + height + 2, borderTop);
        Gui.drawRect(x + width + 2, y - 2, x + width + 3, y + height + 2, borderTop);
        Gui.drawRect(x - 3, y - 3, x + width + 3, y - 2, borderTop);
        Gui.drawRect(x - 3, y + height + 2, x + width + 3, y + height + 3, borderBottom);
    }
}
