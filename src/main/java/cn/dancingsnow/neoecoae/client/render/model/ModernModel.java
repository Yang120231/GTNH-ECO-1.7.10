package cn.dancingsnow.neoecoae.client.render.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModernModel {

    private final Map<String, String> textures = new HashMap<String, String>();
    private final List<ModelElement> elements = new ArrayList<ModelElement>();
    private ModelRenderLayer renderLayer = ModelRenderLayer.CUTOUT;

    public ModernModel copy() {
        ModernModel copy = new ModernModel();
        copy.textures.putAll(this.textures);
        copy.elements.addAll(this.elements);
        copy.renderLayer = this.renderLayer;
        return copy;
    }

    public void appendResolvedElementsFrom(ModernModel model) {
        for (ModelElement element : model.elements) {
            ModelElement resolvedElement = new ModelElement(
                element.getFrom(),
                element.getTo(),
                element.isShade(),
                element.getRenderLayer());
            for (ModelFace face : element.getFaces()
                .values()) {
                resolvedElement.addFace(
                    new ModelFace(
                        face.getSide(),
                        resolveTexture(model, face.getTexture()),
                        face.getCullFace(),
                        face.getMinU(),
                        face.getMinV(),
                        face.getMaxU(),
                        face.getMaxV(),
                        face.getRotation(),
                        face.isFullBright(),
                        face.getRenderLayer(element.getRenderLayer())));
            }
            this.elements.add(resolvedElement);
        }
    }

    public Map<String, String> getTextures() {
        return this.textures;
    }

    public List<ModelElement> getElements() {
        return this.elements;
    }

    public ModelRenderLayer getRenderLayer() {
        return this.renderLayer;
    }

    public void setRenderLayer(ModelRenderLayer renderLayer) {
        this.renderLayer = renderLayer;
    }

    private static String resolveTexture(ModernModel model, String texture) {
        String key = texture;
        int safety = 0;
        while (key.startsWith("#") && safety++ < 8) {
            String resolved = model.textures.get(key.substring(1));
            if (resolved == null) {
                return key;
            }
            key = resolved;
        }
        return key;
    }
}
