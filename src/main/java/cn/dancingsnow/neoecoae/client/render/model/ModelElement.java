package cn.dancingsnow.neoecoae.client.render.model;

import java.util.EnumMap;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;

public class ModelElement {

    private final double[] from;
    private final double[] to;
    private final Map<ForgeDirection, ModelFace> faces = new EnumMap<ForgeDirection, ModelFace>(ForgeDirection.class);
    private final boolean shade;
    private final ModelRenderLayer renderLayer;

    public ModelElement(double[] from, double[] to) {
        this(from, to, true, ModelRenderLayer.CUTOUT);
    }

    public ModelElement(double[] from, double[] to, boolean shade) {
        this(from, to, shade, ModelRenderLayer.CUTOUT);
    }

    public ModelElement(double[] from, double[] to, boolean shade, ModelRenderLayer renderLayer) {
        this.from = from;
        this.to = to;
        this.shade = shade;
        this.renderLayer = renderLayer;
    }

    public double[] getFrom() {
        return this.from;
    }

    public double[] getTo() {
        return this.to;
    }

    public void addFace(ModelFace face) {
        this.faces.put(face.getSide(), face);
    }

    public Map<ForgeDirection, ModelFace> getFaces() {
        return this.faces;
    }

    public boolean isShade() {
        return this.shade;
    }

    public ModelRenderLayer getRenderLayer() {
        return this.renderLayer;
    }
}
