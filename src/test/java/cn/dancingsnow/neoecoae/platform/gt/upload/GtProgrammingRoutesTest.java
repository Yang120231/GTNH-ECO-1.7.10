package cn.dancingsnow.neoecoae.platform.gt.upload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;

class GtProgrammingRoutesTest {

    @Test
    void aCoveredInputRouteSurvivesAnEarlierIdenticalUncoveredRouteInEitherOrder() throws Exception {
        for (boolean coveredFirst : new boolean[] { false, true }) {
            List<Object> routes = new ArrayList<>();
            add(routes, coveredFirst, false);
            add(routes, !coveredFirst, false);
            add(routes, coveredFirst, false);
            assertEquals(2, routes.size(), "Deduplicate only routes with the same programming capability");
            for (Object route : routes) {
                var cover = route.getClass()
                    .getDeclaredField("programmingCover");
                cover.setAccessible(true);
                boolean canProgram = cover.getBoolean(route);
                Object result = evaluate(route, canProgram);
                var level = result.getClass()
                    .getDeclaredField("level");
                level.setAccessible(true);
                assertEquals(
                    canProgram ? MatchResult.Level.EXACT : MatchResult.Level.CIRCUIT_CONFLICT,
                    level.get(result));
            }
        }
    }

    @Test
    void builtInProgrammingInputsAreAlsoDistinctFromPlainInputs() throws Exception {
        List<Object> routes = new ArrayList<>();
        add(routes, false, false);
        add(routes, false, true);
        assertEquals(2, routes.size());
    }

    private static void add(List<Object> routes, boolean cover, boolean programmable) throws Exception {
        Method add = GtPatternRoutes.class.getDeclaredMethod(
            "add",
            List.class,
            Class.forName("gregtech.api.recipe.RecipeMap"),
            ItemStack.class,
            ItemStack.class,
            String.class,
            boolean.class,
            boolean.class);
        add.setAccessible(true);
        add.invoke(null, routes, null, null, null, "mixer", cover, programmable);
    }

    private static Object evaluate(Object route, boolean canProgram) throws Exception {
        Class<?> cacheType = Class.forName(GtRecipeVerifier.class.getName() + "$Cache");
        Object cache = mock(cacheType);
        var verify = cacheType.getDeclaredMethod(
            "verify",
            Class.forName("gregtech.api.recipe.RecipeMap"),
            appeng.api.networking.crafting.ICraftingPatternDetails.class,
            ItemStack.class);
        verify.setAccessible(true);
        verify.invoke(doReturn(GtRecipeVerifier.Verdict.EXACT).when(cache), null, null, null);
        var evaluate = GtPatternRoutes.class.getDeclaredMethod(
            "evaluate",
            cacheType,
            route.getClass(),
            PatternContext.class,
            String.class,
            ItemStack.class,
            ItemStack.class,
            boolean.class,
            boolean.class);
        evaluate.setAccessible(true);
        // Recipe validation is independently successful; only the route's programming capability differs.
        return evaluate.invoke(
            null,
            cache,
            route,
            mock(PatternContext.class),
            null,
            null,
            new ItemStack(new net.minecraft.item.Item(), 1, 2),
            true,
            canProgram);
    }
}
