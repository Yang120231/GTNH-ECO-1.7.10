package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/**
 * The ECO report reads AE2's confirmation container through an accessor mixin and calls its public API. Verify the
 * members in the pinned jar, and in an installed release (e.g. rv3-beta-1080) when NEOECOAE_AE2_COMPAT_JAR is set,
 * without loading AE2's game-only static initialization.
 */
class CraftConfirmContainerContractTest {

    private static final String CONTAINER = "appeng/container/implementations/ContainerCraftConfirm";

    private static final Set<String> FIELDS = new HashSet<>(
        Arrays.asList(
            "job:Ljava/util/concurrent/Future;",
            "result:Lappeng/api/networking/crafting/ICraftingJob;",
            "cpuTable:Lappeng/container/implementations/ContainerCPUTable;"));

    private static final Set<String> METHODS = new HashSet<>(
        Arrays.asList(
            "getGrid()Lappeng/api/networking/IGrid;",
            "setAutoStart(Z)V",
            "setAutoStartAndFollow(Z)V",
            "switchToOriginalGUI()V",
            "hasNoCPU()Z",
            "getSelectedCpu()I",
            "getName()Ljava/lang/String;",
            "getCpuAvailableBytes()J",
            "getCpuCoProcessors()I",
            "cpuCraftingSameItem(Lappeng/container/implementations/CraftingCPUStatus;)Z",
            "cpuMatches(Lappeng/container/implementations/CraftingCPUStatus;)Z"));

    @Test
    void pinnedContainerExposesTheReportedMembers() throws IOException {
        try (InputStream stream = CraftConfirmContainerContractTest.class
            .getResourceAsStream("/" + CONTAINER + ".class")) {
            assertNotNull(stream, "Missing pinned AE2 confirmation container");
            assertMembers(read(stream));
        }
    }

    @Test
    void installedContainerExposesTheReportedMembers() throws IOException {
        String path = System.getenv("NEOECOAE_AE2_COMPAT_JAR");
        assumeTrue(path != null && !path.isEmpty(), "Set NEOECOAE_AE2_COMPAT_JAR to validate an installed AE2 JAR");
        try (ZipFile jar = new ZipFile(path)) {
            var entry = jar.getEntry(CONTAINER + ".class");
            assertNotNull(entry, "Compatibility JAR is missing ContainerCraftConfirm");
            assertMembers(read(jar.getInputStream(entry)));
        }
    }

    private static ClassNode read(InputStream stream) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
        return node;
    }

    private static void assertMembers(ClassNode node) {
        Set<String> fields = new HashSet<>();
        for (FieldNode field : node.fields) fields.add(field.name + ":" + field.desc);
        for (String field : FIELDS) assertTrue(fields.contains(field), "Missing accessor field " + field);
        Set<String> methods = new HashSet<>();
        for (MethodNode method : node.methods) methods.add(method.name + method.desc);
        for (String method : METHODS) assertTrue(methods.contains(method), "Missing container method " + method);
    }
}
