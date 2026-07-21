package cn.dancingsnow.neoecoae.gui.mui;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.factory.GuiData;

public final class NeoEcoGuiData extends GuiData {

    public enum Kind {
        STORAGE_CONTROLLER,
        STORAGE_PRIORITY,
        COMPUTATION_CONTROLLER,
        CRAFTING_CONTROLLER,
        STORAGE_INTERFACE,
        CRAFTING_PATTERN_BUS,
        CRAFTING_HATCH,
        STRUCTURE_TERMINAL,
        STORAGE_RECOVERY_TERMINAL,
        PATTERN_UPLOAD
    }

    private final Kind kind;
    private final int x;
    private final int y;
    private final int z;
    private final int itemSlot;
    private final UUID uploadSession;

    private NeoEcoGuiData(EntityPlayer player, Kind kind, int x, int y, int z, int itemSlot, UUID uploadSession) {
        super(player);
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemSlot = itemSlot;
        this.uploadSession = uploadSession;
    }

    public static NeoEcoGuiData tile(EntityPlayer player, Kind kind, TileEntity tile) {
        return new NeoEcoGuiData(player, kind, tile.xCoord, tile.yCoord, tile.zCoord, -1, null);
    }

    public static NeoEcoGuiData item(EntityPlayer player, Kind kind, int itemSlot) {
        return new NeoEcoGuiData(player, kind, 0, 0, 0, itemSlot, null);
    }

    static NeoEcoGuiData read(EntityPlayer player, Kind kind, int x, int y, int z, int itemSlot, UUID uploadSession) {
        return new NeoEcoGuiData(player, kind, x, y, z, itemSlot, uploadSession);
    }

    public static NeoEcoGuiData upload(EntityPlayer player, UUID session) {
        return new NeoEcoGuiData(player, Kind.PATTERN_UPLOAD, 0, 0, 0, -1, session);
    }

    public Kind getKind() {
        return this.kind;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getItemSlot() {
        return this.itemSlot;
    }

    public UUID getUploadSession() {
        return this.uploadSession;
    }

    public TileEntity getTileEntity() {
        return this.itemSlot < 0 && this.getWorld() != null ? this.getWorld()
            .getTileEntity(this.x, this.y, this.z) : null;
    }

    public ItemStack getItemStack() {
        if (this.itemSlot < 0 || this.itemSlot >= this.getPlayer().inventory.mainInventory.length) {
            return null;
        }
        return this.getPlayer().inventory.getStackInSlot(this.itemSlot);
    }
}
