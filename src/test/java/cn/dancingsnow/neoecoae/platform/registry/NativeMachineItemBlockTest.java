package cn.dancingsnow.neoecoae.platform.registry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;

/** NETooltips' shift details for the network-switch hosts, with the configured maximum length. */
class NativeMachineItemBlockTest {

    @Test
    void controllersDescribeThemselvesAndOtherMachinesStaySilent() {
        int crafting = PlanningSettings.Multiblock.craftingSystemMaxLength;
        try {
            PlanningSettings.Multiblock.craftingSystemMaxLength = 23;
            var lines = NativeMachineItemBlock.controllerTooltip("crafting_system_l9");
            assertEquals(2, lines.size());
            assertTrue(
                lines.get(1)
                    .getString()
                    .contains("23")
                    || lines.get(1)
                        .nativeText()
                        .toString()
                        .contains("23"));
            assertEquals(
                7,
                NativeMachineItemBlock.controllerTooltip("computation_system_l4")
                    .size());
            assertTrue(
                NativeMachineItemBlock.controllerTooltip("crafting_worker")
                    .isEmpty());
            assertTrue(
                NativeMachineItemBlock.controllerTooltip("storage_system_l4")
                    .isEmpty());
        } finally {
            PlanningSettings.Multiblock.craftingSystemMaxLength = crafting;
        }
    }
}
