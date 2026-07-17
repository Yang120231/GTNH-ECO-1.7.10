package cn.dancingsnow.neoecoae.client.render.model;

import net.minecraftforge.common.util.ForgeDirection;

public class ModelFace {

    private final ForgeDirection side;
    private final String texture;
    private final String cullFace;
    private final double minU;
    private final double minV;
    private final double maxU;
    private final double maxV;
    private final int rotation;
    private final boolean fullBright;
    private final ModelRenderLayer renderLayer;

    public ModelFace(ForgeDirection side, String texture, String cullFace, double minU, double minV, double maxU,
        double maxV, int rotation) {
        this(side, texture, cullFace, minU, minV, maxU, maxV, rotation, false);
    }

    public ModelFace(ForgeDirection side, String texture, String cullFace, double minU, double minV, double maxU,
        double maxV, int rotation, boolean fullBright) {
        this(side, texture, cullFace, minU, minV, maxU, maxV, rotation, fullBright, null);
    }

    public ModelFace(ForgeDirection side, String texture, String cullFace, double minU, double minV, double maxU,
        double maxV, int rotation, boolean fullBright, ModelRenderLayer renderLayer) {
        this.side = side;
        this.texture = texture;
        this.cullFace = cullFace;
        this.minU = minU;
        this.minV = minV;
        this.maxU = maxU;
        this.maxV = maxV;
        this.rotation = rotation;
        this.fullBright = fullBright;
        this.renderLayer = renderLayer;
    }

    public ForgeDirection getSide() {
        return this.side;
    }

    public String getTexture() {
        return this.texture;
    }

    public String getCullFace() {
        return this.cullFace;
    }

    public double getMinU() {
        return this.minU;
    }

    public double getMinV() {
        return this.minV;
    }

    public double getMaxU() {
        return this.maxU;
    }

    public double getMaxV() {
        return this.maxV;
    }

    public int getRotation() {
        return this.rotation;
    }

    public boolean isFullBright() {
        return this.fullBright;
    }

    public ModelRenderLayer getRenderLayer(ModelRenderLayer fallback) {
        return this.renderLayer != null ? this.renderLayer : fallback;
    }
}
