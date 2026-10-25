package cn.dancingsnow.neoecoae.platform.minecraft.shape;

import java.util.List;

import net.minecraft.util.AxisAlignedBB;

/** Immutable union of source boxes; the native block adapter exposes each box to Forge collision and ray queries. */
public final class VoxelShape {

    @com.github.bsideup.jabel.Desugar
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public AxisAlignedBB at(int x, int y, int z) {
            return AxisAlignedBB.getBoundingBox(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
        }
    }

    private final List<Box> boxes;

    VoxelShape(List<Box> boxes) {
        this.boxes = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(boxes));
    }

    public List<Box> boxes() {
        return boxes;
    }
}
