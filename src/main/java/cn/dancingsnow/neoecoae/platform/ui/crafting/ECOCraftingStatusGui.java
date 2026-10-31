package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiCraftingStatus;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.container.implementations.ContainerCPUTable;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.container.implementations.CraftingCpuEntry;
import appeng.core.AEConfig;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.core.localization.Localization;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingItemInterface;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.ScheduledReason;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * ECO crafting status page over AE2's own status screen: the CPU column on the left, the running job in the middle
 * and, when the running ECO plan contains cycles, its cycle members on the right. It only takes the ECO look while the
 * server reports an ECO computation system with the fast planner on; otherwise every override defers to AE2. The
 * container, CPU list, entry sync and every button action stay AE2's; this class only re-lays and re-draws them.
 */
@SideOnly(Side.CLIENT)
public final class ECOCraftingStatusGui extends GuiCraftingStatus {

    private static final ResourceLocation REPORT = new ResourceLocation(
        "neoecoae",
        "textures/guis/eco_craftingreport.png");
    private static final ResourceLocation CYCLE = new ResourceLocation(
        "neoecoae",
        "textures/guis/eco_craftingreport_cycle.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation(
        "neoecoae",
        "textures/guis/crafting_widgets.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 384;
    /** Panel height: the texture's 260 rows less the blank tab rows skipped in {@link #drawBG}. */
    private static final int HEIGHT = 237;
    /** Texture rows 232..254 are the bottom tab's plain middle; they are left out since the search moved up. */
    private static final int TAB_SKIP_FROM = 232;
    private static final int TAB_SKIP = 260 - HEIGHT;
    /** The CPU column left of the panel; every other coordinate is panel-relative, as on the ECO report. */
    private static final int CPU_PANEL_WIDTH = 96;
    private static final int CPU_PANEL_HEIGHT = 214;
    private static final int CPU_SCROLL_LEFT = -10;
    private static final int NATIVE_CPU_SCROLL_LEFT = -16;
    /** Panel widths of the report texture and of the cycle texture, whose cycle column widens the top part. */
    private static final int REPORT_WIDTH = 236;
    private static final int CYCLE_WIDTH = 331;

    private static final int TEXT_LIGHT = 0xF2F3F7;
    private static final int TEXT_DARK = 0x20212B;
    private static final int CYCLE_OVERLAY = 0x1AB86BFF;
    private static final int SUSPENDED_COLOR = 0x8A2A2A;

    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 27;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 22;
    private static final int ROW_PITCH = CELL_HEIGHT + 1;
    private static final int COLUMNS = 3;
    private static final int BASE_ROWS = 7;
    private static final int TABLE_SCROLL_X = 218;
    private static final int CYCLE_X = 237;
    private static final int CYCLE_SCROLL_X = 312;
    private static final int CYCLE_STATUS_Y = 191;
    private static final int SCROLL_HEIGHT = 160;
    /** The status line under the tables and the button row in the bottom tab. */
    private static final int STATUS_Y = 195;
    private static final int BUTTON_Y = 209;
    private static final int BUTTON_WIDTH = 54;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_LEFT = 4;
    /** The tab under the tables spans panel x -1..235 on both textures. */
    private static final int TAB_CENTER = 117;
    private static final int TAB_TEXT_WIDTH = 226;
    /** Title above the low part of the header line, with the search box above its raised right part. */
    private static final int TITLE_X = 54;
    private static final int TITLE_Y = 9;
    private static final int SEARCH_X = 148;
    private static final int SEARCH_Y = 1;
    private static final int SEARCH_WIDTH = 64;
    private static final int SEARCH_HEIGHT = 12;
    private static final int TITLE_RIGHT = SEARCH_X - 3;
    /** AE2's CPU table lays its cards from y=19; the ECO column starts them level with the table. */
    private static final int CPU_LIST_SHIFT = TABLE_Y - 19;

    private static final int ICON_NO_TARGET = 132;
    private static final int ICON_LOCK_MODE = 133;
    private static final int ICON_BLOCK_MODE = 134;

    private final ContainerCraftingCPU cpuContainer;
    private final EcoCpuTable ecoCpuTable;
    /** Whether the CPU table could be replaced by the shifting one; without it the page stays AE2's. */
    private final boolean ecoCapable;
    /** Set once AE2's controls could not be found; the page then stays AE2's for good. */
    private boolean unsupported;
    private final GuiScrollbar cycleScroll = new GuiScrollbar();
    private final Map<IAEStack<?>, CraftingCpuEntry> entries = new LinkedHashMap<>();
    private List<CraftingCpuEntry> visible = Collections.emptyList();
    /** The layout currently applied; changed only through {@link #setWorldAndResolution}. */
    private boolean ecoLayout;
    private boolean cycleLayout;
    private ECOCraftingStatusClient.State state = ECOCraftingStatusClient.State.NONE;
    @Nullable
    private ECOCycleItemList cycleKeysOf;
    private Set<AEKey> cycleKeys = Collections.emptySet();

    // AE2's controls, re-read after every initGui because AE2 recreates them there.
    @Nullable
    private GuiButton cancelButton;
    @Nullable
    private GuiButton suspendButton;
    @Nullable
    private GuiButton hideStoredButton;
    @Nullable
    private GuiButton allowButton;
    @Nullable
    private GuiButton selectCpuButton;
    @Nullable
    private GuiButton followButton;
    @Nullable
    private GuiButton tallButton;
    @Nullable
    private MEGuiTextField searchField;

    @Nullable
    private CraftingCpuEntry hoveredEntry;
    @Nullable
    private ItemStack hoveredItem;
    @Nullable
    private List<String> tooltip;
    /** The hovered entry's tooltip in 1.20.1's processing layout; replaces {@link #tooltip} when set. */
    @Nullable
    private List<ProcessingTooltip.Row> processingTooltip;
    @Nullable
    private IAEStack<?> requestedLocations;

    /** Live machine status per output, parsed from the server payload whenever it is replaced. */
    private Map<AEKey, ECOProcessingProgress> processing = Collections.emptyMap();
    @Nullable
    private CompoundTag processingSource;
    /** Whether the selected CPU is an ECO CPU whose processing monitor reports machines at all. */
    private boolean processingMonitored;
    /** Machine details the tooltip wants, and those last asked of the server. */
    @Nullable
    private AEKey detailKey;
    private int detailPage;
    @Nullable
    private AEKey requestedDetailKey;
    private int requestedDetailPage;
    private boolean detailRequested;
    private long nextDetailRequest;
    @Nullable
    private List<NamedDimensionalCoord> interfaceLocations;
    /** 1.21.1's right-click menu of a status cell. */
    private final StatusItemActionsMenu itemMenu = new StatusItemActionsMenu(
        (sequence, action, key, id) -> ECOCraftConfirmPackets
            .requestItemAction(inventorySlots.windowId, sequence, action, key, id));

    public ECOCraftingStatusGui(InventoryPlayer inventoryPlayer, ITerminalHost host) {
        super(inventoryPlayer, host);
        this.cpuContainer = (ContainerCraftingCPU) inventorySlots;
        ContainerCPUTable table = super.getCPUTable().getContainer();
        this.ecoCpuTable = new EcoCpuTable(table);
        this.ecoCapable = replaceCpuTable(ecoCpuTable);
    }

    /** AE2 keeps its table in a private final field that every input handler reads, so the shifting one goes there. */
    private boolean replaceCpuTable(GuiCraftingCPUTable table) {
        try {
            Field field = GuiCraftingStatus.class.getDeclaredField("cpuTable");
            field.setAccessible(true);
            field.set(this, table);
            return field.get(this) == table;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    @Override
    public GuiCraftingCPUTable getCPUTable() {
        return ecoCapable ? ecoCpuTable : super.getCPUTable();
    }

    private int panelWidth() {
        return cycleLayout ? CYCLE_WIDTH : REPORT_WIDTH;
    }

    private int sideX() {
        return panelWidth() + 1;
    }

    private int extra() {
        return (rows - BASE_ROWS) * ROW_PITCH;
    }

    // ---- Layout ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        state = ECOCraftingStatusClient.state(inventorySlots);
        boolean eco = ecoCapable && !unsupported && state.eco;
        boolean cycle = eco && !state.cycles.items()
            .isEmpty();
        if (eco != ecoLayout || cycle != cycleLayout) {
            ecoLayout = eco;
            cycleLayout = cycle;
            setWorldAndResolution(mc, width, height);
        }
        if (!ecoLayout) {
            itemMenu.close();
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        refreshProcessing();
        CompoundTag itemAction = ECOCraftingStatusClient.takeItemAction(inventorySlots);
        if (itemAction != null) itemMenu.accept(itemAction);
        tooltip = null;
        processingTooltip = null;
        hoveredEntry = null;
        hoveredItem = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        requestDetails();
        if (itemMenu.isOpen()) {
            itemMenu.draw(fontRendererObj, mouseX, mouseY);
            GL11.glDisable(GL11.GL_LIGHTING);
        } else if (processingTooltip != null) {
            ProcessingTooltip.draw(fontRendererObj, processingTooltip, mouseX, mouseY, width, height);
            GL11.glDisable(GL11.GL_LIGHTING);
        } else if (tooltip != null && !tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    @Override
    protected void recalculateScreenSize() {
        // Also runs from AE2's constructor, before this class's fields exist; ecoLayout is still false then.
        if (!ecoLayout) {
            super.recalculateScreenSize();
            if (ecoCpuTable != null)
                alignCpuScrollbar(ecoCpuTable, NATIVE_CPU_SCROLL_LEFT, GuiCraftingCPUTable.CPU_TABLE_HEIGHT - 27);
            return;
        }
        boolean tall = AEConfig.instance.getConfigManager()
            .getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.TALL;
        rows = tall ? BASE_ROWS + Math.max(0, (height - 64 - HEIGHT) / ROW_PITCH) : BASE_ROWS;
        xSize = CPU_PANEL_WIDTH + sideX() + 18;
        ySize = HEIGHT + extra();
        GuiCraftingCPUTable.CPU_TABLE_SLOTS = rows;
        GuiCraftingCPUTable.CPU_TABLE_HEIGHT = rows * GuiCraftingCPUTable.CPU_TABLE_SLOT_HEIGHT + 27;
        alignCpuScrollbar(ecoCpuTable, CPU_SCROLL_LEFT, SCROLL_HEIGHT + extra());
    }

    /** AE2's CPU scrollbar is private; the ECO column's track is narrower and taller than AE2's. */
    private static void alignCpuScrollbar(GuiCraftingCPUTable table, int left, int height) {
        try {
            Field field = GuiCraftingCPUTable.class.getDeclaredField("cpuScrollbar");
            field.setAccessible(true);
            ((GuiScrollbar) field.get(table)).setLeft(left)
                .setHeight(height);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // AE2's default placement is a few pixels off the track but still usable.
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        readControls();
        if (!ecoLayout) return;
        if (cancelButton == null || suspendButton == null
            || selectCpuButton == null
            || followButton == null
            || searchField == null) {
            // A future AE2 renamed its controls; fall back to its own page rather than a half-laid one.
            unsupported = true;
            ecoLayout = false;
            cycleLayout = false;
            setWorldAndResolution(mc, width, height);
            return;
        }
        // guiLeft becomes the panel's origin; the CPU column hangs left of it like AE2's.
        guiLeft += CPU_PANEL_WIDTH;
        boolean hasOriginal = originalGuiBtn != null && buttonList.contains(originalGuiBtn);
        buttonList.clear();
        ecoCpuTable.addButtons(buttonList, guiLeft, guiTop);
        int bottom = guiTop + BUTTON_Y + extra();
        GuiButton[] row = { selectCpuButton, suspendButton, followButton, cancelButton };
        for (int i = 0; i < row.length; i++) {
            place(row[i], BUTTON_LEFT + i * (BUTTON_WIDTH + BUTTON_GAP), bottom - guiTop, BUTTON_WIDTH, 20);
            buttonList.add(row[i]);
        }
        if (allowButton != null) {
            place(allowButton, sideX(), 8, allowButton.width, allowButton.height);
            buttonList.add(allowButton);
        }
        if (hideStoredButton != null) {
            place(hideStoredButton, sideX(), 28, hideStoredButton.width, hideStoredButton.height);
            buttonList.add(hideStoredButton);
        }
        if (tallButton != null) {
            place(tallButton, -18, ySize - 18, tallButton.width, tallButton.height);
            buttonList.add(tallButton);
        }
        if (hasOriginal) {
            placeOriginalButton();
            buttonList.add(originalGuiBtn);
        }
        searchField.x = guiLeft + SEARCH_X;
        searchField.y = guiTop + SEARCH_Y;
        searchField.w = SEARCH_WIDTH;
        searchField.h = SEARCH_HEIGHT;
        cycleScroll.setLeft(CYCLE_SCROLL_X)
            .setTop(TABLE_Y)
            .setHeight(SCROLL_HEIGHT + extra());
        updateScrollBar();
    }

    private void place(GuiButton button, int x, int y, int width, int height) {
        button.xPosition = guiLeft + x;
        button.yPosition = guiTop + y;
        button.width = width;
        button.height = height;
    }

    @Override
    public void initPrimaryGuiButton() {
        super.initPrimaryGuiButton();
        if (ecoLayout) placeOriginalButton();
    }

    /** The terminal tab sits on the panel's top edge at its right end, as on AE2's page. */
    private void placeOriginalButton() {
        if (originalGuiBtn == null) return;
        originalGuiBtn.xPosition = guiLeft + panelWidth() - 25;
        originalGuiBtn.yPosition = guiTop - 4;
    }

    private void readControls() {
        cancelButton = control(GuiCraftingCPU.class, "cancel");
        suspendButton = control(GuiCraftingCPU.class, "suspend");
        hideStoredButton = control(GuiCraftingCPU.class, "toggleHideStored");
        allowButton = control(GuiCraftingCPU.class, "changeAllow");
        selectCpuButton = control(GuiCraftingStatus.class, "selectCPU");
        followButton = control(GuiCraftingStatus.class, "follow");
        tallButton = control(GuiCraftingStatus.class, "switchTallMode");
        Object field = read(GuiCraftingCPU.class, "searchField");
        searchField = field instanceof MEGuiTextField text ? text : null;
    }

    private @Nullable GuiButton control(Class<?> owner, String name) {
        Object value = read(owner, name);
        return value instanceof GuiButton button ? button : null;
    }

    private @Nullable Object read(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    // ---- Entries ----

    @Override
    public void postVisualEntryUpdate(CraftingCpuEntry[] updates, boolean clearFirst, int remainingOperations) {
        // Mirrors AE2's visual state, whose own copy is private; AE2's update then refreshes the scrollbar.
        if (clearFirst) entries.clear();
        if (updates != null) for (CraftingCpuEntry update : updates) {
            if (update == null || update.getStack() == null) continue;
            IAEStack<?> key = CraftingCpuEntry.normalizeStack(update.getStack());
            if (update.getTotalAmount() <= 0) entries.remove(key);
            else entries.put(key, update);
        }
        super.postVisualEntryUpdate(updates, clearFirst, remainingOperations);
    }

    @Override
    public void clearItems() {
        entries.clear();
        super.clearItems();
    }

    @Override
    protected void updateScrollBar() {
        super.updateScrollBar();
        if (!ecoLayout) return;
        visible = filterEntries();
        getScrollBar().setTop(TABLE_Y)
            .setLeft(TABLE_SCROLL_X)
            .setHeight(SCROLL_HEIGHT + extra());
        getScrollBar().setRange(0, Math.max(0, (visible.size() + COLUMNS - 1) / COLUMNS - rows), 1);
    }

    /** AE2's filter: the hide-stored setting and the search text. */
    private List<CraftingCpuEntry> filterEntries() {
        boolean hideStored = AEConfig.instance.getConfigManager()
            .getSetting(Settings.HIDE_STORED) == YesNo.YES;
        String query = searchField == null ? ""
            : searchField.getText()
                .toLowerCase(Locale.ROOT);
        List<CraftingCpuEntry> result = new ArrayList<>(entries.size());
        for (CraftingCpuEntry entry : entries.values()) {
            if (hideStored && !entry.isVisibleWhenHideStoredEnabled()) continue;
            if (!entry.matchesSearch(query)) continue;
            result.add(entry);
        }
        return result;
    }

    // ---- Drawing ----

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (!ecoLayout) {
            super.drawBG(offsetX, offsetY, mouseX, mouseY);
            return;
        }
        // AE2 sets the labels before drawing; fit them to the narrower ECO buttons before the buttons are drawn.
        fit(selectCpuButton);
        fit(suspendButton);
        fit(followButton);
        fit(cancelButton);
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(cycleLayout ? CYCLE : REPORT);
        int left = offsetX - CPU_PANEL_WIDTH;
        int width = CPU_PANEL_WIDTH + panelWidth();
        // Top with the first table row, the middle row strip once per further row, then the last row and below.
        blit(left, offsetY, 0, 50, width);
        int y = offsetY + 50;
        for (int i = 0; i < rows - 2; i++, y += ROW_PITCH) blit(left, y, 50, ROW_PITCH, width);
        blit(left, y, 165, TAB_SKIP_FROM - 165, width);
        blit(left, y + TAB_SKIP_FROM - 165, TAB_SKIP_FROM + TAB_SKIP, HEIGHT - TAB_SKIP_FROM, width);
        searchField.drawTextBox();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void fit(@Nullable GuiButton button) {
        if (button == null) return;
        String text = button.displayString;
        int max = button.width - 6;
        if (text == null || fontRendererObj.getStringWidth(text) <= max) return;
        button.displayString = fontRendererObj.trimStringToWidth(text, max - fontRendererObj.getStringWidth("..."))
            + "...";
    }

    private static void blit(int x, int y, int v, int h, int width) {
        Gui.func_146110_a(x, y, 0, v, width, h, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (!ecoLayout) {
            super.drawFG(offsetX, offsetY, mouseX, mouseY);
            return;
        }
        int localX = mouseX - offsetX;
        int localY = mouseY - offsetY;
        drawCpuTierGlyph();
        drawHeader();
        drawEntries(localX, localY);
        if (cycleLayout) {
            drawCycleItems(localX, localY);
            drawCenteredScaled(
                I18n.format("gui.neoecoae.crafting_report.cycle_planning_enabled"),
                CYCLE_X + CELL_WIDTH / 2,
                CYCLE_STATUS_Y + extra(),
                86,
                TEXT_DARK,
                false);
            GL11.glColor4f(1, 1, 1, 1);
            cycleScroll.draw(this);
        }
        drawCenteredScaled(cpuStatusLine(), TAB_CENTER, STATUS_Y + extra(), TAB_TEXT_WIDTH, cpuStatusColor(), false);
        GL11.glColor4f(1, 1, 1, 1);
        // Last, so the CPU card tooltip is not painted over by the table.
        ecoCpuTable.drawFG(offsetX, offsetY, mouseX, mouseY, guiLeft, guiTop);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void drawCpuTierGlyph() {
        int u;
        switch (state.cpuTier) {
            case 4:
                u = 0;
                break;
            case 6:
                u = 13;
                break;
            case 9:
                u = 26;
                break;
            default:
                return;
        }
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(WIDGETS);
        Gui.func_146110_a(38, 5, u, 0, 13, 18, 288, 256);
    }

    /** AE2's title and elapsed time beside the ECO logo. */
    private void drawHeader() {
        String title = GuiText.CraftingStatus.getLocal();
        if (cpuContainer.getElapsedTime() > 0 && hasVisualEntries()) {
            long millis = TimeUnit.MILLISECONDS.convert(cpuContainer.getElapsedTime(), TimeUnit.NANOSECONDS);
            title = title + " - " + DurationFormatUtils.formatDuration(millis, GuiText.ETAFormat.getLocal());
        }
        int titleWidth = TITLE_RIGHT - TITLE_X;
        float scale = Math.min(1F, titleWidth / (float) Math.max(1, fontRendererObj.getStringWidth(title)));
        GL11.glPushMatrix();
        GL11.glTranslatef(TITLE_X, TITLE_Y, 0);
        GL11.glScalef(scale, scale, 1);
        fontRendererObj.drawStringWithShadow(title, 0, 0, 0xFFFFFF);
        GL11.glPopMatrix();
    }

    private void drawEntries(int localX, int localY) {
        List<CraftingCpuEntry> shown = visible;
        int first = getScrollBar().getCurrentScroll() * COLUMNS;
        for (int i = 0; i < rows * COLUMNS; i++) {
            int index = first + i;
            if (index >= shown.size()) break;
            CraftingCpuEntry entry = shown.get(index);
            int cellX = TABLE_X + (i % COLUMNS) * (CELL_WIDTH + 1);
            int cellY = TABLE_Y + (i / COLUMNS) * ROW_PITCH;
            boolean hovered = localX >= cellX && localX < cellX + CELL_WIDTH
                && localY >= cellY
                && localY < cellY + CELL_HEIGHT;
            drawEntry(entry, cellX, cellY, hovered);
        }
    }

    /** AE2's status cell: state colour, scheduling icon, stored/crafting/scheduled lines and the stack. */
    private void drawEntry(CraftingCpuEntry entry, int cellX, int cellY, boolean hovered) {
        boolean active = entry.hasActiveAmount();
        boolean scheduled = entry.hasPendingAmount();
        ScheduledReason reason = entry.getScheduledReason();
        int overlay = 0;
        if (AEConfig.instance.useColoredCraftingStatus && (active || scheduled))
            // AE2's colours are drawn opaque on its light cells; translucent they keep the dark ECO cell readable.
            overlay = stateColor(reason, active) & 0xFFFFFF | 0x80000000;
        else if (isCycleMember(entry.getStack())) overlay = CYCLE_OVERLAY;
        if (overlay != 0) Gui.drawRect(cellX, cellY, cellX + CELL_WIDTH, cellY + CELL_HEIGHT, overlay);
        List<String> lines = new ArrayList<>(3);
        List<String> details = new ArrayList<>(4);
        if (entry.hasStoredAmount()) amountLine(GuiText.Stored.getLocal(), entry.getStoredAmount(), lines, details);
        if (active) amountLine(GuiText.Crafting.getLocal(), entry.getActiveAmount(), lines, details);
        if (scheduled) amountLine(GuiText.Scheduled.getLocal(), entry.getPendingAmount(), lines, details);
        drawLines(lines, cellX, cellY);
        drawReasonIcon(cellX, cellY, reasonIcon(reason));
        drawStack(entry.getStack(), cellX + CELL_WIDTH - 19, cellY + 3);
        ECOProcessingProgress progress = progressOf(entry);
        if (active && progress.hasProgress()) drawProgress(progress, cellX, cellY);
        if (hovered) {
            IAEStack<?> visual = entry.getVisualStack();
            hoveredEntry = entry;
            hoveredItem = visual == null ? null : visual.getItemStackForNEI();
            processingTooltip = entryTooltip(entry, visual, details, progress);
        }
    }

    /** 1.20.1's cycle track along the cell's bottom edge, coloured by the machines' combined state. */
    private static void drawProgress(ECOProcessingProgress progress, int cellX, int cellY) {
        int left = cellX + 2;
        int right = cellX + CELL_WIDTH - 2;
        int top = cellY + CELL_HEIGHT - 3;
        Gui.drawRect(left, top, right, top + 2, 0xFF5B566B);
        int color = switch (progress.state()) {
            case WAITING -> 0xFFE2AA4F;
            case PAUSED -> 0xFF90909F;
            default -> 0xFFC7A6FF;
        };
        int filled = Math.round((right - left) * progress.progress());
        if (filled > 0) Gui.drawRect(left, top, left + filled, top + 2, color);
    }

    private ECOProcessingProgress progressOf(CraftingCpuEntry entry) {
        if (processing.isEmpty()) return ECOProcessingProgress.EMPTY;
        AEKey key = AEKey.fromNative(entry.getStack());
        ECOProcessingProgress progress = key == null ? null : processing.get(key);
        return progress == null ? ECOProcessingProgress.EMPTY : progress;
    }

    private void refreshProcessing() {
        CompoundTag tag = ECOCraftingStatusClient.processing(inventorySlots).tag;
        if (tag == processingSource) return;
        processingSource = tag;
        processingMonitored = tag.getBoolean("eco");
        Map<AEKey, ECOProcessingProgress> parsed = new HashMap<>();
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag row = entries.getCompound(i);
            AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, row.getCompound("key"));
            if (key != null) parsed.put(key, ECOProcessingProgress.read(row.getCompound("progress")));
        }
        processing = parsed;
    }

    /**
     * The server samples machine details only for the hovered output (1.20.1's processingDetails RPC). Requests are
     * throttled; leaving the cell stops the detail sampling once.
     */
    private void requestDetails() {
        CraftingCpuEntry entry = hoveredEntry;
        ECOProcessingProgress progress = entry == null ? ECOProcessingProgress.EMPTY : progressOf(entry);
        AEKey key = entry != null && (progress.machineCount() > 0 || progress.refreshing())
            ? AEKey.fromNative(entry.getStack())
            : null;
        if (key == null ? detailKey != null : !key.equals(detailKey)) {
            detailKey = key;
            detailPage = 0;
        }
        if (detailRequested && Objects.equals(detailKey, requestedDetailKey) && detailPage == requestedDetailPage)
            return;
        long now = System.currentTimeMillis();
        if (now < nextDetailRequest) return;
        ECOCraftConfirmPackets.requestProcessingDetail(inventorySlots.windowId, detailKey, detailPage);
        detailRequested = true;
        requestedDetailKey = detailKey;
        requestedDetailPage = detailPage;
        nextDetailRequest = now + 250;
    }

    private static void amountLine(String label, long amount, List<String> lines, List<String> details) {
        lines.add(label + ": " + ReadableNumberConverter.INSTANCE.toWideReadableForm(amount));
        details.add(
            label + ": "
                + NumberFormat.getInstance()
                    .format(amount));
    }

    private static int stateColor(ScheduledReason reason, boolean active) {
        if (reason == ScheduledReason.UNDEFINED)
            return active ? ColorUtils.craftingCPUActive.getColor() : ColorUtils.craftingCPUInactive.getColor();
        return switch (reason) {
            case UNSUPPORTED_STACK -> ColorUtils.craftingCPUUnsupportedStack.getColor();
            case SAME_NETWORK -> ColorUtils.craftingCPUSameNetwork.getColor();
            case SOMETHING_STUCK -> ColorUtils.craftingCPUSomethingStuck.getColor();
            case NO_TARGET -> ColorUtils.craftingCPUNoTarget.getColor();
            default -> active ? ColorUtils.craftingCPUActive.getColor() : ColorUtils.craftingCPUInactive.getColor();
        };
    }

    private static int reasonIcon(ScheduledReason reason) {
        return switch (reason) {
            case NO_TARGET, UNSUPPORTED_STACK, SAME_NETWORK -> ICON_NO_TARGET;
            case LOCK_MODE -> ICON_LOCK_MODE;
            case BLOCKING_MODE -> ICON_BLOCK_MODE;
            default -> -1;
        };
    }

    private void drawReasonIcon(int x, int y, int icon) {
        if (icon < 0) return;
        bindTexture("guis/states.png");
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, 0.9F);
        GL11.glPushMatrix();
        GL11.glTranslatef(x + 1, y + 1, 0);
        GL11.glScalef(0.75F, 0.75F, 1);
        drawTexturedModalRect(0, 0, (icon % 16) * 16, (icon / 16) * 16, 11, 11);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private @Nullable List<ProcessingTooltip.Row> entryTooltip(CraftingCpuEntry entry, @Nullable IAEStack<?> visual,
        List<String> details, ECOProcessingProgress progress) {
        if (visual == null) return null;
        List<ProcessingTooltip.Row> rows = new ArrayList<>();
        rows.add(ProcessingTooltip.Row.text(Platform.getItemDisplayName(visual)));
        for (String line : details) rows.add(ProcessingTooltip.Row.text(line));
        rows.addAll(
            ProcessingTooltip.rows(entry.hasActiveAmount(), entry.hasPendingAmount(), processingMonitored, progress));
        for (String line : nativeTooltip(visual)) rows.add(ProcessingTooltip.Row.text(line));
        if (processingMonitored) rows.add(
            ProcessingTooltip.Row
                .text(EnumChatFormatting.DARK_GRAY + I18n.format("gui.neoecoae.processing.context_hint")));
        return rows;
    }

    /** AE2's own tooltip lines: the scheduling reason, and with Shift the item tooltip and pattern interfaces. */
    private List<String> nativeTooltip(IAEStack<?> visual) {
        List<String> lines = new ArrayList<>();
        ScheduledReason reason = hoveredEntry == null ? ScheduledReason.UNDEFINED : hoveredEntry.getScheduledReason();
        if (reason != ScheduledReason.UNDEFINED) lines.add(reason.getLocal());
        if (!isShiftKeyDown()) {
            requestedLocations = null;
            interfaceLocations = null;
            lines.add(GuiText.HoldShiftForTooltip.getLocal());
            return lines;
        }
        if (visual instanceof IAEItemStack item) {
            @SuppressWarnings("unchecked")
            List<String> extra = item.getItemStack()
                .getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
            if (!extra.isEmpty()) lines.addAll(extra.subList(1, extra.size()));
        }
        if (requestedLocations == null || !requestedLocations.isSameType(visual)) {
            // One request per hovered stack; AE2 answers through postInterfaceLocationsUpdate.
            requestedLocations = visual.copy();
            interfaceLocations = null;
            try {
                NetworkHandler.instance.sendToServer(new PacketCraftingItemInterface(visual.copy()));
            } catch (Exception ignored) {}
        } else if (interfaceLocations != null && !interfaceLocations.isEmpty()) {
            for (NamedDimensionalCoord pos : interfaceLocations) lines.add(
                String.format(
                    "Dim:%s X:%s Y:%s Z:%s \"%s\"",
                    pos.getDimension(),
                    pos.x,
                    pos.y,
                    pos.z,
                    pos.getCustomName()));
            lines.add(GuiText.HoldShiftClick_HIGHLIGHT_INTERFACE.getLocal());
        }
        return lines;
    }

    @Override
    public void postInterfaceLocationsUpdate(List<NamedDimensionalCoord> locations) {
        // AE2 would highlight on any later shift-click once its copy is set; the ECO page keeps its own.
        if (ecoLayout) interfaceLocations = locations;
        else super.postInterfaceLocationsUpdate(locations);
    }

    private boolean isCycleMember(IAEStack<?> stack) {
        if (state.cycles != cycleKeysOf) {
            cycleKeysOf = state.cycles;
            Set<AEKey> keys = new HashSet<>();
            for (ECOCycleItemList.Entry entry : state.cycles.items()) keys.add(entry.what());
            cycleKeys = keys;
        }
        if (cycleKeys.isEmpty()) return false;
        AEKey key = AEKey.fromNative(stack);
        return key != null && cycleKeys.contains(key);
    }

    private void drawCycleItems(int localX, int localY) {
        List<ECOCycleItemList.Entry> items = state.cycles.items();
        cycleScroll.setRange(0, Math.max(0, items.size() - rows), 1);
        int first = cycleScroll.getCurrentScroll();
        for (int row = 0; row < rows; row++) {
            int index = first + row;
            if (index >= items.size()) break;
            ECOCycleItemList.Entry entry = items.get(index);
            int cellY = TABLE_Y + row * ROW_PITCH;
            boolean hovered = localX >= CYCLE_X && localX < CYCLE_X + CELL_WIDTH
                && localY >= cellY
                && localY < cellY + CELL_HEIGHT;
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(CYCLE);
            Gui.func_146110_a(
                CYCLE_X,
                cellY,
                CPU_PANEL_WIDTH,
                hovered ? 287 : 265,
                CELL_WIDTH,
                CELL_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
            drawLines(Collections.singletonList(cycleTotalLine(entry, false)), CYCLE_X, cellY);
            IAEStack<?> stack = entry.what()
                .toNative(1);
            if (stack != null) drawStack(stack, CYCLE_X + CELL_WIDTH - 19, cellY + 3);
            if (hovered) {
                hoveredItem = stack == null ? null : stack.getItemStackForNEI();
                List<String> lines = new ArrayList<>();
                lines.add(
                    entry.what()
                        .getDisplayName());
                if (entry.componentId() >= 0) lines.add("Cycle #" + entry.componentId());
                lines.add(cycleTotalLine(entry, true));
                tooltip = lines;
            }
        }
    }

    /** The confirmation report's cycle line: the plan's total output or consumption of the member. */
    private static String cycleTotalLine(ECOCycleItemList.Entry entry, boolean full) {
        if (entry.exactMissing()
            .signum() > 0)
            return I18n.format(
                entry.seedShortfallIsEstimate() ? "gui.neoecoae.crafting_report.seed_shortfall_estimate"
                    : "gui.neoecoae.crafting_graph.details.missing",
                cycleAmount(entry.exactMissing(), full));
        if (!entry.totalNetOutputKnown() && entry.displayedTotal()
            .signum() == 0) return I18n.format("gui.neoecoae.crafting_report.quantity_unknown");
        return I18n.format(
            entry.isCycleProduct() ? "gui.neoecoae.crafting_report.total_produced"
                : "gui.neoecoae.crafting_report.total_consumed",
            cycleAmount(entry.displayedTotal(), full));
    }

    private static String cycleAmount(BigInteger amount, boolean full) {
        return full ? BigNumberFormatter.format(amount, 1, true) : HostText.hugeStackAmount(amount);
    }

    /** Up to three half-size lines right-aligned against the cell's stack icon. */
    private void drawLines(List<String> lines, int cellX, int cellY) {
        int itemX = cellX + CELL_WIDTH - 19;
        float lineHeight = fontRendererObj.FONT_HEIGHT * 0.5F + 1;
        float textY = cellY + (CELL_HEIGHT - lines.size() * lineHeight) / 2F + 1;
        GL11.glPushMatrix();
        GL11.glScalef(0.5F, 0.5F, 1);
        for (String line : lines) {
            int width = fontRendererObj.getStringWidth(line);
            fontRendererObj.drawString(line, (int) ((itemX - 2 - width * 0.5F) * 2), (int) (textY * 2), TEXT_LIGHT);
            textY += lineHeight;
        }
        GL11.glPopMatrix();
    }

    private void drawStack(IAEStack<?> stack, int x, int y) {
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        try {
            stack.drawInGui(mc, x, y);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glPopMatrix();
        }
    }

    /** The selected CPU's storage and co-processors, and whether AE2 suspended its job. */
    private String cpuStatusLine() {
        CraftingCPUStatus selected = ecoCpuTable.getContainer()
            .getSelectedCPU();
        if (selected == null) return GuiText.NoCraftingJobs.getLocal();
        String line = I18n.format(
            "gui.neoecoae.crafting_report.cpu_status",
            NEByteFormatter.formatCpuStorage(selected.getStorage()),
            NEByteFormatter.formatCpuCoProcessors(selected.getCoprocessors()));
        return cpuContainer.cachedSuspend ? line + " · " + I18n.format("gui.neoecoae.cpu.status.suspended") : line;
    }

    private int cpuStatusColor() {
        return cpuContainer.cachedSuspend ? SUSPENDED_COLOR : TEXT_DARK;
    }

    private void drawCenteredScaled(String text, int centerX, int y, int maxWidth, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return;
        int width = fontRendererObj.getStringWidth(text);
        float scale = Math.min(1F, maxWidth / (float) Math.max(1, width));
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX - width * scale / 2F, y, 0);
        GL11.glScalef(scale, scale, 1);
        if (shadow) fontRendererObj.drawStringWithShadow(text, 0, 0, color);
        else fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    @Override
    protected void handleTooltip(int mouseX, int mouseY, ITooltip tip) {
        // The ECO header omits AE2's remaining-operations display and its hover zone.
        if (ecoLayout && !(tip instanceof GuiButton)) return;
        super.handleTooltip(mouseX, mouseY, tip);
    }

    // ---- Input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (ecoLayout) {
            if (itemMenu.click(mouseX, mouseY, button)) return;
            CraftingCpuEntry entry = hoveredEntry;
            if (entry != null && button == 1 && openItemMenu(entry, mouseX, mouseY)) return;
            if (entry != null && isShiftKeyDown() && interfaceLocations != null && !interfaceLocations.isEmpty()) {
                highlightInterfaces(interfaceLocations);
                return;
            }
            if (entry != null && button == 2) {
                // AE2's middle click: request more of the hovered stack.
                cpuContainer.setTargetStack(entry.getVisualStack());
                NetworkHandler.instance.sendToServer(
                    new PacketInventoryAction(
                        InventoryAction.AUTO_CRAFT,
                        inventorySlots.inventorySlots.size(),
                        entry.getActiveAmount()));
            }
            if (cycleLayout) cycleScroll.click(this, mouseX - guiLeft, mouseY - guiTop);
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean openItemMenu(CraftingCpuEntry entry, int mouseX, int mouseY) {
        IAEStack<?> visual = entry.getVisualStack();
        AEKey key = AEKey.fromNative(entry.getStack());
        if (visual == null || key == null) return false;
        itemMenu.open(key, Platform.getItemDisplayName(visual), mouseX + 4, mouseY + 4, width, height);
        return true;
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (ecoLayout && itemMenu.keyTyped(keyCode)) return;
        super.keyTyped(character, keyCode);
    }

    @Override
    public void handleMouseInput() {
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0 && ecoLayout && itemMenu.scroll(wheel)) return;
        super.handleMouseInput();
    }

    private void highlightInterfaces(List<NamedDimensionalCoord> locations) {
        Map<NamedDimensionalCoord, String[]> messages = new HashMap<>();
        for (NamedDimensionalCoord block : locations) messages.put(
            block,
            new String[] { PlayerMessages.MachineHighlightedNamed.getUnlocalized(),
                PlayerMessages.MachineInOtherDimNamed.getUnlocalized() });
        BlockPosHighlighter.highlightNamedBlocks(
            mc.thePlayer,
            messages,
            ((Localization) () -> "tile.appliedenergistics2.BlockInterface.name").getLocal());
        closeGui();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceClick) {
        if (ecoLayout && cycleLayout) cycleScroll.clickMove(mouseY - guiTop);
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected boolean mouseWheelEvent(int mouseX, int mouseY, int wheel) {
        if (ecoLayout && isShiftKeyDown() && detailKey != null) {
            // 1.20.1: Shift + wheel pages the hovered output's machine details.
            ECOProcessingProgress progress = processing.get(detailKey);
            int pages = progress == null ? 1 : progress.pages();
            if (pages > 1) {
                detailPage = Math.max(0, Math.min(pages - 1, detailPage + (wheel < 0 ? 1 : -1)));
                return true;
            }
        }
        if (ecoLayout && cycleLayout && mouseX - guiLeft >= CYCLE_X) {
            cycleScroll.wheel(wheel);
            return true;
        }
        return super.mouseWheelEvent(mouseX, mouseY, wheel);
    }

    @Override
    public ItemStack getHoveredStack() {
        return ecoLayout ? hoveredItem : super.getHoveredStack();
    }

    @Override
    public boolean hideItemPanelSlot(int x, int y, int w, int h) {
        return ecoLayout ? hidesPanelSlot(x, y, w, h) : super.hideItemPanelSlot(x, y, w, h);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return ecoLayout ? hidesPanelSlot(x, y, w, h) : super.hideItemPanelSlot(gui, x, y, w, h);
    }

    /** NEI places its panels from guiLeft, which is the panel's origin; the CPU column and side buttons are extra. */
    private boolean hidesPanelSlot(int x, int y, int w, int h) {
        int localX = x - guiLeft;
        int localY = y - guiTop;
        boolean cpuColumn = localX + w > -CPU_PANEL_WIDTH && localX < 0
            && localY + h > 0
            && localY < CPU_PANEL_HEIGHT + extra();
        boolean leftButton = localX + w > -18 && localX < 0 && localY + h > ySize - 18 && localY < ySize;
        boolean sideButtons = localX + w > panelWidth() && localX < sideX() + 18 && localY + h > 0 && localY < 46;
        return cpuColumn || leftButton || sideButtons;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ECOCraftingStatusClient.release(inventorySlots);
    }

    /** AE2's CPU column, moved down to the ECO texture's card rows while the ECO layout is shown. */
    private final class EcoCpuTable extends GuiCraftingCPUTable {

        EcoCpuTable(ContainerCPUTable container) {
            super(ECOCraftingStatusGui.this, container, status -> false);
        }

        private int shift() {
            return ecoLayout ? CPU_LIST_SHIFT : 0;
        }

        @Override
        public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY, int guiLeft, int guiTop) {
            int shift = shift();
            GL11.glPushMatrix();
            GL11.glTranslatef(0, shift, 0);
            super.drawFG(offsetX, offsetY, mouseX, mouseY - shift, guiLeft, guiTop);
            GL11.glPopMatrix();
        }

        @Override
        public void mouseClicked(int x, int y, int button) {
            super.mouseClicked(x, y - shift(), button);
        }

        @Override
        public void mouseClickMove(int x, int y) {
            super.mouseClickMove(x, y - shift());
        }

        @Override
        public boolean handleMouseInput(int guiLeft, int guiTop) {
            return super.handleMouseInput(guiLeft, guiTop + shift());
        }

        @Override
        public boolean hideItemPanelSlot(int x, int y, int w, int h) {
            return super.hideItemPanelSlot(x, y - shift(), w, h);
        }
    }
}
