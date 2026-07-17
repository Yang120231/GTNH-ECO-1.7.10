package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.world.World;

final class ECOControllerRegistry {

    private static final WeakHashMap<World, Set<TileECOController>> CONTROLLERS = new WeakHashMap<World, Set<TileECOController>>();

    private ECOControllerRegistry() {}

    static void register(TileECOController controller) {
        if (controller == null || controller.getWorldObj() == null) {
            return;
        }
        Set<TileECOController> controllers = CONTROLLERS.get(controller.getWorldObj());
        if (controllers == null) {
            controllers = new LinkedHashSet<TileECOController>();
            CONTROLLERS.put(controller.getWorldObj(), controllers);
        }
        controllers.add(controller);
    }

    static void unregister(TileECOController controller) {
        if (controller == null || controller.getWorldObj() == null) {
            return;
        }
        Set<TileECOController> controllers = CONTROLLERS.get(controller.getWorldObj());
        if (controllers == null) {
            return;
        }
        controllers.remove(controller);
        if (controllers.isEmpty()) {
            CONTROLLERS.remove(controller.getWorldObj());
        }
    }

    static List<TileECOController> controllers(World world) {
        Set<TileECOController> controllers = CONTROLLERS.get(world);
        if (controllers == null || controllers.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<TileECOController>(controllers);
    }

    static boolean isDomainBound(World world, UUID domainId, TileECOController except) {
        if (world == null || domainId == null) {
            return false;
        }
        for (TileECOController controller : controllers(world)) {
            if (controller == except || !controller.isFormed() || !controller.canUseHostDomainStorage()) {
                continue;
            }
            if (domainId.equals(controller.getHostDomainId())) {
                return true;
            }
        }
        return false;
    }
}
