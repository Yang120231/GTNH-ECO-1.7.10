package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationResult;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationScanner;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;

public class TileECOController extends TileEntity {

    private static final String TAG_SUBSYSTEM = "Subsystem";
    private static final String TAG_TIER = "Tier";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_MIRRORED = "Mirrored";
    private static final String TAG_FACING_META = "FacingMeta";
    private static final String TAG_HIDDEN_BLOCKS = "HiddenBlocks";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";

    private ECOControllerSubsystem subsystem = ECOControllerSubsystem.STORAGE;
    private ECOControllerTier tier = ECOControllerTier.L4;
    private boolean formed;
    private boolean mirrored;
    private int facingMeta;
    private String lastFormationMessage = "not scanned";
    private final List<ECOFormationBlockPos> hiddenBlocks = new ArrayList<ECOFormationBlockPos>();

    public TileECOController() {}

    public TileECOController(ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        this.subsystem = subsystem;
        this.tier = tier;
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    public ECOControllerTier getTier() {
        return this.tier;
    }

    public boolean isFormed() {
        return this.formed;
    }

    public void setFormed(boolean formed) {
        if (this.formed == formed) {
            return;
        }

        this.formed = formed;
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
            this.worldObj.markBlockRangeForRenderUpdate(
                this.xCoord,
                this.yCoord,
                this.zCoord,
                this.xCoord,
                this.yCoord,
                this.zCoord);
        }
    }

    public boolean isMirrored() {
        return this.mirrored;
    }

    public boolean isHiddenMember(int x, int y, int z) {
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    public String getLastFormationMessage() {
        return this.lastFormationMessage;
    }

    public ECOFormationResult scanFormation() {
        ECOFormationResult result = ECOFormationScanner.scan(this);
        this.lastFormationMessage = result.getMessage();
        this.mirrored = result.isMirrored();
        this.replaceHiddenBlocks(result.getHiddenBlocks());
        this.setFormed(result.isFormed());
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
        return result;
    }

    private void replaceHiddenBlocks(List<ECOFormationBlockPos> newHiddenBlocks) {
        if (this.worldObj != null) {
            ECOFormationVisibility.replace(this.worldObj, this.hiddenBlocks, newHiddenBlocks);
        }
        this.hiddenBlocks.clear();
        this.hiddenBlocks.addAll(newHiddenBlocks);
    }

    public int getFacingMeta() {
        return this.facingMeta;
    }

    public ModelFacing getFacing() {
        return ModelFacing.fromMeta(this.facingMeta);
    }

    public void setFacingMeta(int facingMeta) {
        this.facingMeta = facingMeta & 3;
        this.markDirty();
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        if (this.worldObj.getTotalWorldTime() % 20L == 0L) {
            this.scanFormation();
        }
    }

    @Override
    public void invalidate() {
        this.replaceHiddenBlocks(new ArrayList<ECOFormationBlockPos>());
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.replaceHiddenBlocks(new ArrayList<ECOFormationBlockPos>());
        super.onChunkUnload();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString(TAG_SUBSYSTEM, this.subsystem.getId());
        tag.setString(TAG_TIER, this.tier.getId());
        tag.setBoolean(TAG_FORMED, this.formed);
        tag.setBoolean(TAG_MIRRORED, this.mirrored);
        tag.setInteger(TAG_FACING_META, this.facingMeta);
        NBTTagList hiddenTag = new NBTTagList();
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            NBTTagCompound posTag = new NBTTagCompound();
            posTag.setInteger(TAG_X, pos.getX());
            posTag.setInteger(TAG_Y, pos.getY());
            posTag.setInteger(TAG_Z, pos.getZ());
            hiddenTag.appendTag(posTag);
        }
        tag.setTag(TAG_HIDDEN_BLOCKS, hiddenTag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.subsystem = ECOControllerSubsystem.fromId(tag.getString(TAG_SUBSYSTEM));
        this.tier = ECOControllerTier.fromId(tag.getString(TAG_TIER));
        this.formed = tag.getBoolean(TAG_FORMED);
        this.mirrored = tag.getBoolean(TAG_MIRRORED);
        this.facingMeta = tag.getInteger(TAG_FACING_META) & 3;
        NBTTagList hiddenTag = tag.getTagList(TAG_HIDDEN_BLOCKS, 10);
        List<ECOFormationBlockPos> newHiddenBlocks = new ArrayList<ECOFormationBlockPos>();
        for (int i = 0; i < hiddenTag.tagCount(); i++) {
            NBTTagCompound posTag = hiddenTag.getCompoundTagAt(i);
            newHiddenBlocks.add(
                new ECOFormationBlockPos(posTag.getInteger(TAG_X), posTag.getInteger(TAG_Y), posTag.getInteger(TAG_Z)));
        }
        this.replaceHiddenBlocks(newHiddenBlocks);
        if (this.worldObj != null) {
            this.worldObj.markBlockRangeForRenderUpdate(
                this.xCoord - 16,
                this.yCoord - 3,
                this.zCoord - 16,
                this.xCoord + 16,
                this.yCoord + 3,
                this.zCoord + 16);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.readFromNBT(packet.func_148857_g());
    }
}
