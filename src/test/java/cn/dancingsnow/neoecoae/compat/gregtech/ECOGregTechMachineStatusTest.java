package cn.dancingsnow.neoecoae.compat.gregtech;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

class ECOGregTechMachineStatusTest {

    @Test
    void reportsOnlyTheMatchingCurrentCycleAndTheNativeStallReason() {
        var tile = new Cycle();
        var machine = new Machine();
        var key = AEItemKey.of(TestItems.IRON_INGOT);
        var status = ECOGregTechMachineStatus.sampleMachine(tile, machine, Component.literal("GT"), "0 [1, 2, 3]", key);
        assertEquals(State.RUNNING, status.state());
        assertEquals(30, status.progressTicks());
        assertEquals(100, status.durationTicks());
        assertEquals(4, status.parallel());
        machine.mStuttering = true;
        status = ECOGregTechMachineStatus.sampleMachine(tile, machine, Component.literal("GT"), "", key);
        assertEquals(State.WAITING, status.state());
        assertFalse(status.reason().getString().isEmpty());
        machine.mStuttering = false;
        status = ECOGregTechMachineStatus.sampleMachine(tile, machine, Component.literal("GT"), "", AEItemKey.of(TestItems.GOLD_INGOT));
        assertEquals(State.QUEUED, status.state());
        assertEquals(0, status.durationTicks(), "An unrelated recipe never supplies this output's progress");
    }

    @Test
    void disabledIdleMachinesAndUnformedControllersReportTheirActualState() {
        var tile = new Cycle();
        tile.active = false;
        tile.allowed = false;
        var machine = new Machine();
        var key = AEItemKey.of(TestItems.IRON_INGOT);
        assertEquals(State.PAUSED, ECOGregTechMachineStatus.sampleMachine(tile, machine, Component.literal("GT"), "", key).state());
        tile.allowed = true;
        machine.mMachine = false;
        assertEquals(State.WAITING, ECOGregTechMachineStatus.sampleMachine(tile, machine, Component.literal("GT"), "", key).state());
    }

    public static final class Cycle {
        boolean active = true, allowed = true;
        public int getProgress() { return 30; }
        public int getMaxProgress() { return 100; }
        public boolean isActive() { return active; }
        public boolean isAllowedToWork() { return allowed; }
    }

    public static final class Machine {
        public final ItemStack[] mOutputItems = { new ItemStack(TestItems.IRON_INGOT) };
        public final int lastParallel = 4;
        public boolean mStuttering, mMachine = true;
    }
}
