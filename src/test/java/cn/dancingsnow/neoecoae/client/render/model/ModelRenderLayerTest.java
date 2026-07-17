package cn.dancingsnow.neoecoae.client.render.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

class ModelRenderLayerTest {

    @Test
    void modernRenderTypeNamesMapToLegacyWorldPasses() {
        assertEquals(ModelRenderLayer.CUTOUT, ModelRenderLayer.fromJsonName("solid", ModelRenderLayer.TRANSLUCENT));
        assertEquals(ModelRenderLayer.CUTOUT, ModelRenderLayer.fromJsonName("cutout", ModelRenderLayer.TRANSLUCENT));
        assertEquals(
            ModelRenderLayer.CUTOUT,
            ModelRenderLayer.fromJsonName("cutout_mipped", ModelRenderLayer.TRANSLUCENT));
        assertEquals(
            ModelRenderLayer.TRANSLUCENT,
            ModelRenderLayer.fromJsonName("translucent", ModelRenderLayer.CUTOUT));
        assertEquals(ModelRenderLayer.CUTOUT, ModelRenderLayer.fromRenderPass(0));
        assertEquals(ModelRenderLayer.TRANSLUCENT, ModelRenderLayer.fromRenderPass(1));
    }

    @Test
    void bakedQuadsRetainTheirElementRenderLayer() {
        ModernModel model = new ModernModel();
        model.getTextures()
            .put("cutout", "neoecoae:block/cutout");
        model.getTextures()
            .put("translucent", "neoecoae:block/translucent");
        model.getElements()
            .add(element("#cutout", ModelRenderLayer.CUTOUT));
        model.getElements()
            .add(element("#translucent", ModelRenderLayer.TRANSLUCENT));

        BakedEcoModel baked = new BakedEcoModel(model);

        assertEquals(
            1,
            baked.getQuads(ModelFacing.NORTH)
                .stream()
                .filter(quad -> quad.getRenderLayer() == ModelRenderLayer.CUTOUT)
                .count());
        assertEquals(
            1,
            baked.getQuads(ModelFacing.NORTH)
                .stream()
                .filter(quad -> quad.getRenderLayer() == ModelRenderLayer.TRANSLUCENT)
                .count());
    }

    @Test
    void compositeModelsPreserveChildRenderLayers() {
        ModernModel child = new ModernModel();
        child.getTextures()
            .put("glass", "neoecoae:block/glass");
        child.getElements()
            .add(element("#glass", ModelRenderLayer.TRANSLUCENT));

        ModernModel composite = new ModernModel();
        composite.appendResolvedElementsFrom(child);

        assertEquals(
            ModelRenderLayer.TRANSLUCENT,
            composite.getElements()
                .get(0)
                .getRenderLayer());
    }

    @Test
    void faceRenderLayerOverridesItsElementLayer() {
        ModernModel model = new ModernModel();
        model.getTextures()
            .put("glass", "neoecoae:block/glass");
        ModelElement element = new ModelElement(
            new double[] { 0.0D, 0.0D, 0.0D },
            new double[] { 16.0D, 16.0D, 16.0D },
            true,
            ModelRenderLayer.CUTOUT);
        element.addFace(
            new ModelFace(
                ForgeDirection.NORTH,
                "#glass",
                null,
                0.0D,
                0.0D,
                16.0D,
                16.0D,
                0,
                false,
                ModelRenderLayer.TRANSLUCENT));
        model.getElements()
            .add(element);

        BakedEcoModel baked = new BakedEcoModel(model);

        assertEquals(
            ModelRenderLayer.TRANSLUCENT,
            baked.getQuads(ModelFacing.NORTH)
                .get(0)
                .getRenderLayer());
    }

    private static ModelElement element(String texture, ModelRenderLayer renderLayer) {
        ModelElement element = new ModelElement(
            new double[] { 0.0D, 0.0D, 0.0D },
            new double[] { 16.0D, 16.0D, 16.0D },
            true,
            renderLayer);
        element.addFace(new ModelFace(ForgeDirection.NORTH, texture, null, 0.0D, 0.0D, 16.0D, 16.0D, 0));
        return element;
    }
}
