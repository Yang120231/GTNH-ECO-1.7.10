package cn.dancingsnow.neoecoae.platform.ui.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class CraftingStatusTaskOrderTest {

    @Test
    void laterUpdatesAndNewTasksCannotReorderRunningTasks() {
        var order = new CraftingStatusTaskOrder<String>();
        assertEquals(Arrays.asList("a", "b"), order.update(1, "job", Arrays.asList("a", "b")));
        assertEquals(Arrays.asList("a", "b", "c"), order.update(1, "job", Arrays.asList("c", "b", "a")));
        assertEquals(Arrays.asList("b", "c"), order.update(1, "job", Arrays.asList("c", "b")));
        assertEquals(Arrays.asList("b", "c", "a"), order.update(1, "job", Arrays.asList("a", "b", "c")));
    }

    @Test
    void cpuSwitchNewJobAndClearingResetTheObservedOrder() {
        var order = new CraftingStatusTaskOrder<String>();
        order.update(1, "old", Arrays.asList("a", "b"));
        assertEquals(Arrays.asList("b", "a"), order.update(2, "old", Arrays.asList("b", "a")));
        assertEquals(Arrays.asList("a", "b"), order.update(2, "new", Arrays.asList("a", "b")));
        order.clear();
        assertEquals(Arrays.asList("b", "a"), order.update(2, "new", Arrays.asList("b", "a")));
        assertEquals(Collections.emptyList(), order.update(2, "new", Collections.emptyList()));
    }
}
