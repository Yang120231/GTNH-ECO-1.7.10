package cn.dancingsnow.neoecoae.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cn.dancingsnow.neoecoae.gui.HostUiLayouts;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOCraftingController;
import cn.dancingsnow.neoecoae.gui.state.SimpleHostUiState;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiECOCraftingController extends GuiHostMachineBase {

    private final ContainerECOCraftingController container;
    private List<String> hoveredLines;

    public GuiECOCraftingController(InventoryPlayer playerInventory, TileECOController controller) {
        this(new ContainerECOCraftingController(playerInventory, controller));
    }

    private GuiECOCraftingController(ContainerECOCraftingController container) {
        super(container, HostUiLayouts.CRAFTING.width(), HostUiLayouts.CRAFTING.height());
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
        this.drawDarkInsetRect(10, 24, 132, 146);
        this.drawDarkInsetRect(150, 24, 144, 72);
        this.drawDarkInsetRect(150, 104, 144, 66);
        this.drawPlayerInventorySlots(
            HostUiLayouts.CRAFTING.inventoryX(),
            HostUiLayouts.CRAFTING.inventoryY(),
            HostUiLayouts.CRAFTING.hotbarY());
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        SimpleHostUiState state = this.container.state();
        int color = HostUiStyle.tierColor(state.tier);
        this.drawLocalText(
            tr("gui.neoecoae.crafting_ui.title", "ECO Crafting Host") + " " + state.tier,
            12,
            8,
            HostUiStyle.TEXT_PRIMARY);
        this.drawLocalRight(
            state.formed ? yesNo(true) : yesNo(false),
            this.xSize - 12,
            8,
            state.formed ? HostUiStyle.TEXT_GOOD : HostUiStyle.TEXT_BAD);
        this.drawLocalText(tr("gui.neoecoae.host_ui.status", "Status"), 20, 34, HostUiStyle.TEXT_PRIMARY);
        this.drawLocalText(
            tr("gui.neoecoae.host_ui.members", "Members") + ": " + state.memberCount,
            20,
            54,
            HostUiStyle.TEXT_VALUE);
        this.drawLocalText(
            tr("gui.neoecoae.host_ui.mirrored", "Mirrored") + ": " + yesNo(state.mirrored),
            20,
            68,
            HostUiStyle.TEXT_MUTED);
        this.drawUsageBar(
            20,
            92,
            102,
            10,
            state.formed ? Math.max(1, state.memberCount) : 0,
            Math.max(1, state.memberCount),
            color);
        this.drawLocalText(tr("gui.neoecoae.crafting_ui.modules", "Modules"), 158, 34, HostUiStyle.TEXT_PRIMARY);
        this.drawLocalText(
            tr("gui.neoecoae.crafting_ui.parallel", "Parallel") + ": 0",
            158,
            52,
            HostUiStyle.TEXT_MUTED);
        this.drawLocalText(
            tr("gui.neoecoae.crafting_ui.cooling", "Cooling") + ": " + yesNo(false),
            158,
            66,
            HostUiStyle.TEXT_MUTED);
        this.drawLocalText(tr("gui.neoecoae.crafting_ui.tasks", "Crafting Tasks"), 158, 114, HostUiStyle.TEXT_PRIMARY);
        this.drawLocalCentered(
            tr("gui.neoecoae.host_ui.no_runtime_data", "Runtime backend not connected yet"),
            150,
            136,
            144,
            HostUiStyle.TEXT_MUTED);
        this.drawLocalText(
            tr("container.inventory", "Inventory"),
            HostUiLayouts.CRAFTING.inventoryX(),
            176,
            HostUiStyle.TEXT_MUTED);
        if (this.isMouseIn(10, 24, 132, 146, mouseX, mouseY)) {
            this.hoveredLines = tooltip(state);
        }
    }

    private static List<String> tooltip(SimpleHostUiState state) {
        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.AQUA + tr("gui.neoecoae.crafting_ui.title", "ECO Crafting Host"));
        lines.add(tr("gui.neoecoae.host_ui.formation", "Formation") + ": " + state.formationMessage);
        return lines;
    }

    private static String tr(String key, String fallback) {
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }
}
