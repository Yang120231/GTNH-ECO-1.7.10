package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Observes only destinations which AE2 actually accepted, without repeating its insertion probes. */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class ProcessingTargetMixin implements ECOProcessingTargetProvider {

    @Unique
    private final LinkedHashSet<Target> neoecoae$targets = new LinkedHashSet<>();

    @Inject(method = "pushPattern", at = @At("HEAD"), require = 0)
    private void neoecoae$beginPush(ICraftingPatternDetails pattern, InventoryCrafting inputs,
        CallbackInfoReturnable<Boolean> callback) {
        neoecoae$targets.clear();
    }

    @Inject(method = "onPushPatternSuccess", at = @At("HEAD"), require = 0)
    private void neoecoae$acceptedTarget(TileEntity tile, ForgeDirection side, ICraftingPatternDetails pattern,
        CallbackInfo callback) {
        if (tile.getWorldObj() != null) neoecoae$targets.add(new Target(tile.getWorldObj().provider.dimensionId,
            new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord)));
    }

    @Override
    public Target neoecoae$getProcessingTarget() {
        return neoecoae$targets.isEmpty() ? null : neoecoae$targets.iterator().next();
    }

    @Override
    public List<Target> neoecoae$getProcessingTargets() {
        return new ArrayList<>(neoecoae$targets);
    }
}
