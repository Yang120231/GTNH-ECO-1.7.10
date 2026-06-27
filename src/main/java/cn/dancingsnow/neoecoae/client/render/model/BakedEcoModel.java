package cn.dancingsnow.neoecoae.client.render.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;

public class BakedEcoModel {

    private final ModernModel source;
    private final Map<ModelFacing, List<BakedQuad>> quads = new EnumMap<ModelFacing, List<BakedQuad>>(
        ModelFacing.class);

    public BakedEcoModel(ModernModel source) {
        this.source = source;
        for (ModelFacing facing : ModelFacing.values()) {
            this.quads.put(facing, bakeFacing(source, facing));
        }
    }

    public ModernModel getSource() {
        return this.source;
    }

    public List<BakedQuad> getQuads(ModelFacing facing) {
        return this.quads.get(facing);
    }

    private static List<BakedQuad> bakeFacing(ModernModel model, ModelFacing facing) {
        List<BakedQuad> bakedQuads = new ArrayList<BakedQuad>();
        for (ModelElement element : model.getElements()) {
            for (ModelFace face : element.getFaces()
                .values()) {
                bakedQuads.add(bakeFace(model, element, face, facing));
            }
        }
        return bakedQuads;
    }

    private static BakedQuad bakeFace(ModernModel model, ModelElement element, ModelFace face, ModelFacing facing) {
        double[][] vertices = faceVertices(element, face.getSide());
        double[][] rotatedVertices = new double[4][3];
        for (int i = 0; i < vertices.length; i++) {
            rotatedVertices[i] = rotateY(vertices[i], facing);
        }

        String cullFace = face.getCullFace();
        ForgeDirection cullDirection = cullFace != null ? rotateDirection(toDirection(cullFace), facing)
            : ForgeDirection.UNKNOWN;
        ForgeDirection normal = rotateDirection(face.getSide(), facing);
        return new BakedQuad(
            resolveTexture(model, face.getTexture()),
            cullFace,
            cullDirection,
            normal,
            rotatedVertices,
            rotateUv(uvVertices(face, face.getSide()), face.getRotation()));
    }

    private static String resolveTexture(ModernModel model, String texture) {
        String key = texture;
        int safety = 0;
        while (key.startsWith("#") && safety++ < 8) {
            String resolved = model.getTextures()
                .get(key.substring(1));
            if (resolved == null) {
                return key;
            }
            key = resolved;
        }
        return key;
    }

    private static double[][] faceVertices(ModelElement element, ForgeDirection side) {
        double x1 = element.getFrom()[0] / 16.0D;
        double y1 = element.getFrom()[1] / 16.0D;
        double z1 = element.getFrom()[2] / 16.0D;
        double x2 = element.getTo()[0] / 16.0D;
        double y2 = element.getTo()[1] / 16.0D;
        double z2 = element.getTo()[2] / 16.0D;

        switch (side) {
            case DOWN:
                return new double[][] { { x1, y1, z2 }, { x1, y1, z1 }, { x2, y1, z1 }, { x2, y1, z2 } };
            case UP:
                return new double[][] { { x2, y2, z2 }, { x2, y2, z1 }, { x1, y2, z1 }, { x1, y2, z2 } };
            case NORTH:
                return new double[][] { { x1, y2, z1 }, { x2, y2, z1 }, { x2, y1, z1 }, { x1, y1, z1 } };
            case SOUTH:
                return new double[][] { { x1, y2, z2 }, { x1, y1, z2 }, { x2, y1, z2 }, { x2, y2, z2 } };
            case WEST:
                return new double[][] { { x1, y2, z2 }, { x1, y2, z1 }, { x1, y1, z1 }, { x1, y1, z2 } };
            case EAST:
                return new double[][] { { x2, y1, z2 }, { x2, y1, z1 }, { x2, y2, z1 }, { x2, y2, z2 } };
            default:
                return new double[0][0];
        }
    }

    private static double[][] uvVertices(ModelFace face, ForgeDirection side) {
        double u1 = face.getMinU();
        double v1 = face.getMinV();
        double u2 = face.getMaxU();
        double v2 = face.getMaxV();

        switch (side) {
            case DOWN:
                return new double[][] { { u1, v2 }, { u1, v1 }, { u2, v1 }, { u2, v2 } };
            case UP:
                return new double[][] { { u2, v2 }, { u2, v1 }, { u1, v1 }, { u1, v2 } };
            case NORTH:
                return new double[][] { { u2, v1 }, { u1, v1 }, { u1, v2 }, { u2, v2 } };
            case SOUTH:
                return new double[][] { { u1, v1 }, { u1, v2 }, { u2, v2 }, { u2, v1 } };
            case WEST:
                return new double[][] { { u2, v1 }, { u1, v1 }, { u1, v2 }, { u2, v2 } };
            case EAST:
                return new double[][] { { u1, v2 }, { u2, v2 }, { u2, v1 }, { u1, v1 } };
            default:
                return new double[][] { { u1, v2 }, { u1, v1 }, { u2, v1 }, { u2, v2 } };
        }
    }

    private static double[][] rotateUv(double[][] uv, int rotation) {
        int steps = ((rotation % 360) + 360) % 360 / 90;
        if (steps == 0) {
            return uv;
        }

        double[][] rotated = new double[uv.length][2];
        for (int i = 0; i < uv.length; i++) {
            rotated[i] = uv[(i + steps) % uv.length];
        }
        return rotated;
    }

    private static double[] rotateY(double[] vertex, ModelFacing facing) {
        double x = vertex[0] - 0.5D;
        double z = vertex[2] - 0.5D;
        double rotatedX = x;
        double rotatedZ = z;

        switch (facing) {
            case EAST:
                rotatedX = -z;
                rotatedZ = x;
                break;
            case SOUTH:
                rotatedX = -x;
                rotatedZ = -z;
                break;
            case WEST:
                rotatedX = z;
                rotatedZ = -x;
                break;
            case NORTH:
            default:
                break;
        }

        return new double[] { rotatedX + 0.5D, vertex[1], rotatedZ + 0.5D };
    }

    private static ForgeDirection rotateDirection(ForgeDirection direction, ModelFacing facing) {
        if (direction == ForgeDirection.UP || direction == ForgeDirection.DOWN || direction == ForgeDirection.UNKNOWN) {
            return direction;
        }

        ForgeDirection current = direction;
        int rotations = facing.getMeta();
        for (int i = 0; i < rotations; i++) {
            current = rotateDirectionClockwise(current);
        }
        return current;
    }

    private static ForgeDirection rotateDirectionClockwise(ForgeDirection direction) {
        switch (direction) {
            case NORTH:
                return ForgeDirection.EAST;
            case EAST:
                return ForgeDirection.SOUTH;
            case SOUTH:
                return ForgeDirection.WEST;
            case WEST:
                return ForgeDirection.NORTH;
            default:
                return direction;
        }
    }

    private static ForgeDirection toDirection(String side) {
        if ("down".equals(side)) {
            return ForgeDirection.DOWN;
        }
        if ("up".equals(side)) {
            return ForgeDirection.UP;
        }
        if ("north".equals(side)) {
            return ForgeDirection.NORTH;
        }
        if ("south".equals(side)) {
            return ForgeDirection.SOUTH;
        }
        if ("west".equals(side)) {
            return ForgeDirection.WEST;
        }
        if ("east".equals(side)) {
            return ForgeDirection.EAST;
        }
        return ForgeDirection.UNKNOWN;
    }
}
