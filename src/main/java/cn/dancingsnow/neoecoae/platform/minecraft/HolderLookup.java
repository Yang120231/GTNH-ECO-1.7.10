package cn.dancingsnow.neoecoae.platform.minecraft;

/** Registry context marker: 1.7.10 registry identities are process-wide, supplied by FML. */
public final class HolderLookup {

    private HolderLookup() {}

    public interface Provider {

        default net.minecraftforge.fluids.Fluid fluid(String name) {
            return net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        }

        default ResourceLocation itemKey(net.minecraft.item.Item item) {
            String id = (String) net.minecraft.item.Item.itemRegistry.getNameForObject(item);
            return id == null ? null : ResourceLocation.parse(id);
        }

        default net.minecraft.item.Item item(ResourceLocation id) {
            Object item = net.minecraft.item.Item.itemRegistry.getObject(id.toString());
            return item instanceof net.minecraft.item.Item ? (net.minecraft.item.Item) item : null;
        }
    }
}
