package cn.dancingsnow.neoecoae.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOComputationController;
import cn.dancingsnow.neoecoae.gui.state.SimpleHostUiState;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiECOComputationController extends GuiHostMachineBase {

    private final ContainerECOComputationController container;
    private List<String> hoveredLines;

    public GuiECOComputationController(InventoryPlayer playerInventory, TileECOController controller) {
        this(new ContainerECOComputationController(playerInventory, controller));
    }

    private GuiECOComputationController(ContainerECOComputationController container) {
        super(container, HostUiLayouts.COMPUTATION.width(), HostUiLayouts.COMPUTATION.height());
        this.container = container;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.hoveredLines = null;
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.hoveredLines != null) {
            this.drawTooltip(this.hoveredLines, mouseX, mouseY);
        }
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDarkInsetRect(8, 24, 218, 132);
        this.drawDarkInsetRect(234, 24, 102, 132);
        this.drawDarkInsetRect(180, 194, 156, 52);
        this.drawPlayerInventorySlots(HostUiLayouts.COMPUTATION.inventoryX(), HostUiLayouts.COMPUTATION.inventoryY(), HostUiLayouts.COMPUTATION.hotbarY());
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        SimpleHostUiState state = this.container.state();
        this.drawLocalText(tr("gui.neoecoae.computation_ui.title", "ECO Computation Host") + " " + state.tier, 8, 8, HostUiStyle.TEXT_PRIMARY);
        this.drawLocalRight(state.formed ? yesNo(true) : yesNo(false), this.xSize - 8, 8, state.formed ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_BAD);
        int color = HostUiStyle.tierColor(state.tier);
        this.drawLocalText(tr("gui.neoecoae.host_ui.status", "Status"), 16, 34, HostUiStyle.TEXT_PRIMARY);
        this.drawLocalText(tr("gui.neoecoae.host_ui.subsystem", "Subsystem") + ": " + state.subsystem, 16, 52, HostUiStyle.TEXT_MUTED);
        this.drawLocalText(tr("gui.neoecoae.host_ui.members", "Members") + ": " + state.memberCount, 16, 66, HostUiStyle.TEXT_VALUE);
        this.drawLocalText(tr("gui.neoecoae.host_ui.mirrored", "Mirrored") + ": " + yesNo(state.mirrored), 16, 80, HostUiStyle.TEXT_MUTED);
        this.drawUsageBar(16, 102, 190, 10, state.formed ? Math.max(1, state.memberCount) : 0, Math.max(1, state.memberCount), color);
        this.drawLocalCentered(tr("gui.neoecoae.computation_ui.thread_pool", "Thread Pool"), 234, 34, 102, HostUiStyle.TEXT_PRIMARY);
        this.drawVerticalGauge(280, 58, 10, 64, 0, 0, color);
        this.drawLocalCentered("0 / 0", 234, 132, 102, HostUiStyle.TEXT_MUTED);
        this.drawLocalText(tr("gui.neoecoae.computation_ui.tasks", "Tasks"), 186, 202, HostUiStyle.TEXT_PRIMARY);
        this.drawLocalCentered(tr("gui.neoecoae.host_ui.no_runtime_data", "Runtime backend not connected yet"), 180, 222, 156, HostUiStyle.TEXT_MUTED);
        this.drawLocalText(tr("container.inventory", "Inventory"), HostUiLayouts.COMPUTATION.inventoryX(), 160, HostUiStyle.TEXT_MUTED);
        if (this.isMouseIn(8, 24, 218, 132, mouseX, mouseY)) {
            this.hoveredLines = tooltip(state);
        }
    }

    private static List<String> tooltip(SimpleHostUiState state) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.computation_ui.title", "ECO Computation Host"));
        lines.add(tr("gui.neoecoae.host_ui.formation", "Formation") + ": " + state.formationMessage);
        return lines;
    }

    private static String tr(String key, String fallback) {
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }
}
