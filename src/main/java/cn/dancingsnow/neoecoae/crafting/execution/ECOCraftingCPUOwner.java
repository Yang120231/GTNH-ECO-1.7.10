package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

/** Persistent threading-core owner; a free-capacity CPU has no owner. */
public interface ECOCraftingCPUOwner {

    StorageTierDescriptor getTier();

    void saveChanges();

    void deactivate(ECOCraftingCPU cpu);
}
