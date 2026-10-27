package cn.dancingsnow.neoecoae.platform.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemHandler;

class AppEngInternalInventoryTest {

    @Test
    void handlerAndNativeInventoryShareContentsAndNotifications() {
        InventoryTestBootstrap.initialize();
        AtomicInteger changes = new AtomicInteger();
        AppEngInternalInventory inventory = new AppEngInternalInventory(ignored -> changes.incrementAndGet(), 1, 16);
        IItemHandlerModifiable handler = inventory.toItemHandler();
        IInventory nativeInventory = inventory;
        assertSame(handler, inventory.toItemHandler());
        assertFalse(IInventory.class.isInstance(handler));
        assertFalse(IItemHandler.class.isInstance(inventory));
        assertEquals(1, handler.getSlots());
        assertEquals(16, handler.getSlotLimit(0));

        ItemStack stack = new ItemStack(TestItems.DIAMOND, 4);
        handler.setStackInSlot(0, stack);
        assertSame(stack, nativeInventory.getStackInSlot(0));
        assertSame(stack, new NativeItemHandler(handler).getStackInSlot(0));
        assertEquals(1, changes.get());
        nativeInventory.setInventorySlotContents(0, null);
        assertNull(handler.getStackInSlot(0));
        assertEquals(2, changes.get());
        assertThrows(IndexOutOfBoundsException.class, () -> handler.getStackInSlot(1));
    }

    @Test
    void handlerPreservesSimulationValidationAndSubclassExtractionRules() {
        InventoryTestBootstrap.initialize();
        AtomicInteger changes = new AtomicInteger();
        AppEngInternalInventory inventory = new AppEngInternalInventory(ignored -> changes.incrementAndGet(), 1, 4) {

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack != null && stack.getItem() == TestItems.DIAMOND;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return amount > 1 ? null : super.extractItem(slot, amount, simulate);
            }
        };
        IItemHandlerModifiable handler = inventory.toItemHandler();
        assertFalse(handler.isItemValid(0, new ItemStack(TestItems.STONE)));
        assertNotNull(handler.insertItem(0, new ItemStack(TestItems.STONE), false));
        assertNull(handler.insertItem(0, new ItemStack(TestItems.DIAMOND, 4), true));
        assertNull(handler.getStackInSlot(0));
        assertEquals(0, changes.get());
        assertEquals(2, handler.insertItem(0, new ItemStack(TestItems.DIAMOND, 6), false).stackSize);
        assertEquals(4, handler.getStackInSlot(0).stackSize);
        assertEquals(1, changes.get());
        assertNull(handler.extractItem(0, 2, false));
        assertEquals(1, handler.extractItem(0, 1, true).stackSize);
        assertEquals(4, handler.getStackInSlot(0).stackSize);
        assertEquals(1, changes.get());
        assertEquals(1, handler.extractItem(0, 1, false).stackSize);
        assertEquals(3, handler.getStackInSlot(0).stackSize);
        assertEquals(2, changes.get());
    }

    @Test
    @EnabledIfSystemProperty(named = "neoecoae.reobfJar", matches = ".+")
    void releaseHandlerResolvesTheRemappedNativeGetter() throws Exception {
        URL jar = Paths.get(System.getProperty("neoecoae.reobfJar"))
            .toUri()
            .toURL();
        String inventoryName = AppEngInternalInventory.class.getName();
        // Load the shipped inventory and its adapter, while retaining the test's item-handler
        // interface. This reproduces the release-only invokeinterface failure without a world.
        try (URLClassLoader loader = new URLClassLoader(new URL[] { jar }, getClass().getClassLoader()) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(inventoryName) && !name.startsWith(inventoryName + "$"))
                    return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> result = findLoadedClass(name);
                    if (result == null) result = findClass(name);
                    if (resolve) resolveClass(result);
                    return result;
                }
            }
        }) {
            Class<?> shipped = loader.loadClass(inventoryName);
            assertSame(loader, shipped.getClassLoader());
            assertNotNull(shipped.getDeclaredMethod("func_70301_a", int.class));
            Object inventory = shipped.getConstructor(InternalInventoryHost.class, int.class, int.class)
                .newInstance(null, 1, 64);
            IItemHandlerModifiable handler = (IItemHandlerModifiable) shipped.getMethod("toItemHandler")
                .invoke(inventory);
            assertNull(handler.getStackInSlot(0));
            assertNull(new NativeItemHandler(handler).getStackInSlot(0));
            assertThrows(IndexOutOfBoundsException.class, () -> handler.getStackInSlot(1));
        }
    }
}
