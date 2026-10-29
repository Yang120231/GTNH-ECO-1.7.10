package cn.dancingsnow.neoecoae.platform.structurelib;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.registry.ComputationSystemL4;
import cn.dancingsnow.neoecoae.platform.registry.ComputationSystemL6;
import cn.dancingsnow.neoecoae.platform.registry.ComputationSystemL9;
import cn.dancingsnow.neoecoae.platform.registry.CraftingSystemL4;
import cn.dancingsnow.neoecoae.platform.registry.CraftingSystemL6;
import cn.dancingsnow.neoecoae.platform.registry.CraftingSystemL9;
import cn.dancingsnow.neoecoae.platform.registry.HostL4;
import cn.dancingsnow.neoecoae.platform.registry.HostL6;
import cn.dancingsnow.neoecoae.platform.registry.HostL9;
import cn.dancingsnow.neoecoae.platform.registry.Workstation;

/**
 * StructureLib keys its registry by canonical name while BlockRenderer6343 instantiates with Class.forName; a nested
 * controller class breaks that lookup and aborts the whole NEI StructureLib scan.
 */
class NEStructureRegistrationTest {

    @Test
    void registeredControllersResolveByCanonicalName() {
        for (Class<?> type : new Class<?>[] { HostL4.class, HostL6.class, HostL9.class, CraftingSystemL4.class,
            CraftingSystemL6.class, CraftingSystemL9.class, ComputationSystemL4.class, ComputationSystemL6.class,
            ComputationSystemL9.class, Workstation.class }) assertEquals(type.getName(), type.getCanonicalName());
    }
}
