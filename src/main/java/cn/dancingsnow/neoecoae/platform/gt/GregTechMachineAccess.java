package cn.dancingsnow.neoecoae.platform.gt;

import net.minecraft.tileentity.TileEntity;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;

/** Native API linkage is isolated from the no-GT load path. */
final class GregTechMachineAccess {

    private GregTechMachineAccess() {}

    static void verifyApi() {
        try {
            IGregTechTileEntity.class.getMethod("getMetaTileEntity");
            MTEBasicMachine.class.getField("mProgresstime");
            MTEBasicMachine.class.getField("mMaxProgresstime");
            MTEBasicMachine.class.getField("mEUt");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("GregTech native machine API is incompatible", failure);
        }
    }

    static NativeGregTech.MachineSnapshot inspect(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity)) return null;
        IGregTechTileEntity host = (IGregTechTileEntity) tile;
        var machine = host.getMetaTileEntity();
        if (!(machine instanceof MTEBasicMachine)) return null;
        MTEBasicMachine basic = (MTEBasicMachine) machine;
        return new NativeGregTech.MachineSnapshot(
            host.getMetaTileID(),
            machine.getClass()
                .getName(),
            host.isActive(),
            host.getStoredEU(),
            host.getEUCapacity(),
            basic.mProgresstime,
            basic.mMaxProgresstime,
            basic.mEUt);
    }
}
