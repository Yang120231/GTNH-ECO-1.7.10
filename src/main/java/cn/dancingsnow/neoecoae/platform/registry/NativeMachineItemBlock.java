package cn.dancingsnow.neoecoae.platform.registry;

public final class NativeMachineItemBlock extends net.minecraft.item.ItemBlock {

    public NativeMachineItemBlock(net.minecraft.block.Block block) {
        super(block);
    }

    @Override
    public String getUnlocalizedName(net.minecraft.item.ItemStack stack) {
        return "block.neoecoae." + ((NativeMachineBlock) field_150939_a).model();
    }
}
