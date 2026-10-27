package cn.dancingsnow.neoecoae.platform.ui.upload;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Opens the upload page for the player's active upload session; the page carries no client-chosen identity. */
public final class PatternUploadUiFactory extends AbstractUIFactory<GuiData> {

    public static final PatternUploadUiFactory INSTANCE = new PatternUploadUiFactory();
    private static boolean registered;

    private static final IGuiHolder<GuiData> HOLDER = new IGuiHolder<GuiData>() {

        @Override
        public ModularPanel buildUI(GuiData data, PanelSyncManager sync, UISettings settings) {
            return PatternUploadPage.create(sync);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public ModularScreen createScreen(GuiData data, ModularPanel panel) {
            return new ModularScreen("neoecoae", panel);
        }
    };

    private PatternUploadUiFactory() {
        super("neoecoae:pattern_upload");
    }

    public static void register() {
        if (registered) return;
        registered = true;
        GuiManager.registerFactory(INSTANCE);
    }

    public static void open(EntityPlayerMP player) {
        GuiManager.open(INSTANCE, new GuiData(player), player);
    }

    @Override
    public IGuiHolder<GuiData> getGuiHolder(GuiData data) {
        return HOLDER;
    }

    @Override
    public void writeGuiData(GuiData data, PacketBuffer buffer) {}

    @Override
    public GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new GuiData(player);
    }
}
