package cn.dancingsnow.neoecoae.compat.nei;

import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.item.ItemFluidPacket;

import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;

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

        int height() {
            return this == COOLING ? 118 : 96;
        }
    }

    private final Kind kind;

    WorkstationRecipeHandler(Kind kind) {
        this.kind = kind;
        transferRects.add(
            new RecipeTransferRect(
                kind == Kind.COOLING ? new Rectangle(91, 22, 20, 18) : new Rectangle(101, 30, 12, 18),
                kind.id()));
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
    public boolean hasOverlay(GuiContainer gui, Container container, int recipe) {
        return kind != Kind.COOLING && container instanceof ContainerPatternTerm;
    }

    @Override
    public IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe) {
        return kind != Kind.COOLING && gui.inventorySlots instanceof ContainerPatternTerm
            ? WorkstationPatternOverlay.INSTANCE
            : null;
    }

    @Override
    public String getRecipeName() {
        return StatCollector.translateToLocal(kind.id());
    }

    @Override
    public String getGuiTexture() {
        if (kind == Kind.COOLING) return "minecraft:textures/gui/container/crafting_table.png";
        return "neoecoae:textures/guis/"
            + (kind == Kind.LARGE ? "large_integration_working_station.png" : "integration_working_station.png");
    }

    @Override
    public int getRecipeHeight(int recipe) {
        return kind.height();
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
        if (kind != Kind.COOLING) {
            texture(getGuiTexture(), -1, 0, 168, kind == Kind.LARGE ? 96 : 75);
            if (kind == Kind.INTEGRATED) texture(
                "neoecoae:textures/guis/large_integration_working_station.png",
                -1,
                70,
                168,
                26,
                168,
                96,
                0,
                70);
            return;
        }
        for (int i = 0; i < 9; i++) slot(5 + i % 3 * 18, 4 + i / 3 * 18);
        slot(68, 22);
        slot(118, 13);
        slot(144, 31);
    }

    private static void texture(String resource, int x, int y, int width, int height) {
        texture(resource, x, y, width, height, width, height, 0, 0);
    }

    private static void texture(String resource, int x, int y, int width, int height, int textureWidth,
        int textureHeight, int u, int v) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(new ResourceLocation(resource));
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        double minU = (double) u / textureWidth;
        double maxU = (double) (u + width) / textureWidth;
        double minV = (double) v / textureHeight;
        double maxV = (double) (v + height) / textureHeight;
        tessellator.addVertexWithUV(x, y + height, 0, minU, maxV);
        tessellator.addVertexWithUV(x + width, y + height, 0, maxU, maxV);
        tessellator.addVertexWithUV(x + width, y, 0, maxU, minV);
        tessellator.addVertexWithUV(x, y, 0, minU, minV);
        tessellator.draw();
    }

    private static void slot(int x, int y) {
        Gui.drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF777777);
        Gui.drawRect(x, y, x + 16, y + 16, 0xFFBBBBBB);
    }

    @Override
    public void drawForeground(int index) {
        codechicken.nei.NEIClientUtils.gl2DRenderContext(() -> super.drawForeground(index));
    }

    @Override
    public void drawExtras(int index) {
        var display = (DisplayRecipe) arecipes.get(index);
        var recipe = display.recipe;
        if (kind == Kind.COOLING) {
            GuiDraw.drawString("->", 94, 27, 0x404040, false);
            line("neoecoae.workstation.coolant", recipe.coolant, 62);
            line("neoecoae.workstation.overclock", recipe.maxOverclock, 74);
        } else {
            for (var tank : display.tanks) {
                texture(
                    getGuiTexture(),
                    tank.relx,
                    tank.rely,
                    16,
                    58,
                    168,
                    kind == Kind.LARGE ? 96 : 75,
                    tank.relx + 1,
                    tank.rely);
                com.cleanroommc.modularui.drawable.GuiDraw
                    .drawFluidTexture(tank.fluid(), tank.relx, tank.rely, 16, 58, 0);
            }
            texture("neoecoae:textures/gui/recipe/progress_bar.png", 135, 30, 6, 18);
            infoLine("neoecoae.workstation.energy", recipe.energy, 74);
            infoLine("neoecoae.workstation.ticks", recipe.ticks, 83);
            return;
        }
        if (!recipe.fluid.ingredient()
            .isEmpty()) {
            var fluid = recipe.fluid.ingredient()
                .examples(recipe.fluid.amount())
                .get(0);
            fluidLine("neoecoae.workstation.fluid_input", fluid, kind == Kind.COOLING ? 88 : 122);
        }
        if (recipe.fluidOutput != null)
            fluidLine("neoecoae.workstation.fluid_output", recipe.fluidOutput, kind == Kind.COOLING ? 100 : 134);
    }

    private static void infoLine(String key, int value, int y) {
        String text = StatCollector.translateToLocalFormatted(
            key,
            NumberFormat.getIntegerInstance(Locale.US)
                .format(value));
        float scale = Math.min(1f, 142f / Math.max(1, Minecraft.getMinecraft().fontRenderer.getStringWidth(text)));
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(5, y, 0);
        org.lwjgl.opengl.GL11.glScalef(scale, scale, 1);
        GuiDraw.drawString(text, 0, 0, 0x403E53, false);
        org.lwjgl.opengl.GL11.glPopMatrix();
    }

    private WorkstationTankStack hoveredTank(GuiRecipe<?> gui, int index) {
        for (var tank : ((DisplayRecipe) arecipes.get(index)).tanks) if (gui.isMouseOver(tank, index)) return tank;
        return null;
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int index) {
        var tank = hoveredTank(gui, index);
        return tank == null ? super.handleTooltip(gui, currenttip, index) : tank.tooltip();
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int index) {
        var tank = hoveredTank(gui, index);
        return tank == null ? currenttip : tank.tooltip();
    }

    private static void line(String key, int value, int y) {
        GuiDraw.drawString(StatCollector.translateToLocalFormatted(key, value), 5, y, 0x404040, false);
    }

    private static void fluidLine(String key, FluidStack fluid, int y) {
        String text = StatCollector.translateToLocalFormatted(key, fluid.getLocalizedName(), fluid.amount);
        var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        GuiDraw.drawString(font.trimStringToWidth(text, 160), 5, y, 0x404040, false);
    }

    private ItemStack fluidDisplay(FluidStack fluid) {
        return ItemFluidPacket.newStack(fluid);
    }

    /** Typed IO for the displayed recipe, including fluid-only and combined item/fluid outputs. */
    List<IAEStack<?>> patternStacks(int recipe, boolean outputs) {
        var display = (DisplayRecipe) arecipes.get(recipe);
        List<PositionedStack> stacks = new ArrayList<>();
        if (outputs) {
            if (display.result != null) stacks.add(display.result);
            stacks.addAll(display.others);
        } else stacks.addAll(display.inputs);
        List<IAEStack<?>> typed = new ArrayList<>();
        for (PositionedStack stack : stacks) {
            typed.add(WorkstationPatternOverlay.stack(stack.item, display.fluidSlots.contains(stack)));
        }
        return typed;
    }

    private final class DisplayRecipe extends CachedRecipe {

        private final WorkstationNEIRecipe recipe;
        private final List<PositionedStack> inputs = new ArrayList<>();
        private final List<PositionedStack> others = new ArrayList<>();
        private final List<WorkstationTankStack> tanks = new ArrayList<>();
        private final java.util.Set<PositionedStack> fluidSlots = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<>());
        private final PositionedStack result;

        DisplayRecipe(WorkstationNEIRecipe recipe) {
            this.recipe = recipe;
            var itemInputs = recipe.itemInputs();
            int columns = kind == Kind.LARGE ? 4 : 3;
            for (int i = 0; i < itemInputs.size(); i++) {
                int x = kind == Kind.COOLING ? 5 + i % 3 * 18 : (kind == Kind.LARGE ? 25 : 37) + i % columns * 18;
                int y = kind == Kind.COOLING ? 4 + i / 3 * 18 : 12 + i / columns * 18;
                inputs.add(new PositionedStack(itemInputs.get(i), x, y));
            }
            List<ItemStack> fluids = new ArrayList<>();
            for (var fluid : recipe.fluid.ingredient()
                .examples(recipe.fluid.amount())) {
                ItemStack display = fluidDisplay(fluid);
                if (display != null) fluids.add(display);
            }
            if (!fluids.isEmpty()) {
                if (kind == Kind.COOLING) {
                    var stack = new PositionedStack(fluids, 68, 22);
                    inputs.add(stack);
                    fluidSlots.add(stack);
                } else {
                    var tank = new WorkstationTankStack(
                        fluids,
                        recipe.fluid.ingredient()
                            .examples(recipe.fluid.amount()),
                        4,
                        9);
                    inputs.add(tank);
                    tanks.add(tank);
                    fluidSlots.add(tank);
                }
            }
            PositionedStack fluidResult = null;
            if (recipe.fluidOutput != null) {
                var display = fluidDisplay(recipe.fluidOutput);
                if (display != null) {
                    if (kind == Kind.COOLING) fluidResult = new PositionedStack(display, 144, 31);
                    else {
                        var tank = new WorkstationTankStack(
                            Collections.singletonList(display),
                            Collections.singletonList(recipe.fluidOutput),
                            146,
                            9);
                        fluidResult = tank;
                        tanks.add(tank);
                    }
                }
            }
            if (fluidResult != null) fluidSlots.add(fluidResult);
            result = recipe.output == null ? fluidResult
                : new PositionedStack(
                    recipe.output.copy(),
                    kind == Kind.COOLING ? 118 : 113,
                    kind == Kind.COOLING ? 13 : 31);
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
