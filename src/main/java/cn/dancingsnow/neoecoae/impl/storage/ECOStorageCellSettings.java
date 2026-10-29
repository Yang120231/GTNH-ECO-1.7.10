package cn.dancingsnow.neoecoae.impl.storage;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CustomData;

/** A persistent cell-local offset, added to the host's priority with signed integer saturation. */
public final class ECOStorageCellSettings {

    private static final String PRIORITY = "neoecoae_storage_priority";

    private ECOStorageCellSettings() {}

    public static boolean canConfigure(ItemStack stack) {
        return !ItemStacks.isEmpty(stack) && !(stack.getItem() instanceof ECOInfiniteResourceCellItem)
            && !ECOInfiniteStorageMember.isMember(stack)
            && !ECOInfiniteStorageMember.isMigrating(stack);
    }

    public static int priority(ItemStack stack) {
        return ItemStacks.isEmpty(stack) ? 0
            : CellComponents.readCustomData(stack)
                .copyTag()
                .getInt(PRIORITY);
    }

    public static void setPriority(ItemStack stack, int priority) {
        if (!canConfigure(stack)) return;
        var data = CellComponents.readCustomData(stack)
            .copyTag();
        data.putInt(PRIORITY, priority);
        CellComponents.writeCustomData(stack, CustomData.of(data));
    }
}
