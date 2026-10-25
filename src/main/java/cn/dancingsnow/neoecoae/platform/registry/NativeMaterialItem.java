package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NativeMaterialItem extends Item {

    private final String id;

    public NativeMaterialItem(String id) {
        this.id = id;
    }

    @Override
    public int getSpriteNumber() {
        return this instanceof cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem ? 0
            : super.getSpriteNumber();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraft.util.IIcon getIconFromDamage(int damage) {
        return this instanceof cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem
            ? cn.dancingsnow.neoecoae.platform.render.NativeModelItems.icon(this)
            : super.getIconFromDamage(damage);
    }

    protected void appendTooltip(net.minecraft.item.ItemStack stack,
        java.util.List<cn.dancingsnow.neoecoae.platform.minecraft.Component> lines) {
        if ("eco_infinite_cell_component".equals(id)) {
            lines.add(
                cn.dancingsnow.neoecoae.platform.minecraft.Component
                    .translatable("tooltip.neoecoae.infinite_component.header")
                    .withStyle(
                        net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE,
                        net.minecraft.util.EnumChatFormatting.BOLD));
            lines.add(
                cn.dancingsnow.neoecoae.platform.minecraft.Component
                    .translatable("tooltip.neoecoae.infinite_component.components")
                    .withStyle(net.minecraft.util.EnumChatFormatting.GRAY));
            lines.add(
                cn.dancingsnow.neoecoae.platform.minecraft.Component
                    .translatable("tooltip.neoecoae.infinite_component.matrices")
                    .withStyle(net.minecraft.util.EnumChatFormatting.AQUA));
        }
    }

    @Override
    public net.minecraft.item.EnumRarity getRarity(net.minecraft.item.ItemStack stack) {
        return CoreItemCatalog.rarity(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(net.minecraft.item.ItemStack stack, net.minecraft.entity.player.EntityPlayer player,
        java.util.List lines, boolean advanced) {
        java.util.List<cn.dancingsnow.neoecoae.platform.minecraft.Component> text = new java.util.ArrayList<>();
        appendTooltip(stack, text);
        for (var line : text) lines.add(
            line.nativeText()
                .getFormattedText());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        if (this instanceof cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem) {
            itemIcon = cn.dancingsnow.neoecoae.platform.render.NativeModelItems.icon(this);
            return;
        }
        itemIcon = cn.dancingsnow.neoecoae.platform.render.ItemModelLayers.icons(id, register)[0];
    }
}
