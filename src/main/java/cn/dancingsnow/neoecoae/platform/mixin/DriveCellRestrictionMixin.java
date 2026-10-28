package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.tile.storage.TileDrive;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;

/** Drive slots and automation share this check; the native IO port keeps its own acceptance rules. */
@Mixin(value = TileDrive.class, remap = false)
public abstract class DriveCellRestrictionMixin {

    @Inject(
        // This Minecraft override uses an SRG name in the release AE2 jar.
        method = { "isItemValidForSlot(ILnet/minecraft/item/ItemStack;)Z",
            "func_94041_b(ILnet/minecraft/item/ItemStack;)Z" },
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void neoecoae$rejectEcoCell(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack != null && stack.getItem() instanceof IECOStorageCellItem) callback.setReturnValue(false);
    }
}
