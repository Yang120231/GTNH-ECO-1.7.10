package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public final class HostL9 extends StorageMachines.Host {

    public HostL9() {
        super(
            StorageMachines.HOST_L9_TYPE,
            BlockPos.ZERO,
            ((NativeMachineBlock) StorageMachines.STORAGE_SYSTEM_L9.value()).defaultBlockState(),
            StorageTier.L9);
    }
}
