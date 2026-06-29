package cn.dancingsnow.neoecoae.client.render.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;

public class BakedEcoModel {

    private static final double BOUNDARY_EPSILON = 0.0001D;

    private final Map<ModelFacing, List<BakedQuad>> quads = new EnumMap<>(ModelFacing.class);

    public BakedEcoModel(ModernModel source) {
        this(source, 0.0D, 0.0D, 0.0D);
    }

    private BakedEcoModel(ModernModel source, double offsetX, double offsetY, double offsetZ) {
        this(source, offsetX, offsetY, offsetZ, true);
    }

    private BakedEcoModel(ModernModel source, double offsetX, double offsetY, double offsetZ,
        boolean modelBoundsAreWorldBoundary) {
        for (ModelFacing facing : ModelFacing.values()) {
            this.quads.put(
                facing,
                bakeFacing(
                    source,
                    facing,
                    offsetX,
                    offsetY,
                    offsetZ,
                    modelBoundsAreWorldBoundary,
                    IdentityTransform.INSTANCE));
        }
    }

    public static BakedEcoModel offsetSubModel(ModernModel source, double offsetX, double offsetY, double offsetZ) {
        return new BakedEcoModel(source, offsetX, offsetY, offsetZ, false);
    }

    public static BakedEcoModel transformedSubModel(ModernModel source, VertexTransform transform) {
        BakedEcoModel model = new BakedEcoModel();
        for (ModelFacing facing : ModelFacing.values()) {
            model.quads.put(facing, bakeRawFacing(source, facing, transform));
        }
        return model;
    }

    private BakedEcoModel() {}

    public List<BakedQuad> getQuads(ModelFacing facing) {
        return this.quads.get(facing);
    }

    public int getMaxQuadCount() {
        int max = 0;
        for (List<BakedQuad> facingQuads : this.quads.values()) {
            max = Math.max(max, facingQuads.size());
        }
        return max;
    }

    private static List<BakedQuad> bakeFacing(ModernModel model, ModelFacing facing, double offsetX, double offsetY,
        double offsetZ, boolean modelBoundsAreWorldBoundary, VertexTransform transform) {
        List<BakedQuad> bakedQuads = new ArrayList<>();
        ModelBounds bounds = calculateBounds(model, facing, offsetX, offsetY, offsetZ, transform);
        for (ModelElement element : model.getElements()) {
            for (ModelFace face : element.getFaces()
                .values()) {
                bakedQuads.add(
                    bakeFace(
                        model,
                        element,
                        face,
                        facing,
                        bounds,
                        offsetX,
                        offsetY,
                        offsetZ,
                        modelBoundsAreWorldBoundary,
                        transform));
            }
        }
        return bakedQuads;
    }

    private static List<BakedQuad> bakeRawFacing(ModernModel model, ModelFacing facing, VertexTransform transform) {
        List<BakedQuad> bakedQuads = new ArrayList<>();
        ModelBounds bounds = calculateRawBounds(model, facing, transform);
        for (ModelElement element : model.getElements()) {
            for (ModelFace face : element.getFaces()
                .values()) {
                bakedQuads.add(bakeRawFace(model, element, face, facing, bounds, transform));
            }
        }
        return bakedQuads;
    }

    private static BakedQuad bakeFace(ModernModel model, ModelElement element, ModelFace face, ModelFacing facing,
        ModelBounds bounds, double offsetX, double offsetY, double offsetZ, boolean modelBoundsAreWorldBoundary,
        VertexTransform transform) {
        double[][] vertices = faceVertices(element, face.getSide());
        double[][] rotatedVertices = new double[4][3];
        for (int i = 0; i < vertices.length; i++) {
            rotatedVertices[i] = transform
                .apply(rotateY(offset(vertices[i], offsetX, offsetY, offsetZ), facing), facing);
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
            rotateUv(uvVertices(face, face.getSide()), face.getRotation()),
            face.isFullBright(),
            element.isShade(),
            modelBoundsAreWorldBoundary && isBoundaryFace(rotatedVertices, normal, bounds));
    }

    private static BakedQuad bakeRawFace(ModernModel model, ModelElement element, ModelFace face, ModelFacing facing,
        ModelBounds bounds, VertexTransform transform) {
        double[][] vertices = faceVertices(element, face.getSide());
        double[][] transformedVertices = new double[4][3];
        for (int i = 0; i < vertices.length; i++) {
            transformedVertices[i] = transform.apply(vertices[i], facing);
        }

        ForgeDirection normal = rotateDirection(face.getSide(), facing);
        return new BakedQuad(
            resolveTexture(model, face.getTexture()),
            face.getCullFace(),
            ForgeDirection.UNKNOWN,
            normal,
            transformedVertices,
            rotateUv(uvVertices(face, face.getSide()), face.getRotation()),
            face.isFullBright(),
            element.isShade(),
            isBoundaryFace(transformedVertices, normal, bounds));
    }

    private static boolean isBoundaryFace(double[][] vertices, ForgeDirection normal, ModelBounds bounds) {
        switch (normal) {
            case DOWN:
                return isPlaneAt(vertices, 1, bounds.minY);
            case UP:
                return isPlaneAt(vertices, 1, bounds.maxY);
            case NORTH:
                return isPlaneAt(vertices, 2, bounds.minZ);
            case SOUTH:
                return isPlaneAt(vertices, 2, bounds.maxZ);
            case WEST:
                return isPlaneAt(vertices, 0, bounds.minX);
            case EAST:
                return isPlaneAt(vertices, 0, bounds.maxX);
            default:
                return true;
        }
    }

    private static boolean isPlaneAt(double[][] vertices, int axis, double boundary) {
        for (double[] vertex : vertices) {
            if (Math.abs(vertex[axis] - boundary) > BOUNDARY_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static ModelBounds calculateBounds(ModernModel model, ModelFacing facing, double offsetX, double offsetY,
        double offsetZ, VertexTransform transform) {
        ModelBounds bounds = new ModelBounds();
        for (ModelElement element : model.getElements()) {
            double x1 = element.getFrom()[0] / 16.0D;
            double y1 = element.getFrom()[1] / 16.0D;
            double z1 = element.getFrom()[2] / 16.0D;
            double x2 = element.getTo()[0] / 16.0D;
            double y2 = element.getTo()[1] / 16.0D;
            double z2 = element.getTo()[2] / 16.0D;
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x1, y1, z1 }, offsetX, offsetY, offsetZ), facing), facing));
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x1, y1, z2 }, offsetX, offsetY, offsetZ), facing), facing));
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x2, y1, z1 }, offsetX, offsetY, offsetZ), facing), facing));
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x2, y1, z2 }, offsetX, offsetY, offsetZ), facing), facing));
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x1, y2, z1 }, offsetX, offsetY, offsetZ), facing), facing));
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x1, y2, z2 }, offsetX, offsetY, offsetZ), facing), facing));
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x2, y2, z1 }, offsetX, offsetY, offsetZ), facing), facing));
            bounds.accept(
                transform
                    .apply(rotateY(offset(new double[] { x2, y2, z2 }, offsetX, offsetY, offsetZ), facing), facing));
        }
        return bounds;
    }

    private static ModelBounds calculateRawBounds(ModernModel model, ModelFacing facing, VertexTransform transform) {
        ModelBounds bounds = new ModelBounds();
        for (ModelElement element : model.getElements()) {
            double x1 = element.getFrom()[0] / 16.0D;
            double y1 = element.getFrom()[1] / 16.0D;
            double z1 = element.getFrom()[2] / 16.0D;
            double x2 = element.getTo()[0] / 16.0D;
            double y2 = element.getTo()[1] / 16.0D;
            double z2 = element.getTo()[2] / 16.0D;
            bounds.accept(transform.apply(new double[] { x1, y1, z1 }, facing));
            bounds.accept(transform.apply(new double[] { x1, y1, z2 }, facing));
            bounds.accept(transform.apply(new double[] { x2, y1, z1 }, facing));
            bounds.accept(transform.apply(new double[] { x2, y1, z2 }, facing));
            bounds.accept(transform.apply(new double[] { x1, y2, z1 }, facing));
            bounds.accept(transform.apply(new double[] { x1, y2, z2 }, facing));
            bounds.accept(transform.apply(new double[] { x2, y2, z1 }, facing));
            bounds.accept(transform.apply(new double[] { x2, y2, z2 }, facing));
        }
        return bounds;
    }

    private static class ModelBounds {

        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void accept(double[] vertex) {
            this.minX = Math.min(this.minX, vertex[0]);
            this.minY = Math.min(this.minY, vertex[1]);
            this.minZ = Math.min(this.minZ, vertex[2]);
            this.maxX = Math.max(this.maxX, vertex[0]);
            this.maxY = Math.max(this.maxY, vertex[1]);
            this.maxZ = Math.max(this.maxZ, vertex[2]);
        }
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

    private static double[] offset(double[] vertex, double offsetX, double offsetY, double offsetZ) {
        return new double[] { vertex[0] + offsetX, vertex[1] + offsetY, vertex[2] + offsetZ };
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

    public interface VertexTransform {

        double[] apply(double[] vertex, ModelFacing facing);
    }

    private enum IdentityTransform implements VertexTransform {

        INSTANCE;

        @Override
        public double[] apply(double[] vertex, ModelFacing facing) {
            return vertex;
        }
    }
}
