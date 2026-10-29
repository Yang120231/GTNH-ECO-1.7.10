package cn.dancingsnow.neoecoae.platform.mixin;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.zip.ZipFile;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.util.Annotations;

import appeng.api.storage.StorageChannel;
import appeng.tile.storage.TileDrive;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeCellHandler;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTier;

class DriveCellRestrictionTest {

    private static ItemStack cell(AEKeyType type) {
        return new ItemStack(
            new NativeStorageCellItem(StorageTier.L6, type, new ECOCellType(Component.literal("test"), 65)));
    }

    @Test
    void driveRejectsBothItemAndFluidCellsInEverySlot() throws Exception {
        for (AEKeyType type : new AEKeyType[] { AEKeyType.items(), AEKeyType.fluids() }) {
            ItemStack stack = cell(type);
            for (int slot = 0; slot < 10; slot++) {
                CallbackInfoReturnable<Boolean> callback = checkSlot(slot, stack);
                assertTrue(callback.isCancelled());
                assertEquals(Boolean.FALSE, callback.getReturnValue());
            }
        }
    }

    @Test
    void ordinaryItemsAndEmptyStacksKeepNativeValidation() throws Exception {
        assertFalse(checkSlot(0, new ItemStack(TestItems.DIAMOND)).isCancelled());
        assertFalse(checkSlot(0, null).isCancelled());
    }

    @Test
    void previouslyInsertedCellsCannotMountInNativeDrive() {
        NativeCellHandler handler = new NativeCellHandler();
        TileDrive drive = mock(TileDrive.class);
        for (AEKeyType type : new AEKeyType[] { AEKeyType.items(), AEKeyType.fluids() }) {
            ItemStack stack = cell(type);
            assertTrue(handler.isCell(stack), "ECO cells must remain registered for the native IO port");
            assertNull(handler.getCellInventory(stack, drive, type.nativeChannel()));
        }
    }

    @Test
    void nativeIoPortCanStillOpenMatchingInventories() {
        NativeCellHandler handler = new NativeCellHandler();
        for (AEKeyType type : new AEKeyType[] { AEKeyType.items(), AEKeyType.fluids() }) {
            ItemStack stack = cell(type);
            // TileIOPort opens each cell through the native registry with a null save provider.
            assertNotNull(handler.getCellInventory(stack, null, type.nativeChannel()));
            StorageChannel other = type == AEKeyType.items() ? StorageChannel.FLUIDS : StorageChannel.ITEMS;
            assertNull(handler.getCellInventory(stack, null, other));
            assertNotNull(
                handler.getCellInventory(stack, (cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider) null));
        }
    }

    @Test
    void requiredDriveInjectionTargetExistsInPinnedAe2Jar() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/appeng/tile/storage/TileDrive.class")) {
            assertNotNull(stream);
            assertInjectionTarget(stream);
        }
    }

    @Test
    void requiredDriveInjectionTargetExistsInReleaseAe2Jar() throws Exception {
        String path = System.getenv("NEOECOAE_AE2_COMPAT_JAR");
        assumeTrue(path != null && !path.isEmpty(), "Set NEOECOAE_AE2_COMPAT_JAR to validate a release AE2 JAR");
        try (ZipFile jar = new ZipFile(path)) {
            try (InputStream stream = jar.getInputStream(jar.getEntry("appeng/tile/storage/TileDrive.class"))) {
                assertInjectionTarget(stream);
            }
        }
    }

    private static void assertInjectionTarget(InputStream stream) throws Exception {
        ClassNode drive = new ClassNode();
        new ClassReader(stream).accept(drive, ClassReader.SKIP_CODE);
        ClassNode mixin = new ClassNode();
        try (InputStream hook = DriveCellRestrictionTest.class
            .getResourceAsStream("/cn/dancingsnow/neoecoae/platform/mixin/DriveCellRestrictionMixin.class")) {
            assertNotNull(hook);
            new ClassReader(hook).accept(mixin, ClassReader.SKIP_CODE);
        }
        AnnotationNode inject = Annotations.getVisible(
            mixin.methods.stream()
                .filter(method -> method.name.equals("neoecoae$rejectEcoCell"))
                .findFirst()
                .get(),
            Inject.class);
        assertNotNull(inject);
        List<String> selectors = Annotations.getValue(inject, "method");
        assertEquals(
            1,
            drive.methods.stream()
                .filter(method -> selectors.contains(method.name + method.desc))
                .count(),
            "Exactly one injection selector must match in both development and release jars");
    }

    private static CallbackInfoReturnable<Boolean> checkSlot(int slot, ItemStack stack) throws Exception {
        Method hook = DriveCellRestrictionMixin.class
            .getDeclaredMethod("neoecoae$rejectEcoCell", int.class, ItemStack.class, CallbackInfoReturnable.class);
        hook.setAccessible(true);
        CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>("isItemValidForSlot", true);
        hook.invoke(new DriveCellRestrictionMixin() {}, slot, stack, callback);
        return callback;
    }
}
