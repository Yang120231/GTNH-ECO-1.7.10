package cn.dancingsnow.neoecoae.client.render.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModernModel {

    private final Map<String, String> textures = new HashMap<String, String>();
    private final List<ModelElement> elements = new ArrayList<ModelElement>();

    public ModernModel copy() {
        ModernModel copy = new ModernModel();
        copy.textures.putAll(this.textures);
        copy.elements.addAll(this.elements);
        return copy;
    }

    public Map<String, String> getTextures() {
        return this.textures;
    }

    public List<ModelElement> getElements() {
        return this.elements;
    }
}
