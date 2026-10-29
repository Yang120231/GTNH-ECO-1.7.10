package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class HostL6 extends StorageMachines.Host {

    public HostL6() {
        super(
            StorageMachines.HOST_L6_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) StorageMachines.STORAGE_SYSTEM_L6.value()).defaultBlockState(),
            StorageTier.L6);
    }
}
