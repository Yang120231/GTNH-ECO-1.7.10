package cn.dancingsnow.neoecoae.compat.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.Loader;

/** Separate NEI categories backed by the same catalog the two machine hosts execute. */
public final class WorkstationRecipeHandler extends TemplateRecipeHandler {

    enum Kind {

        INTEGRATED("integrated"),
        LARGE("large"),
        COOLING("cooling");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        String id() {
            return "neoecoae.workstation." + name;
        }
    }

    private final Kind kind;

    WorkstationRecipeHandler(Kind kind) {
        this.kind = kind;
        transferRects.add(new RecipeTransferRect(new Rectangle(91, 22, 20, 18), kind.id()));
    }

    @Override
    public TemplateRecipeHandler newInstance() {
        return new WorkstationRecipeHandler(kind);
    }

    @Override
    public String getHandlerId() {
        return kind.id();
    }

    @Override
    public String getOverlayIdentifier() {
        return kind.id();
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal(kind.id());
    }

    @Override
    public String getGuiTexture() {
        return "minecraft:textures/gui/container/crafting_table.png";
    }

    @Override
    public int getRecipeHeight(int recipe) {
        return 118;
    }

    private List<WorkstationNEIRecipe> recipes() {
        List<WorkstationNEIRecipe> result = new ArrayList<>();
        if (kind == Kind.COOLING) {
            for (var recipe : WorkstationRecipes.cooling()
                .values()) result.add(new WorkstationNEIRecipe(recipe));
        } else {
            for (var recipe : WorkstationRecipes.getAll()
                .values()) result.add(new WorkstationNEIRecipe(recipe));
        }
        return result;
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId.equals(kind.id()) || outputId.equals("all")) {
            for (var recipe : recipes()) arecipes.add(new DisplayRecipe(recipe));
        } else super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        var fluid = StackInfo.getFluid(result);
        for (var recipe : recipes()) if (recipe.produces(result, fluid)) arecipes.add(new DisplayRecipe(recipe));
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        var fluid = StackInfo.getFluid(ingredient);
        for (var recipe : recipes()) if (recipe.uses(ingredient, fluid)) arecipes.add(new DisplayRecipe(recipe));
    }

    @Override
    public void drawBackground(int recipe) {
        for (int i = 0; i < 9; i++) slot(5 + i % 3 * 18, 4 + i / 3 * 18);
        slot(68, 22);
        slot(118, 13);
        slot(144, 31);
    }

    private static void slot(int x, int y) {
        Gui.drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF777777);
        Gui.drawRect(x, y, x + 16, y + 16, 0xFFBBBBBB);
    }

    @Override
    public void drawExtras(int index) {
        var recipe = ((DisplayRecipe) arecipes.get(index)).recipe;
        GuiDraw.drawString("->", 94, 27, 0x404040, false);
        if (kind == Kind.COOLING) {
            line("neoecoae.workstation.coolant", recipe.coolant, 62);
            line("neoecoae.workstation.overclock", recipe.maxOverclock, 74);
        } else {
            line("neoecoae.workstation.energy", recipe.energy, 62);
            line("neoecoae.workstation.ticks", recipe.ticks, 74);
        }
        if (!recipe.fluid.ingredient()
            .isEmpty()) {
            var fluid = recipe.fluid.ingredient()
                .examples(recipe.fluid.amount())
                .get(0);
            fluidLine("neoecoae.workstation.fluid_input", fluid, 88);
        }
        if (recipe.fluidOutput != null) fluidLine("neoecoae.workstation.fluid_output", recipe.fluidOutput, 100);
    }

    private static void line(String key, int value, int y) {
        GuiDraw.drawString(StatCollector.translateToLocalFormatted(key, value), 5, y, 0x404040, false);
    }

    private static void fluidLine(String key, FluidStack fluid, int y) {
        String text = StatCollector.translateToLocalFormatted(key, fluid.getLocalizedName(), fluid.amount);
        var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        GuiDraw.drawString(font.trimStringToWidth(text, 160), 5, y, 0x404040, false);
    }

    private static ItemStack fluidDisplay(FluidStack fluid) {
        if (Loader.isModLoaded("gregtech")) return GregTechFluidDisplay.create(fluid);
        return FluidContainerRegistry.fillFluidContainer(fluid, new ItemStack(Items.bucket));
    }

    private final class DisplayRecipe extends CachedRecipe {

        private final WorkstationNEIRecipe recipe;
        private final List<PositionedStack> inputs = new ArrayList<>();
        private final List<PositionedStack> others = new ArrayList<>();
        private final PositionedStack result;

        DisplayRecipe(WorkstationNEIRecipe recipe) {
            this.recipe = recipe;
            var itemInputs = recipe.itemInputs();
            for (int i = 0; i < itemInputs.size(); i++)
                inputs.add(new PositionedStack(itemInputs.get(i), 5 + i % 3 * 18, 4 + i / 3 * 18));
            List<ItemStack> fluids = new ArrayList<>();
            for (var fluid : recipe.fluid.ingredient()
                .examples(recipe.fluid.amount())) {
                ItemStack display = fluidDisplay(fluid);
                if (display != null) fluids.add(display);
            }
            if (!fluids.isEmpty()) inputs.add(new PositionedStack(fluids, 68, 22));
            PositionedStack fluidResult = null;
            if (recipe.fluidOutput != null) {
                var display = fluidDisplay(recipe.fluidOutput);
                if (display != null) fluidResult = new PositionedStack(display, 144, 31);
            }
            result = recipe.output == null ? fluidResult : new PositionedStack(recipe.output.copy(), 118, 13);
            if (recipe.output != null && fluidResult != null) others.add(fluidResult);
        }

        @Override
        public PositionedStack getResult() {
            return result;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return inputs;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return others;
        }
    }
}
