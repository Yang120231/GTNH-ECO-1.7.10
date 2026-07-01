package cn.dancingsnow.neoecoae;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static String greeting = "Hello World";
    public static boolean enableEcoCraftingFastPath = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        greeting = configuration.getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "How shall I greet?");
        enableEcoCraftingFastPath = configuration.getBoolean(
            "enableEcoCraftingFastPath",
            Configuration.CATEGORY_GENERAL,
            enableEcoCraftingFastPath,
            "Enable ECO crafting planner inspection and caching before accepted work is queued.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
