package cn.dancingsnow.neoecoae.platform.minecraft;

public final class NativeWorldIds {

    private NativeWorldIds() {}

    public static ResourceLocation dimension(net.minecraft.world.World world) {
        int id = world.provider.dimensionId;
        return ResourceLocation.fromNamespaceAndPath(
            id == 0 || id == -1 || id == 1 ? "minecraft" : "forge",
            id == 0 ? "overworld" : id == -1 ? "the_nether" : id == 1 ? "the_end" : "dimension_" + id);
    }
}
