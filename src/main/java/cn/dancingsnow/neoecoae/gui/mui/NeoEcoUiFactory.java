package cn.dancingsnow.neoecoae.gui.mui;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.item.ItemECOStorageRecoveryTerminal;
import cn.dancingsnow.neoecoae.item.ItemECOStructureTerminal;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileCraftingHatch;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class NeoEcoUiFactory implements UIFactory<NeoEcoGuiData> {

    public static final NeoEcoUiFactory INSTANCE = new NeoEcoUiFactory();
    private static boolean registered;

    private NeoEcoUiFactory() {}

    public static void register() {
        if (!registered) {
            GuiManager.registerFactory(INSTANCE);
            registered = true;
        }
    }

    public static void openTile(EntityPlayer player, NeoEcoGuiData.Kind kind, TileEntity tile) {
        if (!(player instanceof EntityPlayerMP) || tile == null) {
            return;
        }
        GuiManager.open(INSTANCE, NeoEcoGuiData.tile(player, kind, tile), (EntityPlayerMP) player);
    }

    public static void openHeldItem(EntityPlayer player, NeoEcoGuiData.Kind kind) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        GuiManager
            .open(INSTANCE, NeoEcoGuiData.item(player, kind, player.inventory.currentItem), (EntityPlayerMP) player);
    }

    public static void openUpload(EntityPlayer player, UUID session) {
        if (!(player instanceof EntityPlayerMP) || PatternUploadSessions.get(session) == null) return;
        GuiManager.open(INSTANCE, NeoEcoGuiData.upload(player, session), (EntityPlayerMP) player);
    }

    @Override
    public @NotNull String getFactoryName() {
        return "neoecoae:main";
    }

    @Override
    public ModularPanel createPanel(NeoEcoGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return NeoEcoPanels.build(data, syncManager, settings);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(NeoEcoGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(NeoECOAE.MODID, mainPanel);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, NeoEcoGuiData data) {
        if (player != data.getPlayer()) {
            return false;
        }
        if (data.getItemSlot() >= 0) {
            ItemStack stack = data.getItemStack();
            if (stack == null) {
                return false;
            }
            return data.getKind() == NeoEcoGuiData.Kind.STRUCTURE_TERMINAL
                ? stack.getItem() instanceof ItemECOStructureTerminal
                : stack.getItem() instanceof ItemECOStorageRecoveryTerminal;
        }
        if (data.getKind() == NeoEcoGuiData.Kind.PATTERN_UPLOAD) {
            return PatternUploadSessions.get(data.getUploadSession()) != null;
        }
        TileEntity tile = data.getTileEntity();
        if (tile == null || tile.isInvalid()
            || player.getDistanceSq(data.getX() + 0.5D, data.getY() + 0.5D, data.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        switch (data.getKind()) {
            case STORAGE_CONTROLLER:
            case STORAGE_PRIORITY:
                return tile instanceof TileECOController
                    && ((TileECOController) tile).getSubsystem() == ECOControllerSubsystem.STORAGE;
            case COMPUTATION_CONTROLLER:
                return tile instanceof TileECOController
                    && ((TileECOController) tile).getSubsystem() == ECOControllerSubsystem.COMPUTATION;
            case CRAFTING_CONTROLLER:
                return tile instanceof TileECOController
                    && ((TileECOController) tile).getSubsystem() == ECOControllerSubsystem.CRAFTING;
            case STORAGE_INTERFACE:
                return tile instanceof TileECOInterface
                    && ((TileECOInterface) tile).getSubsystem() == ECOControllerSubsystem.STORAGE;
            case CRAFTING_PATTERN_BUS:
                return tile instanceof TileCraftingPatternBus;
            case CRAFTING_HATCH:
                return tile instanceof TileCraftingHatch;
            default:
                return false;
        }
    }

    @Override
    public void writeGuiData(NeoEcoGuiData data, PacketBuffer buffer) {
        buffer.writeByte(
            data.getKind()
                .ordinal());
        buffer.writeInt(data.getX());
        buffer.writeInt(data.getY());
        buffer.writeInt(data.getZ());
        buffer.writeByte(data.getItemSlot());
        if (data.getKind() == NeoEcoGuiData.Kind.PATTERN_UPLOAD) {
            UUID session = data.getUploadSession();
            buffer.writeLong(session == null ? 0L : session.getMostSignificantBits());
            buffer.writeLong(session == null ? 0L : session.getLeastSignificantBits());
        }
    }

    @Override
    public @NotNull NeoEcoGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        int ordinal = buffer.readUnsignedByte();
        NeoEcoGuiData.Kind[] kinds = NeoEcoGuiData.Kind.values();
        if (ordinal < 0 || ordinal >= kinds.length) {
            throw new IllegalArgumentException("Invalid Neo ECO UI kind: " + ordinal);
        }
        KindData kindData = new KindData(kinds[ordinal]);
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        int itemSlot = buffer.readByte();
        UUID session = kindData.kind == NeoEcoGuiData.Kind.PATTERN_UPLOAD
            ? new UUID(buffer.readLong(), buffer.readLong())
            : null;
        return NeoEcoGuiData.read(player, kindData.kind, x, y, z, itemSlot, session);
    }

    private static final class KindData {

        private final NeoEcoGuiData.Kind kind;

        private KindData(NeoEcoGuiData.Kind kind) {
            this.kind = kind;
        }
    }
}
