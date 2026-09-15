package cn.dancingsnow.neoecoae.multiblock.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class NEFrequencyAllocatorTest {

    @Test
    void fillsEightHostsBeforeOpeningNextFrequency() {
        List<Integer> assigned = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            int frequency = NEFrequencyAllocator.allocate(assigned);
            assertEquals(i / 8 + 1, frequency);
            assigned.add(frequency);
        }
        assertEquals(1, NEFrequencyAllocator.allocate(assigned));
        assigned.remove(Integer.valueOf(3));
        assertEquals(3, NEFrequencyAllocator.allocate(assigned));
    }
}
