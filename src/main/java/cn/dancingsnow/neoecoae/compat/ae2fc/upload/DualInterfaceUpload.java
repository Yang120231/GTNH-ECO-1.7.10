package cn.dancingsnow.neoecoae.compat.ae2fc.upload;

import net.minecraft.item.ItemStack;

import appeng.api.util.IInterfaceViewable;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * AE2FC's 2-in-1 interface extends AE2's ordinary interface. Only the concrete class or its registered item identity
 * marks it as dual; a shared parent class never does, so ordinary interfaces are not mislabelled.
 */
public final class DualInterfaceUpload {

    private DualInterfaceUpload() {}

    public static boolean isDualInterface(IInterfaceViewable viewable) {
        String name = viewable.getClass()
            .getName();
        if ("com.glodblock.github.common.tile.TileFluidInterface".equals(name)
            || "com.glodblock.github.common.parts.PartFluidInterface".equals(name)) return true;
        ItemStack representation;
        try {
            representation = viewable.getSelfRep();
        } catch (RuntimeException unavailable) {
            return false;
        }
        if (representation == null || representation.getItem() == null) return false;
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(representation.getItem());
        return id != null && "ae2fc".equals(id.modId)
            && ("fluid_interface".equals(id.name) || "part_fluid_interface".equals(id.name));
    }
}
