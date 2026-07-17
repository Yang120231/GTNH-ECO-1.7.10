package cn.dancingsnow.neoecoae.client.gui;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageRecoveryTerminal;
import cn.dancingsnow.neoecoae.item.ItemECOStorageRecoveryTerminal;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.PacketStorageRecoveryTerminalAction;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Two-column host-style selector UI for recovering an orphaned infinite storage domain. */
@SideOnly(Side.CLIENT)
public class GuiECOStorageRecoveryTerminal extends GuiHostMachineBase {

    private static final int UI_WIDTH = 300;
    private static final int UI_HEIGHT = 154;
    private static final int HEADER_X = 6;
    private static final int HEADER_Y = 6;
    private static final int HEADER_W = UI_WIDTH - 12;
    private static final int HEADER_H = 28;

    private static final int CONTENT_Y = 38;
    private static final int CONTENT_H = 110;
    private static final int LEFT_X = 6;
    private static final int LEFT_W = 190;
    private static final int RIGHT_X = 200;
    private static final int RIGHT_W = 94;

    private static final int DOMAIN_FIELD_X = 12;
    private static final int DOMAIN_FIELD_Y = 56;
    private static final int DOMAIN_FIELD_W = 178;
    private static final int DOMAIN_FIELD_H = 34;
    private static final int NAV_Y = 98;
    private static final int NAV_W = 84;
    private static final int PREVIOUS_X = 12;
    private static final int NEXT_X = 106;

    private static final int TARGET_FIELD_X = 206;
    private static final int TARGET_FIELD_Y = 56;
    private static final int TARGET_FIELD_W = 82;
    private static final int TARGET_FIELD_H = 30;

    private final ContainerECOStorageRecoveryTerminal terminalContainer;

    public GuiECOStorageRecoveryTerminal(EntityPlayer player) {
        this(new ContainerECOStorageRecoveryTerminal(player));
    }

    private GuiECOStorageRecoveryTerminal(ContainerECOStorageRecoveryTerminal container) {
        super(container, UI_WIDTH, UI_HEIGHT);
        this.terminalContainer = container;
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDarkInsetRect(HEADER_X, HEADER_Y, HEADER_W, HEADER_H);
        this.drawDarkInsetRect(LEFT_X, CONTENT_Y, LEFT_W, CONTENT_H);
        this.drawDarkInsetRect(RIGHT_X, CONTENT_Y, RIGHT_W, CONTENT_H);
        this.drawTinyInsetRect(DOMAIN_FIELD_X, DOMAIN_FIELD_Y, DOMAIN_FIELD_W, DOMAIN_FIELD_H, 0xFF201E27);
        this.drawTinyInsetRect(TARGET_FIELD_X, TARGET_FIELD_Y, TARGET_FIELD_W, TARGET_FIELD_H, 0xFF201E27);
        this.drawButton(PREVIOUS_X, NAV_Y, NAV_W, 22, mouseX, mouseY);
        this.drawButton(NEXT_X, NAV_Y, NAV_W, 22, mouseX, mouseY);
        this.drawSlotTexture(8, 8);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        ItemStack stack = this.terminalContainer.getTerminalStack();
        UUID selected = ItemECOStorageRecoveryTerminal.getSelectedDomain(stack);
        String selectedText = selected == null ? this.text("none", "No domain selected") : selected.toString();

        this.drawLocalText(this.text("title", "ECO Storage Recovery Terminal"), 34, 9, HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalText(
            this.text("subtitle", "Recover an orphaned infinite storage domain"),
            34,
            19,
            HostUiStyle.DARK_TEXT_MUTED);

        this.drawLocalText(this.text("section.domain", "Stored domain UUID"), 12, 44, HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalCenteredScaled(
            selectedText,
            DOMAIN_FIELD_X + 3,
            DOMAIN_FIELD_Y + 3,
            DOMAIN_FIELD_W - 6,
            DOMAIN_FIELD_H - 6,
            selected == null ? HostUiStyle.DARK_TEXT_PRIMARY : HostUiStyle.DARK_TEXT_BLUE,
            1.0F);
        this.drawButtonLabel(PREVIOUS_X, NAV_Y, NAV_W, 22, this.text("previous", "Previous"), mouseX, mouseY);
        this.drawButtonLabel(NEXT_X, NAV_Y, NAV_W, 22, this.text("next", "Next"), mouseX, mouseY);
        this.drawLocalCentered(this.text("selector", "Select UUID"), 12, 129, 178, HostUiStyle.DARK_TEXT_MUTED);

        this.drawLocalText(this.text("section.target", "Recovery target"), 206, 44, HostUiStyle.DARK_TEXT_PRIMARY);
        String target = ItemECOStorageRecoveryTerminal.getTargetDescription(stack);
        this.drawLocalCenteredScaled(
            target,
            TARGET_FIELD_X + 3,
            TARGET_FIELD_Y + 3,
            TARGET_FIELD_W - 6,
            TARGET_FIELD_H - 6,
            "none".equals(target) ? HostUiStyle.DARK_TEXT_PRIMARY : HostUiStyle.DARK_TEXT_BLUE,
            1.0F);
        this.drawWrappedCentered(
            this.text("shift_hint", "Shift+right-click the target to bind the selected UUID"),
            TARGET_FIELD_X,
            96,
            TARGET_FIELD_W,
            HostUiStyle.DARK_TEXT_WARNING,
            5);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && (this
            .click(PREVIOUS_X, NAV_Y, NAV_W, 22, PacketStorageRecoveryTerminalAction.Action.PREVIOUS, mouseX, mouseY)
            || this.click(NEXT_X, NAV_Y, NAV_W, 22, PacketStorageRecoveryTerminalAction.Action.NEXT, mouseX, mouseY))) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean click(int x, int y, int width, int height, PacketStorageRecoveryTerminalAction.Action action,
        int mouseX, int mouseY) {
        if (!this.isMouseIn(x, y, width, height, mouseX, mouseY)) {
            return false;
        }
        NENetwork.CHANNEL
            .sendToServer(new PacketStorageRecoveryTerminalAction(this.terminalContainer.getItemSlot(), action));
        return true;
    }

    private void drawButton(int x, int y, int width, int height, int mouseX, int mouseY) {
        this.drawButtonTexture(x, y, width, height, this.isMouseIn(x, y, width, height, mouseX, mouseY), true, false);
    }

    private void drawButtonLabel(int x, int y, int width, int height, String label, int mouseX, int mouseY) {
        boolean hovered = this.isMouseIn(x, y, width, height, mouseX, mouseY);
        this.drawLocalCenteredScaled(
            label,
            x,
            y,
            width,
            height,
            hovered ? HostUiStyle.TEXT_HINT : HostUiStyle.TEXT_PRIMARY,
            1.0F);
    }

    private void drawWrappedCentered(String value, int x, int y, int width, int color, int maxLines) {
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(value, width);
        int count = Math.min(maxLines, lines.size());
        for (int index = 0; index < count; index++) {
            this.drawLocalCentered(lines.get(index), x, y + index * 9, width, color);
        }
    }

    private String text(String suffix, String fallback) {
        String key = "gui.neoecoae.storage_recovery_terminal." + suffix;
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }
}
