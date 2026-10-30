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
 * Client-only target list shown as a floating window over an AE2 pattern terminal. It is a plain {@link GuiScreen}:
 * the terminal container stays open on both sides and is drawn underneath, every action is a packet naming the
 * terminal window, and leaving always re-displays the terminal GUI instead of closing the window.
 */
@SideOnly(Side.CLIENT)
final class PatternUploadScreen extends GuiScreen {

    private static final int TEXT = 0x343548, MUTED = 0x6e6c80, DISABLED_TEXT = 0x9a98a8;
    /** Body text is drawn at 7/9 of the vanilla font, like DataEnergistics' upload window. */
    private static final float SMALL = 0.75F;
    private static final int PANEL_WIDTH = 220, TITLE_HEIGHT = 14, ROW_HEIGHT = 20, CARD_HEIGHT = 19;
    private static final int MAX_VISIBLE_ROWS = 10, HEADER = 60, FOOTER = 32, SCROLLBAR_WIDTH = 4;
    private static final int CLOSE_SIZE = 10, TOGGLE_WIDTH = 46, BUTTON_HEIGHT = 14;
    private static final long BUSY_TIMEOUT = 5_000L;

    private static final int FREE = PatternTargetSelector.SlotState.FREE.ordinal();
    private static final int FULL = PatternTargetSelector.SlotState.FULL.ordinal();
    private static final int OFFLINE = PatternTargetSelector.SlotState.OFFLINE.ordinal();
    private static final int EXACT = MatchResult.Level.EXACT.ordinal();
    private static final int ACCEPTED = MatchResult.Level.ACCEPTED.ordinal();
    private static final int INCOMPATIBLE = MatchResult.Level.INCOMPATIBLE.ordinal();

    private static final String[] CLOSE_GLYPH = { "o.....o", ".o...o.", "..o.o..", "...o...", "..o.o..", ".o...o.",
        "o.....o" };

    /** Window offset from the centred position; kept for the session so the window reopens where it was left. */
    private static int offsetX, offsetY;

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
    private boolean draggingWindow;
    private int dragStartX, dragStartY, dragStartOffsetX, dragStartOffsetY;
    /** Shift was held on the last upload: stay on this screen when it succeeds. */
    private boolean stay;
    private long busyUntil;
    private String localMessage;
    private int mouseButtonsDown;
    private boolean returnPending;
    /** The terminal underneath threw while drawing once; fall back to the plain dimmed background. */
    private boolean parentRenderFailed;

    private int left, top, panelWidth, panelHeight, listX, listY, listWidth, visibleRows;
    private int toggleX, toggleY, closeX, closeY;
    private final int[][] footerButtons = new int[3][];

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
        if (parent.width != width || parent.height != height) parent.setWorldAndResolution(mc, width, height);
        panelWidth = Math.min(PANEL_WIDTH, width - 8);
        int rows = (height - 8 - HEADER - FOOTER) / ROW_HEIGHT;
        visibleRows = Math.max(2, Math.min(MAX_VISIBLE_ROWS, rows));
        panelHeight = HEADER + visibleRows * ROW_HEIGHT + FOOTER;
        layout();
    }

    /** Places everything from the window offset; called on init and while the title bar is dragged. */
    private void layout() {
        int centredX = (width - panelWidth) / 2, centredY = (height - panelHeight) / 2;
        left = Math.max(2, Math.min(width - panelWidth - 2, centredX + offsetX));
        top = Math.max(2, Math.min(height - panelHeight - 2, centredY + offsetY));
        offsetX = left - centredX;
        offsetY = top - centredY;
        closeX = left + panelWidth - CLOSE_SIZE - 3;
        closeY = top + 2;
        listX = left + 5;
        listY = top + HEADER;
        listWidth = panelWidth - 10 - SCROLLBAR_WIDTH - 2;
        toggleX = left + panelWidth - 5 - TOGGLE_WIDTH;
        toggleY = top + 38;
        String text = search != null ? search.getText() : searchText == null ? "" : searchText;
        boolean focused = search != null && search.isFocused();
        search = new GuiTextField(fontRendererObj, left + 6, toggleY + 1, toggleX - 3 - (left + 6), 10);
        search.setMaxStringLength(128);
        search.setText(text);
        search.setFocused(focused);
        int buttonY = top + panelHeight - BUTTON_HEIGHT - 5;
        int buttonWidth = (panelWidth - 10 - 2 * 3) / 3;
        for (int index = 0; index < footerButtons.length; index++) footerButtons[index] = new int[] {
            left + 5 + index * (buttonWidth + 3), buttonY, buttonWidth, BUTTON_HEIGHT };
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
        int index = rowAt(mouseX, mouseY);
        if (button == 1 && index >= 0) {
            click();
            locate(visible.get(index));
            return;
        }
        if (button != 0) return;
        if (inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            click();
            back();
            return;
        }
        if (inside(mouseX, mouseY, left, top, panelWidth, TITLE_HEIGHT)) {
            draggingWindow = true;
            dragStartX = mouseX;
            dragStartY = mouseY;
            dragStartOffsetX = offsetX;
            dragStartOffsetY = offsetY;
            return;
        }
        if (inside(mouseX, mouseY, toggleX, toggleY, TOGGLE_WIDTH, 12)) {
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
        for (int footer = 0; footer < footerButtons.length; footer++) {
            int[] box = footerButtons[footer];
            if (!inside(mouseX, mouseY, box[0], box[1], box[2], box[3]) || !footerEnabled(footer)) continue;
            click();
            switch (footer) {
                case 0 -> autoUpload(isShiftKeyDown());
                case 1 -> undo();
                default -> refresh();
            }
            return;
        }
        if (index < 0) return;
        Row row = visible.get(index);
        if (uploadable(row) && !busy()) {
            click();
            upload(row, isShiftKeyDown());
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state < 0) return;
        if (state < 8) mouseButtonsDown &= ~(1 << state);
        if (state == 0) {
            draggingScrollbar = false;
            draggingWindow = false;
        }
        if (returnPending && mouseButtonsDown == 0) back();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (draggingWindow) {
            offsetX = dragStartOffsetX + mouseX - dragStartX;
            offsetY = dragStartOffsetY + mouseY - dragStartY;
            layout();
        } else if (draggingScrollbar) dragScrollbar(mouseY);
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
        int size = Math.max(10, track * visibleRows / visible.size());
        int y = listY + Math.round((track - size) * (scroll / (float) max));
        return new int[] { y, size };
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, listX, listY, listWidth, visibleRows * ROW_HEIGHT)) return -1;
        int slot = (mouseY - listY) / ROW_HEIGHT;
        if ((mouseY - listY) % ROW_HEIGHT >= CARD_HEIGHT) return -1;
        int index = scroll + slot;
        return index >= 0 && index < visible.size() ? index : -1;
    }

    private boolean footerEnabled(int index) {
        return switch (index) {
            case 0 -> list.autoToken != 0 && !busy();
            case 1 -> list.receipt && !busy();
            default -> !busy();
        };
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawParent(partialTicks);
        GL11.glColor4f(1, 1, 1, 1);
        UploadDraw.panel(left, top, panelWidth, panelHeight);
        drawTitle(mouseX, mouseY);
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

    /** The terminal stays visible behind the floating window; it never sees the real mouse position. */
    private void drawParent(float partialTicks) {
        if (!parentRenderFailed) {
            try {
                if (parent instanceof AEBaseGui) AEBaseGui.aeRenderItem.parent = (AEBaseGui) parent;
                parent.drawScreen(-10_000, -10_000, partialTicks);
            } catch (RuntimeException ignored) {
                parentRenderFailed = true;
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, 1);
            // Items in the terminal were drawn with depth; the window must not be clipped by them.
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            UploadDraw.rect(0, 0, width, height, 0x50000000);
        }
        if (parentRenderFailed) drawDefaultBackground();
    }

    private void drawTitle(int mouseX, int mouseY) {
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(
                StatCollector.translateToLocal("gui.neoecoae.pattern_upload.title"),
                closeX - left - 10),
            left + 6,
            top + 4,
            TEXT,
            false);
        boolean hover = inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        UploadDraw.glyph(closeX + 1, closeY + 1, CLOSE_GLYPH, 'o', hover ? 0xFFC03030 : 0xFF4A4860);
        UploadDraw.rect(left + 4, top + TITLE_HEIGHT, left + panelWidth - 4, top + TITLE_HEIGHT + 1, 0x30000000);
    }

    private void drawHeader() {
        int y = top + TITLE_HEIGHT + 3;
        UploadDraw.slot(left + 5, y);
        item(list.pattern, left + 6, y + 1);
        int textX = left + 27, textWidth = left + panelWidth - 5 - textX;
        String name = list.pattern == null ? StatCollector.translateToLocal("gui.neoecoae.pattern_upload.no_pattern")
            : list.pattern.getDisplayName();
        small(trim(name, textWidth), textX, y + 2, TEXT);
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
        small(trim(detail.toString(), textWidth), textX, y + 10, MUTED);
    }

    private void drawSearch(int mouseX, int mouseY) {
        search.drawTextBox();
        if (search.getText()
            .isEmpty() && !search.isFocused())
            small(
                trim(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search"), search.width - 6),
                search.xPosition + 3,
                search.yPosition + 2,
                0x707070);
        boolean hover = inside(mouseX, mouseY, toggleX, toggleY, TOGGLE_WIDTH, 12);
        UploadDraw.button(toggleX, toggleY, TOGGLE_WIDTH, 12, hover);
        String label = StatCollector.translateToLocal(
            showAll ? "gui.neoecoae.pattern_upload.show_available" : "gui.neoecoae.pattern_upload.show_all");
        smallCentered(label, toggleX, toggleY + 3, TOGGLE_WIDTH, TEXT);
        StringBuilder info = new StringBuilder(
            StatCollector
                .translateToLocalFormatted("gui.neoecoae.pattern_upload.count", visible.size(), list.rows.size()));
        if (hiddenCount > 0 && !showAll) info.append(" · ")
            .append(StatCollector.translateToLocalFormatted("gui.neoecoae.pattern_upload.hidden", hiddenCount));
        if (queryIgnored) info.append(" · ")
            .append(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search.no_match"));
        small(trim(info.toString(), panelWidth - 12), left + 6, toggleY + 14, MUTED);
    }

    private void drawRows(int mouseX, int mouseY) {
        if (visible.isEmpty()) {
            smallCentered(
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
            int x = listX, y = listY + slot * ROW_HEIGHT, w = listWidth, h = CARD_HEIGHT;
            boolean enabled = uploadable(row);
            boolean hover = inside(mouseX, mouseY, x, y, w, h);
            UploadDraw.button(x, y, w, h, hover && enabled && !busy);
            if (!enabled) UploadDraw.rect(x, y, x + w, y + h, 0x40000000);
            UploadDraw.rect(x + 2, y + 2, x + 4, y + h - 3, accent(row));
            item(row.icon, x + 6, y + 1);
            String count = slotCount(row);
            float countWidth = fontRendererObj.getStringWidth(count) * SMALL;
            float countX = x + w - 5 - countWidth;
            small(count, countX, y + 6, enabled ? slotColor(row) : DISABLED_TEXT);
            String name = text(row.name);
            if (row.token == list.autoToken && list.autoToken != 0) name = "§6★§r " + name;
            int nameX = x + 25;
            small(trim(name, (int) (countX - 4 - nameX)), nameX, y + 6, enabled ? TEXT : DISABLED_TEXT);
        }
    }

    private void drawScrollbar() {
        int x = listX + listWidth + 2, height = visibleRows * ROW_HEIGHT - 1;
        UploadDraw.rect(x, listY, x + SCROLLBAR_WIDTH, listY + height, 0x30000000);
        int[] thumb = thumb();
        if (thumb == null) return;
        int size = Math.min(thumb[1], listY + height - thumb[0]);
        UploadDraw.rect(x, thumb[0], x + SCROLLBAR_WIDTH, thumb[0] + size, draggingScrollbar ? 0xFF8A88A0 : 0xFFB8B6C8);
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
        small(trim(message, panelWidth - 12), left + 6, messageY + 1, color);
        String[] labels = { "gui.neoecoae.pattern_upload.auto_upload", "gui.neoecoae.pattern_upload.undo",
            "gui.neoecoae.pattern_upload.refresh" };
        for (int index = 0; index < footerButtons.length; index++) {
            int[] box = footerButtons[index];
            boolean enabled = footerEnabled(index);
            boolean hover = inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
            UploadDraw.button(box[0], box[1], box[2], box[3], hover && enabled);
            if (!enabled) UploadDraw.rect(box[0], box[1], box[0] + box[2], box[1] + box[3], 0x40000000);
            smallCentered(
                StatCollector.translateToLocal(labels[index]),
                box[0],
                box[1] + 4,
                box[2],
                enabled ? TEXT : DISABLED_TEXT);
        }
    }

    private List<String> tooltip(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        if (draggingWindow) return lines;
        if (inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.back.tooltip"));
            return lines;
        }
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
            }
            return lines;
        }
        if (inside(mouseX, mouseY, search.xPosition, search.yPosition, search.width, search.height)) {
            lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search.tooltip"));
            return lines;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) return lines;
        Row row = visible.get(index);
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
        lines.add("§b" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.row.locate"));
        return lines;
    }

    /** Trims to {@code width} screen pixels of small text. */
    private String trim(String text, int width) {
        return fontRendererObj.trimStringToWidth(text, Math.max(0, (int) (width / SMALL)));
    }

    private void small(String text, float x, float y, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(SMALL, SMALL, 1);
        fontRendererObj.drawString(text, 0, 0, color, false);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void smallCentered(String text, int x, int y, int width, int color) {
        String trimmed = trim(text, width - 4);
        small(trimmed, x + (width - fontRendererObj.getStringWidth(trimmed) * SMALL) / 2, y, color);
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

    /** Used / total pattern slots, as DataEnergistics shows it. */
    private static String slotCount(Row row) {
        if (row.totalSlots <= 0) return "-";
        return Math.max(0, row.totalSlots - row.emptySlots) + "/" + row.totalSlots;
    }

    private static int slotColor(Row row) {
        if (row.totalSlots <= 0) return MUTED;
        long used = Math.max(0, row.totalSlots - row.emptySlots);
        if (used * 100L < row.totalSlots * 70L) return 0x168a16;
        if (used * 100L < row.totalSlots * 90L) return 0xc09000;
        return 0xd52c2c;
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
