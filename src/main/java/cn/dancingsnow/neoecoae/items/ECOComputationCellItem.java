package cn.dancingsnow.neoecoae.items;

import java.util.List;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.registry.NativeMaterialItem;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public class ECOComputationCellItem extends NativeMaterialItem implements IECOComputationCellItem {

    private final StorageTierDescriptor tier;

    public ECOComputationCellItem(StorageTierDescriptor tier) {
        super(
            "eco_computation_cell_" + tier.getPanelTierName()
                .getString()
                .toLowerCase(java.util.Locale.ROOT));
        setMaxStackSize(8);
        this.tier = tier;
    }

    public StorageTierDescriptor getTier() {
        return tier;
    }

    @Override
    protected void appendTooltip(ItemStack stack, List<Component> tooltipComponents) {
        if (stack.getItem() instanceof IECOComputationCellItem cellItem) {
            StorageTierDescriptor tier = cellItem.getTier();
            tooltipComponents.add(
                Component.translatable(
                    "tooltip.neoecoae.computation_cell",
                    java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT)
                        .format(cellItem.getComputationBytes(stack))));
            tooltipComponents.add(Component.translatable("tooltip.neoecoae.computation_cell.install"));
        }
    }
}
