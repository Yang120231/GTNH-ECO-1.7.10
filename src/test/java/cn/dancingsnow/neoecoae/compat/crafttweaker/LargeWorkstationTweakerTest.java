package cn.dancingsnow.neoecoae.compat.crafttweaker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import minetweaker.api.item.IIngredient;
import minetweaker.api.item.IngredientStack;
import minetweaker.mc1710.item.MCItemStack;
import minetweaker.mc1710.liquid.MCLiquidStack;

public class LargeWorkstationTweakerTest {

    public static final minetweaker.api.item.IItemStack SCRIPT_IRON = item(TestItems.IRON_INGOT, 1);
    public static final minetweaker.api.item.IItemStack SCRIPT_DIAMOND = item(TestItems.DIAMOND, 1);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void actualZenScriptCompilerExecutesOptionalArgumentsAndReloadsWithoutDuplicates() {
        var logger = mock(minetweaker.runtime.ILogger.class);
        minetweaker.MineTweakerImplementationAPI.logger.addLogger(logger);
        var original = LargeWorkstationRecipes.getAll(null);
        try {
            LargeWorkstationTweaker.register();
            minetweaker.runtime.GlobalRegistry.registerGlobal(
                "ecoTestIron",
                minetweaker.runtime.GlobalRegistry.getStaticField(LargeWorkstationTweakerTest.class, "SCRIPT_IRON"));
            minetweaker.runtime.GlobalRegistry.registerGlobal(
                "ecoTestDiamond",
                minetweaker.runtime.GlobalRegistry.getStaticField(LargeWorkstationTweakerTest.class, "SCRIPT_DIAMOND"));
            var scripts = new minetweaker.runtime.providers.ScriptProviderCustom("eco_integration");
            scripts.add(
                "large_workstation.zs",
                "import mods.neoecoae.LargeIntegratedWorkingStation;\n"
                    + "LargeIntegratedWorkingStation.addRecipe(\"test:compiled\", ecoTestDiamond * 2, [ecoTestIron * 4], 42000, 37);\n"
                    + "LargeIntegratedWorkingStation.addRecipe(\"test:explicit_null\", ecoTestDiamond, [ecoTestIron], 100, 20, null, null);\n"
                    + "LargeIntegratedWorkingStation.removeRecipe(\"test:explicit_null\");\n");
            minetweaker.MineTweakerAPI.tweaker.setScriptProvider(scripts);
            for (int reload = 0; reload < 2; reload++) {
                minetweaker.MineTweakerAPI.tweaker.load();
                var compiled = LargeWorkstationRecipes.getById(null, ResourceLocation.parse("test:compiled"));
                assertNotNull(compiled);
                assertEquals(
                    1,
                    compiled.matchingMultiplier(items(TestItems.IRON_INGOT, 4), items(TestItems.DIAMOND, 2)));
                assertNull(LargeWorkstationRecipes.getById(null, ResourceLocation.parse("test:explicit_null")));
                assertEquals(
                    original.size() + 1,
                    LargeWorkstationRecipes.getAll(null)
                        .size());
                assertTrue(
                    minetweaker.MineTweakerAPI.tweaker.rollback()
                        .isEmpty());
                assertEquals(original, LargeWorkstationRecipes.getAll(null));
            }
            verify(logger, never()).logError(anyString(), any());
        } finally {
            minetweaker.MineTweakerAPI.tweaker.rollback();
            minetweaker.MineTweakerImplementationAPI.logger.removeLogger(logger);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void inheritedRecipeRemovalAffectsOnlyTheLargeWorkstationAndIsReversible() throws Exception {
        var field = WorkstationRecipes.class.getDeclaredField("RECIPES");
        field.setAccessible(true);
        var nativeRecipes = (java.util.Map<ResourceLocation, cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe>) field
            .get(null);
        var recipe = simpleRecipe("test:inherited", TestItems.DIAMOND);
        nativeRecipes.put(recipe.id(), recipe.display());
        try {
            assertNotNull(LargeWorkstationRecipes.getById(null, recipe.id()));
            Runnable undo = LargeWorkstationRecipes.removeScriptRecipes(
                entry -> entry.id()
                    .equals(recipe.id()));
            try {
                assertNull(LargeWorkstationRecipes.getById(null, recipe.id()));
                assertSame(recipe.display(), WorkstationRecipes.getById(recipe.id()));
            } finally {
                undo.run();
            }
            assertNotNull(LargeWorkstationRecipes.getById(null, recipe.id()));
        } finally {
            nativeRecipes.remove(recipe.id());
        }
    }

    @Test
    void sizedScriptIngredientsAndFluidOutputsMatchScaledProcessingPatterns() {
        IIngredient ingredient = new IngredientStack(new MCItemStack(new ItemStack(TestItems.IRON_INGOT)), 4);
        var recipe = LargeWorkstationTweaker.recipe(
            "test:script_scaled",
            item(TestItems.DIAMOND, 2),
            new IIngredient[] { ingredient },
            42000,
            37,
            new MCLiquidStack(fluid(TestFluids.WATER, 100)),
            new MCLiquidStack(fluid(TestFluids.LAVA, 50)));
        var inputs = items(TestItems.IRON_INGOT, 12);
        inputs.add(AEFluidKey.of(TestFluids.WATER), 300);
        var outputs = items(TestItems.DIAMOND, 6);
        outputs.add(AEFluidKey.of(TestFluids.LAVA), 150);
        assertEquals(3, recipe.matchingMultiplier(inputs, outputs));
        assertEquals(42000, recipe.energy());
        assertEquals(37, recipe.processingTicks());
        inputs.add(AEItemKey.of(TestItems.IRON_INGOT), 1);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
    }

    @Test
    void conditionsKeepNbtAndProbeTheConfiguredBaseAmount() {
        var tagged = new ItemStack(TestItems.IRON_INGOT, 4);
        tagged.setStackDisplayName("Required");
        IIngredient input = new MCItemStack(tagged).only(stack -> {
            ItemStack nativeStack = (ItemStack) stack.getInternal();
            return nativeStack.stackSize == 4 && ItemStack.areItemStackTagsEqual(nativeStack, tagged);
        });
        var recipe = LargeWorkstationTweaker
            .recipe("test:script_nbt", item(TestItems.DIAMOND, 1), new IIngredient[] { input }, 100, 10, null, null);
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(tagged), 8);
        assertEquals(2, recipe.matchingMultiplier(inputs, items(TestItems.DIAMOND, 2)));
        assertEquals(0, recipe.matchingMultiplier(items(TestItems.IRON_INGOT, 8), items(TestItems.DIAMOND, 2)));
        assertEquals(
            4,
            recipe.display()
                .inputItems()
                .get(0)
                .count());
    }

    @Test
    void twelveInputsAndFluidOnlyRecipesAreAccepted() {
        IIngredient[] inputs = new IIngredient[12];
        Arrays.fill(inputs, item(TestItems.IRON_INGOT, 1));
        var recipe = LargeWorkstationTweaker
            .recipe("test:twelve", item(TestItems.DIAMOND, 1), inputs, 0, 200, null, null);
        assertEquals(1, recipe.matchingMultiplier(items(TestItems.IRON_INGOT, 12), items(TestItems.DIAMOND, 1)));
        var fluid = LargeWorkstationTweaker.recipe(
            "test:fluid",
            null,
            new IIngredient[0],
            10,
            1,
            new MCLiquidStack(fluid(TestFluids.WATER, 100)),
            new MCLiquidStack(fluid(TestFluids.LAVA, 50)));
        assertFalse(
            fluid.display()
                .hasItemOutput());
        assertEquals(
            50,
            fluid.display()
                .fluidOutput().amount);
    }

    @Test
    void scriptEditsInvalidatePatternCatalogEvenWhenRecipeCountStaysEqualAndUndoInReverse() {
        var original = LargeWorkstationRecipes.getAll(null);
        var nativeRecipes = WorkstationRecipes.getAll();
        var first = simpleRecipe("test:same_id", TestItems.DIAMOND);
        var add = new LargeWorkstationTweaker.RecipeAction("add", () -> LargeWorkstationRecipes.addScriptRecipe(first));
        add.apply();
        var added = LargeWorkstationRecipes.getAll(null);
        var remove = new LargeWorkstationTweaker.RecipeAction(
            "remove",
            () -> LargeWorkstationRecipes.removeScriptRecipes(
                recipe -> recipe.id()
                    .equals(first.id())));
        remove.apply();
        var second = simpleRecipe("test:same_id", TestItems.GOLD_INGOT);
        var replace = new LargeWorkstationTweaker.RecipeAction(
            "replace",
            () -> LargeWorkstationRecipes.addScriptRecipe(second));
        replace.apply();
        try {
            var replaced = LargeWorkstationRecipes.getAll(null);
            assertEquals(added.size(), replaced.size());
            assertNotSame(added, replaced);
            assertSame(second, LargeWorkstationRecipes.getById(null, first.id()));
            assertEquals(nativeRecipes, WorkstationRecipes.getAll());
            assertNull(WorkstationRecipes.getById(first.id()));
            assertThrows(IllegalArgumentException.class, () -> LargeWorkstationRecipes.addScriptRecipe(second));
        } finally {
            replace.undo();
            remove.undo();
            assertSame(first, LargeWorkstationRecipes.getById(null, first.id()));
            add.undo();
        }
        assertEquals(original, LargeWorkstationRecipes.getAll(null));
        assertTrue(add.canUndo());
        assertNull(add.getOverrideKey());
        add.apply();
        add.undo();
        assertEquals(original, LargeWorkstationRecipes.getAll(null));
    }

    @Test
    void removeAllAndUndoRestoreExistingScriptEntries() {
        var recipe = simpleRecipe("test:remove_all", TestItems.DIAMOND);
        Runnable undoAdd = LargeWorkstationRecipes.addScriptRecipe(recipe);
        try {
            var catalog = LargeWorkstationRecipes.getAll(null);
            Runnable undoRemove = LargeWorkstationRecipes.removeScriptRecipes(entry -> true);
            try {
                assertTrue(
                    LargeWorkstationRecipes.getAll(null)
                        .isEmpty());
            } finally {
                undoRemove.run();
            }
            assertEquals(catalog, LargeWorkstationRecipes.getAll(null));
        } finally {
            undoAdd.run();
        }
    }

    @Test
    void invalidContractsFailBeforeChangingTheCatalog() {
        var original = LargeWorkstationRecipes.getAll(null);
        assertInvalid("missing_namespace", 1, 10, new IIngredient[] { item(TestItems.IRON_INGOT, 1) });
        assertInvalid("test:negative_energy", -1, 10, new IIngredient[] { item(TestItems.IRON_INGOT, 1) });
        assertInvalid("test:time", 1, 201, new IIngredient[] { item(TestItems.IRON_INGOT, 1) });
        assertInvalid("test:zero_time", 1, 0, new IIngredient[] { item(TestItems.IRON_INGOT, 1) });
        assertInvalid("test:too_many", 1, 10, new IIngredient[13]);
        assertInvalid("test:null_input", 1, 10, new IIngredient[] { null });
        assertInvalid("test:zero_input", 1, 10, new IIngredient[] { item(TestItems.IRON_INGOT, 0) });
        assertInvalid("test:empty", 1, 10, new IIngredient[0]);
        assertThrows(
            IllegalArgumentException.class,
            () -> LargeWorkstationTweaker
                .recipe("test:no_output", null, new IIngredient[] { item(TestItems.IRON_INGOT, 1) }, 1, 1, null, null));
        var fluid = fluid(TestFluids.WATER, 100);
        fluid.tag = new NBTTagCompound();
        assertThrows(
            IllegalArgumentException.class,
            () -> LargeWorkstationTweaker.recipe(
                "test:fluid_nbt",
                item(TestItems.DIAMOND, 1),
                new IIngredient[0],
                1,
                1,
                new MCLiquidStack(fluid),
                null));
        assertSame(original, LargeWorkstationRecipes.getAll(null));
    }

    private static void assertInvalid(String id, int energy, int ticks, IIngredient[] inputs) {
        assertThrows(
            IllegalArgumentException.class,
            () -> LargeWorkstationTweaker.recipe(id, item(TestItems.DIAMOND, 1), inputs, energy, ticks, null, null));
    }

    private static LargeWorkstationRecipe simpleRecipe(String id, net.minecraft.item.Item output) {
        return LargeWorkstationTweaker
            .recipe(id, item(output, 1), new IIngredient[] { item(TestItems.IRON_INGOT, 4) }, 100, 10, null, null);
    }

    private static MCItemStack item(net.minecraft.item.Item item, int amount) {
        return new MCItemStack(new ItemStack(item, amount));
    }

    private static FluidStack fluid(net.minecraftforge.fluids.Fluid fluid, int amount) {
        var stack = mock(FluidStack.class);
        stack.amount = amount;
        when(stack.getFluid()).thenReturn(fluid);
        when(stack.copy()).thenAnswer(invocation -> {
            FluidStack copy = fluid(fluid, stack.amount);
            copy.tag = stack.tag == null ? null : (NBTTagCompound) stack.tag.copy();
            return copy;
        });
        return stack;
    }

    private static KeyCounter items(net.minecraft.item.Item item, long amount) {
        var result = new KeyCounter();
        result.add(AEItemKey.of(item), amount);
        return result;
    }
}
