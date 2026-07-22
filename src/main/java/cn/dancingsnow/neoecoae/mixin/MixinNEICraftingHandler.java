package cn.dancingsnow.neoecoae.mixin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.integration.modules.NEIHelpers.NEICraftingHandler;
import cn.dancingsnow.neoecoae.crafting.upload.PatternCircuitCompat;
import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.common.items.ItemIntegratedCircuit;

/** Captures the GT recipe map while NEI is filling an AE2 pattern terminal. */
@Mixin(value = NEICraftingHandler.class, remap = false)
public abstract class MixinNEICraftingHandler {

    @Inject(method = "overlayRecipe", at = @At("HEAD"))
    private void neoecoae$captureRecipeRoute(GuiContainer gui, IRecipeHandler handler, int recipeIndex, boolean shift,
        CallbackInfo ci) {
        if (!(gui instanceof GuiPatternTerm)) return;
        if (handler == null) {
            NEPatternUploadNetwork.clearRouteContext();
            return;
        }

        // Keep this reflection-based so the client mixin remains loadable with other NEI recipe handlers.
        Class<?> type = handler.getClass();
        boolean gregTechHandler = false;
        while (type != null) {
            if ("gregtech.nei.GTNEIDefaultHandler".equals(type.getName())) {
                gregTechHandler = true;
                break;
            }
            type = type.getSuperclass();
        }
        if (!gregTechHandler) {
            NEPatternUploadNetwork.clearRouteContext();
            return;
        }

        try {
            Method getRecipeMap = handler.getClass()
                .getMethod("getRecipeMap");
            Object recipeMap = getRecipeMap.invoke(handler);
            if (recipeMap == null) {
                NEPatternUploadNetwork.clearRouteContext();
                return;
            }
            Field name = recipeMap.getClass()
                .getField("unlocalizedName");
            Object value = name.get(recipeMap);
            if (value instanceof String && !((String) value).trim()
                .isEmpty()) {
                NEPatternUploadNetwork.requestRouteContext((String) value, findVirtualCircuit(handler, recipeIndex));
            } else {
                NEPatternUploadNetwork.clearRouteContext();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Other GT/NEI builds may hide the implementation detail; matching then falls back to declarations.
            NEPatternUploadNetwork.clearRouteContext();
        }
    }

    /**
     * GT's NEI overlay intentionally removes non-consumable virtual circuits from the ingredient
     * list sent to AE2. The original GTRecipe still carries the circuit, so capture it as route
     * metadata before AE2 packs the reduced ingredient list.
     */
    private static ItemStack findVirtualCircuit(IRecipeHandler handler, int recipeIndex) {
        // overlayRecipe indexes the currently loaded `arecipes` list. GT's getCache() is a
        // separate global/category cache and can have a different order after tier filtering.
        if (handler instanceof TemplateRecipeHandler) {
            try {
                TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
                if (template.arecipes != null && recipeIndex >= 0 && recipeIndex < template.arecipes.size()) {
                    ItemStack circuit = findCircuitInRecipeObject(template.arecipes.get(recipeIndex));
                    if (circuit != null) return circuit;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the reflection/cache paths used by older NEI builds.
            }
        }
        try {
            Method getCache = handler.getClass()
                .getMethod("getCache");
            Object cache = getCache.invoke(handler);
            if (!(cache instanceof List) || recipeIndex < 0 || recipeIndex >= ((List<?>) cache).size()) return null;
            Object cachedRecipe = ((List<?>) cache).get(recipeIndex);
            ItemStack circuit = findCircuitInRecipeObject(cachedRecipe);
            if (circuit != null) return circuit;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Other GT/NEI builds may expose a different cache shape. Keep the route map hint and
            // let the normal pattern-input path handle versions that do not expose the raw recipe.
        }
        try {
            List<PositionedStack> inputs = handler.getIngredientStacks(recipeIndex);
            if (inputs != null) {
                for (PositionedStack positioned : inputs) {
                    if (positioned == null || positioned.items == null) continue;
                    for (ItemStack input : positioned.items) {
                        if (isVirtualCircuit(input)) return input.copy();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A recipe handler may rebuild its page while the click is being processed.
        }
        return null;
    }

    private static ItemStack findCircuitInRecipeObject(Object cachedRecipe) {
        if (cachedRecipe == null) return null;
        try {
            Field recipeField = findField(cachedRecipe.getClass(), "mRecipe");
            Object recipe = recipeField == null ? cachedRecipe : recipeField.get(cachedRecipe);
            if (recipe == null) return null;
            Field inputsField = findField(recipe.getClass(), "mInputs");
            Object inputs = inputsField == null ? null : inputsField.get(recipe);
            return findCircuitInValue(inputs);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static ItemStack findCircuitInValue(Object value) {
        if (value == null) return null;
        if (value instanceof ItemStack) {
            return isVirtualCircuit((ItemStack) value) ? ((ItemStack) value).copy() : null;
        }
        if (value instanceof Iterable) {
            for (Object entry : (Iterable<?>) value) {
                ItemStack circuit = findCircuitInValue(entry);
                if (circuit != null) return circuit;
            }
            return null;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                ItemStack circuit = findCircuitInValue(Array.get(value, i));
                if (circuit != null) return circuit;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isVirtualCircuit(ItemStack stack) {
        if (PatternCircuitCompat.isVirtualCircuit(stack)) return true;
        if (stack == null || !(stack.getItem() instanceof ItemIntegratedCircuit)) return false;
        int damage = stack.getItemDamage();
        return damage >= 0 && damage <= ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER;
    }
}
