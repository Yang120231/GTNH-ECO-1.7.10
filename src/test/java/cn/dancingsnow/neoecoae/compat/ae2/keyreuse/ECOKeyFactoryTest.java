package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;

class ECOKeyFactoryTest {

    private static final net.minecraft.item.Item ITEM = new net.minecraft.item.Item();

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var loader = mock(cpw.mods.fml.common.Loader.class);
        var container = mock(cpw.mods.fml.common.ModContainer.class);
        when(container.getModId()).thenReturn("neoecoae_local_key_tests");
        when(loader.activeModContainer()).thenReturn(container);
        try (var loading = mockStatic(cpw.mods.fml.common.Loader.class)) {
            loading.when(cpw.mods.fml.common.Loader::instance)
                .thenReturn(loader);
            net.minecraft.item.Item.itemRegistry.addObject(29002, "neoecoae_local_key_tests:plain", ITEM);
        }
    }

    @AfterEach
    void stop() {
        KeyReuse.configure(false);
    }

    @Test
    void oldAndOwnedKeysKeepOneExactInventoryAcrossReloadAndDisable() {
        KeyReuse.configure(false);
        ItemStack source = new ItemStack(ITEM, 64, 0);
        AEItemKey old = AEItemKey.of(source);
        KeyReuse.configure(true);
        AEItemKey owned = AEItemKey.of(source);
        assertNotSame(old, owned);
        assertSame(owned, ECOKeyFactory.canonicalize(old));
        assertEquals(old, owned);
        assertEquals(64, source.stackSize);
        var engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        BigInteger huge = BigInteger.TEN.pow(40);
        assertEquals(huge, engine.insert(old, huge, Actionable.MODULATE));
        assertEquals(
            huge,
            engine.getAmount(owned)
                .toBigInteger());
        KeyReuse.invalidate();
        assertEquals(31, engine.extract(AEItemKey.of(source), 31, Actionable.MODULATE));
        KeyReuse.configure(false);
        assertSame(old, ECOKeyFactory.canonicalize(old));
        assertEquals(
            huge.subtract(BigInteger.valueOf(31)),
            engine.getAmount(old)
                .toBigInteger());
    }

    @Test
    void emptyTagAndRawMetadataStayDistinctAtTheEcoBoundary() {
        KeyReuse.configure(true);
        ItemStack plain = new ItemStack(ITEM, 1, 257);
        AEItemKey owned = AEItemKey.of(plain);
        ItemStack tagged = plain.copy();
        tagged.setTagCompound(new NBTTagCompound());
        AEItemKey complex = AEItemKey.of(tagged);
        assertSame(complex, ECOKeyFactory.canonicalize(complex));
        assertNotEquals(owned, complex);
        assertNotEquals(owned, AEItemKey.of(new ItemStack(ITEM, 1, 258)));
    }
}
