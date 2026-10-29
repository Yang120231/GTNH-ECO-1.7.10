package cn.dancingsnow.neoecoae.platform.forge;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.launchwrapper.Launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** GTNHLib reads the categories and keys written by the previous hand-written Forge configuration. */
class PlanningSettingsTest {

    @Test
    void existingConfigFilesKeepTheirValues(@TempDir Path directory) throws Exception {
        int storage = PlanningSettings.Multiblock.storageSystemMaxLength,
            crafting = PlanningSettings.Multiblock.craftingSystemMaxLength,
            computation = PlanningSettings.Multiblock.computationSystemMaxLength;
        boolean temperature = PlanningSettings.Workstation.largeWorkstationTemperatureEnabled;
        int uploadX = PlanningSettings.PatternUpload.patternUploadButtonOffsetX;
        java.lang.reflect.Field home = null;
        for (var field : cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredFields())
            if (field.getType() == File.class && java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                home = field;
        assertNotNull(home, "FML minecraft home");
        home.setAccessible(true);
        Object previousHome = home.get(null);
        File previousLaunchHome = Launch.minecraftHome;
        // Forge's Configuration resolves paths against the game directory, which a unit test has not set up.
        home.set(null, directory.toFile());
        Launch.minecraftHome = directory.toFile();
        try {
            Path file = directory.resolve("config")
                .resolve("neoecoae.cfg");
            Files.createDirectories(file.getParent());
            Files.write(
                file,
                String
                    .join(
                        "\n",
                        "multiblock {",
                        "    I:computationSystemMaxLength=9",
                        "    I:craftingSystemMaxLength=21",
                        "}",
                        "",
                        "pattern_upload {",
                        "    I:patternUploadButtonOffsetX=-7",
                        "}",
                        "",
                        "workstation {",
                        "    B:largeWorkstationTemperatureEnabled=false",
                        "}",
                        "")
                    .getBytes(StandardCharsets.UTF_8));
            PlanningSettings.register();
            assertEquals(21, PlanningSettings.Multiblock.craftingSystemMaxLength);
            assertEquals(9, PlanningSettings.Multiblock.computationSystemMaxLength);
            assertEquals(15, PlanningSettings.Multiblock.storageSystemMaxLength, "Missing keys take their default");
            assertEquals(-7, PlanningSettings.PatternUpload.patternUploadButtonOffsetX);
            assertFalse(PlanningSettings.Workstation.largeWorkstationTemperatureEnabled);
            assertEquals(200_000, PlanningSettings.Crafting.ecoCpuPushTickLimit);
            String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertTrue(written.contains("I:craftingSystemMaxLength=21"), written);
            assertTrue(written.contains("I:storageSystemMaxLength=15"), written);
            assertTrue(written.contains("B:incrementalNetworkStorageList=true"), written);
        } finally {
            home.set(null, previousHome);
            Launch.minecraftHome = previousLaunchHome;
            PlanningSettings.Multiblock.storageSystemMaxLength = storage;
            PlanningSettings.Multiblock.craftingSystemMaxLength = crafting;
            PlanningSettings.Multiblock.computationSystemMaxLength = computation;
            PlanningSettings.Workstation.largeWorkstationTemperatureEnabled = temperature;
            PlanningSettings.PatternUpload.patternUploadButtonOffsetX = uploadX;
            PlanningSettings.Crafting.ecoCpuPushTickLimit = PlanningSettings.MAX_ECO_CPU_PUSH_TICK_LIMIT;
        }
    }
}
