package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fluids.FluidTank;

import org.junit.jupiter.api.Test;

import appeng.parts.automation.UpgradeInventory;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageInterfaceSession;
import cn.dancingsnow.neoecoae.platform.ui.storage.NativeStoragePrioritySession;
import cn.dancingsnow.neoecoae.platform.ui.workstation.NativeWorkstationSession;
import cpw.mods.fml.common.asm.transformers.SideTransformer;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

/** Real FML stripping, real MUI2 menu construction, and an intentionally unsafe method-reference control. */
class DedicatedServerUiTest {

    private static final String UI = "cn.dancingsnow.neoecoae.platform.ui.";
    private static final String MUI = "com.cleanroommc.modularui.";

    @Test
    void priorityPopupBuildsAfterFmlDeletesItsClientActions() throws Exception {
        try (var loader = new ServerLoader()) {
            Class<?> session = loader.loadClass(UI + "storage.NativeStoragePrioritySession");
            assertThrows(NoSuchMethodException.class, () -> session.getDeclaredMethod("set", String.class));
            Object state = session.getConstructor(ECOStorageSystemBlockEntity.class).newInstance((Object) null);
            Object handler = mock(loader.loadClass(MUI + "api.IPanelHandler"));
            assertNotNull(loader.loadClass(UI + "storage.NativeStoragePriorityPages")
                .getMethod("create", session, handlerType(loader)).invoke(null, state, handler));
            assertMissingCallback(loader, "priority", session, state);
        }
    }

    @Test
    void storageInterfaceBuildsAfterFmlDeletesItsClientActions() throws Exception {
        try (var loader = new ServerLoader()) {
            Class<?> session = loader.loadClass(UI + "storage.NativeStorageInterfaceSession");
            assertThrows(NoSuchMethodException.class, () -> session.getDeclaredMethod("toggleCreative"));
            var host = mock(ECOMachineInterfaceBlockEntity.class);
            Object state = session.getConstructor(ECOMachineInterfaceBlockEntity.class).newInstance(host);
            assertNotNull(loader.loadClass(UI + "storage.NativeStorageInterfacePages")
                .getMethod("create", ECOMachineInterfaceBlockEntity.class, EntityPlayer.class, managerType(loader))
                .invoke(null, host, player(), mock(managerType(loader), RETURNS_DEEP_STUBS)));
            assertMissingCallback(loader, "storageInterface", session, state);
        }
    }

    @Test
    void workstationBuildsAfterFmlDeletesItsClientActions() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var loader = new ServerLoader()) {
            Class<?> session = loader.loadClass(UI + "workstation.NativeWorkstationSession");
            assertThrows(NoSuchMethodException.class, () -> session.getDeclaredMethod("clearInput"));
            var host = mock(ECOIntegratedWorkingStationBlockEntity.class);
            when(host.getInputInv()).thenReturn(new AppEngInternalInventory(null, 9, 64));
            when(host.getOutputInv()).thenReturn(new AppEngInternalInventory(null, 1, 64));
            when(host.getInputTank()).thenReturn(new FluidTank(1000));
            when(host.getOutputTank()).thenReturn(new FluidTank(1000));
            var upgrades = mock(UpgradeInventory.class);
            when(upgrades.getSizeInventory()).thenReturn(4);
            when(host.getUpgrades()).thenReturn(upgrades);
            Object state = session.getConstructor(ECOIntegratedWorkingStationBlockEntity.class).newInstance(host);
            assertNotNull(loader.loadClass(UI + "workstation.NativeWorkstationPages")
                .getMethod("create", ECOIntegratedWorkingStationBlockEntity.class, EntityPlayer.class, managerType(loader))
                .invoke(null, host, player(), mock(managerType(loader), RETURNS_DEEP_STUBS)));
            assertMissingCallback(loader, "workstation", session, state);
        }
    }

    private static EntityPlayer player() {
        var player = mock(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static Class<?> handlerType(ServerLoader loader) throws ClassNotFoundException {
        return loader.loadClass(MUI + "api.IPanelHandler");
    }

    private static Class<?> managerType(ServerLoader loader) throws ClassNotFoundException {
        return loader.loadClass(MUI + "value.sync.PanelSyncManager");
    }

    private static void assertMissingCallback(ServerLoader loader, String method, Class<?> session, Object state) throws Exception {
        Class<?> fixture = loader.loadClass(UnsafeCallbacks.class.getName());
        var failure = assertThrows(InvocationTargetException.class, () -> fixture.getMethod(method, session).invoke(null, state));
        assertInstanceOf(NoSuchMethodError.class, failure.getCause());
    }

    public static class UnsafeCallbacks {
        public static Consumer<String> priority(NativeStoragePrioritySession session) { return session::set; }
        public static Runnable storageInterface(NativeStorageInterfaceSession session) { return session::toggleCreative; }
        public static Runnable workstation(NativeWorkstationSession session) { return session::clearInput; }
    }

    private static final class ServerLoader extends ClassLoader implements AutoCloseable {
        private final Field launchSide, transformerSide;
        private final Object previousLaunchSide, previousTransformerSide;
        private final SideTransformer transformer;

        ServerLoader() throws Exception {
            super(DedicatedServerUiTest.class.getClassLoader());
            launchSide = FMLLaunchHandler.class.getDeclaredField("side");
            launchSide.setAccessible(true);
            previousLaunchSide = launchSide.get(null);
            launchSide.set(null, Side.SERVER);
            transformer = new SideTransformer();
            transformerSide = SideTransformer.class.getDeclaredField("SIDE");
            transformerSide.setAccessible(true);
            previousTransformerSide = transformerSide.get(null);
            transformerSide.set(null, "SERVER");
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || name.startsWith("cpw.mods.fml.client."))
                throw new ClassNotFoundException("Dedicated server cannot load " + name);
            // GTNH patches FML's legacy ASM at launch. Strip our production UI with the real cleaner;
            // use the normal MUI2 dependency to avoid rewriting its interface lambdas with unpatched FML ASM.
            if (!name.startsWith(UI) || name.startsWith(DedicatedServerUiTest.class.getName())
                && !name.equals(UnsafeCallbacks.class.getName())) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] raw = com.google.common.io.ByteStreams.toByteArray(input);
                        byte[] bytes = transformer.transform(name, name, raw);
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }

        @Override
        public void close() throws Exception {
            transformerSide.set(null, previousTransformerSide);
            launchSide.set(null, previousLaunchSide);
        }
    }
}
