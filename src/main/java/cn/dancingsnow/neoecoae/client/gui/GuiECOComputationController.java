package cn.dancingsnow.neoecoae.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostSnapshot;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOComputationController;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.PacketComputationHostAction;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiECOComputationController extends GuiHostMachineBase {

    private static final int CPU_BUTTON_ID = 7601;

    private final ContainerECOComputationController container;
    private final TileECOController controller;
    private GuiButton cpuModeButton;
    private List<String> hoveredLines;
    private int taskScroll;

    public GuiECOComputationController(InventoryPlayer playerInventory, TileECOController controller) {
        this(new ContainerECOComputationController(playerInventory, controller));
    }

    private GuiECOComputationController(ContainerECOComputationController container) {
        super(container, HostUiLayouts.COMPUTATION.width(), HostUiLayouts.COMPUTATION.height());
        this.container = container;
        this.controller = container.getController();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.cpuModeButton = new InvisibleButton(
            CPU_BUTTON_ID,
            this.guiLeft + ComputationControllerLayout.TOOLBAR_X,
            this.guiTop + ComputationControllerLayout.TOOLBAR_Y,
            ComputationControllerLayout.TOOLBAR_SIZE,
            ComputationControllerLayout.TOOLBAR_SIZE);
        this.buttonList.add(this.cpuModeButton);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (!this.isMouseIn(
            ComputationControllerLayout.TASK_X,
            ComputationControllerLayout.TASK_Y,
            ComputationControllerLayout.TASK_W,
            ComputationControllerLayout.TASK_H,
            mouseX,
            mouseY)) {
            return;
        }
        int direction = wheel < 0 ? ComputationControllerLayout.TASK_SCROLL_STEP
            : -ComputationControllerLayout.TASK_SCROLL_STEP;
        this.taskScroll = Math.max(0, Math.min(this.taskScroll + direction, this.maxTaskScroll()));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.hoveredLines = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.hoveredLines != null && !this.hoveredLines.isEmpty()) {
            this.drawTooltip(this.hoveredLines, mouseX, mouseY);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == this.cpuModeButton) {
            NENetwork.CHANNEL.sendToServer(
                new PacketComputationHostAction(this.controller, PacketComputationHostAction.Action.CYCLE_CPU_MODE));
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDarkInsetRect(
            ComputationControllerLayout.MAIN_X,
            ComputationControllerLayout.MAIN_Y,
            ComputationControllerLayout.MAIN_W,
            ComputationControllerLayout.MAIN_H);
        this.drawDarkInsetRect(
            ComputationControllerLayout.TASK_X,
            ComputationControllerLayout.TASK_Y,
            ComputationControllerLayout.TASK_W,
            ComputationControllerLayout.TASK_H);
        this.drawPlayerInventorySlots(
            HostUiLayouts.COMPUTATION.inventoryX(),
            HostUiLayouts.COMPUTATION.inventoryY(),
            HostUiLayouts.COMPUTATION.hotbarY());
        this.drawCpuModeButtonBackground(mouseX, mouseY);
        if (this.cpuModeButton != null) {
            this.cpuModeButton.displayString = "";
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int localMouseX, int localMouseY) {
        int mouseX = this.guiLeft + localMouseX;
        int mouseY = this.guiTop + localMouseY;
        ComputationHostSnapshot state = this.container.state();
        this.drawHeader(state);
        this.drawCpuModeButtonText(state, mouseX, mouseY);
        this.drawMainStats(state, mouseX, mouseY);
        this.drawTaskPanel(state, mouseX, mouseY);
        this.drawLocalText(
            tr("gui.neoecoae.common.inventory", "Inventory"),
            HostUiLayouts.COMPUTATION.inventoryX(),
            159,
            HostUiStyle.TEXT_MUTED);
    }

    private void drawHeader(ComputationHostSnapshot state) {
        this.drawLocalText(
            tr("gui.neoecoae.computation_ui.title", "ECO Computation Host") + " " + state.tier,
            8,
            ComputationControllerLayout.HEADER_Y,
            HostUiStyle.TEXT_PRIMARY);
        String formedLabel = tr("gui.neoecoae.machine.formed", "Formed") + ": ";
        String activeLabel = "    " + tr("gui.neoecoae.machine.active", "Active") + ": ";
        String formedValue = yesNo(state.formed);
        String activeValue = yesNo(state.active);
        int width = this.fontRendererObj.getStringWidth(formedLabel) + this.fontRendererObj.getStringWidth(formedValue)
            + this.fontRendererObj.getStringWidth(activeLabel)
            + this.fontRendererObj.getStringWidth(activeValue);
        int x = Math.max(8, ComputationControllerLayout.TOOLBAR_X - 4 - width);
        x += this.drawLocalSegment(formedLabel, x, ComputationControllerLayout.HEADER_Y, HostUiStyle.TEXT_SECONDARY);
        x += this.drawLocalSegment(
            formedValue,
            x,
            ComputationControllerLayout.HEADER_Y,
            state.formed ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_BAD);
        x += this.drawLocalSegment(activeLabel, x, ComputationControllerLayout.HEADER_Y, HostUiStyle.TEXT_SECONDARY);
        this.drawLocalText(
            activeValue,
            x,
            ComputationControllerLayout.HEADER_Y,
            state.active ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_MUTED);
    }

    private void drawCpuModeButtonBackground(int mouseX, int mouseY) {
        boolean hovered = this.isMouseIn(
            ComputationControllerLayout.TOOLBAR_X,
            ComputationControllerLayout.TOOLBAR_Y,
            ComputationControllerLayout.TOOLBAR_SIZE,
            ComputationControllerLayout.TOOLBAR_SIZE,
            mouseX,
            mouseY);
        this.drawButtonTexture(
            ComputationControllerLayout.TOOLBAR_X,
            ComputationControllerLayout.TOOLBAR_Y,
            ComputationControllerLayout.TOOLBAR_SIZE,
            ComputationControllerLayout.TOOLBAR_SIZE,
            hovered,
            true,
            false);
    }

    private void drawCpuModeButtonText(ComputationHostSnapshot state, int mouseX, int mouseY) {
        boolean hovered = this.isMouseIn(
            ComputationControllerLayout.TOOLBAR_X,
            ComputationControllerLayout.TOOLBAR_Y,
            ComputationControllerLayout.TOOLBAR_SIZE,
            ComputationControllerLayout.TOOLBAR_SIZE,
            mouseX,
            mouseY);
        ItemStack icon = ComputationCpuModeIcons.icon(state.cpuSelectionMode);
        if (icon == null) {
            String symbol = cpuModeSymbol(state.cpuSelectionMode);
            this.drawLocalCentered(
                symbol,
                ComputationControllerLayout.TOOLBAR_X,
                ComputationControllerLayout.TOOLBAR_Y + 4,
                ComputationControllerLayout.TOOLBAR_SIZE,
                HostUiStyle.TEXT_VALUE);
        } else {
            this.drawLocalItemIcon(icon, ComputationControllerLayout.TOOLBAR_X, ComputationControllerLayout.TOOLBAR_Y);
        }
        if (hovered) {
            List<String> lines = new ArrayList<String>();
            lines
                .add(EnumChatFormatting.AQUA + tr("gui.neoecoae.computation.cpu_selection_mode", "CPU Selection Mode"));
            lines.add(cpuModeName(state.cpuSelectionMode));
            lines.add(
                EnumChatFormatting.GRAY
                    + tr("gui.neoecoae.computation.cpu_selection_mode.click", "Click to cycle mode"));
            this.hoveredLines = lines;
        }
    }

    private void drawMainStats(ComputationHostSnapshot state, int mouseX, int mouseY) {
        int x = ComputationControllerLayout.STAT_X;
        int y = ComputationControllerLayout.MAIN_Y + 8;
        this.drawUsedTotal(
            tr("gui.neoecoae.computation.threads", "Threads") + ": ",
            state.usedThreads,
            state.totalThreads,
            "",
            x,
            y,
            false,
            HostUiStyle.DARK_TEXT_USED);
        this.drawUsageBarLocal(
            ComputationControllerLayout.STAT_BAR_X,
            ComputationControllerLayout.THREAD_BAR_Y,
            ComputationControllerLayout.STAT_BAR_W,
            ComputationControllerLayout.STAT_BAR_H,
            state.usedThreads,
            state.totalThreads,
            HostUiStyle.TEXT_GOOD);
        y += 24;
        this.drawLocalText(
            tr("gui.neoecoae.computation.parallel_count", "Parallel Capacity") + ": "
                + this.formatNumber(state.parallelCount),
            x,
            y,
            HostUiStyle.DARK_TEXT_PRIMARY);
        y += 12;
        this.drawLocalText(
            tr("gui.neoecoae.computation.cpu_selection_mode.short", "Mode") + ": "
                + cpuModeShortName(state.cpuSelectionMode),
            x,
            y,
            HostUiStyle.DARK_TEXT_VALUE);
        this.drawUsedTotal(
            tr("gui.neoecoae.computation.storage_used", "Storage") + ": ",
            state.usedComputationBytes,
            state.totalBytes,
            "",
            x,
            ComputationControllerLayout.STORAGE_TEXT_Y,
            true,
            HostUiStyle.DARK_TEXT_BLUE);
        this.drawUsageBarLocal(
            ComputationControllerLayout.STAT_BAR_X,
            ComputationControllerLayout.STORAGE_BAR_Y,
            ComputationControllerLayout.STAT_BAR_W,
            ComputationControllerLayout.STAT_BAR_H,
            state.usedComputationBytes,
            state.totalBytes,
            HostUiStyle.DARK_TEXT_BLUE);
        this.drawLocalText(
            tr("gui.neoecoae.computation.parallel_cores", "Parallel Cores") + ": "
                + this.formatNumber(state.parallelCores),
            x,
            ComputationControllerLayout.PARALLEL_CORES_Y,
            HostUiStyle.DARK_TEXT_PRIMARY);
        if (this.isMouseIn(
            ComputationControllerLayout.STAT_BAR_X,
            ComputationControllerLayout.THREAD_BAR_Y,
            ComputationControllerLayout.STAT_BAR_W,
            ComputationControllerLayout.STAT_BAR_H,
            mouseX,
            mouseY)) {
            this.hoveredLines = usedTotalTooltip(
                tr("gui.neoecoae.computation.threads", "Threads"),
                state.usedThreads,
                state.totalThreads,
                false);
        } else if (this.isMouseIn(
            ComputationControllerLayout.STAT_BAR_X,
            ComputationControllerLayout.STORAGE_BAR_Y,
            ComputationControllerLayout.STAT_BAR_W,
            ComputationControllerLayout.STAT_BAR_H,
            mouseX,
            mouseY)) {
                this.hoveredLines = usedTotalTooltip(
                    tr("gui.neoecoae.computation.available_storage", "Available Storage"),
                    state.usedComputationBytes,
                    state.totalBytes,
                    true);
            } else if (this.isMouseIn(
                ComputationControllerLayout.MAIN_X,
                ComputationControllerLayout.MAIN_Y,
                ComputationControllerLayout.MAIN_W,
                ComputationControllerLayout.MAIN_H,
                mouseX,
                mouseY)) {
                    List<String> lines = new ArrayList<String>();
                    lines
                        .add(EnumChatFormatting.AQUA + tr("gui.neoecoae.computation_ui.title", "ECO Computation Host"));
                    lines.add(tr("gui.neoecoae.host_ui.formation", "Formation") + ": " + state.formationMessage);
                    this.hoveredLines = lines;
                }
    }

    private void drawTaskPanel(ComputationHostSnapshot state, int mouseX, int mouseY) {
        this.drawLocalText(
            tr("gui.neoecoae.crafting.tasks", "Crafting Tasks"),
            ComputationControllerLayout.TASK_X + 8,
            ComputationControllerLayout.TASK_Y + 6,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalRight(
            this.formatNumber(state.tasks.size()),
            ComputationControllerLayout.TASK_X + ComputationControllerLayout.TASK_W - 8,
            ComputationControllerLayout.TASK_Y + 6,
            HostUiStyle.DARK_TEXT_VALUE);
        if (state.tasks.isEmpty()) {
            this.taskScroll = 0;
            this.drawLocalCentered(
                tr("gui.neoecoae.crafting.no_tasks", "No tasks"),
                ComputationControllerLayout.TASK_X,
                ComputationControllerLayout.TASK_Y + ComputationControllerLayout.TASK_H / 2 - 4,
                ComputationControllerLayout.TASK_W,
                HostUiStyle.DARK_TEXT_MUTED);
            return;
        }
        this.taskScroll = Math.min(this.taskScroll, this.maxTaskScroll());
        int visible = (ComputationControllerLayout.TASK_H - 24) / ComputationControllerLayout.TASK_CARD_STEP;
        this.beginScissor(
            ComputationControllerLayout.TASK_X + 4,
            ComputationControllerLayout.TASK_CARD_Y,
            ComputationControllerLayout.TASK_W - 8,
            ComputationControllerLayout.TASK_H - 23);
        for (int i = 0; i < visible && i + this.taskScroll < state.tasks.size(); i++) {
            int taskIndex = i + this.taskScroll;
            ComputationHostSnapshot.TaskEntry task = state.tasks.get(taskIndex);
            int y = ComputationControllerLayout.TASK_CARD_Y + i * ComputationControllerLayout.TASK_CARD_STEP;
            this.drawTaskCard(task, ComputationControllerLayout.TASK_CARD_X, y, mouseX, mouseY);
        }
        this.endScissor();
        if (this.maxTaskScroll() > 0) {
            this.drawTaskScrollMarker(state);
        }
    }

    private void drawTaskCard(ComputationHostSnapshot.TaskEntry task, int x, int y, int mouseX, int mouseY) {
        boolean hovered = this.isMouseIn(
            x,
            y,
            ComputationControllerLayout.TASK_CARD_W,
            ComputationControllerLayout.TASK_CARD_H,
            mouseX,
            mouseY);
        this.drawTinyInsetLocal(
            x,
            y,
            ComputationControllerLayout.TASK_CARD_W,
            ComputationControllerLayout.TASK_CARD_H,
            hovered ? 0xFF2A2535 : 0xFF201E27);
        this.drawLocalItemIcon(task.outputStack, x + 4, y + 4);
        this.drawLocalText(
            this.trimToWidth(task.outputName, ComputationControllerLayout.TASK_CARD_W - 90),
            x + 24,
            y + 4,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalRight(
            this.formatElapsedNanos(task.elapsedNanos),
            x + ComputationControllerLayout.TASK_CARD_W - 5,
            y + 11,
            HostUiStyle.DARK_TEXT_VALUE);
        if (hovered) {
            List<String> lines = new ArrayList<String>();
            lines.add(EnumChatFormatting.AQUA + task.outputName);
            lines.add(
                EnumChatFormatting.GRAY + tr("gui.neoecoae.computation.elapsed", "Elapsed")
                    + ": "
                    + this.formatElapsedNanos(task.elapsedNanos));
            this.hoveredLines = lines;
        }
    }

    private void drawTaskScrollMarker(ComputationHostSnapshot state) {
        int trackX = ComputationControllerLayout.TASK_X + ComputationControllerLayout.TASK_W - 5;
        int trackY = ComputationControllerLayout.TASK_CARD_Y;
        int trackH = ComputationControllerLayout.TASK_H - 27;
        int maxScroll = Math.max(1, this.maxTaskScroll());
        int visible = Math
            .max(1, (ComputationControllerLayout.TASK_H - 24) / ComputationControllerLayout.TASK_CARD_STEP);
        int thumbH = Math.max(8, trackH * visible / Math.max(1, state.tasks.size()));
        int thumbY = trackY + (trackH - thumbH) * this.taskScroll / maxScroll;
        drawRect(trackX, trackY, trackX + 2, trackY + trackH, 0xFF2C2833);
        drawRect(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFBEB6D4);
    }

    private int maxTaskScroll() {
        ComputationHostSnapshot state = this.container.state();
        int visible = Math
            .max(1, (ComputationControllerLayout.TASK_H - 24) / ComputationControllerLayout.TASK_CARD_STEP);
        return Math.max(0, state.tasks.size() - visible);
    }

    private void drawUsedTotal(String prefix, long used, long total, String suffix, int x, int y, boolean storageBytes,
        int usedColor) {
        int cursor = this.drawLocalSegment(prefix, x, y, HostUiStyle.DARK_TEXT_MUTED);
        String usedText = storageBytes ? this.formatStorageBytes(used) : this.formatNumber(used);
        String totalText = storageBytes ? this.formatStorageBytes(total) : this.formatNumber(total);
        cursor += this.drawLocalSegment(usedText, x + cursor, y, usedColor);
        cursor += this.drawLocalSegment(" / ", x + cursor, y, HostUiStyle.DARK_TEXT_MUTED);
        cursor += this.drawLocalSegment(totalText, x + cursor, y, HostUiStyle.DARK_TEXT_VALUE);
        this.drawLocalText(suffix, x + cursor, y, HostUiStyle.DARK_TEXT_MUTED);
    }

    private List<String> usedTotalTooltip(String title, long used, long total, boolean storageBytes) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + title);
        lines.add(
            (storageBytes ? this.formatStorageBytes(used) : this.formatNumber(used)) + " / "
                + (storageBytes ? this.formatStorageBytes(total) : this.formatNumber(total)));
        return lines;
    }

    private String trimToWidth(String text, int width) {
        String safe = text == null ? "" : text;
        if (this.fontRendererObj.getStringWidth(safe) <= width) {
            return safe;
        }
        return this.fontRendererObj
            .trimStringToWidth(safe, Math.max(0, width - this.fontRendererObj.getStringWidth("..."))) + "...";
    }

    private String formatElapsedNanos(long elapsedNanos) {
        long seconds = Math.max(0L, elapsedNanos / 1000000000L);
        long hours = seconds / 3600L;
        long minutes = seconds / 60L % 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0L) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds);
        }
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, remainingSeconds);
    }

    private static String cpuModeSymbol(ComputationCpuSelectionMode mode) {
        if (mode == ComputationCpuSelectionMode.PLAYER_ONLY) {
            return "P";
        }
        if (mode == ComputationCpuSelectionMode.MACHINE_ONLY) {
            return "M";
        }
        return "A";
    }

    private static String cpuModeName(ComputationCpuSelectionMode mode) {
        if (mode == ComputationCpuSelectionMode.PLAYER_ONLY) {
            return tr("gui.neoecoae.computation.cpu_selection_mode.player", "Player Only");
        }
        if (mode == ComputationCpuSelectionMode.MACHINE_ONLY) {
            return tr("gui.neoecoae.computation.cpu_selection_mode.machine", "Machine Only");
        }
        return tr("gui.neoecoae.computation.cpu_selection_mode.any", "Any");
    }

    private static String cpuModeShortName(ComputationCpuSelectionMode mode) {
        if (mode == ComputationCpuSelectionMode.PLAYER_ONLY) {
            return tr("gui.neoecoae.computation.cpu_selection_mode.short.player", "Player");
        }
        if (mode == ComputationCpuSelectionMode.MACHINE_ONLY) {
            return tr("gui.neoecoae.computation.cpu_selection_mode.short.machine", "Machine");
        }
        return tr("gui.neoecoae.computation.cpu_selection_mode.short.any", "Any");
    }

    private static String tr(String key, String fallback) {
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }

    private static final class InvisibleButton extends GuiButton {

        private InvisibleButton(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height, "");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {}
    }
}
