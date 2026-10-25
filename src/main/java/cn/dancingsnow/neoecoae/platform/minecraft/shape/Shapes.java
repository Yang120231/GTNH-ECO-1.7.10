package cn.dancingsnow.neoecoae.platform.minecraft.shape;

public final class Shapes {

    private Shapes() {}

    public static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (x1 < x0 || y1 < y0 || z1 < z0) throw new IllegalArgumentException("Inverted source shape");
        return new VoxelShape(
            java.util.Collections
                .singletonList(new VoxelShape.Box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16)));
    }

    public static VoxelShape block() {
        return box(0, 0, 0, 16, 16, 16);
    }

    public static VoxelShape join(VoxelShape first, VoxelShape second, BooleanOp operation) {
        if (operation != BooleanOp.OR) throw new IllegalArgumentException("Unsupported source shape operation");
        var boxes = new java.util.ArrayList<>(first.boxes());
        boxes.addAll(second.boxes());
        return new VoxelShape(boxes);
    }
}
