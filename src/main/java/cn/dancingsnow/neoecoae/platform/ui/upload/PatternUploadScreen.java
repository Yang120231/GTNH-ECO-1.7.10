package cn.dancingsnow.neoecoae.platform.ui.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.client.gui.AEBaseGui;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets.Row;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets.TargetList;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only target list shown over an AE2 pattern terminal. It is a plain {@link GuiScreen}: the terminal container
 * stays open on both sides, every action is a packet naming the terminal window, and leaving always re-displays the
 * terminal GUI instead of closing the window.
 */
@SideOnly(Side.CLIENT)
final class PatternUploadScreen extends GuiScreen {

    private static final int TEXT = 0x3f3d52, MUTED = 0x6e6c80, DISABLED_TEXT = 0x9a98a8;
    private static final int ROW_HEIGHT = 26, MAX_VISIBLE_ROWS = 10, HEADER = 60, FOOTER = 46;
    private static final int LOCATE_SIZE = 16, SCROLLBAR_WIDTH = 6;
    private static final long BUSY_TIMEOUT = 5_000L;

    private static final int FREE = PatternTargetSelector.SlotState.FREE.ordinal();
    private static final int FULL = PatternTargetSelector.SlotState.FULL.ordinal();
    private static final int OFFLINE = PatternTargetSelector.SlotState.OFFLINE.ordinal();
    private static final int EXACT = MatchResult.Level.EXACT.ordinal();
    private static final int ACCEPTED = MatchResult.Level.ACCEPTED.ordinal();
    private static final int INCOMPATIBLE = MatchResult.Level.INCOMPATIBLE.ordinal();

    private static final String[] LOCATE_GLYPH = { "......oo......", "......oo......", "....oooooo....",
        "...o..oo..o...", "..o...oo...o..", "..o........o..", "oooo..oo..oooo", "oooo..oo..oooo", "..o........o..",
        "..o...oo...o..", "...o..oo..o...", "....oooooo....", "......oo......", "......oo......" };

    final GuiContainer parent;
    final int windowId;

    private TargetList list;
    private final Map<Row, String> haystacks = new IdentityHashMap<>();
    private List<Row> visible = Collections.emptyList();
    private int hiddenCount;
    private boolean queryIgnored;
    private String prefill = "";
    private String searchText;
    private GuiTextField search;
    private boolean showAll;
    private int scroll;
    private boolean draggingScrollbar;
    private int dragOffset;
    /** Shift was held on the last upload: stay on this screen when it succeeds. */
    private boolean stay;
    private long busyUntil;
    private String localMessage;
    private int mouseButtonsDown;
    private boolean returnPending;

    private int left, top, panelWidth, panelHeight, listX, listY, listWidth, visibleRows;
    private int toggleX, toggleY, toggleWidth;
    private final int[][] footerButtons = new int[4][];

    PatternUploadScreen(GuiContainer parent, TargetList list) {
        this.parent = parent;
        this.windowId = parent.inventorySlots.windowId;
        accept(list);
    }

    // ---- data -------------------------------------------------------------------------------------------------

    /** A complete list for this window arrived (client thread). */
    void accept(TargetList next) {
        boolean firstOrNewCategory = list == null || !next.category.equals(prefill);
        list = next;
        busyUntil = 0;
        if (!next.message.isEmpty()) localMessage = null;
        haystacks.clear();
        for (Row row : next.rows) haystacks.put(row, haystack(row));
        if (firstOrNewCategory) {
            prefill = next.category;
            searchText = prefill;
            if (search != null) search.setText(prefill);
            scroll = 0;
        }
        refilter();
        if (next.event == PatternUploadPackets.EVENT_UPLOADED && !stay && mc != null) back();
        stay = false;
    }

    private static String haystack(Row row) {
        return strip(text(row.name)) + "\n" + strip(text(row.route)) + "\n" + strip(kindName(row));
    }

    private static String strip(String value) {
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        return (plain == null ? "" : plain).toLowerCase(Locale.ROOT);
    }

    private static boolean available(Row row) {
        return row.state != FULL && row.state != OFFLINE && row.level != INCOMPATIBLE;
    }

    private static boolean uploadable(Row row) {
        return row.manual && row.state == FREE;
    }

    private void refilter() {
        String text = search == null ? (searchText == null ? "" : searchText) : search.getText();
        String[] terms = text.trim()
            .toLowerCase(Locale.ROOT)
            .split("\\s+");
        List<Row> byQuery = new ArrayList<>();
        for (Row row : list.rows) if (matches(row, terms)) byQuery.add(row);
        List<Row> shown = byState(byQuery);
        queryIgnored = false;
        if (shown.isEmpty() && !text.trim()
            .isEmpty() && text.equals(prefill)) {
            // An NEI category that names no target must not hide every target.
            queryIgnored = true;
            byQuery = list.rows;
            shown = byState(byQuery);
        }
        hiddenCount = byQuery.size() - shown.size();
        visible = shown;
        if (search != null) search.setTextColor(queryIgnored ? 0x808080 : 0xE0E0E0);
        scroll = clampScroll(scroll);
    }

    private List<Row> byState(List<Row> rows) {
        if (showAll) return rows;
        List<Row> result = new ArrayList<>(rows.size());
        for (Row row : rows) if (available(row)) result.add(row);
        return result;
    }

    private boolean matches(Row row, String[] terms) {
        String haystack = haystacks.get(row);
        if (haystack == null) return true;
        for (String term : terms) if (!term.isEmpty() && !haystack.contains(term)) return false;
        return true;
    }

    private int clampScroll(int value) {
        return Math.max(0, Math.min(value, visible.size() - visibleRows));
    }

    private boolean busy() {
        return System.currentTimeMillis() < busyUntil;
    }

    // ---- actions ----------------------------------------------------------------------------------------------

    private void send(int action, int argument, boolean blocking) {
        if (blocking) busyUntil = System.currentTimeMillis() + BUSY_TIMEOUT;
        PatternUploadPackets.action(windowId, action, argument);
    }

    private void upload(Row row, boolean stayHere) {
        if (busy() || !uploadable(row)) return;
        stay = stayHere;
        localMessage = null;
        send(PatternUploadPackets.ACTION_UPLOAD, row.token, true);
    }

    private void uploadTop(boolean stayHere) {
        for (Row row : visible) if (uploadable(row)) {
            upload(row, stayHere);
            return;
        }
    }

    private void locate(Row row) {
        localMessage = "gui.neoecoae.pattern_upload.located";
        send(PatternUploadPackets.ACTION_LOCATE, row.token, false);
    }

    private void autoUpload(boolean stayHere) {
        if (busy() || list.autoToken == 0) return;
        stay = stayHere;
        localMessage = null;
        send(PatternUploadPackets.ACTION_AUTO, 0, true);
    }

    private void undo() {
        if (busy() || !list.receipt) return;
        stay = true;
        localMessage = null;
        send(PatternUploadPackets.ACTION_UNDO, 0, true);
    }

    private void refresh() {
        if (busy()) return;
        send(PatternUploadPackets.ACTION_REFRESH, 0, true);
    }

    /** Back to the terminal; deferred until every mouse button is released so the release is not fed to it. */
    void back() {
        if (mouseButtonsDown != 0) {
            returnPending = true;
            return;
        }
        returnPending = false;
        PatternUploadClient.screenClosed(windowId);
        if (mc == null || mc.currentScreen != this) return;
        if (!containerAlive()) {
            mc.displayGuiScreen(null);
            return;
        }
        mc.displayGuiScreen(parent);
        // AEBaseGui.onGuiClosed cleared the shared item renderer's parent; only its constructor sets it again.
        if (mc.currentScreen == parent && parent instanceof AEBaseGui)
            AEBaseGui.aeRenderItem.parent = (AEBaseGui) parent;
    }

    private boolean containerAlive() {
        return mc.thePlayer != null && mc.thePlayer.openContainer == parent.inventorySlots
            && mc.thePlayer.openContainer.windowId == windowId;
    }

    private void click() {
        mc.getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("minecraft:gui.button.press"), 1.0F));
    }

    // ---- screen lifecycle -------------------------------------------------------------------------------------

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.min(340, width - 16);
        int rows = (height - 16 - HEADER - FOOTER) / ROW_HEIGHT;
        visibleRows = Math.max(2, Math.min(MAX_VISIBLE_ROWS, rows));
        panelHeight = HEADER + visibleRows * ROW_HEIGHT + FOOTER;
        left = (width - panelWidth) / 2;
        top = Math.max(2, (height - panelHeight) / 2);
        listX = left + 7;
        listY = top + HEADER;
        listWidth = panelWidth - 14 - SCROLLBAR_WIDTH - 2;
        toggleWidth = 78;
        toggleX = left + panelWidth - 7 - toggleWidth;
        toggleY = top + 30;
        String text = search != null ? search.getText() : searchText == null ? "" : searchText;
        boolean focused = search != null && search.isFocused();
        search = new GuiTextField(fontRendererObj, left + 8, top + 31, toggleX - 4 - (left + 8), 14);
        search.setMaxStringLength(128);
        search.setText(text);
        search.setFocused(focused);
        int buttonY = listY + visibleRows * ROW_HEIGHT + FOOTER - 26;
        int buttonWidth = (panelWidth - 14 - 3 * 4) / 4;
        for (int index = 0; index < 4; index++)
            footerButtons[index] = new int[] { left + 7 + index * (buttonWidth + 4), buttonY, buttonWidth, 20 };
        refilter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (search != null) searchText = search.getText();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        if (search != null) search.updateCursorCounter();
        if (mc.thePlayer == null || !containerAlive()) {
            // The server closed or replaced the terminal window; nothing to return to.
            PatternUploadClient.screenClosed(windowId);
            if (mc.currentScreen == this) mc.displayGuiScreen(null);
        }
    }

    // ---- input ------------------------------------------------------------------------------------------------

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            back();
            return;
        }
        boolean enter = key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER;
        if (enter) {
            uploadTop(isShiftKeyDown());
            return;
        }
        if (search.isFocused()) {
            if (search.textboxKeyTyped(character, key)) {
                scroll = 0;
                refilter();
            }
            return;
        }
        if (key == mc.gameSettings.keyBindInventory.getKeyCode()) {
            back();
            return;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(character)) {
            search.setFocused(true);
            if (search.textboxKeyTyped(character, key)) {
                scroll = 0;
                refilter();
            }
        }
    }

    @Override
    public void handleMouseInput() {
        if (mc.currentScreen != this) return;
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && mc.currentScreen == this) scroll = clampScroll(scroll + (wheel > 0 ? -1 : 1));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button >= 0 && button < 8) mouseButtonsDown |= 1 << button;
        if (inside(mouseX, mouseY, search.xPosition, search.yPosition, search.width, search.height) && button == 1) {
            search.setText("");
            search.setFocused(true);
            scroll = 0;
            refilter();
            return;
        }
        search.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        if (inside(mouseX, mouseY, toggleX, toggleY, toggleWidth, 16)) {
            click();
            showAll = !showAll;
            refilter();
            return;
        }
        int barX = listX + listWidth + 2;
        if (inside(mouseX, mouseY, barX, listY, SCROLLBAR_WIDTH, visibleRows * ROW_HEIGHT)) {
            int[] thumb = thumb();
            if (thumb != null) {
                draggingScrollbar = true;
                dragOffset = mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1] ? mouseY - thumb[0] : thumb[1] / 2;
                dragScrollbar(mouseY);
            }
            return;
        }
        for (int index = 0; index < footerButtons.length; index++) {
            int[] box = footerButtons[index];
            if (!inside(mouseX, mouseY, box[0], box[1], box[2], box[3]) || !footerEnabled(index)) continue;
            click();
            switch (index) {
                case 0 -> autoUpload(isShiftKeyDown());
                case 1 -> undo();
                case 2 -> refresh();
                default -> back();
            }
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) return;
        Row row = visible.get(index);
        int rowY = listY + (index - scroll) * ROW_HEIGHT;
        if (overLocate(mouseX, mouseY, rowY)) {
            click();
            locate(row);
        } else if (uploadable(row) && !busy()) {
            click();
            upload(row, isShiftKeyDown());
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state < 0) return;
        if (state < 8) mouseButtonsDown &= ~(1 << state);
        if (state == 0) draggingScrollbar = false;
        if (returnPending && mouseButtonsDown == 0) back();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (draggingScrollbar) dragScrollbar(mouseY);
    }

    private void dragScrollbar(int mouseY) {
        int[] thumb = thumb();
        if (thumb == null) return;
        int track = visibleRows * ROW_HEIGHT - thumb[1];
        int max = visible.size() - visibleRows;
        if (track <= 0 || max <= 0) return;
        float ratio = (mouseY - dragOffset - listY) / (float) track;
        scroll = clampScroll(Math.round(ratio * max));
    }

    /** Thumb top and height, or null when everything fits. */
    private int[] thumb() {
        int max = visible.size() - visibleRows;
        if (max <= 0) return null;
        int track = visibleRows * ROW_HEIGHT;
        int size = Math.max(12, track * visibleRows / visible.size());
        int y = listY + Math.round((track - size) * (scroll / (float) max));
        return new int[] { y, size };
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, listX, listY, listWidth, visibleRows * ROW_HEIGHT)) return -1;
        int slot = (mouseY - listY) / ROW_HEIGHT;
        if ((mouseY - listY) % ROW_HEIGHT >= ROW_HEIGHT - 2) return -1;
        int index = scroll + slot;
        return index >= 0 && index < visible.size() ? index : -1;
    }

    private boolean overLocate(int mouseX, int mouseY, int rowY) {
        return inside(mouseX, mouseY, locateX(), rowY + 4, LOCATE_SIZE, LOCATE_SIZE);
    }

    private int locateX() {
        return listX + listWidth - LOCATE_SIZE - 4;
    }

    private boolean footerEnabled(int index) {
        return switch (index) {
            case 0 -> list.autoToken != 0 && !busy();
            case 1 -> list.receipt && !busy();
            case 2 -> !busy();
            default -> true;
        };
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GL11.glColor4f(1, 1, 1, 1);
        UploadDraw.panel(left, top, panelWidth, panelHeight);
        drawHeader();
        drawSearch(mouseX, mouseY);
        drawRows(mouseX, mouseY);
        drawScrollbar();
        drawFooter(mouseX, mouseY);
        List<String> tooltip = tooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    private void drawHeader() {
        UploadDraw.slot(left + 7, top + 6);
        item(list.pattern, left + 8, top + 7);
        int textX = left + 29, textWidth = panelWidth - 36;
        String name = list.pattern == null ? StatCollector.translateToLocal("gui.neoecoae.pattern_upload.no_pattern")
            : list.pattern.getDisplayName();
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(name, textWidth), textX, top + 7, TEXT, false);
        StringBuilder detail = new StringBuilder();
        if (list.pattern != null) detail.append(
            StatCollector.translateToLocal(
                list.processing ? "gui.neoecoae.pattern_upload.processing" : "gui.neoecoae.pattern_upload.crafting"));
        if (!list.category.isEmpty()) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(
                StatCollector.translateToLocalFormatted("gui.neoecoae.pattern_upload.nei_category", list.category));
        }
        if (list.hinted) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.hinted"));
        }
        fontRendererObj
            .drawString(fontRendererObj.trimStringToWidth(detail.toString(), textWidth), textX, top + 17, MUTED, false);
    }

    private void drawSearch(int mouseX, int mouseY) {
        search.drawTextBox();
        if (search.getText()
            .isEmpty() && !search.isFocused())
            fontRendererObj.drawString(
                StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search"),
                search.xPosition + 4,
                search.yPosition + 3,
                0x707070,
                false);
        boolean hover = inside(mouseX, mouseY, toggleX, toggleY, toggleWidth, 16);
        UploadDraw.button(toggleX, toggleY, toggleWidth, 16, hover);
        String label = StatCollector.translateToLocal(
            showAll ? "gui.neoecoae.pattern_upload.show_available" : "gui.neoecoae.pattern_upload.show_all");
        centered(label, toggleX, toggleY + 4, toggleWidth, TEXT);
        StringBuilder info = new StringBuilder(
            StatCollector
                .translateToLocalFormatted("gui.neoecoae.pattern_upload.count", visible.size(), list.rows.size()));
        if (hiddenCount > 0 && !showAll) info.append(" · ")
            .append(StatCollector.translateToLocalFormatted("gui.neoecoae.pattern_upload.hidden", hiddenCount));
        if (queryIgnored) info.append(" · ")
            .append(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search.no_match"));
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(info.toString(), panelWidth - 14),
            left + 8,
            top + 49,
            MUTED,
            false);
    }

    private void drawRows(int mouseX, int mouseY) {
        if (visible.isEmpty()) {
            centered(
                StatCollector.translateToLocal("gui.neoecoae.pattern_upload.empty"),
                listX,
                listY + 8,
                listWidth,
                MUTED);
            return;
        }
        boolean busy = busy();
        for (int slot = 0; slot < visibleRows; slot++) {
            int index = scroll + slot;
            if (index >= visible.size()) break;
            Row row = visible.get(index);
            int x = listX, y = listY + slot * ROW_HEIGHT, w = listWidth, h = ROW_HEIGHT - 2;
            boolean clickable = uploadable(row) && !busy;
            boolean overLocate = overLocate(mouseX, mouseY, y);
            boolean hover = inside(mouseX, mouseY, x, y, w, h) && !overLocate;
            UploadDraw.button(x, y, w, h, hover && clickable);
            if (!uploadable(row)) UploadDraw.rect(x, y, x + w, y + h, 0x40000000);
            UploadDraw.rect(x + 2, y + 2, x + 5, y + h - 4, accent(row));
            item(row.icon, x + 7, y + 3);
            int textX = x + 27, textWidth = locateX() - 3 - textX;
            String title = title(row);
            if (row.token == list.autoToken && list.autoToken != 0) title = "§6★§r " + title;
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(title, textWidth),
                textX,
                y + 4,
                uploadable(row) ? TEXT : DISABLED_TEXT,
                false);
            fontRendererObj
                .drawString(fontRendererObj.trimStringToWidth(detail(row), textWidth), textX, y + 13, MUTED, false);
            int lx = locateX(), ly = y + 4;
            UploadDraw.button(lx, ly, LOCATE_SIZE, LOCATE_SIZE, overLocate);
            UploadDraw.glyph(lx + 1, ly + 1, LOCATE_GLYPH, 'o', overLocate ? 0xFFC03030 : 0xFF4A4860);
        }
    }

    private void drawScrollbar() {
        int x = listX + listWidth + 2, height = visibleRows * ROW_HEIGHT - 2;
        UploadDraw.rect(x, listY, x + SCROLLBAR_WIDTH, listY + height, 0x30000000);
        int[] thumb = thumb();
        if (thumb == null) return;
        int size = Math.min(thumb[1], listY + height - thumb[0]);
        UploadDraw.button(x, thumb[0], SCROLLBAR_WIDTH, size, draggingScrollbar);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int messageY = listY + visibleRows * ROW_HEIGHT + 2;
        String message;
        int color;
        if (busy()) {
            message = StatCollector.translateToLocal("gui.neoecoae.pattern_upload.busy");
            color = MUTED;
        } else if (localMessage != null) {
            message = text(localMessage);
            color = 0x2a7a2a;
        } else if (!list.message.isEmpty()) {
            message = text(list.message);
            color = list.error ? 0xb02020 : 0x2a7a2a;
        } else {
            message = StatCollector.translateToLocal("gui.neoecoae.pattern_upload.keys");
            color = MUTED;
        }
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(message, panelWidth - 14),
            left + 8,
            messageY + 2,
            color,
            false);
        String[] labels = { "gui.neoecoae.pattern_upload.auto_upload", "gui.neoecoae.pattern_upload.undo",
            "gui.neoecoae.pattern_upload.refresh", "gui.neoecoae.pattern_upload.back" };
        for (int index = 0; index < footerButtons.length; index++) {
            int[] box = footerButtons[index];
            boolean enabled = footerEnabled(index);
            boolean hover = inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
            UploadDraw.button(box[0], box[1], box[2], box[3], hover && enabled);
            if (!enabled) UploadDraw.rect(box[0], box[1], box[0] + box[2], box[1] + box[3], 0x40000000);
            centered(
                StatCollector.translateToLocal(labels[index]),
                box[0],
                box[1] + 5,
                box[2],
                enabled ? TEXT : DISABLED_TEXT);
        }
    }

    private List<String> tooltip(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < footerButtons.length; index++) {
            int[] box = footerButtons[index];
            if (!inside(mouseX, mouseY, box[0], box[1], box[2], box[3])) continue;
            if (index == 0) {
                if (list.autoToken != 0) {
                    lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.auto_ready"));
                    for (Row row : list.rows) if (row.token == list.autoToken) lines.add("§6★ §r" + title(row));
                    lines.add("§7" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.row.stay"));
                } else if (!list.autoReason.isEmpty()) lines.add("§7" + text(list.autoReason));
            } else if (index == 1) {
                lines.add(
                    list.receipt ? StatCollector
                        .translateToLocalFormatted("gui.neoecoae.pattern_upload.undo.tooltip", text(list.receiptName))
                        : "§7" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.reason.no_receipt"));
            } else
                if (index == 3) lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.back.tooltip"));
            return lines;
        }
        if (inside(mouseX, mouseY, search.xPosition, search.yPosition, search.width, search.height)) {
            lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search.tooltip"));
            return lines;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) return lines;
        Row row = visible.get(index);
        if (overLocate(mouseX, mouseY, listY + (index - scroll) * ROW_HEIGHT)) {
            lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.locate"));
            lines.add("§7" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.locate.tooltip"));
            return lines;
        }
        lines.add(title(row));
        String kind = kindName(row);
        if (!kind.isEmpty()) lines.add("§7" + kind);
        lines.add(
            levelColor(row) + text(
                MatchResult.Level.values()[safe(row.level, MatchResult.Level.values().length)].translationKey()));
        if (!row.reason.isEmpty()) lines.add("§7" + text(row.reason));
        lines.add(text(stateKey(row)));
        lines.add(
            StatCollector
                .translateToLocalFormatted("gui.neoecoae.pattern_upload.empty_slots", row.emptySlots, row.totalSlots));
        lines.add(
            StatCollector
                .translateToLocalFormatted("gui.neoecoae.pattern_upload.free_members", row.freeMembers, row.members));
        if (row.duplicates > 0) lines
            .add(StatCollector.translateToLocalFormatted("gui.neoecoae.pattern_upload.duplicates", row.duplicates));
        if (row.circuit >= 0) lines.add(
            StatCollector
                .translateToLocalFormatted("gui.neoecoae.pattern_upload.button.auto.tooltip.circuit", row.circuit));
        if (row.programmingCover)
            lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.programming_cover"));
        lines.add(
            "§7" + StatCollector
                .translateToLocalFormatted("gui.neoecoae.pattern_upload.location", row.dimension, row.x, row.y, row.z));
        if (row.token == list.autoToken && list.autoToken != 0)
            lines.add("§6★ " + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.auto_choice"));
        if (uploadable(row)) {
            lines.add("§a" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.row.click"));
            lines.add("§a" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.row.stay"));
        } else lines.add("§c" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.row.disabled"));
        return lines;
    }

    private void centered(String text, int x, int y, int width, int color) {
        String trimmed = fontRendererObj.trimStringToWidth(text, width - 4);
        fontRendererObj.drawString(trimmed, x + (width - fontRendererObj.getStringWidth(trimmed)) / 2, y, color, false);
    }

    private void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;
        GL11.glPushMatrix();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1, 1, 1, 1);
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        } catch (RuntimeException ignored) {
            // A broken item renderer must not take the list down.
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glPopMatrix();
        }
    }

    // ---- text -------------------------------------------------------------------------------------------------

    private static int accent(Row row) {
        if (row.state != FREE) return 0xff9a9a9a;
        if (row.level == EXACT || row.level == ACCEPTED) return 0xff3fa03f;
        if (row.level == INCOMPATIBLE) return 0xffb03030;
        return 0xffd0a020;
    }

    private static String levelColor(Row row) {
        if (row.level == EXACT || row.level == ACCEPTED) return "§a";
        if (row.level == INCOMPATIBLE) return "§c";
        return "§e";
    }

    private static int safe(int ordinal, int length) {
        return ordinal >= 0 && ordinal < length ? ordinal : length - 1;
    }

    private static String stateKey(Row row) {
        PatternTargetSelector.SlotState[] states = PatternTargetSelector.SlotState.values();
        return states[safe(row.state, states.length)].translationKey();
    }

    static String title(Row row) {
        String name = text(row.name);
        return row.route.isEmpty() ? name : name + " → " + text(row.route);
    }

    private static String detail(Row row) {
        StringBuilder line = new StringBuilder(
            text(MatchResult.Level.values()[safe(row.level, MatchResult.Level.values().length)].translationKey()))
                .append(" · ")
                .append(text(stateKey(row)))
                .append(" · ")
                .append(
                    StatCollector.translateToLocalFormatted(
                        "gui.neoecoae.pattern_upload.empty_slots",
                        row.emptySlots,
                        row.totalSlots))
                .append(" · ")
                .append(StatCollector.translateToLocalFormatted("gui.neoecoae.pattern_upload.members", row.members));
        if (row.duplicates > 0) line.append(" · ")
            .append(StatCollector.translateToLocalFormatted("gui.neoecoae.pattern_upload.duplicates", row.duplicates));
        return line.toString();
    }

    static String kindName(Row row) {
        PatternUploadTarget.Kind[] kinds = PatternUploadTarget.Kind.values();
        return row.kind >= 0 && row.kind < kinds.length
            ? StatCollector.translateToLocal(kinds[row.kind].translationKey())
            : "";
    }

    /** Translation key, block/item key without ".name", or a literal. */
    static String text(String key) {
        if (key == null || key.isEmpty()) return "";
        if (StatCollector.canTranslate(key)) return StatCollector.translateToLocal(key);
        if (StatCollector.canTranslate(key + ".name")) return StatCollector.translateToLocal(key + ".name");
        return key;
    }
}
