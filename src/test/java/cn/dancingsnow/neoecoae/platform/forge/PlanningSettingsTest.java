package cn.dancingsnow.neoecoae.platform.forge;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The crafting and computation length limits are read from the multiblock category like the storage one. */
class PlanningSettingsTest {

    @Test
    void controllerLengthsAreReadFromTheMultiblockCategory(@TempDir Path directory) throws Exception {
        int storage = PlanningSettings.storageSystemMaxLength, crafting = PlanningSettings.craftingSystemMaxLength,
            computation = PlanningSettings.computationSystemMaxLength;
        java.lang.reflect.Field home = null;
        Object previousHome = null;
        for (var field : cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredFields())
            if (field.getType() == File.class && java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                home = field;
        assertNotNull(home, "FML minecraft home");
        home.setAccessible(true);
        previousHome = home.get(null);
        // Forge's Configuration resolves paths against the game directory, which a unit test has not set up.
        if (previousHome == null) home.set(null, directory.toFile());
        try {
            File file = directory.resolve("neoecoae.cfg")
                .toFile();
            PlanningSettings.load(file);
            assertEquals(15, PlanningSettings.craftingSystemMaxLength);
            assertEquals(15, PlanningSettings.computationSystemMaxLength);
            String written = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertTrue(written.contains("I:craftingSystemMaxLength=15"), written);
            assertTrue(written.contains("I:computationSystemMaxLength=15"), written);
            Files.write(
                file.toPath(),
                written.replace("I:craftingSystemMaxLength=15", "I:craftingSystemMaxLength=21")
                    .replace("I:computationSystemMaxLength=15", "I:computationSystemMaxLength=9")
                    .getBytes(StandardCharsets.UTF_8));
            PlanningSettings.load(file);
            assertEquals(21, PlanningSettings.craftingSystemMaxLength);
            assertEquals(9, PlanningSettings.computationSystemMaxLength);
        } finally {
            home.set(null, previousHome);
            PlanningSettings.storageSystemMaxLength = storage;
            PlanningSettings.craftingSystemMaxLength = crafting;
            PlanningSettings.computationSystemMaxLength = computation;
        }
    }
}
