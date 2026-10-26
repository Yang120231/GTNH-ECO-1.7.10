package cn.dancingsnow.neoecoae.platform.registry;

public final class NativeMachineItemBlock extends net.minecraft.item.ItemBlock {

    public NativeMachineItemBlock(net.minecraft.block.Block block) {
        super(block);
    }

    @Override
    public String getUnlocalizedName(net.minecraft.item.ItemStack stack) {
        return "block.neoecoae." + ((NativeModelIdentity) field_150939_a).model();
    }

    @Override
    public String getItemStackDisplayName(net.minecraft.item.ItemStack stack) {
        return net.minecraft.util.StatCollector.translateToLocal(getUnlocalizedName(stack) + ".name");
    }

    @Override
    public net.minecraft.item.EnumRarity getRarity(net.minecraft.item.ItemStack stack) {
        return CoreBlocks.rarity(((NativeModelIdentity) field_150939_a).model());
    }
}
