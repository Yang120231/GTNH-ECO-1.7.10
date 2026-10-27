package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeCpuTargets;

/** Verify actual pinned dependency bytecode without loading its game-only static initialization. */
class NativeCpuTargetsContractTest {

    @Test
    void requiredNativeHooksExistInPinnedJar() throws IOException {
        Set<String> methods = readMethods("/appeng/me/cache/CraftingGridCache.class");
        for (String descriptor : Arrays.asList(
            NativeCpuTargets.PUBLISH,
            NativeCpuTargets.ADD,
            NativeCpuTargets.REMOVE,
            NativeCpuTargets.PLAN,
            NativeCpuTargets.SUBMIT))
            assertTrue(
                methods.contains(descriptor),
                "Pinned AE2 jar does not contain required mixin target " + descriptor);
    }

    private static Set<String> readMethods(String resource) throws IOException {
        InputStream stream = NativeCpuTargetsContractTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing pinned native dependency");
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
