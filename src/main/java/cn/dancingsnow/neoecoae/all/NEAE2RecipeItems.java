package cn.dancingsnow.neoecoae.all;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.util.AEColor;
import appeng.api.util.AEColoredItemDefinition;

public final class NEAE2RecipeItems {

    private NEAE2RecipeItems() {}

    public static ItemStack cellComponent256k() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .cell256kPart());
    }

    public static ItemStack denseEnergyCell() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .energyCellDense());
    }

    public static ItemStack controller() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .controller());
    }

    public static ItemStack drive() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .drive());
    }

    public static ItemStack interfaceBlock() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .iface());
    }

    public static ItemStack interfacePart() {
        return stack(
            AEApi.instance()
                .definitions()
                .parts()
                .iface());
    }

    public static ItemStack quartzVibrantGlass() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .quartzVibrantGlass());
    }

    public static ItemStack craftingUnit() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingUnit());
    }

    public static ItemStack craftingAccelerator() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingAccelerator());
    }

    public static ItemStack craftingMonitor() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingMonitor());
    }

    public static ItemStack craftingStorage256k() {
        return stack(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingStorage256k());
    }

    public static ItemStack patternProvider() {
        return stack(
            AEApi.instance()
                .definitions()
                .parts()
                .patternTerminalEx());
    }

    public static ItemStack smartDenseCable() {
        return coloredStack(
            AEApi.instance()
                .definitions()
                .parts()
                .cableDense());
    }

    public static ItemStack coveredDenseCable() {
        return coloredStack(
            AEApi.instance()
                .definitions()
                .parts()
                .cableDenseCovered());
    }

    public static ItemStack fluixCrystal() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .fluixCrystal());
    }

    public static ItemStack fluixDust() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .fluixDust());
    }

    public static ItemStack certusQuartzDust() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .certusQuartzDust());
    }

    public static ItemStack certusQuartzCrystalCharged() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .certusQuartzCrystalCharged());
    }

    public static ItemStack skyDust() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .skyDust());
    }

    public static ItemStack silicon() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .silicon());
    }

    public static ItemStack siliconPrint() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .siliconPrint());
    }

    public static ItemStack engineeringProcessorPress() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .engProcessorPress());
    }

    public static ItemStack calculationProcessorPress() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .calcProcessorPress());
    }

    public static ItemStack logicProcessorPress() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .logicProcessorPress());
    }

    public static ItemStack logicProcessor() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .logicProcessor());
    }

    public static ItemStack calculationProcessor() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .calcProcessor());
    }

    public static ItemStack engineeringProcessor() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .engProcessor());
    }

    public static ItemStack singularity() {
        return stack(
            AEApi.instance()
                .definitions()
                .materials()
                .singularity());
    }

    private static ItemStack stack(IItemDefinition definition) {
        if (definition == null || !definition.isEnabled()) {
            return null;
        }
        com.google.common.base.Optional<ItemStack> stack = definition.maybeStack(1);
        return stack.isPresent() ? stack.get() : null;
    }

    private static ItemStack coloredStack(AEColoredItemDefinition definition) {
        return definition == null ? null : definition.stack(AEColor.Transparent, 1);
    }
}
