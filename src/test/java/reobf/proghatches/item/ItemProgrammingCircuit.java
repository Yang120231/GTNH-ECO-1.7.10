package reobf.proghatches.item;

import java.util.Optional;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Optional API fixture with the same public getCircuit contract as Programmable Hatches. */
public class ItemProgrammingCircuit extends Item {

    public static Optional<ItemStack> getCircuit(ItemStack stack) {
        return stack.hasTagCompound() ? Optional.ofNullable(
            ItemStack.loadItemStackFromNBT(
                stack.getTagCompound()
                    .getCompoundTag("targetCircuit")))
            : Optional.empty();
    }
}
