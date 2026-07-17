package cn.dancingsnow.neoecoae.client.gui;

import java.util.Collections;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageInterface;
import cn.dancingsnow.neoecoae.gui.storage.StorageInterfaceSnapshot;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.PacketStorageInterfaceAction;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pixel-for-pixel 1.7.10 rendering of the 1.20.1 LDLib storage-interface panel. */
@SideOnly(Side.CLIENT)
public class GuiECOStorageInterface extends GuiHostMachineBase {

    public static final int UI_WIDTH = 224;
    public static final int UI_HEIGHT = 116;

    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 24;
    private static final int PANEL_W = UI_WIDTH - 16;
    private static final int PANEL_H = UI_HEIGHT - 32;
    private static final int MODE_BUTTON_Y = PANEL_Y + 10;
    private static final int MODE_BUTTON_W = 62;
    private static final int MODE_BUTTON_H = 20;
    private static final int STORAGE_BUTTON_X = PANEL_X + 8;
    private static final int INPUT_BUTTON_X = PANEL_X + (PANEL_W - MODE_BUTTON_W) / 2;
    private static final int OUTPUT_BUTTON_X = PANEL_X + PANEL_W - MODE_BUTTON_W - 8;
    private static final int TEXT_X = PANEL_X + 10;
    private static final int TEXT_Y = PANEL_Y + 40;
    private static final int TEXT_STEP = 12;
    private static final int STATUS_VALUE_X = TEXT_X + 72;

    private static final int STORAGE_BUTTON_ID = 8100;
    private static final int INPUT_BUTTON_ID = 8101;
    private static final int OUTPUT_BUTTON_ID = 8102;

    private final ContainerECOStorageInterface container;
    private final TileECOInterface storageInterface;

    public GuiECOStorageInterface(TileECOInterface storageInterface) {
        this(new ContainerECOStorageInterface(storageInterface));
    }

    private GuiECOStorageInterface(ContainerECOStorageInterface container) {
        super(container, UI_WIDTH, UI_HEIGHT);
        this.container = container;
        this.storageInterface = container.getStorageInterface();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(
            new InvisibleButton(
                STORAGE_BUTTON_ID,
                this.guiLeft + STORAGE_BUTTON_X,
                this.guiTop + MODE_BUTTON_Y,
                MODE_BUTTON_W,
                MODE_BUTTON_H));
        this.buttonList.add(
            new InvisibleButton(
                INPUT_BUTTON_ID,
                this.guiLeft + INPUT_BUTTON_X,
                this.guiTop + MODE_BUTTON_Y,
                MODE_BUTTON_W,
                MODE_BUTTON_H));
        this.buttonList.add(
            new InvisibleButton(
                OUTPUT_BUTTON_ID,
                this.guiLeft + OUTPUT_BUTTON_X,
                this.guiTop + MODE_BUTTON_Y,
                MODE_BUTTON_W,
                MODE_BUTTON_H));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        ECOStorageInterfaceMode mode = button.id == STORAGE_BUTTON_ID ? ECOStorageInterfaceMode.STORAGE
            : button.id == INPUT_BUTTON_ID ? ECOStorageInterfaceMode.INPUT
                : button.id == OUTPUT_BUTTON_ID ? ECOStorageInterfaceMode.OUTPUT : null;
        if (mode != null) {
            NENetwork.CHANNEL.sendToServer(new PacketStorageInterfaceAction(this.storageInterface, mode));
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDarkInsetRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        StorageInterfaceSnapshot state = this.container.state();
        this.drawModeButton(STORAGE_BUTTON_X, ECOStorageInterfaceMode.STORAGE, mouseX, mouseY, state);
        this.drawModeButton(INPUT_BUTTON_X, ECOStorageInterfaceMode.INPUT, mouseX, mouseY, state);
        this.drawModeButton(OUTPUT_BUTTON_X, ECOStorageInterfaceMode.OUTPUT, mouseX, mouseY, state);
    }

    private void drawModeButton(int x, ECOStorageInterfaceMode mode, int mouseX, int mouseY,
        StorageInterfaceSnapshot state) {
        boolean hovered = this.isMouseIn(x, MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H, mouseX, mouseY);
        this.drawButtonTexture(x, MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H, hovered, true, state.mode == mode);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        StorageInterfaceSnapshot state = this.container.state();
        this.drawLocalText(
            tr("gui.neoecoae.storage_interface.title", "Storage Interface"),
            8,
            8,
            HostUiStyle.TEXT_PRIMARY);
        this.drawLocalCentered(
            tr("gui.neoecoae.storage_interface.mode.storage", "Storage"),
            STORAGE_BUTTON_X,
            MODE_BUTTON_Y + 6,
            MODE_BUTTON_W,
            buttonTextColor(state.mode == ECOStorageInterfaceMode.STORAGE));
        this.drawLocalCentered(
            tr("gui.neoecoae.storage_interface.mode.input", "Input"),
            INPUT_BUTTON_X,
            MODE_BUTTON_Y + 6,
            MODE_BUTTON_W,
            buttonTextColor(state.mode == ECOStorageInterfaceMode.INPUT));
        this.drawLocalCentered(
            tr("gui.neoecoae.storage_interface.mode.output", "Output"),
            OUTPUT_BUTTON_X,
            MODE_BUTTON_Y + 6,
            MODE_BUTTON_W,
            buttonTextColor(state.mode == ECOStorageInterfaceMode.OUTPUT));

        int y = TEXT_Y;
        this.drawStatusLine(
            tr("gui.neoecoae.storage_interface.structure", "Structure"),
            state.formed ? tr("gui.neoecoae.storage_interface.formed", "Formed")
                : tr("gui.neoecoae.storage_interface.unformed", "Unformed"),
            state.formed,
            y);
        y += TEXT_STEP;
        this.drawStatusLine(
            tr("gui.neoecoae.storage_interface.network", "Network"),
            state.targetOnline ? tr("gui.neoecoae.storage_interface.connected", "Connected")
                : tr("gui.neoecoae.storage_interface.disconnected", "Disconnected"),
            state.targetOnline,
            y);
        y += TEXT_STEP;
        if (state.mode == ECOStorageInterfaceMode.INPUT) {
            this.drawLocalText(
                tr(
                    "gui.neoecoae.storage_interface.import",
                    "Import: %s / tick",
                    this.formatNumber(state.transferredLastTick)),
                TEXT_X,
                y,
                HostUiStyle.DARK_TEXT_VALUE);
        } else if (state.mode == ECOStorageInterfaceMode.OUTPUT) {
            this.drawLocalText(
                tr(
                    "gui.neoecoae.storage_interface.export",
                    "Export: %s / tick",
                    this.formatNumber(state.transferredLastTick)),
                TEXT_X,
                y,
                HostUiStyle.DARK_TEXT_VALUE);
        } else {
            this.drawLocalText(
                tr("gui.neoecoae.storage_interface.storage_mode", "Mode: Mounted as ECO storage"),
                TEXT_X,
                y,
                HostUiStyle.DARK_TEXT_MUTED);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.isMouseIn(INPUT_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H, mouseX, mouseY)) {
            this.drawTooltip(
                Collections.singletonList(
                    tr(
                        "gui.neoecoae.storage_interface.input_tooltip",
                        "Transfer contents from the ME network into the storage subsystem.")),
                mouseX,
                mouseY);
        } else if (this.isMouseIn(OUTPUT_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H, mouseX, mouseY)) {
            this.drawTooltip(
                Collections.singletonList(
                    tr(
                        "gui.neoecoae.storage_interface.output_tooltip",
                        "Transfer contents from the storage subsystem into the ME network.")),
                mouseX,
                mouseY);
        }
    }

    private void drawStatusLine(String label, String value, boolean ok, int y) {
        this.drawLocalText(label + ": ", TEXT_X, y, HostUiStyle.DARK_TEXT_MUTED);
        this.drawLocalText(value, STATUS_VALUE_X, y, ok ? HostUiStyle.DARK_TEXT_SUCCESS : HostUiStyle.DARK_TEXT_ERROR);
    }

    private static int buttonTextColor(boolean selected) {
        return selected ? HostUiStyle.DARK_TEXT_SUCCESS : HostUiStyle.DARK_TEXT_PRIMARY;
    }

    private static String tr(String key, String fallback, Object... args) {
        String value = StatCollector.translateToLocalFormatted(key, args);
        return key.equals(value) ? String.format(fallback, args) : value;
    }

    private static final class InvisibleButton extends GuiButton {

        private InvisibleButton(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height, "");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {}
    }
}
