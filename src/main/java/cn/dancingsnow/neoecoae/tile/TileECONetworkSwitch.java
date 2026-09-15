package cn.dancingsnow.neoecoae.tile;

import java.util.EnumSet;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.block.BlockECONetworkSwitch;

/** A real grid node; job/provider ownership remains on the host's existing interface. */
public final class TileECONetworkSwitch extends TileECOInterface {

    private IGridConnection hostConnection;
    private IGridNode connectedHost;

    public TileECONetworkSwitch() {
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        super.updateEntity();
        IGridNode target = null;
        if (this.getBlockType() instanceof BlockECONetworkSwitch block) {
            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                TileEntity tile = this.worldObj.getTileEntity(
                    this.xCoord + direction.offsetX,
                    this.yCoord + direction.offsetY,
                    this.zCoord + direction.offsetZ);
                if (!(tile instanceof TileECOController controller) || controller.getNetworkSwitch() != block) continue;
                for (cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos pos : controller.getHiddenBlocks()) {
                    TileEntity member = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
                    if (member instanceof TileECOInterface port && !(port instanceof TileECONetworkSwitch)
                        && port.getSubsystem() == controller.getSubsystem()) {
                        target = port.getGridNode(ForgeDirection.UNKNOWN);
                        break;
                    }
                }
            }
        }
        if (target != this.connectedHost) {
            this.disconnectHost();
            this.getProxy()
                .setValidSides(
                    target == null ? EnumSet.noneOf(ForgeDirection.class)
                        : EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN)));
            if (target != null && this.getGridNode(ForgeDirection.UNKNOWN) != null) {
                try {
                    this.hostConnection = AEApi.instance()
                        .createGridConnection(this.getGridNode(ForgeDirection.UNKNOWN), target);
                    this.connectedHost = target;
                } catch (appeng.api.exceptions.FailedConnection ignored) {}
            }
        }
    }

    private void disconnectHost() {
        if (this.hostConnection != null) this.hostConnection.destroy();
        this.hostConnection = null;
        this.connectedHost = null;
    }

    @Override
    public void invalidate() {
        this.disconnectHost();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.disconnectHost();
        super.onChunkUnload();
    }
}
