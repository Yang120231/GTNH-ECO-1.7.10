package cn.dancingsnow.neoecoae.all;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public final class NECreativeTabs {

    public static final CreativeTabs NEO_ECO_AE = new CreativeTabs("neoecoae") {

        @Override
        public Item getTabIconItem() {
            return NEItems.aluminumIngot;
        }
    };

    private NECreativeTabs() {}
}
