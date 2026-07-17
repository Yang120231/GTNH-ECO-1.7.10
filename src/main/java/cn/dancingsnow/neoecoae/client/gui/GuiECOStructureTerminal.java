package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cn.dancingsnow.neoecoae.gui.container.ContainerECOStructureTerminal;
import cn.dancingsnow.neoecoae.item.ItemECOStructureTerminal;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.PacketStructureTerminalAction;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Two-column host-style layout for the non-preview portion of the structure terminal. */
@SideOnly(Side.CLIENT)
public class GuiECOStructureTerminal extends GuiHostMachineBase {

    private static final int UI_WIDTH = 300;
    private static final int UI_HEIGHT = 176;
    private static final int HEADER_X = 6;
    private static final int HEADER_Y = 6;
    private static final int HEADER_W = UI_WIDTH - 12;
    private static final int HEADER_H = 28;

    private static final int CONTENT_Y = 38;
    private static final int CONTENT_H = 132;
    private static final int LEFT_X = 6;
    private static final int LEFT_W = 188;
    private static final int RIGHT_X = 198;
    private static final int RIGHT_W = 96;

    private static final int SELECTOR_X_1 = 12;
    private static final int SELECTOR_X_2 = 72;
    private static final int SELECTOR_X_3 = 132;
    private static final int SELECTOR_W = 56;
    private static final int SELECTOR_H = 22;
    private static final int HOST_Y = 55;
    private static final int TIER_Y = 93;

    private static final int LENGTH_Y = 55;
    private static final int LENGTH_MINUS_X = 204;
    private static final int LENGTH_VALUE_X = 228;
    private static final int LENGTH_PLUS_X = 268;
    private static final int LENGTH_SIDE_W = 20;
    private static final int LENGTH_VALUE_W = 36;

    private static final int MODE_X = 204;
    private static final int MODE_W = 84;
    private static final int MODE_H = 20;
    private static final int BUILD_Y = 93;
    private static final int MIRROR_Y = 117;
    private static final int DISMANTLE_Y = 141;

    private static final int SUMMARY_X = 12;
    private static final int SUMMARY_Y = 121;
    private static final int SUMMARY_W = 176;
    private static final int SUMMARY_H = 42;

    private final ContainerECOStructureTerminal terminalContainer;

    public GuiECOStructureTerminal(EntityPlayer player) {
        this(new ContainerECOStructureTerminal(player));
    }

    private GuiECOStructureTerminal(ContainerECOStructureTerminal container) {
        super(container, UI_WIDTH, UI_HEIGHT);
        this.terminalContainer = container;
    }

    @Override
    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        ItemStack stack = this.terminalContainer.getTerminalStack();
        this.drawDarkInsetRect(HEADER_X, HEADER_Y, HEADER_W, HEADER_H);
        this.drawDarkInsetRect(LEFT_X, CONTENT_Y, LEFT_W, CONTENT_H);
        this.drawDarkInsetRect(RIGHT_X, CONTENT_Y, RIGHT_W, CONTENT_H);
        this.drawTinyInsetRect(SUMMARY_X, SUMMARY_Y, SUMMARY_W, SUMMARY_H, 0xFF201E27);
        this.drawSlotTexture(8, 8);

        StructureTerminalHostType host = ItemECOStructureTerminal.getHostType(stack);
        this.drawButton(
            SELECTOR_X_1,
            HOST_Y,
            SELECTOR_W,
            SELECTOR_H,
            host == StructureTerminalHostType.CRAFTING,
            mouseX,
            mouseY);
        this.drawButton(
            SELECTOR_X_2,
            HOST_Y,
            SELECTOR_W,
            SELECTOR_H,
            host == StructureTerminalHostType.STORAGE,
            mouseX,
            mouseY);
        this.drawButton(
            SELECTOR_X_3,
            HOST_Y,
            SELECTOR_W,
            SELECTOR_H,
            host == StructureTerminalHostType.COMPUTATION,
            mouseX,
            mouseY);

        String tier = ItemECOStructureTerminal.getHostTier(stack);
        this.drawButton(SELECTOR_X_1, TIER_Y, SELECTOR_W, SELECTOR_H, "l4".equals(tier), mouseX, mouseY);
        this.drawButton(SELECTOR_X_2, TIER_Y, SELECTOR_W, SELECTOR_H, "l6".equals(tier), mouseX, mouseY);
        this.drawButton(SELECTOR_X_3, TIER_Y, SELECTOR_W, SELECTOR_H, "l9".equals(tier), mouseX, mouseY);

        this.drawButton(LENGTH_MINUS_X, LENGTH_Y, LENGTH_SIDE_W, SELECTOR_H, false, mouseX, mouseY);
        this.drawButton(LENGTH_VALUE_X, LENGTH_Y, LENGTH_VALUE_W, SELECTOR_H, false, mouseX, mouseY);
        this.drawButton(LENGTH_PLUS_X, LENGTH_Y, LENGTH_SIDE_W, SELECTOR_H, false, mouseX, mouseY);

        StructureTerminalMode mode = ItemECOStructureTerminal.getOperationMode(stack);
        this.drawButton(MODE_X, BUILD_Y, MODE_W, MODE_H, mode == StructureTerminalMode.BUILD, mouseX, mouseY);
        this.drawButton(MODE_X, MIRROR_Y, MODE_W, MODE_H, mode == StructureTerminalMode.MIRRORED_BUILD, mouseX, mouseY);
        this.drawButton(MODE_X, DISMANTLE_Y, MODE_W, MODE_H, mode == StructureTerminalMode.DISMANTLE, mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        ItemStack stack = this.terminalContainer.getTerminalStack();
        StructureTerminalHostType host = ItemECOStructureTerminal.getHostType(stack);
        String tier = ItemECOStructureTerminal.getHostTier(stack);
        StructureTerminalMode mode = ItemECOStructureTerminal.getOperationMode(stack);

        this.drawLocalText(this.text("title", "ECO Structure Terminal"), 34, 9, HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalText(
            this.text("target", "Target") + ": "
                + this.truncate(ItemECOStructureTerminal.getTargetDescription(stack), 250),
            34,
            19,
            HostUiStyle.DARK_TEXT_MUTED);

        this.drawLocalText(this.text("section.host", "Host profile"), 12, 44, HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalText(this.text("section.tier", "Tier"), 12, 82, HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalText(this.text("section.length", "Build length"), 204, 44, HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalText(this.text("section.mode", "Operation"), 204, 82, HostUiStyle.DARK_TEXT_PRIMARY);

        this.drawButtonLabel(
            SELECTOR_X_1,
            HOST_Y,
            SELECTOR_W,
            SELECTOR_H,
            this.text("host.crafting", "Crafting"),
            host == StructureTerminalHostType.CRAFTING,
            mouseX,
            mouseY);
        this.drawButtonLabel(
            SELECTOR_X_2,
            HOST_Y,
            SELECTOR_W,
            SELECTOR_H,
            this.text("host.storage", "Storage"),
            host == StructureTerminalHostType.STORAGE,
            mouseX,
            mouseY);
        this.drawButtonLabel(
            SELECTOR_X_3,
            HOST_Y,
            SELECTOR_W,
            SELECTOR_H,
            this.text("host.computation", "Computation"),
            host == StructureTerminalHostType.COMPUTATION,
            mouseX,
            mouseY);

        this.drawButtonLabel(
            SELECTOR_X_1,
            TIER_Y,
            SELECTOR_W,
            SELECTOR_H,
            this.text("tier.l4", "L4/F4/C4"),
            "l4".equals(tier),
            mouseX,
            mouseY);
        this.drawButtonLabel(
            SELECTOR_X_2,
            TIER_Y,
            SELECTOR_W,
            SELECTOR_H,
            this.text("tier.l6", "L6/F6/C6"),
            "l6".equals(tier),
            mouseX,
            mouseY);
        this.drawButtonLabel(
            SELECTOR_X_3,
            TIER_Y,
            SELECTOR_W,
            SELECTOR_H,
            this.text("tier.l9", "L9/F9/C9"),
            "l9".equals(tier),
            mouseX,
            mouseY);

        this.drawButtonLabel(LENGTH_MINUS_X, LENGTH_Y, LENGTH_SIDE_W, SELECTOR_H, "-", false, mouseX, mouseY);
        this.drawButtonLabel(
            LENGTH_VALUE_X,
            LENGTH_Y,
            LENGTH_VALUE_W,
            SELECTOR_H,
            String.valueOf(ItemECOStructureTerminal.getBuildLength(stack)),
            false,
            mouseX,
            mouseY);
        this.drawButtonLabel(LENGTH_PLUS_X, LENGTH_Y, LENGTH_SIDE_W, SELECTOR_H, "+", false, mouseX, mouseY);

        this.drawButtonLabel(
            MODE_X,
            BUILD_Y,
            MODE_W,
            MODE_H,
            this.text("mode.build", "Build"),
            mode == StructureTerminalMode.BUILD,
            mouseX,
            mouseY);
        this.drawButtonLabel(
            MODE_X,
            MIRROR_Y,
            MODE_W,
            MODE_H,
            this.text("mode.mirror", "Mirror"),
            mode == StructureTerminalMode.MIRRORED_BUILD,
            mouseX,
            mouseY);
        this.drawButtonLabel(
            MODE_X,
            DISMANTLE_Y,
            MODE_W,
            MODE_H,
            this.text("mode.dismantle", "Dismantle"),
            mode == StructureTerminalMode.DISMANTLE,
            mouseX,
            mouseY);

        this.drawLocalText(
            this.text("section.summary", "Current plan"),
            SUMMARY_X + 6,
            SUMMARY_Y + 5,
            HostUiStyle.DARK_TEXT_PRIMARY);
        this.drawLocalText(
            this.hostName(host) + "  ·  " + this.tierName(tier),
            SUMMARY_X + 6,
            SUMMARY_Y + 17,
            HostUiStyle.DARK_TEXT_BLUE);
        String details = this.text("section.length", "Build length") + " "
            + ItemECOStructureTerminal.getBuildLength(stack)
            + "  ·  "
            + this.modeName(mode);
        this.drawLocalText(
            this.truncate(details, SUMMARY_W - 12),
            SUMMARY_X + 6,
            SUMMARY_Y + 29,
            HostUiStyle.DARK_TEXT_MUTED);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && (this.click(
            SELECTOR_X_1,
            HOST_Y,
            SELECTOR_W,
            SELECTOR_H,
            PacketStructureTerminalAction.Action.SELECT_CRAFTING,
            mouseX,
            mouseY)
            || this.click(
                SELECTOR_X_2,
                HOST_Y,
                SELECTOR_W,
                SELECTOR_H,
                PacketStructureTerminalAction.Action.SELECT_STORAGE,
                mouseX,
                mouseY)
            || this.click(
                SELECTOR_X_3,
                HOST_Y,
                SELECTOR_W,
                SELECTOR_H,
                PacketStructureTerminalAction.Action.SELECT_COMPUTATION,
                mouseX,
                mouseY)
            || this.click(
                SELECTOR_X_1,
                TIER_Y,
                SELECTOR_W,
                SELECTOR_H,
                PacketStructureTerminalAction.Action.SELECT_TIER_1,
                mouseX,
                mouseY)
            || this.click(
                SELECTOR_X_2,
                TIER_Y,
                SELECTOR_W,
                SELECTOR_H,
                PacketStructureTerminalAction.Action.SELECT_TIER_2,
                mouseX,
                mouseY)
            || this.click(
                SELECTOR_X_3,
                TIER_Y,
                SELECTOR_W,
                SELECTOR_H,
                PacketStructureTerminalAction.Action.SELECT_TIER_3,
                mouseX,
                mouseY)
            || this.click(
                LENGTH_MINUS_X,
                LENGTH_Y,
                LENGTH_SIDE_W,
                SELECTOR_H,
                PacketStructureTerminalAction.Action.DECREASE,
                mouseX,
                mouseY)
            || this.click(
                LENGTH_PLUS_X,
                LENGTH_Y,
                LENGTH_SIDE_W,
                SELECTOR_H,
                PacketStructureTerminalAction.Action.INCREASE,
                mouseX,
                mouseY)
            || this.click(
                MODE_X,
                BUILD_Y,
                MODE_W,
                MODE_H,
                PacketStructureTerminalAction.Action.BUILD_LINKED,
                mouseX,
                mouseY)
            || this.click(
                MODE_X,
                MIRROR_Y,
                MODE_W,
                MODE_H,
                PacketStructureTerminalAction.Action.BUILD_MIRRORED_LINKED,
                mouseX,
                mouseY)
            || this.click(
                MODE_X,
                DISMANTLE_Y,
                MODE_W,
                MODE_H,
                PacketStructureTerminalAction.Action.DISMANTLE_LINKED,
                mouseX,
                mouseY))) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean click(int x, int y, int width, int height, PacketStructureTerminalAction.Action action, int mouseX,
        int mouseY) {
        if (!this.isMouseIn(x, y, width, height, mouseX, mouseY)) {
            return false;
        }
        NENetwork.CHANNEL.sendToServer(new PacketStructureTerminalAction(this.terminalContainer.getItemSlot(), action));
        return true;
    }

    private void drawButton(int x, int y, int width, int height, boolean selected, int mouseX, int mouseY) {
        this.drawButtonTexture(
            x,
            y,
            width,
            height,
            this.isMouseIn(x, y, width, height, mouseX, mouseY),
            true,
            selected);
    }

    private void drawButtonLabel(int x, int y, int width, int height, String label, boolean selected, int mouseX,
        int mouseY) {
        boolean hovered = this.isMouseIn(x, y, width, height, mouseX, mouseY);
        int color = selected ? HostUiStyle.TEXT_GOOD : hovered ? HostUiStyle.TEXT_HINT : HostUiStyle.TEXT_PRIMARY;
        this.drawLocalCenteredScaled(label, x, y, width, height, color, 1.0F);
    }

    private String hostName(StructureTerminalHostType host) {
        if (host == StructureTerminalHostType.STORAGE) {
            return this.text("host.storage", "Storage");
        }
        if (host == StructureTerminalHostType.COMPUTATION) {
            return this.text("host.computation", "Computation");
        }
        return this.text("host.crafting", "Crafting");
    }

    private String tierName(String tier) {
        return this.text("tier." + tier, tier.toUpperCase());
    }

    private String modeName(StructureTerminalMode mode) {
        if (mode == StructureTerminalMode.MIRRORED_BUILD) {
            return this.text("mode.mirror", "Mirror");
        }
        if (mode == StructureTerminalMode.DISMANTLE) {
            return this.text("mode.dismantle", "Dismantle");
        }
        return this.text("mode.build", "Build");
    }

    private String text(String suffix, String fallback) {
        String key = "gui.neoecoae.structure_terminal." + suffix;
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }

    private String truncate(String value, int maxWidth) {
        if (value == null || this.fontRendererObj.getStringWidth(value) <= maxWidth) {
            return value == null ? "none" : value;
        }
        String ellipsis = "...";
        int length = value.length();
        while (length > 0 && this.fontRendererObj.getStringWidth(value.substring(0, length))
            + this.fontRendererObj.getStringWidth(ellipsis) > maxWidth) {
            length--;
        }
        return length <= 0 ? ellipsis : value.substring(0, length) + ellipsis;
    }
}
