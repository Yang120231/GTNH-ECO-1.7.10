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
    private final boolean cullable;
    private final float worldShade;
    private final double sampleX;
    private final double sampleY;
    private final double sampleZ;

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
        this.cullable = cullDirection != ForgeDirection.UNKNOWN;
        this.worldShade = calculateWorldShade(normal);
        this.sampleX = sampleCoordinate(vertices, 0);
        this.sampleY = sampleCoordinate(vertices, 1);
        this.sampleZ = sampleCoordinate(vertices, 2);
    }

    public String getTexture() {
        return this.texture;
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

    public boolean isCullable() {
        return this.cullable;
    }

    public float getWorldShade() {
        return this.worldShade;
    }

    public double getSampleX() {
        return this.sampleX;
    }

    public double getSampleY() {
        return this.sampleY;
    }

    public double getSampleZ() {
        return this.sampleZ;
    }

    private static double sampleCoordinate(double[][] vertices, int axis) {
        double total = 0.0D;
        for (double[] vertex : vertices) {
            total += vertex[axis];
        }
        return total / vertices.length;
    }

    private static float calculateWorldShade(ForgeDirection normal) {
        if (normal == ForgeDirection.DOWN) {
            return 0.5F;
        }
        if (normal == ForgeDirection.UP) {
            return 1.0F;
        }
        if (normal == ForgeDirection.NORTH || normal == ForgeDirection.SOUTH) {
            return 0.8F;
        }
        return 0.6F;
    }
}
