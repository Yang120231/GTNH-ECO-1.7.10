package cn.dancingsnow.neoecoae.platform.gt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

class NativeGregTechLoadingTest {

    @Test
    void optionalEntryPointAndPublicSignaturesLoadWithEveryGregTechClassAbsent() throws Exception {
        String entryName = NativeGregTech.class.getName();
        ClassLoader absentGt = new ClassLoader(getClass().getClassLoader()) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("gregtech.") || name.startsWith("cn.dancingsnow.neoecoae.platform.gt.GregTech"))
                    throw new ClassNotFoundException("GT intentionally absent: " + name);
                if (!name.startsWith(entryName)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> found = findLoadedClass(name);
                    if (found == null) {
                        String resource = name.replace('.', '/') + ".class";
                        try (InputStream input = getParent().getResourceAsStream(resource)) {
                            if (input == null) throw new ClassNotFoundException(name);
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            byte[] buffer = new byte[4096];
                            int read;
                            while ((read = input.read(buffer)) >= 0) bytes.write(buffer, 0, read);
                            byte[] data = bytes.toByteArray();
                            found = defineClass(name, data, 0, data.length);
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    if (resolve) resolveClass(found);
                    return found;
                }
            }
        };
        Class<?> entry = Class.forName(entryName, true, absentGt);
        assertEquals(absentGt, entry.getClassLoader());
        assertNotNull(entry.getDeclaredMethod("initialize"));
        assertNotNull(entry.getDeclaredMethod("isAvailable"));
        assertNotNull(entry.getDeclaredMethod("inspect", net.minecraft.tileentity.TileEntity.class));
        assertNotNull(entry.getDeclaredMethod("recipeItem", String.class, int.class));
        assertNotNull(entry.getDeclaredMethod("recipeFluid", String.class));
        assertNotNull(
            Class.forName(entryName + "$MachineSnapshot", true, absentGt)
                .getDeclaredConstructors());
    }
}
