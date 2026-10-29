package cn.dancingsnow.neoecoae.compat.gregtech;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechMachineStatus.Sample;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

class ECOGregTechMachineStatusTest {

    @Test
    void reportsOnlyTheMatchingCurrentCycleAndTheNativeStallReason() {
        var key = AEItemKey.of(TestItems.IRON_INGOT);
        var status = ECOGregTechMachineStatus
            .sampleMachine(sample(true, true, false, true), Component.literal("GT"), "0 [1, 2, 3]", key);
        assertEquals(State.RUNNING, status.state());
        assertEquals(30, status.progressTicks());
        assertEquals(100, status.durationTicks());
        assertEquals(4, status.parallel());
        status = ECOGregTechMachineStatus
            .sampleMachine(sample(true, true, true, true), Component.literal("GT"), "", key);
        assertEquals(State.WAITING, status.state());
        assertFalse(
            status.reason()
                .getString()
                .isEmpty());
        status = ECOGregTechMachineStatus.sampleMachine(
            sample(true, true, false, true),
            Component.literal("GT"),
            "",
            AEItemKey.of(TestItems.GOLD_INGOT));
        assertEquals(State.QUEUED, status.state());
        assertEquals(0, status.durationTicks(), "An unrelated recipe never supplies this output's progress");
    }

    @Test
    void disabledIdleMachinesAndUnformedControllersReportTheirActualState() {
        var key = AEItemKey.of(TestItems.IRON_INGOT);
        assertEquals(
            State.PAUSED,
            ECOGregTechMachineStatus.sampleMachine(sample(false, false, false, true), Component.literal("GT"), "", key)
                .state());
        assertEquals(
            State.WAITING,
            ECOGregTechMachineStatus.sampleMachine(sample(false, true, false, false), Component.literal("GT"), "", key)
                .state());
    }

    private static Sample sample(boolean active, boolean allowed, boolean stuttering, boolean formed) {
        return new Sample(
            "GT",
            Collections.singletonList(new ItemStack(TestItems.IRON_INGOT)),
            30,
            100,
            active,
            allowed,
            stuttering,
            formed,
            null,
            4);
    }
}
