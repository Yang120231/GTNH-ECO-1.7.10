package cn.dancingsnow.neoecoae.compat.nei;

import net.minecraft.item.ItemStack;

import com.github.vfyjxf.nee.config.NEEConfig;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shared NEE policy for ECO's type-only filters. Quantities belong to pattern inputs, never to these filters. */
@SideOnly(Side.CLIENT)
public final class NEEGhostSlots {

    private NEEGhostSlots() {}

    public static boolean allowsDrop(int button) {
        return (button == 0 || button == 1) && (!Loader.isModLoaded("neenergistics")
            || NEEConfig.enableNEIDragDrop && !(Loader.isModLoaded("NEIAddons") && NEEConfig.useNEIDragFromNEIAddons));
    }

    public static void acceptedDrop(ItemStack dragged) {
        if (Loader.isModLoaded("neenergistics") && !NEEConfig.keepGhostitems) dragged.stackSize = 0;
    }
}
