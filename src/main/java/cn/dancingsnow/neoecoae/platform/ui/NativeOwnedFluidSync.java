package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;

import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import io.netty.buffer.Unpooled;

/** Native container conservation and cursor sync, guarded by the physical tile's live server menu. */
public final class NativeOwnedFluidSync extends FluidSlotSyncHandler {

    private final TileEntity tile;
    private final FluidTank clientTank;
    private int cachedCapacity = -1;
    private final ServerTasks.Session serverSession = ServerTasks.captureSession();

    public NativeOwnedFluidSync(TileEntity tile, IFluidTank tank, boolean fill, boolean drain) {
        super(tank);
        this.tile = tile;
        // A multiblock's client controller may only expose its smaller, standalone tank.
        this.clientTank = tile.getWorldObj().isRemote ? new FluidTank(tank.getCapacity()) : null;
        canFillSlot(fill);
        canDrainSlot(drain);
        controlsAmount(false);
    }

    @Override
    public boolean updateCacheFromSource(boolean isFirstSync) {
        boolean changed = super.updateCacheFromSource(isFirstSync);
        int capacity = super.getFluidTank().getCapacity();
        if (cachedCapacity != capacity) {
            cachedCapacity = capacity;
            changed = true;
        }
        return changed;
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeInt(super.getFluidTank().getCapacity());
        super.write(buffer);
    }

    @Override
    public void read(PacketBuffer buffer) {
        clientTank.setCapacity(buffer.readInt());
        setValue(NetworkUtils.readFluidStack(buffer), false, false);
        clientTank.setFluid(getValue());
    }

    @Override
    public IFluidTank getFluidTank() {
        return clientTank == null ? super.getFluidTank() : clientTank;
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) {
        if (id != SYNC_CLICK) throw new IllegalArgumentException("Unsupported physical tank action: " + id);
        MouseData click = MouseData.readPacket(buffer);
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing physical tank action bytes");
        serverSession.execute(() -> {
            if (!isValid()
                || !NativeUiAccess.canEdit(tile, getSyncManager().getPlayer(), getSyncManager().getContainer())) return;
            PacketBuffer owned = new PacketBuffer(Unpooled.buffer());
            try {
                click.writeToPacket(owned);
                super.readOnServer(SYNC_CLICK, owned);
            } finally {
                owned.release();
            }
        });
    }
}
