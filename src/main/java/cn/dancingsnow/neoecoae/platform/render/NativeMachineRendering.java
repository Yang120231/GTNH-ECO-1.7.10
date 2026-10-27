package cn.dancingsnow.neoecoae.platform.render;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModernModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateHolder;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Imported blockstate variants select models; the preserved loader/baker/drawer owns all mesh processing. */
@SideOnly(Side.CLIENT)
public final class NativeMachineRendering implements ISimpleBlockRenderingHandler, ModelRenderState {

    private static final Map<Block, BlockModels> BLOCKS = new IdentityHashMap<>();
    private static final Map<net.minecraft.item.Item, String> CELLS = new IdentityHashMap<>();

    public static void install() {
        NativeMachineBlock.renderId = RenderingRegistry.getNextAvailableRenderId();
        NativeMachineRendering renderer = new NativeMachineRendering();
        RenderingRegistry.registerBlockHandler(renderer);
        ModelRenderBridge.bind(renderer);
        for (var entry : NativeContent.items()
            .entrySet()) {
            if (entry.getValue() instanceof NativeStorageCellItem) {
                NativeStorageCellItem cell = (NativeStorageCellItem) entry.getValue();
                String tier = cell.getTier()
                    .getPanelTierName()
                    .getString()
                    .toLowerCase(Locale.ROOT);
                String kind = cell.isLegacyBulk() ? (tier.equals("l9") ? "bulk_item" : "bulk")
                    : cell.getKeyType() == cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType.items() ? "item"
                        : "fluid";
                CELLS.put(cell, "cell/storage_cell_" + tier + "_" + kind);
            }
        }
        CELLS.put(NativeContent.item("eco_infinite_item_cell"), "cell/storage_cell_l9_infinite_resource");
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
            cn.dancingsnow.neoecoae.platform.registry.StorageMachines.Drive.class,
            new NativeDriveLedRenderer());
    }

    public static IIcon register(NativeMachineBlock block, IIconRegister register) {
        BlockModels models = new BlockModels();
        String name = block.model();
        models.hideWhenFormed = name.equals("crafting_network_switch")
            || name.equals("crafting_high_energy_network_switch")
            || name.equals("computation_network_switch")
            || name.equals("computation_high_energy_network_switch")
            || name.equals("storage_interface")
            || name.equals("crafting_interface")
            || name.equals("computation_interface")
            || name.equals("large_integrated_working_station_casing")
            || name.equals("large_integrated_working_station_interface")
            || name.equals("large_integrated_working_station_input_hatch")
            || name.equals("large_integrated_working_station_output_hatch")
            || name.equals("input_hatch")
            || name.equals("output_hatch");
        ResourceLocation location = new ResourceLocation("neoecoae", "blockstates/" + block.model() + ".json");
        try (var reader = new InputStreamReader(
            Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location)
                .getInputStream(),
            StandardCharsets.UTF_8)) {
            JsonObject states = new JsonParser().parse(reader)
                .getAsJsonObject()
                .getAsJsonObject("variants");
            for (var entry : states.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonArray())
                    throw new IllegalArgumentException("Unimplemented weighted ECO model variant: " + location);
                JsonObject definition = value.getAsJsonObject();
                if (definition.has("x") && definition.get("x")
                    .getAsInt() != 0)
                    throw new IllegalArgumentException(
                        "ECO model requires a vertical orientation adapter: " + location);
                String model = definition.get("model")
                    .getAsString();
                int rotation = definition.has("y") ? definition.get("y")
                    .getAsInt() : 0;
                models.variants.add(new Variant(entry.getKey(), model, ModelFacing.fromMeta(rotation / 90)));
                if (model.equals("neoecoae:block/builtin/eco_drive")) {
                    models.load("neoecoae:block/eco_drive_empty", register, false);
                    models.load("neoecoae:block/eco_drive_full", register, false);
                } else models.load(model, register, false);
            }
            if (block instanceof cn.dancingsnow.neoecoae.platform.registry.NativeDriveBlock) {
                models.load("neoecoae:block/cell/storage_cell_default", register, true);
                for (String cell : CELLS.values()) models.load("neoecoae:block/" + cell, register, true);
            }
            if (block.partRole() == ECOMultiblockParts.Role.COMPUTATION_DRIVE)
                models.computationDrive = new NativeComputationDriveModels(models, register);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot register imported models for " + location, failure);
        }
        BLOCKS.put(block, models);
        return models.icons.values()
            .iterator()
            .next();
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        NativeMachineBlock machine = (NativeMachineBlock) block;
        BlockModels models = required(block);
        Variant variant = models.select(machine.defaultBlockState());
        String model = variant.model.equals("neoecoae:block/builtin/eco_drive") ? "neoecoae:block/eco_drive_empty"
            : variant.model;
        EcoModelRenderer.renderInventoryBlock(models.meshes.get(model), variant.facing, models.icons);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        NativeMachineBlock machine = (NativeMachineBlock) block;
        var tile = world.getTileEntity(x, y, z);
        BlockState state = tile instanceof NativeStateHolder ? ((NativeStateHolder) tile).nativeBlockState()
            : machine.fromMetadata(world.getBlockMetadata(x, y, z));
        if (state == null || !state.is(block)) state = machine.fromMetadata(world.getBlockMetadata(x, y, z));
        BlockModels models = required(block);
        if (models.isHidden(state)) return true;
        Variant variant = models.select(state);
        String model = variant.model;
        if (model.equals("neoecoae:block/builtin/eco_drive"))
            model = state.getValue(NativeMachineBlock.HAS_CELL) ? "neoecoae:block/eco_drive_full"
                : "neoecoae:block/eco_drive_empty";
        EcoModelRenderer
            .renderWorld(models.meshes.get(model), variant.facing, models.icons, world, x, y, z, block, renderer);
        if (tile instanceof ECODriveBlockEntity) {
            var cell = ((ECODriveBlockEntity) tile).getCellStack();
            if (!cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(cell)) {
                String cellModel = "neoecoae:block/" + CELLS.getOrDefault(cell.getItem(), "cell/storage_cell_default");
                EcoModelRenderer.renderWorld(
                    models.meshes.get(cellModel),
                    variant.facing,
                    models.icons,
                    world,
                    x,
                    y,
                    z,
                    block,
                    renderer);
            }
        }
        if (tile instanceof ECOComputationDriveBlockEntity && models.computationDrive != null)
            models.computationDrive.render(
                (ECOComputationDriveBlockEntity) tile,
                state,
                variant.facing,
                models.icons,
                world,
                x,
                y,
                z,
                block,
                renderer);
        return true;
    }

    private static BlockModels required(Block block) {
        BlockModels models = BLOCKS.get(block);
        if (models == null) throw new IllegalStateException("Model textures were not registered");
        return models;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return NativeMachineBlock.renderId;
    }

    @Override
    public boolean isHidden(IBlockAccess world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        BlockModels models = BLOCKS.get(block);
        if (models == null) return false;
        var tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof NativeStateHolder)) return false;
        BlockState state = ((NativeStateHolder) tile).nativeBlockState();
        return state != null && state.is(block) && models.isHidden(state);
    }

    @Override
    public boolean isModelBlock(Block block) {
        return block instanceof NativeMachineBlock
            || block instanceof cn.dancingsnow.neoecoae.platform.registry.NativeSimpleModelBlock;
    }

    static final class BlockModels {

        final List<Variant> variants = new ArrayList<>();
        final Map<String, BakedEcoModel> meshes = new LinkedHashMap<>();
        final Map<String, IIcon> icons = new LinkedHashMap<>();
        private final Map<BlockState, Variant> selectedVariants = new ConcurrentHashMap<>();
        private boolean hideWhenFormed;
        private NativeComputationDriveModels computationDrive;

        void load(String id, IIconRegister register, boolean cell) {
            if (meshes.containsKey(id)) return;
            ResourceLocation location = new ResourceLocation(id);
            ModernModel source = ModernModelLoader.loadModel(
                new ResourceLocation(location.getResourceDomain(), "models/" + location.getResourcePath() + ".json"),
                0);
            BakedEcoModel mesh = cell ? BakedEcoModel.offsetSubModel(source, 2 / 16.0, 2 / 16.0, 0)
                : new BakedEcoModel(source);
            meshes.put(id, mesh);
            registerTextures(mesh, register);
        }

        void registerTextures(BakedEcoModel mesh, IIconRegister register) {
            for (var quad : mesh.getQuads(ModelFacing.NORTH)) {
                String texture = quad.getTexture();
                if (texture.startsWith("#"))
                    throw new IllegalArgumentException("Unresolved source texture: " + texture);
                if (!icons.containsKey(texture))
                    icons.put(texture, register.registerIcon(texture.replace(":block/", ":")));
            }
        }

        boolean isHidden(BlockState state) {
            return (state.hasProperty(NativeMachineBlock.INVISIBLE) && state.getValue(NativeMachineBlock.INVISIBLE))
                || (hideWhenFormed && state.getValue(NativeMachineBlock.FORMED));
        }

        Variant select(BlockState state) {
            Variant selected = selectedVariants.get(state);
            if (selected == null) {
                for (Variant variant : variants) if (variant.matches(state)) {
                    selected = variant;
                    break;
                }
                if (selected == null)
                    throw new IllegalStateException("No source model variant for " + state.getValues());
                // Only immutable property projections are cached, never world or tile objects.
                if (selectedVariants.size() >= 256) selectedVariants.clear();
                selectedVariants.put(state, selected);
            }
            return selected;
        }
    }

    static final class Variant {

        final String condition, model;
        final ModelFacing facing;
        private final Map<String, String> expectedValues = new LinkedHashMap<>();

        Variant(String condition, String model, ModelFacing facing) {
            this.condition = condition;
            this.model = model;
            this.facing = facing;
            if (!condition.isEmpty()) for (String test : condition.split(",")) {
                String[] pair = test.split("=", 2);
                if (pair.length != 2) throw new IllegalArgumentException("Invalid source model condition: " + test);
                expectedValues.put(pair[0], pair[1]);
            }
        }

        boolean matches(BlockState state) {
            if (expectedValues.isEmpty()) return true;
            int matched = 0;
            for (var entry : state.getValues()
                .entrySet()) {
                String expected = expectedValues.get(
                    entry.getKey()
                        .getName());
                if (expected == null) continue;
                if (!entry.getValue()
                    .toString()
                    .toLowerCase(Locale.ROOT)
                    .equals(expected)) return false;
                matched++;
            }
            return matched == expectedValues.size();
        }
    }
}
