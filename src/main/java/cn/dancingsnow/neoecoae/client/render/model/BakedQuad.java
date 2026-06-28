package cn.dancingsnow.neoecoae.client.render.model;

import net.minecraftforge.common.util.ForgeDirection;

public class BakedQuad {

    private final String texture;
    private final String cullFace;
    private final ForgeDirection cullDirection;
    private final ForgeDirection normal;
    private final double[][] vertices;
    private final double[][] uv;
    private final boolean fullBright;
    private final boolean shade;
    private final boolean boundaryFace;

    public BakedQuad(String texture, String cullFace, ForgeDirection cullDirection, ForgeDirection normal,
        double[][] vertices, double[][] uv) {
        this(texture, cullFace, cullDirection, normal, vertices, uv, false, true, true);
    }

    public BakedQuad(String texture, String cullFace, ForgeDirection cullDirection, ForgeDirection normal,
        double[][] vertices, double[][] uv, boolean fullBright) {
        this(texture, cullFace, cullDirection, normal, vertices, uv, fullBright, true, true);
    }

    public BakedQuad(String texture, String cullFace, ForgeDirection cullDirection, ForgeDirection normal,
        double[][] vertices, double[][] uv, boolean fullBright, boolean shade) {
        this(texture, cullFace, cullDirection, normal, vertices, uv, fullBright, shade, true);
    }

    public BakedQuad(String texture, String cullFace, ForgeDirection cullDirection, ForgeDirection normal,
        double[][] vertices, double[][] uv, boolean fullBright, boolean shade, boolean boundaryFace) {
        this.texture = texture;
        this.cullFace = cullFace;
        this.cullDirection = cullDirection;
        this.normal = normal;
        this.vertices = vertices;
        this.uv = uv;
        this.fullBright = fullBright;
        this.shade = shade;
        this.boundaryFace = boundaryFace;
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

    public boolean isFullBright() {
        return this.fullBright;
    }

    public boolean isShade() {
        return this.shade;
    }

    public boolean isBoundaryFace() {
        return this.boundaryFace;
    }
}
