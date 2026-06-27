package cn.dancingsnow.neoecoae.client.render.model;

import net.minecraftforge.common.util.ForgeDirection;

public class BakedQuad {

    private final String texture;
    private final String cullFace;
    private final ForgeDirection cullDirection;
    private final ForgeDirection normal;
    private final double[][] vertices;
    private final double[][] uv;

    public BakedQuad(String texture, String cullFace, ForgeDirection cullDirection, ForgeDirection normal,
        double[][] vertices, double[][] uv) {
        this.texture = texture;
        this.cullFace = cullFace;
        this.cullDirection = cullDirection;
        this.normal = normal;
        this.vertices = vertices;
        this.uv = uv;
    }

    public String getTexture() {
        return this.texture;
    }

    public String getCullFace() {
        return this.cullFace;
    }

    public ForgeDirection getCullDirection() {
        return this.cullDirection;
    }

    public ForgeDirection getNormal() {
        return this.normal;
    }

    public double[][] getVertices() {
        return this.vertices;
    }

    public double[][] getUv() {
        return this.uv;
    }
}
