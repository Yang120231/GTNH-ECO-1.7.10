package cn.dancingsnow.neoecoae.platform.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.dancingsnow.neoecoae.platform.forge.PlanningSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class NativeMachineItemBlock extends net.minecraft.item.ItemBlock {

    public NativeMachineItemBlock(net.minecraft.block.Block block) {
        super(block);
    }

    @Override
    public String getUnlocalizedName(net.minecraft.item.ItemStack stack) {
        return getUnlocalizedName();
    }

    /** AE2 and other mods translate {@code getUnlocalizedName() + ".name"} without a stack. */
    @Override
    public String getUnlocalizedName() {
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

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(net.minecraft.item.ItemStack stack, net.minecraft.entity.player.EntityPlayer player,
        List lines, boolean advanced) {
        List<Component> details = controllerTooltip(((NativeModelIdentity) field_150939_a).model());
        if (details.isEmpty()) return;
        if (!net.minecraft.client.gui.GuiScreen.isShiftKeyDown()) {
            lines.add(
                Component.translatable("tooltip.neoecoae.holdshift")
                    .withStyle(net.minecraft.util.EnumChatFormatting.GRAY)
                    .nativeText()
                    .getFormattedText());
            return;
        }
        for (Component line : details) lines.add(
            line.nativeText()
                .getFormattedText());
    }

    /** NETooltips' shift details for the crafting and computation controllers (the network-switch hosts). */
    static List<Component> controllerTooltip(String model) {
        List<Component> result = new ArrayList<>();
        if (model.startsWith("crafting_system_")) {
            result.add(Component.translatable("tooltip.neoecoae.crafting_system"));
            result.add(
                Component
                    .translatable("tooltip.neoecoae.max_lenth", PlanningSettings.Multiblock.craftingSystemMaxLength));
        } else if (model.startsWith("computation_system_")) {
            result.add(Component.translatable("tooltip.neoecoae.computation_system"));
            result.add(
                Component.translatable(
                    "tooltip.neoecoae.max_lenth",
                    PlanningSettings.Multiblock.computationSystemMaxLength));
            for (int index = 0; index < 5; index++)
                result.add(Component.translatable("tooltip.neoecoae.computation_system_desc." + index));
        } else return Collections.emptyList();
        return result;
    }
}
