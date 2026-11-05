package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

class KeyReuseRoutingTest {

    private static Item first;
    private static Item second;

    @BeforeAll
    static void registerCollidingItems() {
        Loader loader = mock(Loader.class);
        ModContainer container = mock(ModContainer.class);
        when(container.getModId()).thenReturn("neoecoae_key_reuse_tests");
        when(loader.activeModContainer()).thenReturn(container);
        try (var loading = mockStatic(Loader.class)) {
            loading.when(Loader::instance)
                .thenReturn(loader);
            Map<Integer, Item> candidates = new HashMap<>();
            for (int i = 0; i <= 4096; i++) {
                Item item = new Item();
                Item prior = candidates.putIfAbsent(System.identityHashCode(item) & 4095, item);
                if (prior != null) {
                    first = prior;
                    second = item;
                    break;
                }
            }
            assertNotNull(second);
            Item.itemRegistry.addObject(29000, "neoecoae_key_reuse_tests:route_a", first);
            Item.itemRegistry.addObject(29001, "neoecoae_key_reuse_tests:route_b", second);
        }
    }

    @BeforeEach
    void start() {
        KeyReuse.configure(true);
    }

    @AfterEach
    void stop() {
        KeyReuse.configure(false);
    }

    @Test
    void collisionsRetainEachItemsMetadataAndCanonicalKeys() {
        AEItemKey a = AEItemKey.of(new ItemStack(first, 1, 257));
        AEItemKey b = AEItemKey.of(new ItemStack(second, 1, 257));
        assertNotEquals(a, b);
        assertNotEquals(a, AEItemKey.of(new ItemStack(first, 1, 258)));
        for (int i = 0; i < 32; i++) {
            assertSame(a, AEItemKey.of(new ItemStack(first, 64, 257)));
            assertSame(b, AEItemKey.of(new ItemStack(second, 64, 257)));
        }
    }

    @Test
    void routingReloadPreservesOldValueLookupsAndUnregisteredFallback() {
        AEItemKey old = AEItemKey.of(first);
        Map<AEItemKey, Long> inventory = java.util.Collections.singletonMap(old, 987L);
        KeyReuse.invalidate();
        AEItemKey refreshed = AEItemKey.of(first);
        assertNotSame(old, refreshed);
        assertEquals(old, refreshed);
        assertEquals(Long.valueOf(987L), inventory.get(refreshed));
        Item unknown = new Item();
        assertNotSame(AEItemKey.of(unknown), AEItemKey.of(unknown));
    }
}
