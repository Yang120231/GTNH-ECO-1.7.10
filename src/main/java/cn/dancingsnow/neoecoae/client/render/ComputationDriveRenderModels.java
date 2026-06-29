package cn.dancingsnow.neoecoae.client.render;

import java.util.HashMap;
import java.util.Map;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

public final class ComputationDriveRenderModels {

    private static final String CELL_L4 = "compute/cell_l4";
    private static final String CELL_L6 = "compute/cell_l6";
    private static final String CELL_L9 = "compute/cell_l9";
    private static final String CELL_L4_FORMED = "compute/cell_l4_formed";
    private static final String CELL_L6_FORMED = "compute/cell_l6_formed";
    private static final String CELL_L9_FORMED = "compute/cell_l9_formed";
    private static final String CABLE_L4 = "compute/cable_l4";
    private static final String CABLE_L6 = "compute/cable_l6";
    private static final String CABLE_L9 = "compute/cable_l9";
    private static final String CABLE_L4_DIS = "compute/cable_l4_dis";
    private static final String CABLE_L6_DIS = "compute/cable_l6_dis";
    private static final String CABLE_L9_DIS = "compute/cable_l9_dis";
    private static final Map<String, BakedEcoModel> CELL_MODELS = new HashMap<String, BakedEcoModel>();
    private static final Map<String, BakedEcoModel> CABLE_MODELS = new HashMap<String, BakedEcoModel>();

    private ComputationDriveRenderModels() {}

    public static void preload() {
        for (String modelName : new String[] { CELL_L4, CELL_L6, CELL_L9, CELL_L4_FORMED, CELL_L6_FORMED,
            CELL_L9_FORMED }) {
            loadCell(modelName);
        }
    }

    public static BakedEcoModel getCell(String tier, boolean formed, ECOControllerTier driveTier) {
        return loadCell(modelForCell(tier, formed && canWork(tier, driveTier)));
    }

    public static BakedEcoModel getCable(ECOControllerTier tier, boolean connected, boolean lowerDrive) {
        String modelName = modelForCable(tier, connected);
        String cacheKey = modelName + "|" + connected + "|" + lowerDrive;
        BakedEcoModel model = CABLE_MODELS.get(cacheKey);
        if (model != null) {
            return model;
        }
        model = BakedEcoModel.transformedSubModel(
            ModernModelLoader.loadBlockModel(modelName),
            (vertex, facing) -> transformCableVertex(vertex, facing, connected, lowerDrive));
        CABLE_MODELS.put(cacheKey, model);
        NeoECOAE.LOG.debug("Loaded ECO computation drive cable model {} with {} quads", cacheKey,
            model.getMaxQuadCount());
        return model;
    }

    private static BakedEcoModel loadCell(String modelName) {
        BakedEcoModel model = CELL_MODELS.get(modelName);
        if (model != null) {
            return model;
        }
        model = BakedEcoModel.transformedSubModel(
            ModernModelLoader.loadBlockModel(modelName),
            ComputationDriveRenderModels::transformDriveInteriorVertex);
        CELL_MODELS.put(modelName, model);
        NeoECOAE.LOG.debug("Loaded ECO computation drive model {} with {} quads", modelName, model.getMaxQuadCount());
        return model;
    }

    private static double[] transformDriveInteriorVertex(double[] vertex, ModelFacing facing) {
        double[] rotated = rotateAroundOriginY(vertex, facing);
        return new double[] {
            rotated[0] + 0.5D + 0.25D * facing.getDirection().offsetX,
            rotated[1] + 0.5D,
            rotated[2] + 0.5D + 0.25D * facing.getDirection().offsetZ };
    }

    private static double[] transformCableVertex(double[] vertex, ModelFacing facing, boolean connected,
        boolean lowerDrive) {
        double x = vertex[0];
        double y = vertex[1];
        double z = vertex[2];
        if (connected) {
            z -= 0.35D;
        } else if (lowerDrive) {
            y += 0.688D;
            z -= 0.3D;
        } else {
            y -= 0.688D;
            z -= 0.3D;
        }

        if (lowerDrive) {
            x = -x;
            y = -y;
            if (connected) {
                x = -x;
                y = -y;
            }
        }

        return transformDriveInteriorVertex(new double[] { x, y, z }, facing);
    }

    private static double[] rotateAroundOriginY(double[] vertex, ModelFacing facing) {
        double x = vertex[0];
        double z = vertex[2];
        switch (facing) {
            case EAST:
                return new double[] { z, vertex[1], -x };
            case SOUTH:
                return new double[] { -x, vertex[1], -z };
            case WEST:
                return new double[] { -z, vertex[1], x };
            case NORTH:
            default:
                return new double[] { x, vertex[1], z };
        }
    }

    private static String modelForCell(String tier, boolean formed) {
        if ("CE9".equals(tier)) {
            return formed ? CELL_L9_FORMED : CELL_L9;
        }
        if ("CE6".equals(tier)) {
            return formed ? CELL_L6_FORMED : CELL_L6;
        }
        return formed ? CELL_L4_FORMED : CELL_L4;
    }

    private static String modelForCable(ECOControllerTier tier, boolean connected) {
        ECOControllerTier safeTier = tier == null ? ECOControllerTier.L4 : tier;
        if (safeTier == ECOControllerTier.L9) {
            return connected ? CABLE_L9 : CABLE_L9_DIS;
        }
        if (safeTier == ECOControllerTier.L6) {
            return connected ? CABLE_L6 : CABLE_L6_DIS;
        }
        return connected ? CABLE_L4 : CABLE_L4_DIS;
    }

    public static boolean canWork(String cellTier, ECOControllerTier driveTier) {
        return driveTier != null && driveTier.supports(tierForCell(cellTier));
    }

    public static ECOControllerTier tierForCell(String cellTier) {
        if ("CE9".equals(cellTier)) {
            return ECOControllerTier.L9;
        }
        if ("CE6".equals(cellTier)) {
            return ECOControllerTier.L6;
        }
        return ECOControllerTier.L4;
    }
}
