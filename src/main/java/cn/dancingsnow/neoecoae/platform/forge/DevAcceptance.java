package cn.dancingsnow.neoecoae.platform.forge;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * Entry into the in-game acceptance harnesses, which live in the separate "acceptance" source set and never ship in
 * the mod jar. Every hook is a no-op unless a {@code -Dneoecoae.*Acceptance} property is set and the development
 * run puts that source set on the classpath.
 */
public final class DevAcceptance {

    private static final String IMPLEMENTATION = "cn.dancingsnow.neoecoae.platform.forge.AcceptanceHooks";
    private static Hooks hooks = new Hooks() {};

    private DevAcceptance() {}

    /** The calls production code makes at the points where the harnesses used to be wired in directly. */
    public interface Hooks {

        default void preInit() {}

        default void clientInit() {}

        default void gregTechInit() {}

        default void serverStarting(FMLServerStartingEvent event) {}

        default void serverTick() {}

        default void recipesRegistered() {}

        default void worldTick(WorldServer world) {}

        default void worldUnload(World world) {}
    }

    public static Hooks hooks() {
        return hooks;
    }

    static void load() {
        if (!requested()) return;
        try {
            hooks = (Hooks) Class.forName(IMPLEMENTATION)
                .getConstructor()
                .newInstance();
        } catch (ClassNotFoundException missing) {
            NeoECOAE.LOG.warn("Acceptance was requested, but the acceptance source set is not on the classpath");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot start the acceptance harness", failure);
        }
    }

    private static boolean requested() {
        for (String name : System.getProperties()
            .stringPropertyNames())
            if (name.startsWith("neoecoae.") && name.contains("Acceptance") && Boolean.getBoolean(name)) return true;
        return false;
    }
}
