package cn.dancingsnow.neoecoae.platform.ui.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;

import appeng.client.gui.AEBaseGui;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTargetSelector;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets.Row;
import cn.dancingsnow.neoecoae.platform.network.PatternUploadPackets.TargetList;
import cn.dancingsnow.neoecoae.platform.ui.NativeAe2Button;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
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
    /** Same character as {@code GtPatternRoutes.MODE_SEPARATOR}, kept here so the screen never loads GT classes. */
    private static final char MODE_SEPARATOR = '';
    private static final int PANEL_WIDTH = 420, TITLE_HEIGHT = 14, ROW_HEIGHT = 24, CARD_HEIGHT = 23;
    private static final int DEFAULT_VISIBLE_ROWS = 12, HEADER = 102, FOOTER = 58, SCROLLBAR_WIDTH = 7;
    private static final int CLOSE_SIZE = 10, BUTTON_HEIGHT = 16;
    private static final String[] HEIGHT_ICON = { "..o..", ".ooo.", "o.o.o", "..o..", "o.o.o", ".ooo.", "..o.." };
    private static final long BUSY_TIMEOUT = 5_000L;

    private static final int FREE = PatternTargetSelector.SlotState.FREE.ordinal();
    private static final int FULL = PatternTargetSelector.SlotState.FULL.ordinal();
    private static final int OFFLINE = PatternTargetSelector.SlotState.OFFLINE.ordinal();
    private static final int DUPLICATE = PatternTargetSelector.SlotState.DUPLICATE.ordinal();
    private static final String[] FILTERS = { "all", "available", "duplicate", "full", "offline", "incompatible" };
    private static final int ACCEPTED = MatchResult.Level.ACCEPTED.ordinal();
    private static final int INCOMPATIBLE = MatchResult.Level.INCOMPATIBLE.ordinal();

    /** Window offset from the centred position; kept for the session so the window reopens where it was left. */
    private static int offsetX, offsetY;
    /** Preferred list height, retained when the upload window is reopened during this game session. */
    private static int preferredRows = DEFAULT_VISIBLE_ROWS;

    final GuiContainer parent;
    final int windowId;

    private TargetList list;
    private final Map<Row, String> haystacks = new IdentityHashMap<>();
    private List<Row> visible = Collections.emptyList();
    private List<Row> scoped = Collections.emptyList();
    private final List<TreeRow> tree = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private boolean mergeTypes = true, revealSelection;
    private int filter = 1;
    private Integer dimension;
    private int hiddenCount;
    private boolean queryIgnored;
    private String prefill = "";
    private String searchText;
    private GuiTextField search;
    private boolean mappingMode;
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
    private int toggleX, toggleY, closeX, closeY, heightX;
    private int dimensionX, dimensionWidth, mergeWidth;
    private final int[][] footerButtons = new int[3][];
    private final int[][] mappingButtons = new int[3][];

    PatternUploadScreen(GuiContainer parent, TargetList list) {
        this.parent = parent;
        this.windowId = parent.inventorySlots.windowId;
        accept(list);
    }

    // ---- data -------------------------------------------------------------------------------------------------

    /** A complete list for this window arrived (client thread). */
    void accept(TargetList next) {
        boolean firstOrNewCategory = list == null || !next.categoryId.equals(list.categoryId)
            || !next.category.equals(list.category);
        list = next;
        busyUntil = 0;
        if (!next.message.isEmpty()) localMessage = null;
        haystacks.clear();
        for (Row row : next.rows) haystacks.put(row, haystack(row));
        if (firstOrNewCategory || next.event == PatternUploadPackets.EVENT_MAPPING) {
            revealSelection = true;
            mappingMode = false;
            prefill = text(next.category);
            // Material orders target a container rather than the multiblock named by the NEI page.
            if (cn.dancingsnow.neoecoae.crafting.upload.RouteHint.MATERIAL_ORDER_CATEGORY.equals(next.categoryId))
                prefill = "";
            if (next.mappingEnabled) for (Row row : next.rows) if (row.token == next.mappingToken) {
                prefill = hostName(row);
                break;
            }
            searchText = prefill;
            if (search != null) search.setText(prefill);
            scroll = 0;
        }
        if (!next.canMap) mappingMode = false;
        refilter();
        if (next.event == PatternUploadPackets.EVENT_UPLOADED && !stay && mc != null) back();
        stay = false;
    }

    private static String haystack(Row row) {
        return strip(hostName(row)) + "\n"
            + strip(hostText(row.route, row.icon))
            + "\n"
            + strip(kindName(row))
            + "\n"
            + strip(row.voltageTier)
            + "\n"
            + row.dimension
            + " "
            + row.x
            + " "
            + row.y
            + " "
            + row.z
            + " "
            + row.circuit;
    }

    private static String strip(String value) {
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        return (plain == null ? "" : plain).toLowerCase(Locale.ROOT);
    }

    private static boolean uploadable(Row row) {
        return row.manual && row.state == FREE;
    }

    private static boolean mappable(Row row) {
        return row.mappable;
    }

    private static MatchResult.Level level(Row row) {
        MatchResult.Level[] levels = MatchResult.Level.values();
        return row.level >= 0 && row.level < levels.length ? levels[row.level] : MatchResult.Level.INCOMPATIBLE;
    }

    private void refilter() {
        String text = search == null ? (searchText == null ? "" : searchText) : search.getText();
        String[] terms = text.trim()
            .toLowerCase(Locale.ROOT)
            .split("\\s+");
        List<Row> byQuery = new ArrayList<>();
        for (Row row : list.rows)
            if ((dimension == null || row.dimension == dimension) && matches(row, terms)) byQuery.add(row);
        List<Row> shown = byState(byQuery);
        queryIgnored = false;
        if (byQuery.isEmpty() && !text.trim()
            .isEmpty() && text.equals(prefill)) {
            // An NEI category that names no target must not hide every target.
            queryIgnored = true;
            byQuery = new ArrayList<>();
            for (Row row : list.rows) if (dimension == null || row.dimension == dimension) byQuery.add(row);
            shown = byState(byQuery);
        }
        hiddenCount = byQuery.size() - shown.size();
        scoped = byQuery;
        visible = shown;
        rebuildTree();
        if (search != null) search.setTextColor(queryIgnored ? 0x808080 : 0xE0E0E0);
        scroll = clampScroll(scroll);
    }

    private List<Row> byState(List<Row> rows) {
        List<Row> result = new ArrayList<>(rows.size());
        for (Row row : rows) if (mappingMode ? mappable(row) : matchesFilter(row, filter)) result.add(row);
        return result;
    }

    private static boolean matchesFilter(Row row, int filter) {
        return switch (filter) {
            case 1 -> uploadable(row);
            case 2 -> row.state == DUPLICATE;
            case 3 -> row.state == FULL;
            case 4 -> row.state == OFFLINE;
            case 5 -> row.level == INCOMPATIBLE;
            default -> true;
        };
    }

    /** Presentation groups never replace the server tokens of concrete upload inventories. */
    private static final class TreeRow {

        final Row target;
        final String key;
        final List<Row> children;
        final boolean child;

        TreeRow(Row target, String key, List<Row> children, boolean child) {
            this.target = target;
            this.key = key;
            this.children = children;
            this.child = child;
        }

        boolean group() {
            return children != null;
        }
    }

    private static String machineKey(Row row) {
        String machine = row.route.isEmpty() ? row.name : row.route;
        return row.kind + "\n"
            + machine
            + "\n"
            + row.voltageTier
            + "\n"
            + (row.icon == null ? "" : Item.getIdFromItem(row.icon.getItem()) + ":" + row.icon.getItemDamage());
    }

    private static String machineName(Row row) {
        return row.route.isEmpty() ? hostText(row.name, row.icon) : hostText(row.route, row.icon);
    }

    private void rebuildTree() {
        tree.clear();
        if (!mergeTypes) {
            for (Row row : visible) tree.add(new TreeRow(row, "", null, false));
            revealSelection = false;
            return;
        }
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : visible) groups.computeIfAbsent(machineKey(row), ignored -> new ArrayList<>())
            .add(row);
        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {
            List<Row> children = entry.getValue();
            if (children.size() == 1) {
                tree.add(new TreeRow(children.get(0), "", null, false));
                continue;
            }
            String key = entry.getKey();
            if (revealSelection) for (Row row : children)
                if (row.token == list.autoToken || row.token == list.mappingToken) expanded.add(key);
            tree.add(new TreeRow(children.get(0), key, children, false));
            if (expanded.contains(key)) for (Row row : children) tree.add(new TreeRow(row, key, null, true));
        }
        revealSelection = false;
    }

    private void cycleDimension(int direction) {
        List<Integer> dimensions = new ArrayList<>();
        for (Row row : list.rows) if (!dimensions.contains(row.dimension)) dimensions.add(row.dimension);
        Collections.sort(dimensions);
        dimensions.add(0, null);
        int index = dimensions.indexOf(dimension);
        dimension = dimensions.get(Math.floorMod(index + direction, dimensions.size()));
        scroll = 0;
        refilter();
    }

    private boolean matches(Row row, String[] terms) {
        String haystack = haystacks.get(row);
        if (haystack == null) return true;
        for (String term : terms) if (!term.isEmpty() && !UploadSearch.contains(haystack, term)) return false;
        return true;
    }

    private int clampScroll(int value) {
        return Math.max(0, Math.min(value, tree.size() - visibleRows));
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

    private void mappingAction(int action) {
        if (!mappingEnabled(action)) return;
        if (action == 0) {
            mappingMode = !mappingMode;
            localMessage = mappingMode ? "gui.neoecoae.pattern_upload.mapping.pick" : null;
            if (mappingMode && search.getText()
                .equals(prefill)) search.setText("");
            scroll = 0;
            refilter();
        } else {
            localMessage = null;
            send(
                action == 1 ? PatternUploadPackets.ACTION_TOGGLE_MAPPING : PatternUploadPackets.ACTION_REMOVE_MAPPING,
                0,
                true);
        }
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
        visibleRows = Math.max(1, Math.min(preferredRows, maxRows()));
        panelHeight = HEADER + visibleRows * ROW_HEIGHT + FOOTER;
        layout();
    }

    private int maxRows() {
        return Math.max(1, (height - 8 - HEADER - FOOTER) / ROW_HEIGHT);
    }

    private void resize(int direction) {
        int rows = Math.max(1, Math.min(maxRows(), visibleRows + direction));
        if (rows == visibleRows) return;
        preferredRows = rows;
        visibleRows = rows;
        panelHeight = HEADER + visibleRows * ROW_HEIGHT + FOOTER;
        // Keep the height control under the pointer unless the expanded window reaches a screen edge.
        offsetY = top - (height - panelHeight) / 2;
        draggingScrollbar = false;
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
        heightX = left + 3;
        listX = left + 5;
        listY = top + HEADER;
        listWidth = panelWidth - 10 - SCROLLBAR_WIDTH - 2;
        mergeWidth = Math.min(78, panelWidth / 4);
        dimensionWidth = Math.min(86, panelWidth / 4);
        toggleX = left + panelWidth - 5 - mergeWidth;
        dimensionX = toggleX - 3 - dimensionWidth;
        toggleY = top + 82;
        String text = search != null ? search.getText() : searchText == null ? "" : searchText;
        boolean focused = search != null && search.isFocused();
        // The field uses local font coordinates; rendering and mouse input share the same scale.
        search = new GuiTextField(fontRendererObj, 0, 0, (int) ((dimensionX - left - 14) / SMALL), 10);
        search.setEnableBackgroundDrawing(false);
        search.setMaxStringLength(128);
        search.setText(text);
        search.setFocused(focused);
        int buttonY = top + panelHeight - BUTTON_HEIGHT - 5;
        int buttonWidth = (panelWidth - 10 - 2 * 3) / 3;
        for (int index = 0; index < footerButtons.length; index++) footerButtons[index] = new int[] {
            left + 5 + index * (buttonWidth + 3), buttonY, buttonWidth, BUTTON_HEIGHT };
        for (int index = 0; index < mappingButtons.length; index++) mappingButtons[index] = new int[] {
            left + 5 + index * (buttonWidth + 3), buttonY - BUTTON_HEIGHT - 3, buttonWidth, BUTTON_HEIGHT };
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
            if (!mappingMode) uploadTop(isShiftKeyDown());
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
        if (insideSearch(mouseX, mouseY) && button == 1) {
            search.setText("");
            search.setFocused(true);
            scroll = 0;
            refilter();
            return;
        }
        search.mouseClicked(
            (int) Math.floor((mouseX - left - 8) / SMALL),
            (int) Math.floor((mouseY - toggleY - 4) / SMALL),
            button);
        search.setFocused(insideSearch(mouseX, mouseY));
        if ((button == 0 || button == 1) && inside(mouseX, mouseY, heightX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            click();
            resize(button == 0 ? 1 : -1);
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (button == 1 && index >= 0) {
            if (tree.get(index)
                .group()) return;
            click();
            locate(tree.get(index).target);
            return;
        }
        if ((button == 0 || button == 1) && inside(mouseX, mouseY, dimensionX, toggleY, dimensionWidth, 16)) {
            click();
            cycleDimension(button == 0 ? 1 : -1);
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
        if (inside(mouseX, mouseY, toggleX, toggleY, mergeWidth, 16)) {
            click();
            mergeTypes = !mergeTypes;
            scroll = 0;
            refilter();
            return;
        }
        if (!mappingMode && inside(mouseX, mouseY, left + 5, top + 65, panelWidth - 10, 15)) {
            click();
            filter = Math.min(FILTERS.length - 1, (mouseX - left - 5) * FILTERS.length / (panelWidth - 10));
            scroll = 0;
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
        for (int action = 0; action < mappingButtons.length; action++) {
            int[] box = mappingButtons[action];
            if (!inside(mouseX, mouseY, box[0], box[1], box[2], box[3]) || !mappingEnabled(action)) continue;
            click();
            mappingAction(action);
            return;
        }
        if (index < 0) return;
        TreeRow entry = tree.get(index);
        if (entry.group()) {
            click();
            if (!expanded.remove(entry.key)) expanded.add(entry.key);
            rebuildTree();
            scroll = clampScroll(scroll);
            return;
        }
        Row row = entry.target;
        if (mouseX >= listX + listWidth - 18) {
            click();
            locate(row);
            return;
        }
        if (mappingMode) {
            if (!busy() && mappable(row)) {
                click();
                localMessage = null;
                send(PatternUploadPackets.ACTION_SAVE_MAPPING, row.token, true);
            }
            return;
        }
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
        int track = visibleRows * ROW_HEIGHT - 1 - thumb[1];
        int max = tree.size() - visibleRows;
        if (track <= 0 || max <= 0) return;
        float ratio = (mouseY - dragOffset - listY) / (float) track;
        scroll = clampScroll(Math.round(ratio * max));
    }

    /** Thumb top and height, or null when everything fits. */
    private int[] thumb() {
        int max = tree.size() - visibleRows;
        if (max <= 0) return null;
        int track = visibleRows * ROW_HEIGHT - 1;
        int size = 15;
        int y = listY + Math.round((track - size) * (scroll / (float) max));
        return new int[] { y, size };
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, listX, listY, listWidth, visibleRows * ROW_HEIGHT)) return -1;
        int slot = (mouseY - listY) / ROW_HEIGHT;
        if ((mouseY - listY) % ROW_HEIGHT >= CARD_HEIGHT) return -1;
        int index = scroll + slot;
        return index >= 0 && index < tree.size() ? index : -1;
    }

    private boolean footerEnabled(int index) {
        return switch (index) {
            case 0 -> list.autoToken != 0 && !busy() && !mappingMode;
            case 1 -> list.receipt && !busy();
            default -> !busy();
        };
    }

    private boolean mappingEnabled(int index) {
        return !busy() && (index == 0 ? list.canMap : list.hasMapping);
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
        drawOverview();
        drawFilters(mouseX, mouseY);
        drawSearch(mouseX, mouseY);
        UploadDraw.rect(listX - 1, listY - 1, listX + listWidth + 1, listY + visibleRows * ROW_HEIGHT, 0xFF888697);
        drawRows(mouseX, mouseY);
        drawScrollbar();
        drawFooter(mouseX, mouseY);
        List<String> tooltip = tooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            List<String> wrapped = new ArrayList<>();
            for (String line : tooltip) wrapped
                .addAll(fontRendererObj.listFormattedStringToWidth(line, Math.max(40, Math.min(300, width - 24))));
            drawHoveringText(wrapped, mouseX, mouseY, fontRendererObj);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1, 1, 1, 1);
        } else {
            UploadRowTooltip rowTooltip = rowTooltip(mouseX, mouseY);
            if (rowTooltip != null) rowTooltip.draw(fontRendererObj, mouseX, mouseY, width, height);
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
        boolean heightHover = inside(mouseX, mouseY, heightX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        UploadDraw.button(heightX, closeY, CLOSE_SIZE, CLOSE_SIZE, maxRows() > 1, heightHover);
        UploadDraw.glyph(
            heightX + 2,
            closeY + 1 + NativeAe2Button.contentOffset(maxRows() > 1, heightHover),
            HEIGHT_ICON,
            'o',
            0xFF000000 | (maxRows() > 1 ? TEXT : DISABLED_TEXT));
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(
                StatCollector.translateToLocal("gui.neoecoae.pattern_upload.title"),
                closeX - heightX - CLOSE_SIZE - 8),
            heightX + CLOSE_SIZE + 4,
            top + 4,
            TEXT,
            false);
        GuiTextures.CLOSE.draw(closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
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
                StatCollector.translateToLocalFormatted(
                    list.inferred ? "gui.neoecoae.pattern_upload.inferred_category"
                        : list.hinted ? "gui.neoecoae.pattern_upload.nei_category"
                            : "gui.neoecoae.pattern_upload.category",
                    text(list.category)));
        }
        if (list.hinted) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(
                text(
                    list.categoryOnly ? "gui.neoecoae.pattern_upload.hinted_category_only"
                        : "gui.neoecoae.pattern_upload.hinted"));
        }
        small(trim(detail.toString(), textWidth), textX, y + 10, MUTED);
    }

    private boolean insideSearch(int mouseX, int mouseY) {
        return inside(mouseX, mouseY, left + 5, toggleY + 2, dimensionX - left - 8, 12);
    }

    private void drawSearch(int mouseX, int mouseY) {
        NativePageLayout
            .texture("neoecoae:textures/gui/vendor/ae2/text_field.png", 0, search.isFocused() ? 24 : 0, 128, 12, 1, 0)
            .draw(left + 5, toggleY + 2, dimensionX - left - 8, 12);
        GL11.glPushMatrix();
        GL11.glTranslatef(left + 8, toggleY + 4, 0);
        GL11.glScalef(SMALL, SMALL, 1);
        search.drawTextBox();
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
        if (search.getText()
            .isEmpty() && !search.isFocused())
            small(
                trim(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search"), dimensionX - left - 14),
                left + 8,
                toggleY + 4,
                0x707070);
        UploadDraw.button(
            dimensionX,
            toggleY,
            dimensionWidth,
            16,
            inside(mouseX, mouseY, dimensionX, toggleY, dimensionWidth, 16));
        NativeAe2Button.drawLabel(
            Component.literal(dimension == null ? ui("dimensions") : ui("dimension", dimension)),
            dimensionX,
            toggleY,
            dimensionWidth,
            16,
            SMALL,
            true,
            inside(mouseX, mouseY, dimensionX, toggleY, dimensionWidth, 16),
            TEXT);
        UploadDraw.toggle(toggleX, toggleY + 2, mergeTypes, inside(mouseX, mouseY, toggleX, toggleY, mergeWidth, 16));
        small(trim(ui("merge"), mergeWidth - 26), toggleX + 26, toggleY + 5, TEXT);
    }

    private static String ui(String key, Object... arguments) {
        return StatCollector.translateToLocalFormatted("gui.neoecoae.pattern_upload.manager." + key, arguments);
    }

    private void drawOverview() {
        long total = 0, empty = 0;
        int ready = 0;
        for (Row row : list.rows) {
            if (uploadable(row)) ready++;
            total += Math.max(0, row.totalSlots);
            empty += Math.max(0, Math.min(row.emptySlots, row.totalSlots));
        }
        int cardWidth = (panelWidth - 16) / 3;
        drawMetric(
            0,
            cardWidth,
            ui("targets"),
            Integer.toString(list.rows.size()),
            ui("inventories"),
            list.rows.isEmpty() ? 0 : 1,
            0xFF468BBA);
        drawMetric(
            1,
            cardWidth,
            ui("ready"),
            ready + " / " + list.rows.size(),
            ui("unavailable", list.rows.size() - ready),
            list.rows.isEmpty() ? 0 : ready / (double) list.rows.size(),
            0xFF439A54);
        drawMetric(
            2,
            cardWidth,
            ui("slots"),
            Long.toString(empty),
            ui("total_slots", total),
            total == 0 ? 0 : empty / (double) total,
            0xFF468BBA);
    }

    private void drawMetric(int index, int cardWidth, String label, String value, String detail, double fraction,
        int color) {
        int x = left + 5 + index * (cardWidth + 3), y = top + 36;
        if (index > 0) UploadDraw.rect(x - 2, y, x - 1, y + 29, 0xFFABA8B8);
        small(trim(label, cardWidth / 2), x + 2, y + 1, MUTED);
        float valueWidth = fontRendererObj.getStringWidth(value) * SMALL;
        small(trim(value, cardWidth / 2), x + cardWidth - Math.min(valueWidth, cardWidth / 2F) - 2, y + 1, TEXT);
        UploadDraw.rect(x + 2, y + 12, x + cardWidth - 2, y + 15, 0xFFABA8B8);
        UploadDraw
            .rect(x + 2, y + 12, x + 2 + (int) ((cardWidth - 4) * Math.max(0, Math.min(1, fraction))), y + 15, color);
        small(trim(detail, cardWidth - 4), x + 2, y + 20, MUTED);
    }

    private void drawFilters(int mouseX, int mouseY) {
        for (int index = 0; index < FILTERS.length; index++) {
            int x = left + 5 + index * (panelWidth - 10) / FILTERS.length;
            int end = left + 5 + (index + 1) * (panelWidth - 10) / FILTERS.length;
            int count = 0;
            for (Row row : scoped) if (mappingMode ? index == 0 && mappable(row) : matchesFilter(row, index)) count++;
            boolean selected = mappingMode ? index == 0 : index == filter;
            UploadDraw.button(
                x,
                top + 65,
                end - x - 2,
                15,
                !mappingMode || index == 0,
                selected || !mappingMode && inside(mouseX, mouseY, x, top + 65, end - x - 2, 15));
            if (selected) UploadDraw.rect(x + 2, top + 77, end - 4, top + 79, 0xFF47A7BC);
            NativeAe2Button.drawLabel(
                Component.literal(ui("filter." + FILTERS[index]) + " " + count),
                x,
                top + 65,
                end - x - 2,
                15,
                SMALL,
                !mappingMode || index == 0,
                selected || inside(mouseX, mouseY, x, top + 65, end - x - 2, 15),
                TEXT);
        }
    }

    private void drawRows(int mouseX, int mouseY) {
        if (tree.isEmpty()) {
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
            if (index >= tree.size()) break;
            TreeRow entry = tree.get(index);
            Row row = entry.target;
            int x = listX, y = listY + slot * ROW_HEIGHT, w = listWidth, h = CARD_HEIGHT;
            if (entry.group()) {
                drawGroup(entry, x, y, w, inside(mouseX, mouseY, x, y, w, h));
                continue;
            }
            boolean enabled = mappingMode ? mappable(row) : uploadable(row);
            boolean hover = inside(mouseX, mouseY, x, y, w, h);
            boolean automatic = row.token == list.autoToken && list.autoToken != 0;
            UploadDraw.rect(
                x,
                y,
                x + w,
                y + h,
                hover ? 0xFFD0EBF1 : automatic ? 0xFFDEEAF0 : entry.child ? 0xFFB8B7C3 : 0xFFC7C5D1);
            if (hover || automatic) UploadDraw.rect(x, y, x + w, y + 1, 0xFF79C4D7);
            int indent = entry.child ? 12 : 0;
            if (entry.child) {
                UploadDraw.rect(x + 7, y, x + 8, y + h, 0xFF8D899F);
                UploadDraw.rect(x + 7, y + 13, x + 13, y + 14, 0xFF8D899F);
            }
            UploadDraw.rect(x + 2 + indent, y + 4, x + 4 + indent, y + h - 4, accent(row));
            item(row.icon, x + 6 + indent, y + 3);
            String count = slotCount(row);
            float countWidth = fontRendererObj.getStringWidth(count) * SMALL;
            float countX = x + w - 60 - countWidth;
            small(count, countX, y + 3, slotColor(row));
            String name = hostName(row);
            if (automatic) name = "§6★§r " + name;
            else if (row.token == list.mappingToken) name = "§5◆§r " + name;
            int nameX = x + 25 + indent;
            machineTitle(name, row, nameX, y + 3, (int) (countX - 4 - nameX), enabled ? TEXT : MUTED);
            String status = text(stateKey(row)) + " · " + text(level(row).translationKey());
            if (row.programmingCover) status += " · " + text("gui.neoecoae.pattern_upload.programmable");
            else if (row.circuit >= 0) status += " · " + ui("circuit", row.circuit);
            status += " · D" + row.dimension + " " + row.x + ", " + row.y + ", " + row.z;
            int statusWidth = w - 85 - indent;
            small(trim(status, statusWidth), nameX, y + 14, enabled ? 0x33783D : MUTED);
            boolean actionHover = inside(mouseX, mouseY, x + w - 54, y + 4, 34, 16);
            UploadDraw.button(x + w - 54, y + 4, 34, 16, enabled && !busy, actionHover);
            NativeAe2Button.drawLabel(
                Component.literal(ui(mappingMode ? "bind" : "upload")),
                x + w - 54,
                y + 4,
                34,
                16,
                SMALL,
                enabled && !busy,
                actionHover,
                TEXT);
            boolean locateHover = inside(mouseX, mouseY, x + w - 18, y + 4, 16, 16);
            UploadDraw.button(x + w - 18, y + 4, 16, 16, locateHover);
            GuiTextures.MOVE_TO.draw(x + w - 16, y + 6 + NativeAe2Button.contentOffset(true, locateHover), 12, 12);
            int barWidth = Math.max(30, (int) countWidth);
            UploadDraw.rect(x + w - 60 - barWidth, y + 11, x + w - 60, y + 13, 0xFF9A97A9);
            if (row.totalSlots > 0) UploadDraw.rect(
                x + w - 60 - barWidth,
                y + 11,
                x + w
                    - 60
                    - barWidth
                    + (int) (barWidth
                        * Math.min(1, Math.max(0, (row.totalSlots - (double) row.emptySlots) / row.totalSlots))),
                y + 13,
                0xFF000000 | slotColor(row));
        }
    }

    private void drawGroup(TreeRow entry, int x, int y, int w, boolean hover) {
        UploadDraw.rect(x, y, x + w, y + CARD_HEIGHT, hover ? 0xFFD0EBF1 : 0xFFCCC9D7);
        boolean open = expanded.contains(entry.key), automatic = false;
        int ready = 0;
        for (Row row : entry.children) {
            if (mappingMode ? mappable(row) : uploadable(row)) ready++;
            if (row.token == list.autoToken && list.autoToken != 0) automatic = true;
        }
        small(open ? "▼" : "▶", x + 3, y + 10, 0x53647C);
        item(entry.target.icon, x + 13, y + 3);
        String name = (automatic ? "§6★§r " : "") + machineName(entry.target);
        machineTitle(name, entry.target, x + 33, y + 3, w - 68, TEXT);
        small(trim(ui("group", entry.children.size(), ready), w - 42), x + 33, y + 14, MUTED);
        smallCentered("×" + entry.children.size(), x + w - 32, y + 8, 28, TEXT);
    }

    private void drawScrollbar() {
        int x = listX + listWidth + 2, height = visibleRows * ROW_HEIGHT - 1;
        UploadDraw.rect(x, listY, x + SCROLLBAR_WIDTH, listY + height, 0x30000000);
        int[] thumb = thumb();
        if (thumb == null) return;
        int size = Math.min(thumb[1], listY + height - thumb[0]);
        NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/sprites/small_scroller.png", 0, 0, 7, 15, 2, 2)
            .draw(x, thumb[0], SCROLLBAR_WIDTH, size);
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
            message = ui("results", visible.size(), list.rows.size());
            if (hiddenCount > 0) message += " · " + ui("hidden", hiddenCount);
            if (queryIgnored) message += " · " + text("gui.neoecoae.pattern_upload.search.no_match");
            color = MUTED;
        }
        small(trim(message, panelWidth - 12), left + 6, messageY + 1, color);
        String[] labels = { "gui.neoecoae.pattern_upload.auto_upload", "gui.neoecoae.pattern_upload.undo",
            "gui.neoecoae.pattern_upload.refresh" };
        UITexture[] icons = { GuiTextures.CHECKMARK, GuiTextures.SHIFT_BACKWARD, GuiTextures.REFRESH };
        for (int index = 0; index < footerButtons.length; index++)
            drawActionButton(footerButtons[index], labels[index], icons[index], footerEnabled(index), mouseX, mouseY);
        small(
            trim(mappingStatus(), panelWidth - 12),
            left + 6,
            messageY + 9,
            list.hasMapping && list.mappingEnabled ? 0x665090 : MUTED);
        String[] mappingLabels = {
            mappingMode ? "gui.neoecoae.pattern_upload.mapping.cancel"
                : list.hasMapping ? "gui.neoecoae.pattern_upload.mapping.edit"
                    : "gui.neoecoae.pattern_upload.mapping.add",
            list.mappingEnabled ? "gui.neoecoae.pattern_upload.mapping.disable"
                : "gui.neoecoae.pattern_upload.mapping.enable",
            "gui.neoecoae.pattern_upload.mapping.remove" };
        UITexture[] mappingIcons = { list.hasMapping ? GuiTextures.EDIT : GuiTextures.ADD, GuiTextures.CHECKMARK,
            GuiTextures.REMOVE };
        for (int index = 0; index < mappingButtons.length; index++) drawActionButton(
            mappingButtons[index],
            mappingLabels[index],
            mappingIcons[index],
            mappingEnabled(index),
            mouseX,
            mouseY);
    }

    private String mappingStatus() {
        if (mappingMode) return text("gui.neoecoae.pattern_upload.mapping.pick");
        if (!list.hasMapping) {
            if (list.canMap) return text("gui.neoecoae.pattern_upload.mapping.none");
            for (Row row : list.rows) if (!row.mappingFailure.isEmpty()
                && !"gui.neoecoae.pattern_upload.mapping.no_category".equals(row.mappingFailure))
                return text(row.mappingFailure);
            return text("gui.neoecoae.pattern_upload.mapping.no_category");
        }
        String name = text(list.mappingName) + list.mappingNameSuffix;
        for (Row row : list.rows) if (row.token == list.mappingToken) {
            name = hostName(row);
            break;
        }
        return StatCollector.translateToLocalFormatted(
            list.mappingEnabled ? "gui.neoecoae.pattern_upload.mapping.current"
                : "gui.neoecoae.pattern_upload.mapping.inactive",
            name);
    }

    private void drawActionButton(int[] box, String label, UITexture icon, boolean enabled, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
        UploadDraw.button(box[0], box[1], box[2], box[3], enabled, hover);
        GL11.glColor4f(1, 1, 1, enabled ? 1 : 0.4F);
        icon.draw(box[0] + 3, box[1] + 2 + NativeAe2Button.contentOffset(enabled, hover), 12, 12);
        GL11.glColor4f(1, 1, 1, 1);
        NativeAe2Button.drawLabel(
            Component.translatable(label),
            box[0] + 16,
            box[1],
            box[2] - 18,
            box[3],
            SMALL,
            enabled,
            hover,
            TEXT);
    }

    private List<String> tooltip(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        if (draggingWindow) return lines;
        if (inside(mouseX, mouseY, heightX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            lines.add(ui("height", visibleRows, maxRows()));
            lines.add("§7" + ui("height_tip"));
            return lines;
        }
        if (inside(mouseX, mouseY, dimensionX, toggleY, dimensionWidth, 16)) {
            lines.add(ui("dimension_tip"));
            return lines;
        }
        if (inside(mouseX, mouseY, toggleX, toggleY, mergeWidth, 16)) {
            lines.add(ui("merge_tip"));
            return lines;
        }
        if (inside(mouseX, mouseY, left + 5, top + 36, panelWidth - 10, 28)) {
            lines.add(ui("stats_tip"));
            return lines;
        }
        int rowIndex = rowAt(mouseX, mouseY);
        if (rowIndex >= 0 && !tree.get(rowIndex)
            .group() && mouseX >= listX + listWidth - 18) {
            Row row = tree.get(rowIndex).target;
            lines.add(ui("locate"));
            lines.add(
                "§7" + StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.pattern_upload.tooltip.location",
                    row.dimension,
                    row.x,
                    row.y,
                    row.z));
            return lines;
        }
        if (rowIndex >= 0 && tree.get(rowIndex)
            .group()) {
            TreeRow group = tree.get(rowIndex);
            lines.add(withVoltageTier(machineName(group.target), group.target));
            lines.add("§7" + ui("expand_tip"));
            return lines;
        }
        if (inside(mouseX, mouseY, left + 5, listY + visibleRows * ROW_HEIGHT, panelWidth - 10, 12)) {
            lines.add(ui("results", visible.size(), list.rows.size()));
            if (queryIgnored) lines.add(text("gui.neoecoae.pattern_upload.search.no_match"));
            lines.add("§7" + text("gui.neoecoae.pattern_upload.keys"));
            return lines;
        }
        if (inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.back.tooltip"));
            return lines;
        }
        for (int index = 0; index < footerButtons.length; index++) {
            int[] box = footerButtons[index];
            if (!inside(mouseX, mouseY, box[0], box[1], box[2], box[3])) continue;
            if (index == 0) {
                if (mappingMode) {
                    lines.add(text("gui.neoecoae.pattern_upload.mapping.pick"));
                    return lines;
                }
                if (list.autoToken != 0) {
                    lines.add(
                        StatCollector.translateToLocal(
                            list.autoMapped ? "gui.neoecoae.pattern_upload.mapping.used"
                                : "gui.neoecoae.pattern_upload.auto_ready"));
                    for (Row row : list.rows) if (row.token == list.autoToken) lines.add("§6★ §r" + title(row));
                    if (!list.autoReason.isEmpty()) lines.add("§e" + text(list.autoReason));
                    lines.add("§7" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.row.stay"));
                } else if (!list.autoReason.isEmpty()) lines.add("§7" + text(list.autoReason));
            } else if (index == 1) {
                lines.add(
                    list.receipt
                        ? StatCollector.translateToLocalFormatted(
                            "gui.neoecoae.pattern_upload.undo.tooltip",
                            text(list.receiptName) + list.receiptNameSuffix)
                        : "§7" + StatCollector.translateToLocal("gui.neoecoae.pattern_upload.reason.no_receipt"));
            }
            return lines;
        }
        for (int index = 0; index < mappingButtons.length; index++) {
            int[] box = mappingButtons[index];
            if (!inside(mouseX, mouseY, box[0], box[1], box[2], box[3])) continue;
            lines.add(mappingStatus());
            lines.add(
                "§7" + text(
                    index == 0 ? "gui.neoecoae.pattern_upload.mapping.add.tooltip"
                        : index == 1 ? "gui.neoecoae.pattern_upload.mapping.toggle.tooltip"
                            : "gui.neoecoae.pattern_upload.mapping.remove.tooltip"));
            return lines;
        }
        if (inside(mouseX, mouseY, left + 5, listY + visibleRows * ROW_HEIGHT + 12, panelWidth - 10, 8)) {
            lines.add(mappingStatus());
            if (list.canMap) lines.add("§7" + text("gui.neoecoae.pattern_upload.mapping.add.tooltip"));
            return lines;
        }
        if (insideSearch(mouseX, mouseY)) {
            lines.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.search.tooltip"));
            return lines;
        }
        return lines;
    }

    /** Card tooltip for the hovered target row, or null. */
    private UploadRowTooltip rowTooltip(int mouseX, int mouseY) {
        if (draggingWindow) return null;
        int index = rowAt(mouseX, mouseY);
        if (index < 0) return null;
        TreeRow entry = tree.get(index);
        if (entry.group()) return null;
        Row row = entry.target;
        UploadRowTooltip tip = new UploadRowTooltip();
        String host = hostName(row), kind = kindName(row);
        if (row.route.isEmpty()) {
            tip.title = host;
            tip.subtitle = kind;
        } else {
            // A route reads "machine<MODE_SEPARATOR>recipe map"; the mode is shown apart from the machine.
            int separator = row.route.indexOf(MODE_SEPARATOR);
            tip.title = hostText(separator < 0 ? row.route : row.route.substring(0, separator), row.icon);
            tip.mode = separator < 0 ? "" : text(row.route.substring(separator + 1));
            tip.subtitle = host.equals(tip.title) || host.isEmpty() ? kind
                : kind.isEmpty() ? host : host + " · " + kind;
        }
        tip.title = withVoltageTier(tip.title, row);
        tip.star = row.token == list.autoToken && list.autoToken != 0;
        tip.level = text(level(row).translationKey());
        tip.levelColor = goodMatch(row) ? UploadRowTooltip.GOOD
            : row.level == INCOMPATIBLE ? UploadRowTooltip.BAD : UploadRowTooltip.WARN;
        tip.state = text(stateKey(row));
        tip.stateGood = row.state == FREE;
        tip.free = row.freeMembers;
        tip.members = row.members;
        tip.duplicates = row.duplicates;
        tip.emptySlots = row.emptySlots;
        tip.totalSlots = row.totalSlots;
        if (!row.reason.isEmpty()) tip.notes.add(text(row.reason));
        int modeSeparator = row.route.indexOf(MODE_SEPARATOR);
        if (!row.configuredRecipeMap.isEmpty()
            && (modeSeparator < 0 || !row.configuredRecipeMap.equals(row.route.substring(modeSeparator + 1))))
            tip.notes.add(
                StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.pattern_upload.tooltip.configured_recipe_map",
                    text(row.configuredRecipeMap)));
        if (mappingMode && !row.mappable && !row.mappingFailure.isEmpty()) tip.notes.add(text(row.mappingFailure));
        if (row.programmingCover)
            tip.notes.add(StatCollector.translateToLocal("gui.neoecoae.pattern_upload.programmable"));
        else if (row.circuit >= 0) tip.notes.add(
            StatCollector
                .translateToLocalFormatted("gui.neoecoae.pattern_upload.button.auto.tooltip.circuit", row.circuit));
        if (tip.star) tip.notes.add(
            "§6★ §r" + StatCollector.translateToLocal(
                list.autoMapped ? "gui.neoecoae.pattern_upload.mapping.used"
                    : "gui.neoecoae.pattern_upload.auto_choice"));
        if (row.token == list.mappingToken && !tip.star) tip.notes.add(mappingStatus());
        tip.location = StatCollector.translateToLocalFormatted(
            "gui.neoecoae.pattern_upload.tooltip.location",
            row.dimension,
            row.x,
            row.y,
            row.z);
        tip.uploadable = mappingMode ? mappable(row) : uploadable(row);
        tip.mappingAction = mappingMode && mappable(row);
        tip.disabledReason = text(
            mappingMode && !row.mappingFailure.isEmpty() ? row.mappingFailure
                : "gui.neoecoae.pattern_upload.row.disabled");
        return tip;
    }

    /** Keep the voltage suffix visible even when a long machine name needs trimming. */
    private void machineTitle(String name, Row row, int x, int y, int width, int color) {
        String suffix = row.voltageTier.isEmpty() ? "" : " " + row.voltageTier;
        int suffixWidth = (int) Math.ceil(fontRendererObj.getStringWidth(suffix) * SMALL);
        small(trim(name, width - suffixWidth) + suffix, x, y, color);
    }

    private static String withVoltageTier(String name, Row row) {
        return row.voltageTier.isEmpty() ? name : name + " " + row.voltageTier;
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
        if (goodMatch(row)) return 0xff3fa03f;
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
        if (goodMatch(row)) return "§a";
        if (row.level == INCOMPATIBLE) return "§c";
        return "§e";
    }

    private static boolean goodMatch(Row row) {
        return row.level == ACCEPTED || level(row).allowsAuto();
    }

    private static int safe(int ordinal, int length) {
        return ordinal >= 0 && ordinal < length ? ordinal : length - 1;
    }

    private static String stateKey(Row row) {
        PatternTargetSelector.SlotState[] states = PatternTargetSelector.SlotState.values();
        return states[safe(row.state, states.length)].translationKey();
    }

    /** Translated host name followed by its literal suffix (e.g. GT's ghost circuit " [1]"). */
    static String hostName(Row row) {
        // With a route the icon is the route's machine; hostText only uses it when the host is that same machine.
        return hostText(row.name, row.icon) + row.nameSuffix;
    }

    /**
     * Host or route machine name; when it is an untranslatable key (AE2 reports the adjacent block's raw unlocalized
     * name, GT routes report "unlocalized name.name", and some machines, e.g. BartWorks' bio lab, never register that
     * key because their item names itself), falls back to the icon's display name.
     */
    static String hostText(String name, ItemStack icon) {
        if (name == null || name.isEmpty()) return "";
        int mode = name.indexOf(MODE_SEPARATOR);
        if (mode >= 0) {
            String machine = hostText(name.substring(0, mode), icon);
            String recipeMap = text(name.substring(mode + 1));
            return recipeMap.isEmpty() ? machine : machine + " (" + recipeMap + ")";
        }
        String text = text(name);
        if (icon == null || !text.equals(name)) return text;
        try {
            // The raw name is exactly the icon's unlocalized name (optionally with ".name"); anything else (custom
            // names, a different icon) is left as is.
            String key = icon.getUnlocalizedName();
            if (key == null || !(name.equals(key) || name.equals(key + ".name"))) return text;
            String display = icon.getDisplayName();
            return display == null || display.isEmpty() ? text : display;
        } catch (RuntimeException broken) {
            return text;
        }
    }

    static String title(Row row) {
        String name = hostName(row);
        return withVoltageTier(row.route.isEmpty() ? name : name + " → " + hostText(row.route, row.icon), row);
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
        // Machine name plus the recipe map of the mode it runs in (GtPatternRoutes.MODE_SEPARATOR).
        int mode = key.indexOf(MODE_SEPARATOR);
        if (mode >= 0) {
            String machine = text(key.substring(0, mode));
            String recipeMap = text(key.substring(mode + 1));
            return recipeMap.isEmpty() ? machine : machine + " (" + recipeMap + ")";
        }
        if (StatCollector.canTranslate(key)) return StatCollector.translateToLocal(key);
        if (StatCollector.canTranslate(key + ".name")) return StatCollector.translateToLocal(key + ".name");
        return key;
    }
}
