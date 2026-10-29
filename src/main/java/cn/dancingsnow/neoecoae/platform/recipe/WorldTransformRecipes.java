package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Pinned AE2 water/explosion transformations bound to real 1.7.10 server world events. */
public final class WorldTransformRecipes {

    private static final Map<ResourceLocation, NativeTransformRecipe> RECIPES = new LinkedHashMap<>();
    private static final Set<Item> FLUID_ANCHORS = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final Set<Item> EXPLOSION_ANCHORS = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final WorldTransformRecipes EVENTS = new WorldTransformRecipes();
    private final Map<EntityItem, Integer> fluidTime = new WeakHashMap<>();
    private static boolean bound;

    private WorldTransformRecipes() {}

    static void install(ResourceLocation id, JsonObject definition) {
        List<SourceIngredient> ingredients = new ArrayList<>();
        for (var input : definition.getAsJsonArray("ingredients")) ingredients.add(SourceIngredient.of(input));
        boolean explosion = false;
        SourceFluidIngredient fluid = SourceFluidIngredient.of(FluidRegistry.WATER);
        if (definition.has("circumstance")) {
            var circumstance = definition.getAsJsonObject("circumstance");
            String type = circumstance.get("type")
                .getAsString();
            if (type.equals("explosion")) {
                explosion = true;
                fluid = SourceFluidIngredient.empty();
            } else if (type.equals("fluid")) fluid = SourceFluidIngredient.of(circumstance);
            else throw new IllegalArgumentException("Unknown source transformation circumstance " + type);
        }
        var recipe = new NativeTransformRecipe(id, ingredients, SourceRecipes.output(definition), explosion, fluid);
        if (RECIPES.putIfAbsent(id, recipe) != null) throw new IllegalStateException("Duplicate transformation " + id);
        if (explosion) for (var ingredient : ingredients)
            for (ItemStack stack : ingredient.examples()) EXPLOSION_ANCHORS.add(stack.getItem());
        else for (ItemStack stack : ingredients.get(0)
            .examples()) FLUID_ANCHORS.add(stack.getItem());
    }

    static void bind() {
        if (bound) throw new IllegalStateException("World transformation events registered twice");
        MinecraftForge.EVENT_BUS.register(EVENTS);
        FMLCommonHandler.instance()
            .bus()
            .register(EVENTS);
        bound = true;
    }

    public static Map<ResourceLocation, NativeTransformRecipe> getAll() {
        return Collections.unmodifiableMap(RECIPES);
    }

    public static NativeTransformRecipe getById(ResourceLocation id) {
        return RECIPES.get(id);
    }

    static int observedFluidTicks(EntityItem item) {
        return EVENTS.fluidTime.getOrDefault(item, 0);
    }

    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || event.phase != TickEvent.Phase.END) return;
        for (Object value : new ArrayList<>(event.world.loadedEntityList)) {
            if (!(value instanceof EntityItem)) continue;
            var item = (EntityItem) value;
            if (item.isDead || !FLUID_ANCHORS.contains(
                item.getEntityItem()
                    .getItem())) {
                fluidTime.remove(item);
                continue;
            }
            var block = event.world.getBlock(
                MathHelper.floor_double(item.posX),
                MathHelper.floor_double((item.boundingBox.minY + item.boundingBox.maxY) / 2),
                MathHelper.floor_double(item.posZ));
            Fluid fluid = block == net.minecraft.init.Blocks.water || block == net.minecraft.init.Blocks.flowing_water
                ? FluidRegistry.WATER
                : block == net.minecraft.init.Blocks.lava || block == net.minecraft.init.Blocks.flowing_lava
                    ? FluidRegistry.LAVA
                    : FluidRegistry.lookupFluidForBlock(block);
            boolean valid = false;
            for (var recipe : RECIPES.values()) {
                if (recipe.inFluid(fluid) && recipe.acceptsAnchor(item.getEntityItem())) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                fluidTime.remove(item);
                continue;
            }
            int elapsed = fluidTime.getOrDefault(item, 0) + 1;
            if (elapsed > 60 && !tryTransform(item, recipe -> recipe.inFluid(fluid))) elapsed = 0;
            fluidTime.put(item, elapsed);
        }
        if (event.world instanceof net.minecraft.world.WorldServer)
            cn.dancingsnow.neoecoae.platform.forge.DevAcceptance.hooks()
                .worldTick((net.minecraft.world.WorldServer) event.world);
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate event) {
        if (event.world.isRemote) return;
        for (Entity entity : new ArrayList<>(event.getAffectedEntities())) {
            if (entity instanceof EntityItem && !entity.isDead
                && EXPLOSION_ANCHORS.contains(
                    ((EntityItem) entity).getEntityItem()
                        .getItem())
                && tryTransform((EntityItem) entity, NativeTransformRecipe::explosion))
                event.getAffectedEntities()
                    .remove(entity);
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        fluidTime.keySet()
            .removeIf(entity -> entity.worldObj == event.world);
        cn.dancingsnow.neoecoae.platform.forge.DevAcceptance.hooks()
            .worldUnload(event.world);
    }

    public static boolean tryTransform(EntityItem anchor, Predicate<NativeTransformRecipe> circumstance) {
        if (anchor.worldObj.isRemote || anchor.isDead) return false;
        List<EntityItem> nearby = new ArrayList<>();
        List<ItemStack> provided = new ArrayList<>();
        var region = AxisAlignedBB.getBoundingBox(
            anchor.posX - 1,
            anchor.posY - 1,
            anchor.posZ - 1,
            anchor.posX + 1,
            anchor.posY + 1,
            anchor.posZ + 1);
        for (Object value : anchor.worldObj.getEntitiesWithinAABB(EntityItem.class, region)) {
            var item = (EntityItem) value;
            if (!item.isDead) {
                nearby.add(item);
                provided.add(item.getEntityItem());
            }
        }
        for (var recipe : RECIPES.values()) {
            if (!circumstance.test(recipe)) continue;
            int[] consumed = recipe.claimed(provided, anchor.getEntityItem());
            if (consumed == null) continue;
            for (int i = 0; i < consumed.length; i++) {
                if (consumed[i] == 0) continue;
                var item = nearby.get(i);
                var stack = item.getEntityItem();
                stack.stackSize -= consumed[i];
                if (stack.stackSize <= 0) item.setDead();
                else item.setEntityItemStack(stack);
            }
            var random = anchor.worldObj.rand;
            var output = new EntityItem(
                anchor.worldObj,
                Math.floor(anchor.posX) + .25 + random.nextDouble() * .5,
                Math.floor(anchor.posY) + .25 + random.nextDouble() * .5,
                Math.floor(anchor.posZ) + .25 + random.nextDouble() * .5,
                recipe.result());
            output.motionX = random.nextDouble() * .25 - .125;
            output.motionY = random.nextDouble() * .25 - .125;
            output.motionZ = random.nextDouble() * .25 - .125;
            anchor.worldObj.spawnEntityInWorld(output);
            return true;
        }
        return false;
    }
}
