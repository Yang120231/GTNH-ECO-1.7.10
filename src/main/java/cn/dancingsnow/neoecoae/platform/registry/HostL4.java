package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class HostL4 extends StorageMachines.Host {

    public HostL4() {
        super(
            StorageMachines.HOST_L4_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) StorageMachines.STORAGE_SYSTEM_L4.value()).defaultBlockState(),
            StorageTier.L4);
    }
}
