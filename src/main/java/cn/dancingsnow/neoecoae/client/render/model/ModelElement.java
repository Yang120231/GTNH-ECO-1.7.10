package cn.dancingsnow.neoecoae.client.render.model;

import java.util.EnumMap;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;

public class ModelElement {

    private final double[] from;
    private final double[] to;
    private final Map<ForgeDirection, ModelFace> faces = new EnumMap<ForgeDirection, ModelFace>(ForgeDirection.class);

    public ModelElement(double[] from, double[] to) {
        this.from = from;
        this.to = to;
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
}
