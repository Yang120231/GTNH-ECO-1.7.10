package cn.dancingsnow.neoecoae.platform.ae2.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.FieldNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.mixin.NetworkInventoryHandlerMixin;
import cn.dancingsnow.neoecoae.platform.mixin.NetworkMonitorMixin;

/**
 * NetworkMonitorMixin and NetworkInventoryHandlerMixin are bound to rv3-beta-1095's bytecode; this pins the members
 * they shadow and the single {@code hasChanged = true} write that the incremental list replaces.
 */
class NetworkMonitorTargetsContractTest {

    private static final String SOURCE = "Lappeng/api/networking/security/BaseActionSource;";

    @Test
    void networkMonitorStillHasTheShadowedMembersAndOneChangeFlagWrite() throws IOException {
        ClassNode monitor = read("appeng/me/cache/NetworkMonitor");
        field(monitor, "hasChanged", "Z");
        field(monitor, "locked", "Z");
        field(monitor, "cachedList", "Lappeng/api/storage/data/IItemList;");
        field(monitor, "stackType", "Lappeng/api/storage/data/IAEStackType;");
        method(monitor, "postChange", "(ZLjava/lang/Iterable;" + SOURCE + ")V");
        method(monitor, "getStorageList", "()Lappeng/api/storage/data/IItemList;");
        method(monitor, "onTick", "()V");
        method(monitor, "getHandler", "()Lappeng/api/storage/IMEInventoryHandler;");
        MethodNode notify = method(monitor, "notifyListenersOfChange", "(Ljava/lang/Iterable;" + SOURCE + ")V");
        int writes = 0;
        for (AbstractInsnNode insn = notify.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn.getOpcode() == Opcodes.PUTFIELD && "hasChanged".equals(((FieldInsnNode) insn).name)) writes++;
        assertEquals(1, writes);
        // The removal direction only reaches notifyListenersOfChange through postChange.
        MethodNode post = method(monitor, "postChange", "(ZLjava/lang/Iterable;" + SOURCE + ")V");
        boolean calls = false;
        for (AbstractInsnNode insn = post.instructions.getFirst(); insn != null; insn = insn.getNext())
            calls |= insn instanceof org.spongepowered.asm.lib.tree.MethodInsnNode
                && "notifyListenersOfChange".equals(((org.spongepowered.asm.lib.tree.MethodInsnNode) insn).name);
        assertTrue(calls);
    }

    @Test
    void networkInventoryHandlerStillKeepsItsPriorityList() throws IOException {
        field(read("appeng/me/storage/NetworkInventoryHandler"), "priorityInventory", "Ljava/util/List;");
    }

    @Test
    void theRedirectIsSingleAndRequired() throws NoSuchMethodException {
        Redirect redirect = cn.dancingsnow.neoecoae.platform.mixin.NetworkMonitorMixin.class
            .getDeclaredMethod("neoecoae$applyChanges", appeng.me.cache.NetworkMonitor.class, boolean.class)
            .getAnnotation(Redirect.class);
        assertNotNull(redirect);
        assertEquals(1, redirect.require());
        assertEquals(1, redirect.allow());
    }

    @Test
    void bothNetworkMonitorInsertionPathsAreGuarded() throws Exception {
        MethodNode insert = method(
            read("appeng/me/cache/NetworkMonitor"),
            "injectItems",
            "(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;" + SOURCE
                + ")Lappeng/api/storage/data/IAEStack;");
        int calls = 0;
        for (AbstractInsnNode insn : insert.instructions.toArray()) {
            if (insn instanceof org.spongepowered.asm.lib.tree.MethodInsnNode call
                && call.owner.equals("appeng/api/storage/IMEInventoryHandler")
                && call.name.equals("injectItems")) calls++;
        }
        Redirect redirect = monitorInsertion().getAnnotation(Redirect.class);
        assertEquals(2, calls);
        assertEquals(calls, redirect.require());
        assertEquals(calls, redirect.allow());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void craftingAcceptanceInvalidatesStockButPhysicalInsertionAndSimulationKeepTheCache() throws Exception {
        NetworkInventoryHandlerMixin inventory = new NetworkInventoryHandlerMixin() {};
        var insertTarget = NetworkInventoryHandlerMixin.class.getDeclaredMethod(
            "neoecoae$insertPhysicalTarget",
            IMEInventoryHandler.class,
            IAEStack.class,
            Actionable.class,
            BaseActionSource.class);
        insertTarget.setAccessible(true);
        IAEStack offered = mock(IAEStack.class);
        when(offered.getStackSize()).thenReturn(1L);
        IMEInventoryHandler target = mock(IMEInventoryHandler.class);
        IMEInventoryHandler network = mock(
            IMEInventoryHandler.class,
            withSettings().extraInterfaces(NativeNetworkReads.class));
        NativeNetworkReads reads = (NativeNetworkReads) network;
        when(reads.neoecoae$craftingInsertionVersion())
            .thenAnswer(invocation -> inventory.neoecoae$craftingInsertionVersion());
        when(network.injectItems(any(), any(), any())).thenAnswer(
            invocation -> insertTarget.invoke(
                inventory,
                target,
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2)));
        NetworkMonitorMixin monitor = new NetworkMonitorMixin() {

            @Override
            public IMEInventoryHandler<?> getHandler() {
                return network;
            }
        };
        var changed = NetworkMonitorMixin.class.getDeclaredField("hasChanged");
        changed.setAccessible(true);
        var insert = monitorInsertion();
        insert.setAccessible(true);

        // Physical insertion really contributes one stored item: its difference remains safe to apply.
        assertNull(insert.invoke(monitor, network, offered, Actionable.MODULATE, new BaseActionSource()));
        assertFalse(changed.getBoolean(monitor));
        assertEquals(0, inventory.neoecoae$craftingInsertionVersion());

        when(target.isAutoCraftingInventory()).thenReturn(true);
        insert.invoke(monitor, network, offered, Actionable.SIMULATE, new BaseActionSource());
        assertFalse(changed.getBoolean(monitor));
        assertEquals(0, inventory.neoecoae$craftingInsertionVersion());

        // A CPU accepts one item but contributes zero stored items. The +1 notification must force a rebuild.
        insert.invoke(monitor, network, offered, Actionable.MODULATE, new BaseActionSource());
        assertTrue(changed.getBoolean(monitor));
        assertEquals(1, inventory.neoecoae$craftingInsertionVersion());

        changed.setBoolean(monitor, false);
        when(target.injectItems(any(), any(), any())).thenReturn(offered);
        assertSame(offered, insert.invoke(monitor, network, offered, Actionable.MODULATE, new BaseActionSource()));
        assertFalse(changed.getBoolean(monitor));
        assertEquals(1, inventory.neoecoae$craftingInsertionVersion());
    }

    private static java.lang.reflect.Method monitorInsertion() throws NoSuchMethodException {
        return NetworkMonitorMixin.class.getDeclaredMethod(
            "neoecoae$insertAndInvalidateCrafting",
            IMEInventoryHandler.class,
            IAEStack.class,
            Actionable.class,
            BaseActionSource.class);
    }

    private static ClassNode read(String name) throws IOException {
        try (InputStream stream = NetworkMonitorTargetsContractTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static FieldNode field(ClassNode owner, String name, String desc) {
        for (FieldNode field : owner.fields) if (field.name.equals(name) && field.desc.equals(desc)) return field;
        return fail(owner.name + " lost field " + name + " " + desc);
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        return fail(owner.name + " lost method " + name + desc);
    }
}
