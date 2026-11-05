package cn.dancingsnow.neoecoae.dev;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Legacy chemical materials are exposed through the registered fluid channel. */
final class GeneratorPool {

    private static List<AEKey> cached;

    @SuppressWarnings("unchecked")
    static List<AEKey> keys() {
        if (cached != null) return cached;
        var keys = new LinkedHashSet<AEKey>();
        for (Object entry : Item.itemRegistry) {
            Item item = (Item) entry;
            if (item == Item.getItemFromBlock(DevResourceGenerator.BLOCK)) continue;
            var variants = new ArrayList<ItemStack>();
            if (item.getHasSubtypes()) item.getSubItems(item, item.getCreativeTab(), variants);
            else variants.add(new ItemStack(item));
            for (ItemStack stack : variants) {
                if (stack != null && stack.getItem() != null && !stack.hasTagCompound()) keys.add(AEItemKey.of(stack));
            }
        }
        for (var fluid : FluidRegistry.getRegisteredFluids()
            .values()) keys.add(AEFluidKey.of(fluid));
        cached = new ArrayList<>(keys);
        return cached;
    }
}
