package cn.dancingsnow.neoecoae.platform.ae2.storage;

import static org.junit.jupiter.api.Assertions.*;

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

/**
 * NetworkMonitorMixin and NetworkInventoryHandlerMixin are bound to rv3-beta-990's bytecode; this pins the members
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
