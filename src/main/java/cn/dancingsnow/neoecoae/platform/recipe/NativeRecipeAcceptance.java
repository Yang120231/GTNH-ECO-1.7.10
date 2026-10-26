package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonObject;

import appeng.api.AEApi;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.registry.RecipeMaterials;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;

/** Explicit development acceptance against real FML registries and live world events. */
public final class NativeRecipeAcceptance {

    private static final Set<WorldServer> FINISHED = Collections.newSetFromMap(new WeakHashMap<>());
    private static Pending pending;

    private NativeRecipeAcceptance() {}

    public static void checkRegistered() {
        require(
            SourceRecipes.status()
                .size() == 164,
            "164 core recipe definitions");
        for (var entry : SourceRecipes.status()
            .entrySet())
            require(
                entry.getValue()
                    .startsWith("installed:"),
                "Uninstalled core recipe: " + entry);
        require(
            SourceRecipes.crafting()
                .size() == 91,
            "91 native crafting recipes");
        require(
            WorkstationRecipes.getAll()
                .size() == 30,
            "30 workstation recipes");
        require(
            WorkstationRecipes.cooling()
                .size() == 2,
            "2 cooling recipes");
        require(
            BlastingRecipes.getAll()
                .size() == 8,
            "8 distinct blasting recipes");
        require(
            WorldTransformRecipes.getAll()
                .size() == 6,
            "6 live world transformations");
        var catalog = SourceRecipes.read("neoecoae/recipe-catalog.json");
        for (var element : catalog.getAsJsonArray("recipes")) {
            String path = element.getAsJsonObject()
                .get("path")
                .getAsString();
            String name = path.substring("data/neoecoae/recipe/".length(), path.length() - 5);
            var value = SourceRecipes.read(path);
            var id = ResourceLocation.parse("neoecoae:" + name);
            String type = value.get("type")
                .getAsString();
            if (SourceRecipes.crafting()
                .containsKey(name)) checkCrafting(name, value);
            else if (type.equals("minecraft:smelting")) {
                var ingredient = SourceIngredient.of(value.get("ingredient"));
                for (ItemStack input : ingredient.examples()) require(
                    same(
                        FurnaceRecipes.smelting()
                            .getSmeltingResult(input),
                        SourceRecipes.output(value)),
                    "Native furnace result " + id);
            } else if (type.equals("neoecoae:integrated_working_station")) {
                var recipe = WorkstationRecipes.getById(id);
                List<ItemStack> inputs = new ArrayList<>();
                for (var ingredient : recipe.inputItems()) {
                    var stack = ingredient.ingredient()
                        .examples()
                        .get(0);
                    stack.stackSize = ingredient.count();
                    inputs.add(stack);
                }
                var fluids = recipe.inputFluid()
                    .ingredient()
                    .examples(
                        recipe.inputFluid()
                            .amount());
                FluidStack fluid = fluids.isEmpty() ? null : fluids.get(0);
                require(
                    recipe.matches(new IntegratedWorkingStationRecipe.Input(inputs, fluid), null),
                    "Original counted workstation inputs " + id);
                require(WorkstationRecipes.find(inputs, fluid) != null, "Native machine recipe lookup " + id);
                require(
                    recipe.energy() == value.get("energy")
                        .getAsInt(),
                    "Original workstation energy " + id);
                require(
                    recipe.processingTicks() == (value.has("processingTicks") ? value.get("processingTicks")
                        .getAsInt() : 100),
                    "Original workstation duration " + id);
                if (value.has("itemOutput")) require(
                    same(recipe.itemOutput(), SourceRecipes.output(value.getAsJsonObject("itemOutput"), false)),
                    "Original workstation item output " + id);
                if (value.has("fluidOutput")) {
                    var expected = SourceFluids.output(value.getAsJsonObject("fluidOutput"));
                    require(
                        recipe.fluidOutput()
                            .isFluidStackIdentical(expected),
                        "Original workstation fluid output " + id);
                }
                if (!inputs.isEmpty()) {
                    inputs.get(inputs.size() - 1).stackSize--;
                    require(
                        !recipe.matches(new IntegratedWorkingStationRecipe.Input(inputs, fluid), null),
                        "No free or repeated ingredient quantity " + id);
                }
            } else if (type.equals("minecraft:blasting")) {
                var recipe = BlastingRecipes.getById(id);
                require(
                    recipe.cookingTime() == value.get("cookingtime")
                        .getAsInt(),
                    "Original blast duration " + id);
                require(
                    recipe.experience() == value.get("experience")
                        .getAsFloat(),
                    "Original blast experience " + id);
                require(same(recipe.result(), SourceRecipes.output(value)), "Original blast result " + id);
                for (ItemStack stack : recipe.ingredient()
                    .examples()) require(BlastingRecipes.find(stack) != null, "Native blast directory lookup " + id);
            } else if (type.equals("neoecoae:cooling")) {
                var recipe = WorkstationRecipes.cooling()
                    .get(id);
                FluidStack input = recipe.input()
                    .ingredient()
                    .examples(recipe.inputAmount())
                    .get(0);
                require(WorkstationRecipes.findCooling(input, null) != null, "Native coolant lookup " + id);
                input.amount--;
                require(
                    !recipe.matches(new cn.dancingsnow.neoecoae.recipe.CoolingRecipe.Input(input, null), null),
                    "Coolant minimum amount " + id);
                require(
                    recipe.coolant() == value.get("coolant")
                        .getAsInt(),
                    "Original coolant capacity " + id);
                require(
                    recipe.maxOverclock() == value.get("max_overclock")
                        .getAsInt(),
                    "Original coolant tier " + id);
            } else if (type.equals("ae2:storage_cell_disassembly")) {
                var cell = SourceItems.stack(
                    value.get("cell")
                        .getAsString(),
                    1);
                var returns = StorageRecipes.disassemble(cell.getItem());
                require(
                    returns.size() == value.getAsJsonArray("cell_disassembly_items")
                        .size(),
                    "Complete cell disassembly returns " + id);
                for (int i = 0; i < returns.size(); i++) require(
                    same(
                        returns.get(i),
                        SourceRecipes.output(
                            value.getAsJsonArray("cell_disassembly_items")
                                .get(i)
                                .getAsJsonObject(),
                            false)),
                    "Original cell disassembly return " + id);
            } else if (type.equals("ae2:inscriber")) {
                boolean found = false;
                for (var recipe : AEApi.instance()
                    .registries()
                    .inscriber()
                    .getRecipes()) if (same(recipe.getOutput(), SourceRecipes.output(value))) {
                        found = true;
                        break;
                    }
                require(found, "Native AE2 inscriber registry " + id);
            } else if (type.equals("ae2:transform")) {
                var recipe = WorldTransformRecipes.getById(id);
                List<ItemStack> inputs = new ArrayList<>();
                for (var ingredient : recipe.ingredients()) inputs.add(
                    ingredient.examples()
                        .get(0));
                require(recipe.claimed(inputs, inputs.get(0)) != null, "Complete transform claims " + id);
                require(same(recipe.result(), SourceRecipes.output(value)), "Original transformation result " + id);
            } else throw new IllegalStateException("No native acceptance for recipe type " + type);
        }
        var table = table();
        for (int slot = 0; slot < 9; slot++)
            table.setInventorySlotContents(slot, new ItemStack(net.minecraft.init.Blocks.packed_ice));
        require(
            same(
                CraftingManager.getInstance()
                    .findMatchingRecipe(table, null),
                new ItemStack(RecipeMaterials.BLUE_ICE)),
            "Nine packed ice make a distinct blue ice block");
        require(RecipeMaterials.BLUE_ICE.getExplosionResistance(null) == 2.8F, "Blue ice native explosion resistance");
        require(RecipeMaterials.BLUE_ICE.slipperiness == .989F, "Blue ice friction");
        NeoECOAE.LOG.info(
            "NEOECOAE_NATIVE_RECIPES_OK definitions=164 installed=164 pending=0 crafting=91 smelting=8 inscriber=10 disassembly=9 workstation=30 cooling=2 blasting=8 transform=6 blue_ice=true");
    }

    private static void checkCrafting(String id, JsonObject value) {
        var table = table();
        switch (value.get("type")
            .getAsString()) {
            case "minecraft:crafting_shaped": {
                var pattern = value.getAsJsonArray("pattern");
                for (int y = 0; y < pattern.size(); y++) {
                    String row = pattern.get(y)
                        .getAsString();
                    for (int x = 0; x < row.length(); x++) if (row.charAt(x) != ' ') table.setInventorySlotContents(
                        x + y * 3,
                        SourceIngredient.of(
                            value.getAsJsonObject("key")
                                .get(String.valueOf(row.charAt(x))))
                            .examples()
                            .get(0));
                }
                break;
            }
            case "minecraft:crafting_shapeless": {
                int slot = 0;
                for (var ingredient : value.getAsJsonArray("ingredients")) table.setInventorySlotContents(
                    slot++,
                    SourceIngredient.of(ingredient)
                        .examples()
                        .get(0));
                break;
            }
            case "minecraft:smithing_transform": {
                int slot = 0;
                for (String part : new String[] { "template", "base", "addition" }) table.setInventorySlotContents(
                    slot++,
                    SourceIngredient.of(value.get(part))
                        .examples()
                        .get(0));
                break;
            }
            default:
                throw new IllegalStateException("Unexpected native crafting type " + id);
        }
        var recipe = SourceRecipes.crafting()
            .get(id);
        require(
            CraftingManager.getInstance()
                .getRecipeList()
                .contains(recipe),
            "Actual native crafting registration " + id);
        require(recipe.matches(table, null), "Original native crafting input " + id);
        require(
            same(recipe.getCraftingResult(table), SourceRecipes.output(value)),
            "Original native crafting output " + id);
    }

    public static void startWorld(WorldServer world) {
        if (!Boolean.getBoolean("neoecoae.recipeAcceptance"))
            throw new IllegalStateException("Native recipe world acceptance requires explicit JVM property");
        if (pending != null || FINISHED.contains(world)) return;
        pending = new Pending(
            world,
            new ArrayList<>(
                WorldTransformRecipes.getAll()
                    .values()));
        begin(pending);
    }

    static void worldTick(WorldServer world) {
        if (!Boolean.getBoolean("neoecoae.recipeAcceptance") || world.provider.dimensionId != 0) return;
        if (pending == null) {
            startWorld(world);
            return;
        }
        if (pending.world != world) return;
        long elapsed = world.getTotalWorldTime() - pending.started;
        var recipe = pending.recipes.get(pending.index);
        if (!recipe.explosion() && elapsed == 60)
            require(outputs(pending).isEmpty(), "Water transform cannot run at 60 ticks " + recipe.id());
        if (!recipe.explosion() && elapsed < 61 || recipe.explosion() && elapsed < 1) return;
        var outputs = outputs(pending);
        if (outputs.size() != 1 || !same(
            outputs.get(0)
                .getEntityItem(),
            recipe.result())) {
            NeoECOAE.LOG.error(
                "NEOECOAE_RECIPE_WORLD_DIAGNOSTIC recipe={} elapsed={} outputs={}",
                recipe.id(),
                elapsed,
                outputs.size());
            for (var input : pending.inputs) NeoECOAE.LOG.error(
                "NEOECOAE_RECIPE_WORLD_INPUT stack={} dead={} position={},{},{} valid_fluid_ticks={}",
                input.getEntityItem(),
                input.isDead,
                input.posX,
                input.posY,
                input.posZ,
                WorldTransformRecipes.observedFluidTicks(input));
            for (var output : outputs)
                NeoECOAE.LOG.error("NEOECOAE_RECIPE_WORLD_OUTPUT stack={}", output.getEntityItem());
        }
        require(
            outputs.size() == 1 && same(
                outputs.get(0)
                    .getEntityItem(),
                recipe.result()),
            "One live transformation output " + recipe.id() + " elapsed=" + elapsed);
        require(
            !pending.inputs.get(0).isDead && pending.inputs.get(0)
                .getEntityItem().stackSize == 1,
            "Anchor keeps exactly its unused item and survives explosion " + recipe.id());
        for (int i = 1; i < pending.inputs.size(); i++)
            require(pending.inputs.get(i).isDead, "Every other ingredient consumed exactly once " + recipe.id());
        cleanup(pending);
        pending.index++;
        if (pending.index == pending.recipes.size()) {
            FINISHED.add(world);
            pending = null;
            NeoECOAE.LOG.info(
                "NEOECOAE_NATIVE_WORLD_RECIPES_OK transformations=6 water=4 explosion=2 water_delay=61 exact_consumption=true anchor_survives=true");
            if (Boolean.getBoolean("neoecoae.recipeAcceptance.stopAfter"))
                net.minecraft.server.MinecraftServer.getServer()
                    .initiateShutdown();
        } else begin(pending);
    }

    private static void begin(Pending task) {
        task.x = task.world.getSpawnPoint().posX + 16;
        task.z = task.world.getSpawnPoint().posZ + 48;
        for (int cx = (task.x >> 4) - 2; cx <= (task.x >> 4) + 2; cx++)
            for (int cz = (task.z >> 4) - 2; cz <= (task.z >> 4) + 2; cz++) task.world.getChunkFromChunkCoords(cx, cz);
        for (int x = task.x - 1; x <= task.x + 1; x++) for (int z = task.z - 1; z <= task.z + 1; z++) {
            require(
                task.world.isAirBlock(x, 239, z) && task.world.isAirBlock(x, 240, z),
                "Unused recipe acceptance region");
            task.world.setBlock(x, 239, z, net.minecraft.init.Blocks.stone);
        }
        var recipe = task.recipes.get(task.index);
        if (!recipe.explosion()) for (int x = task.x - 1; x <= task.x + 1; x++)
            for (int z = task.z - 1; z <= task.z + 1; z++) task.world.setBlock(
                x,
                240,
                z,
                x == task.x && z == task.z ? net.minecraft.init.Blocks.water : net.minecraft.init.Blocks.stone);
        task.inputs.clear();
        for (var ingredient : recipe.ingredients()) {
            var stack = ingredient.examples()
                .get(0);
            stack.stackSize = task.inputs.isEmpty() ? 2 : 1;
            var item = new EntityItem(task.world, task.x + .5, 240.1, task.z + .5, stack);
            item.motionX = item.motionY = item.motionZ = 0;
            task.world.spawnEntityInWorld(item);
            task.inputs.add(item);
        }
        task.started = task.world.getTotalWorldTime();
        if (recipe.explosion()) task.world.newExplosion(null, task.x + .5, 240.1, task.z + .5, 1, false, false);
    }

    private static List<EntityItem> outputs(Pending task) {
        List<EntityItem> result = new ArrayList<>();
        for (Object value : task.world.getEntitiesWithinAABB(
            EntityItem.class,
            AxisAlignedBB.getBoundingBox(task.x - 2, 239, task.z - 2, task.x + 3, 243, task.z + 3))) {
            var item = (EntityItem) value;
            if (!item.isDead && !task.inputs.contains(item)) result.add(item);
        }
        return result;
    }

    private static void cleanup(Pending task) {
        for (EntityItem item : outputs(task)) item.setDead();
        for (EntityItem item : task.inputs) item.setDead();
        for (int x = task.x - 1; x <= task.x + 1; x++) for (int z = task.z - 1; z <= task.z + 1; z++) {
            task.world.setBlockToAir(x, 240, z);
            task.world.setBlockToAir(x, 239, z);
        }
    }

    static void unload(net.minecraft.world.World world) {
        FINISHED.remove(world);
        if (pending != null && pending.world == world) pending = null;
    }

    private static boolean same(ItemStack first, ItemStack second) {
        return first != null && second != null
            && first.stackSize == second.stackSize
            && first.isItemEqual(second)
            && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static InventoryCrafting table() {
        return new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, 3, 3);
    }

    private static void require(boolean success, String message) {
        if (!success) throw new IllegalStateException(message);
    }

    private static final class Pending {

        final WorldServer world;
        final List<NativeTransformRecipe> recipes;
        final List<EntityItem> inputs = new ArrayList<>();
        int index, x, z;
        long started;

        Pending(WorldServer world, List<NativeTransformRecipe> recipes) {
            this.world = world;
            this.recipes = recipes;
        }
    }
}
