package cn.dancingsnow.neoecoae.compat.nei;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.dancingsnow.neoecoae.NeoECOAE;
import codechicken.nei.api.API;
import codechicken.nei.api.IOverlayHandler;

/**
 * Lets NotEnoughEnergistics fill AE2 pattern terminals from workstation recipes. AE2 rv3 itself only transfers
 * crafting-grid recipes, and NEE packs a processing pattern only through a processor that claims the recipe's
 * overlay identifier. NEE is not a compile dependency, so its processor interface is implemented by a proxy.
 */
final class NEEWorkstationBridge {

    private static final String PROCESSOR_ID = "Neo ECO AE Extension";

    private NEEWorkstationBridge() {}

    @SuppressWarnings("unchecked")
    static void install(List<String> identifiers) {
        try {
            ClassLoader loader = NEEWorkstationBridge.class.getClassLoader();
            Class<?> processorType = Class.forName("com.github.vfyjxf.nee.processor.IRecipeProcessor", true, loader);
            var processors = (List<Object>) Class
                .forName("com.github.vfyjxf.nee.processor.RecipeProcessor", true, loader)
                .getField("recipeProcessors")
                .get(null);
            Set<String> claimed = Collections.unmodifiableSet(new LinkedHashSet<>(identifiers));
            boolean present = false;
            for (Object processor : processors) present |= PROCESSOR_ID.equals(
                processorType.getMethod("getRecipeProcessorId")
                    .invoke(processor));
            if (!present) processors.add(
                Proxy.newProxyInstance(
                    loader,
                    new Class<?>[] { processorType },
                    (proxy, method, args) -> processor(claimed, proxy, method.getName(), args)));
            var overlay = (IOverlayHandler) Class
                .forName("com.github.vfyjxf.nee.nei.NEEPatternTerminalHandler", true, loader)
                .getField("instance")
                .get(null);
            // NEE collects identifiers once in its own loadConfig; NEI plugin order is unspecified, so register here.
            for (String terminal : new String[] { "appeng.client.gui.implementations.GuiPatternTerm",
                "appeng.client.gui.implementations.GuiPatternTermEx" }) {
                Class<?> gui;
                try {
                    gui = Class.forName(terminal, false, loader);
                } catch (ClassNotFoundException absent) {
                    continue;
                }
                for (String identifier : claimed) {
                    API.registerGuiOverlay(
                        gui.asSubclass(net.minecraft.client.gui.inventory.GuiContainer.class),
                        identifier);
                    API.registerGuiOverlayHandler(
                        gui.asSubclass(net.minecraft.client.gui.inventory.GuiContainer.class),
                        overlay,
                        identifier);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException failure) {
            NeoECOAE.LOG.warn("NotEnoughEnergistics pattern transfer is unavailable for workstation recipes", failure);
        }
    }

    private static Object processor(Set<String> claimed, Object proxy, String method, Object[] args) {
        switch (method) {
            case "getAllOverlayIdentifier":
                return claimed;
            case "getRecipeProcessorId":
                return PROCESSOR_ID;
            case "getRecipeInput":
            case "getRecipeOutput":
                if (!(args[0] instanceof WorkstationRecipeHandler handler) || !claimed.contains(args[2]))
                    return Collections.emptyList();
                return handler.patternStacks((Integer) args[1], method.equals("getRecipeOutput"));
            case "mergeStacks":
                return true;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return PROCESSOR_ID;
            default:
                throw new UnsupportedOperationException(method);
        }
    }
}
