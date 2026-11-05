package cn.dancingsnow.neoecoae.dev;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.TestResourceKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;



import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneratorStateTest {

    @Test void fixedDistinctPaletteSurvivesRateChangesAndOnlyRerollsOnRequest() {
        List<AEKey> pool = java.util.Arrays.asList(new TestResourceKey("stone", false), new TestResourceKey("dirt", false), new TestResourceKey("diamond", false));
        try (var discovery = mockStatic(GeneratorPool.class)) {
            discovery.when(GeneratorPool::keys).thenReturn(pool);
            var state = new GeneratorState();
            state.configure(new GeneratorSettings(2, 1, 64, 20));
            var selected = new java.util.ArrayList<>(state.selected);
            assertEquals(2, selected.stream().distinct().count());
            state.configure(new GeneratorSettings(2, 100, 200, 1));
            assertEquals(selected, state.selected);
            discovery.when(GeneratorPool::keys).thenReturn(java.util.Arrays.asList(new TestResourceKey("gold_ingot", false), new TestResourceKey("iron_ingot", false)));
            state.reroll();
            assertEquals(2, state.selected.size());
            assertTrue(state.selected.stream().noneMatch(selected::contains));
            var settings = state.settings;
            assertThrows(IllegalArgumentException.class, () -> state.configure(new GeneratorSettings(3, 1, 1, 1)));
            assertEquals(settings, state.settings);
        }
    }

    @Test void paletteAndConfigurationSurviveReload() {
        var state = new GeneratorState();
        state.settings = new GeneratorSettings(2, 20, Long.MAX_VALUE, 5);
        state.selected.add(new TestResourceKey("stone", false));
        state.selected.add(new TestResourceKey("water", true));
        var restored = GeneratorState.read(state.write(cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY),
            cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY);
        assertEquals(state.settings, restored.settings);
        assertEquals(state.selected, restored.selected);
    }

    @Test void insertsIntoActualStorageAtIntervalAndDiscardsRejectedOutput() {
        var grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        MEStorage storage = grid.getStorageService().getInventory();
        var source = mock(IActionSource.class);
        AEKey key = new TestResourceKey("stone", false);
        var state = new GeneratorState();
        state.settings = new GeneratorSettings(1, 10, 10, 3);
        state.selected.add(key);
        var saves = new AtomicInteger();
        when(storage.insert(key, 10, Actionable.MODULATE, source)).thenReturn(4L, 0L, 10L);
        state.tick(grid, source, saves::incrementAndGet);
        state.tick(grid, source, saves::incrementAndGet);
        verify(storage, never()).insert(any(), anyLong(), any(), any());
        for (int round = 0; round < 3; round++) {
            state.tick(grid, source, saves::incrementAndGet);
            assertEquals(new long[]{4, 0, 10}[round], state.accepted);
            if (round < 2) {
                state.tick(grid, source, saves::incrementAndGet);
                state.tick(grid, source, saves::incrementAndGet);
            }
        }
        verify(storage, times(3)).insert(key, 10, Actionable.MODULATE, source);
        assertEquals(java.util.Arrays.asList(key), state.selected);
        assertEquals(0, saves.get());
    }
}
