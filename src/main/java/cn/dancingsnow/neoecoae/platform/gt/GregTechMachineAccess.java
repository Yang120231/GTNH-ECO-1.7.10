package cn.dancingsnow.neoecoae.platform.gt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechMachineStatus;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

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

    /** A recipe controller's current cycle, or null for hatches and other non-processing tiles. */
    static ECOGregTechMachineStatus.Sample sample(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity)) return null;
        IGregTechTileEntity host = (IGregTechTileEntity) tile;
        IMetaTileEntity machine = host.getMetaTileEntity();
        String name = machine == null ? null : machine.getLocalName();
        if (name == null && machine != null) name = machine.getMetaName();
        if (machine instanceof MTEMultiBlockBase) {
            MTEMultiBlockBase multi = (MTEMultiBlockBase) machine;
            List<Object> outputs = new ArrayList<>();
            if (multi.mOutputItems != null) outputs.addAll(Arrays.asList(multi.mOutputItems));
            if (multi.mOutputFluids != null) outputs.addAll(Arrays.asList(multi.mOutputFluids));
            var result = multi.getCheckRecipeResult();
            return new ECOGregTechMachineStatus.Sample(
                name,
                outputs,
                host.getProgress(),
                host.getMaxProgress(),
                host.isActive(),
                host.isAllowedToWork(),
                false,
                multi.mMachine,
                result == null || result.wasSuccessful() ? null : result.getDisplayString(),
                multi.lastParallel);
        }
        if (machine instanceof MTEBasicMachine) {
            MTEBasicMachine basic = (MTEBasicMachine) machine;
            List<Object> outputs = new ArrayList<>(Arrays.asList(basic.mOutputItems));
            if (basic.mOutputFluid != null) outputs.add(basic.mOutputFluid);
            return new ECOGregTechMachineStatus.Sample(
                name,
                outputs,
                host.getProgress(),
                host.getMaxProgress(),
                host.isActive(),
                host.isAllowedToWork(),
                basic.mStuttering,
                true,
                null,
                1);
        }
        return null;
    }

    /** Input hatch and bus tiles of a formed multiblock controller. */
    static List<TileEntity> inputs(Object machine) {
        if (!(machine instanceof MTEMultiBlockBase)) return Collections.emptyList();
        MTEMultiBlockBase multi = (MTEMultiBlockBase) machine;
        List<TileEntity> tiles = new ArrayList<>();
        for (List<?> hatches : Arrays.<List<?>>asList(
            multi.mInputBusses,
            multi.mInputHatches,
            multi.mDualInputHatches,
            multi.mSmartInputHatches)) {
            if (hatches == null) continue;
            for (Object hatch : hatches) if (hatch instanceof IMetaTileEntity) {
                IGregTechTileEntity base = ((IMetaTileEntity) hatch).getBaseMetaTileEntity();
                if (base instanceof TileEntity) tiles.add((TileEntity) base);
            }
        }
        return tiles;
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
