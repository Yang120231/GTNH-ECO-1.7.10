package cn.dancingsnow.neoecoae.platform.mixin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.integration.modules.NEIHelpers.NEICraftingHandler;
import cn.dancingsnow.neoecoae.platform.ui.upload.PatternUploadClient;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * Captures the GT recipe map (and the virtual circuit GT's NEI overlay strips) while NEI fills an AE2 pattern
 * terminal. Anything unreadable clears the hint; the server re-verifies every route regardless.
 */
@Mixin(value = NEICraftingHandler.class, remap = false)
public abstract class NeiPatternRouteMixin {

    @Inject(method = "overlayRecipe", at = @At("HEAD"), require = 1)
    private void neoecoae$captureRoute(GuiContainer gui, IRecipeHandler handler, int recipeIndex, boolean shift,
        CallbackInfo ci) {
        String map = null;
        ItemStack circuit = null;
        try {
            if (neoecoae$isGregTechHandler(handler)) {
                Object recipeMap = handler.getClass()
                    .getMethod("getRecipeMap")
                    .invoke(handler);
                Object name = recipeMap == null ? null
                    : recipeMap.getClass()
                        .getField("unlocalizedName")
                        .get(recipeMap);
                if (name instanceof String && !((String) name).trim()
                    .isEmpty()) {
                    map = (String) name;
                    circuit = neoecoae$circuit(handler, recipeIndex);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            map = null;
            circuit = null;
        }
        PatternUploadClient.captureHint(gui, map, circuit);
    }

    @Unique
    private static boolean neoecoae$isGregTechHandler(IRecipeHandler handler) {
        for (Class<?> type = handler == null ? null : handler.getClass(); type != null; type = type.getSuperclass())
            if ("gregtech.nei.GTNEIDefaultHandler".equals(type.getName())) return true;
        return false;
    }

    /** Uses the page's own {@code arecipes[recipeIndex]}; a global cache has a different order after filtering. */
    @Unique
    private static ItemStack neoecoae$circuit(IRecipeHandler handler, int recipeIndex) {
        if (handler instanceof TemplateRecipeHandler) {
            List<?> recipes = ((TemplateRecipeHandler) handler).arecipes;
            if (recipes != null && recipeIndex >= 0 && recipeIndex < recipes.size()) {
                Object recipe = recipes.get(recipeIndex);
                Object raw = neoecoae$field(recipe, "mRecipe");
                ItemStack circuit = neoecoae$find(neoecoae$field(raw == null ? recipe : raw, "mInputs"));
                if (circuit != null) return circuit;
            }
        }
        List<PositionedStack> inputs = handler.getIngredientStacks(recipeIndex);
        if (inputs != null) for (PositionedStack positioned : inputs) {
            if (positioned == null || positioned.items == null) continue;
            for (ItemStack stack : positioned.items) if (neoecoae$isCircuit(stack)) return stack.copy();
        }
        return null;
    }

    @Unique
    private static ItemStack neoecoae$find(Object value) {
        if (value instanceof ItemStack)
            return neoecoae$isCircuit((ItemStack) value) ? ((ItemStack) value).copy() : null;
        if (value instanceof Iterable) {
            for (Object entry : (Iterable<?>) value) {
                ItemStack circuit = neoecoae$find(entry);
                if (circuit != null) return circuit;
            }
        } else if (value != null && value.getClass()
            .isArray()) {
                for (int index = 0; index < Array.getLength(value); index++) {
                    ItemStack circuit = neoecoae$find(Array.get(value, index));
                    if (circuit != null) return circuit;
                }
            }
        return null;
    }

    @Unique
    private static boolean neoecoae$isCircuit(ItemStack stack) {
        return stack != null && stack.getItem() != null
            && "gregtech.common.items.ItemIntegratedCircuit".equals(
                stack.getItem()
                    .getClass()
                    .getName());
    }

    @Unique
    private static Object neoecoae$field(Object owner, String name) {
        for (Class<?> type = owner == null ? null : owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException inherited) {
                // Continue with the superclass.
            } catch (ReflectiveOperationException | RuntimeException unreadable) {
                return null;
            }
        }
        return null;
    }
}
