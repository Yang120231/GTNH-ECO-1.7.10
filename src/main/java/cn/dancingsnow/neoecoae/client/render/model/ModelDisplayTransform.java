package cn.dancingsnow.neoecoae.client.render.model;

public class ModelDisplayTransform {

    public static final ModelDisplayTransform GUI_DEFAULT = new ModelDisplayTransform(
        new double[] { 30.0D, -135.0D, 0.0D },
        new double[] { 0.0D, 0.0D, 0.0D },
        new double[] { 0.625D, 0.625D, 0.625D });

    private final double[] rotation;
    private final double[] translation;
    private final double[] scale;

    public ModelDisplayTransform(double[] rotation, double[] translation, double[] scale) {
        this.rotation = rotation;
        this.translation = translation;
        this.scale = scale;
    }

    public double[] getRotation() {
        return this.rotation;
    }

    public double[] getTranslation() {
        return this.translation;
    }

    public double[] getScale() {
        return this.scale;
    }
}
