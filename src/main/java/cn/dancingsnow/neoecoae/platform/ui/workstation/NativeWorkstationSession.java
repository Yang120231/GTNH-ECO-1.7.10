package cn.dancingsnow.neoecoae.platform.ui.workstation;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.ui.NativeTileSession;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Server-owned single-station settings, processing progress and adjacent block icons. */
public final class NativeWorkstationSession extends NativeTileSession {

    private final ECOIntegratedWorkingStationBlockEntity host;
    private CompoundTag state = new CompoundTag(), previous;
    private static volatile NativeWorkstationSession inspection;

    public NativeWorkstationSession(ECOIntegratedWorkingStationBlockEntity host) {
        super(host);
        this.host = host;
        if (Boolean.getBoolean("neoecoae.workstationUiAcceptance") && host.getWorldObj().isRemote) inspection = this;
    }

    public static NativeWorkstationSession acceptanceInspection() {
        return inspection;
    }

    public boolean autoExport() {
        return state.getBoolean("autoExport");
    }

    public boolean working() {
        return state.getBoolean("working");
    }

    public int progress() {
        return state.getInt("progress");
    }

    public int maxProgress() {
        return Math.max(1, state.getInt("maxProgress"));
    }

    public boolean outputAllowed(RelativeSide side) {
        return state.getBoolean("output." + side.name());
    }

    public ItemStack adjacentItem(RelativeSide side) {
        return ItemStacks.parseOptional(RegistryAccess.EMPTY, state.getCompound("adjacent." + side.name()));
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("autoExport", host.isAutoExport());
        tag.putBoolean("working", host.isWorking());
        tag.putInt("progress", host.getProcessingTime());
        tag.putInt("maxProgress", host.getMaxProcessingTime());
        for (RelativeSide side : RelativeSide.values()) {
            tag.putBoolean("output." + side.name(), host.isOutputAllowed(side));
            var pos = host.getBlockPos()
                .relative(host.getOutputDirection(side));
            var block = host.getLevel()
                .getBlockState(pos)
                .getBlock();
            ItemStack adjacent = net.minecraft.item.Item.getItemFromBlock(block) == null ? null
                : new ItemStack(
                    block,
                    1,
                    block.damageDropped(
                        host.getWorldObj()
                            .getBlockMetadata(pos.getX(), pos.getY(), pos.getZ())));
            tag.put("adjacent." + side.name(), ItemStacks.save(RegistryAccess.EMPTY, adjacent));
        }
        state = tag;
        if (init || !tag.equals(previous)) {
            previous = tag.copy();
            syncToClient(0, output -> new RegistryFriendlyByteBuf(output, RegistryAccess.EMPTY).writeNbt(tag));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown workstation snapshot: " + id);
        state = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        Runnable action;
        if (id == 0) action = () -> host.setAutoExport(!host.isAutoExport());
        else if (id == 1) action = host::clearFluid;
        else if (id == 2) action = host::clearFluidOut;
        else if (id == 3) {
            int side = buffer.readUnsignedByte();
            if (side >= RelativeSide.values().length) throw new IOException("Invalid workstation output side");
            action = () -> host.toggleOutputSide(RelativeSide.values()[side]);
        } else throw new IOException("Unknown workstation action: " + id);
        if (buffer.isReadable()) throw new IOException("Trailing workstation action bytes");
        edit(action);
    }

    @SideOnly(Side.CLIENT)
    public void toggleExport() {
        syncToServer(0);
    }

    @SideOnly(Side.CLIENT)
    public void clearInput() {
        syncToServer(1);
    }

    @SideOnly(Side.CLIENT)
    public void clearOutput() {
        syncToServer(2);
    }

    @SideOnly(Side.CLIENT)
    public void toggleSide(RelativeSide side) {
        syncToServer(3, output -> output.writeByte(side.ordinal()));
    }

    /** Source tank stays authoritative and every container change invalidates the cached recipe. */
    public IFluidTank tank(boolean input) {
        return new IFluidTank() {

            private IFluidTank delegate() {
                return input ? host.getInputTank() : host.getOutputTank();
            }

            public FluidStack getFluid() {
                return delegate().getFluid();
            }

            public int getFluidAmount() {
                return delegate().getFluidAmount();
            }

            public int getCapacity() {
                return delegate().getCapacity();
            }

            public FluidTankInfo getInfo() {
                return delegate().getInfo();
            }

            public int fill(FluidStack resource, boolean execute) {
                int result = delegate().fill(resource, execute);
                if (execute && result > 0) host.onChangeTank();
                return result;
            }

            public FluidStack drain(int amount, boolean execute) {
                FluidStack result = delegate().drain(amount, execute);
                if (execute && result != null && result.amount > 0) host.onChangeTank();
                return result;
            }
        };
    }
}
