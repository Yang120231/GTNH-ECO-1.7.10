package cn.dancingsnow.neoecoae.platform.render;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;
import cn.dancingsnow.neoecoae.platform.registry.NativeSimpleModelBlock;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Static source blockstates use the preserved mesh pipeline without synthetic tile entities. */
@SideOnly(Side.CLIENT)
public final class NativeMaterialBlockRendering implements ISimpleBlockRenderingHandler {

    private static final Map<Block, Mesh> MODELS = new IdentityHashMap<>();

    public static void install() {
        NativeSimpleModelBlock.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new NativeMaterialBlockRendering());
    }

    public static IIcon register(NativeSimpleModelBlock block, IIconRegister register) {
        var location = new ResourceLocation("neoecoae", "blockstates/" + block.model() + ".json");
        try (var reader = new InputStreamReader(
            Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location)
                .getInputStream(),
            StandardCharsets.UTF_8)) {
            var variants = new JsonParser().parse(reader)
                .getAsJsonObject()
                .getAsJsonObject("variants");
            boolean pending = block instanceof cn.dancingsnow.neoecoae.platform.registry.NativeRegisteredBlock;
            if (!pending && (variants == null || variants.entrySet()
                .size() != 1
                || !variants.has("")
                || !variants.get("")
                    .isJsonObject()))
                throw new IllegalArgumentException("Expected one static source model: " + location);
            var definition = pending ? new com.google.gson.JsonObject() : variants.getAsJsonObject("");
            if (pending) definition.addProperty(
                "model",
                ((cn.dancingsnow.neoecoae.platform.registry.NativeRegisteredBlock) block).sourceModel());
            if ((definition.has("x") && definition.get("x")
                .getAsInt() != 0) || (definition.has("y")
                    && definition.get("y")
                        .getAsInt() != 0))
                throw new IllegalArgumentException("Unexpected static model rotation: " + location);
            var modelId = new ResourceLocation(
                definition.get("model")
                    .getAsString());
            var model = new BakedEcoModel(
                ModernModelLoader.loadModel(
                    new ResourceLocation(modelId.getResourceDomain(), "models/" + modelId.getResourcePath() + ".json"),
                    0));
            var icons = new LinkedHashMap<String, IIcon>();
            for (var quad : model.getQuads(ModelFacing.NORTH)) {
                String texture = quad.getTexture();
                if (texture.startsWith("#"))
                    throw new IllegalArgumentException("Unresolved static source texture: " + texture);
                icons.computeIfAbsent(texture, key -> register.registerIcon(key.replace(":block/", ":")));
            }
            if (icons.isEmpty()) throw new IllegalStateException("Empty static source mesh: " + modelId);
            MODELS.put(block, new Mesh(model, icons));
            return icons.values()
                .iterator()
                .next();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot register source material block: " + location, failure);
        }
    }

    private static Mesh required(Block block) {
        var mesh = MODELS.get(block);
        if (mesh == null) throw new IllegalStateException("Static model textures not registered: " + block);
        return mesh;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        var mesh = required(block);
        EcoModelRenderer.renderInventoryBlock(mesh.model, ModelFacing.NORTH, mesh.icons);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        var mesh = required(block);
        EcoModelRenderer.renderWorld(mesh.model, ModelFacing.NORTH, mesh.icons, world, x, y, z, block, renderer);
        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return NativeSimpleModelBlock.renderId;
    }

    private static final class Mesh {

        final BakedEcoModel model;
        final Map<String, IIcon> icons;

        Mesh(BakedEcoModel model, Map<String, IIcon> icons) {
            this.model = model;
            this.icons = icons;
        }
    }
}
