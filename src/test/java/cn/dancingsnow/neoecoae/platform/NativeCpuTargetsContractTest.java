package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorByName;
import org.spongepowered.asm.mixin.injection.selectors.dynamic.DynamicSelectorDesc;
import org.spongepowered.asm.util.Annotations;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeCpuTargets;

/** Verify dependency bytecode without loading AE2's game-only static initialization. */
class NativeCpuTargetsContractTest {

    @Test
    void requiredNativeHooksExistInPinnedJar() throws IOException {
        Set<String> methods = readMethods("/appeng/me/cache/CraftingGridCache.class");
        assertRequiredHooks(methods);
        assertTrue(methods.contains(NativeCpuTargets.PLAN), "Pinned AE2 must exercise the legacy planning entry");
    }

    @Test
    void optionalPlanningSelectorsStayExactDuringPermissiveRemapping() throws IOException {
        Set<String> pinnedMethods = readMethods("/appeng/me/cache/CraftingGridCache.class");
        assertPlanningSelectors(pinnedMethods, NativeCpuTargets.PLAN);
        Set<String> liteMethods = new HashSet<>(pinnedMethods);
        liteMethods.remove(NativeCpuTargets.PLAN);
        liteMethods.add(NativeCpuTargets.PLAN_WITH_LITE_MODE);
        assertPlanningSelectors(liteMethods, NativeCpuTargets.PLAN_WITH_LITE_MODE);
    }

    @Test
    void requiredNativeHooksExistInInstalledCompatibilityJar() throws IOException {
        String path = System.getenv("NEOECOAE_AE2_COMPAT_JAR");
        assumeTrue(path != null && !path.isEmpty(), "Set NEOECOAE_AE2_COMPAT_JAR to validate an installed AE2 JAR");
        try (ZipFile jar = new ZipFile(path)) {
            var entry = jar.getEntry("appeng/me/cache/CraftingGridCache.class");
            assertNotNull(entry, "Compatibility JAR is missing CraftingGridCache");
            assertRequiredHooks(readMethods(jar.getInputStream(entry)));
        }
    }

    private static void assertRequiredHooks(Set<String> methods) {
        for (String descriptor : Arrays.asList(
            NativeCpuTargets.PUBLISH,
            NativeCpuTargets.UPDATE_TICK,
            NativeCpuTargets.ADD,
            NativeCpuTargets.REMOVE,
            NativeCpuTargets.SUBMIT))
            assertTrue(methods.contains(descriptor), "AE2 JAR does not contain required mixin target " + descriptor);
        long planningEntries = Arrays.asList(NativeCpuTargets.PLAN, NativeCpuTargets.PLAN_WITH_LITE_MODE)
            .stream()
            .filter(methods::contains)
            .count();
        assertEquals(1, planningEntries, "Exactly one supported planning entry must match the injection group");
    }

    private static void assertPlanningSelectors(Set<String> methods, String expectedTarget) throws IOException {
        ClassNode mixin = new ClassNode();
        try (InputStream stream = NativeCpuTargetsContractTest.class
            .getResourceAsStream("/cn/dancingsnow/neoecoae/platform/mixin/CraftingCpuPublicationMixin.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(mixin, ClassReader.SKIP_CODE);
        }
        int handlers = 0;
        Set<String> matched = new HashSet<>();
        for (MethodNode method : mixin.methods) {
            if (!method.name.equals("neoecoae$plan") && !method.name.equals("neoecoae$planWithLiteMode")) continue;
            handlers++;
            AnnotationNode inject = Annotations.getVisible(method, Inject.class);
            assertNotNull(inject);
            List<AnnotationNode> descriptors = Annotations.getValue(inject, "target");
            assertNotNull(descriptors, "Optional planning hooks must use strict @Desc selectors");
            assertEquals(1, descriptors.size());
            ITargetSelector selector = DynamicSelectorDesc.parse(Annotations.handleOf(descriptors.get(0)), null)
                .configure(ITargetSelector.Configure.SELECT_MEMBER);
            ITargetSelector permissive = selector.configure(ITargetSelector.Configure.PERMISSIVE);
            assertSame(selector, permissive, "Development remapping must not discard the overload descriptor");
            for (String candidate : methods) {
                int args = candidate.indexOf('(');
                if (((ITargetSelectorByName) permissive)
                    .matches(
                        "appeng/me/cache/CraftingGridCache",
                        candidate.substring(0, args),
                        candidate.substring(args))
                    .isExactMatch()) assertTrue(matched.add(candidate), "Only one planning handler may match");
            }
        }
        assertEquals(2, handlers);
        assertEquals(java.util.Collections.singleton(expectedTarget), matched);
    }

    private static Set<String> readMethods(String resource) throws IOException {
        InputStream stream = NativeCpuTargetsContractTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing pinned native dependency");
        return readMethods(stream);
    }

    private static Set<String> readMethods(InputStream stream) throws IOException {
        try (DataInputStream input = new DataInputStream(stream)) {
            assertEquals(0xcafebabe, input.readInt());
            input.readUnsignedShort();
            input.readUnsignedShort();
            int size = input.readUnsignedShort();
            String[] utf8 = new String[size];
            for (int i = 1; i < size; i++) switch (input.readUnsignedByte()) {
                case 1:
                    utf8[i] = input.readUTF();
                    break;
                case 3:
                case 4:
                    input.readInt();
                    break;
                case 5:
                case 6:
                    input.readLong();
                    i++;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    input.readUnsignedShort();
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    input.readInt();
                    break;
                case 15:
                    input.readUnsignedByte();
                    input.readUnsignedShort();
                    break;
                default:
                    throw new IOException("Unknown class constant");
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            input.readUnsignedShort();
            int interfaces = input.readUnsignedShort();
            for (int i = 0; i < interfaces; i++) input.readUnsignedShort();
            int fields = input.readUnsignedShort();
            for (int i = 0; i < fields; i++) {
                input.readUnsignedShort();
                input.readUnsignedShort();
                input.readUnsignedShort();
                skipAttributes(input);
            }
            Set<String> methods = new HashSet<>();
            int count = input.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                input.readUnsignedShort();
                String name = utf8[input.readUnsignedShort()], descriptor = utf8[input.readUnsignedShort()];
                methods.add(name + descriptor);
                skipAttributes(input);
            }
            return methods;
        }
    }

    private static void skipAttributes(DataInputStream input) throws IOException {
        int attrs = input.readUnsignedShort();
        for (int i = 0; i < attrs; i++) {
            input.readUnsignedShort();
            int remaining = input.readInt();
            while (remaining > 0) {
                int skipped = input.skipBytes(remaining);
                if (skipped == 0) throw new EOFException();
                remaining -= skipped;
            }
        }
    }
}
