package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final String TAG_FORMED_MEMBER_BLOCKS = "FormedMemberBlocks";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_MEMBER_TIER = "MemberTier";

    private ECOControllerSubsystem subsystem = ECOControllerSubsystem.STORAGE;
    private ECOControllerTier tier = ECOControllerTier.L4;
    private boolean formed;
    private boolean mirrored;
    private int facingMeta;
    private String lastFormationMessage = "not scanned";
    private final List<ECOFormationBlockPos> hiddenBlocks = new ArrayList<>();
    private final List<ECOFormationBlockPos> formedMemberBlocks = new ArrayList<>();

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
        this.applyFormationResult(result);
        return result;
    }

    private void applyFormationResult(ECOFormationResult result) {
        FormationChange change = this.calculateFormationChange(result);
        this.lastFormationMessage = result.getMessage();
        this.mirrored = result.isMirrored();
        this.replaceHiddenBlocks(result.getHiddenBlocks());
        this.replaceFormedMemberBlocks(result.getFormedMemberBlocks(), change.mirroredChanged);
        this.setFormed(result.isFormed());
        this.syncFormationChange(change);
    }

    private FormationChange calculateFormationChange(ECOFormationResult result) {
        boolean mirroredChanged = this.mirrored != result.isMirrored();
        boolean stateChanged = this.formed != result.isFormed() || mirroredChanged
            || !samePositions(this.hiddenBlocks, result.getHiddenBlocks())
            || !samePositions(this.formedMemberBlocks, result.getFormedMemberBlocks());
        return new FormationChange(stateChanged, mirroredChanged);
    }

    private void syncFormationChange(FormationChange change) {
        if (change.stateChanged) {
            this.markDirty();
        }
        if (change.stateChanged && this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    private void replaceHiddenBlocks(List<ECOFormationBlockPos> newHiddenBlocks) {
        if (samePositions(this.hiddenBlocks, newHiddenBlocks)) {
            return;
        }
        if (this.worldObj != null) {
            ECOFormationVisibility.replace(this.worldObj, this.hiddenBlocks, newHiddenBlocks);
        }
        this.hiddenBlocks.clear();
        this.hiddenBlocks.addAll(newHiddenBlocks);
    }

    private void replaceFormedMemberBlocks(List<ECOFormationBlockPos> newFormedMemberBlocks) {
        this.replaceFormedMemberBlocks(newFormedMemberBlocks, false);
    }

    private void replaceFormedMemberBlocks(List<ECOFormationBlockPos> newFormedMemberBlocks, boolean force) {
        if (!force && samePositions(this.formedMemberBlocks, newFormedMemberBlocks)) {
            return;
        }
        if (this.worldObj != null) {
            ECOFormationVisibility
                .replaceFormedMembers(this.worldObj, this.formedMemberBlocks, newFormedMemberBlocks, this.mirrored);
        }
        this.formedMemberBlocks.clear();
        this.formedMemberBlocks.addAll(newFormedMemberBlocks);
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
        this.replaceHiddenBlocks(new ArrayList<>());
        this.replaceFormedMemberBlocks(new ArrayList<>());
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.replaceHiddenBlocks(new ArrayList<>());
        this.replaceFormedMemberBlocks(new ArrayList<>());
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
            hiddenTag.appendTag(writePos(pos));
        }
        tag.setTag(TAG_HIDDEN_BLOCKS, hiddenTag);
        NBTTagList formedMemberTag = new NBTTagList();
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            formedMemberTag.appendTag(writePos(pos));
        }
        tag.setTag(TAG_FORMED_MEMBER_BLOCKS, formedMemberTag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.subsystem = ECOControllerSubsystem.fromId(tag.getString(TAG_SUBSYSTEM));
        this.tier = ECOControllerTier.fromId(tag.getString(TAG_TIER));
        this.formed = tag.getBoolean(TAG_FORMED);
        this.mirrored = tag.getBoolean(TAG_MIRRORED);
        this.facingMeta = tag.getInteger(TAG_FACING_META) & 3;
        this.replaceHiddenBlocks(readPositions(tag.getTagList(TAG_HIDDEN_BLOCKS, 10)));
        this.replaceFormedMemberBlocks(readPositions(tag.getTagList(TAG_FORMED_MEMBER_BLOCKS, 10)));
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

    private static boolean samePositions(List<ECOFormationBlockPos> left, List<ECOFormationBlockPos> right) {
        Set<ECOFormationBlockPos> leftSet = new HashSet<ECOFormationBlockPos>(left);
        Set<ECOFormationBlockPos> rightSet = new HashSet<ECOFormationBlockPos>(right);
        return leftSet.equals(rightSet);
    }

    private static final class FormationChange {

        private final boolean stateChanged;
        private final boolean mirroredChanged;

        private FormationChange(boolean stateChanged, boolean mirroredChanged) {
            this.stateChanged = stateChanged;
            this.mirroredChanged = mirroredChanged;
        }
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.readFromNBT(packet.func_148857_g());
    }

    private static NBTTagCompound writePos(ECOFormationBlockPos pos) {
        NBTTagCompound posTag = new NBTTagCompound();
        posTag.setInteger(TAG_X, pos.getX());
        posTag.setInteger(TAG_Y, pos.getY());
        posTag.setInteger(TAG_Z, pos.getZ());
        if (pos.getTier() != null) {
            posTag.setString(
                TAG_MEMBER_TIER,
                pos.getTier()
                    .getId());
        }
        return posTag;
    }

    private static List<ECOFormationBlockPos> readPositions(NBTTagList positionsTag) {
        List<ECOFormationBlockPos> positions = new ArrayList<>();
        for (int i = 0; i < positionsTag.tagCount(); i++) {
            NBTTagCompound posTag = positionsTag.getCompoundTagAt(i);
            String tierId = posTag.hasKey(TAG_MEMBER_TIER) ? posTag.getString(TAG_MEMBER_TIER) : "";
            ECOControllerTier tier = !tierId.isEmpty() ? ECOControllerTier.fromId(tierId) : null;
            positions.add(
                new ECOFormationBlockPos(
                    posTag.getInteger(TAG_X),
                    posTag.getInteger(TAG_Y),
                    posTag.getInteger(TAG_Z),
                    tier));
        }
        return positions;
    }
}
