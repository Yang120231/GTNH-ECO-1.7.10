package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.StorageName;
import appeng.items.contents.WirelessPatternTerminalGuiObject;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.inventory.InvOperation;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cpw.mods.fml.common.FMLCommonHandler;

/** Loading another encoded pattern starts another draft; encoding itself is bracketed by the container mixin. */
@Mixin(value = { PartPatternTerminal.class, WirelessPatternTerminalGuiObject.class }, remap = false)
public abstract class PatternTerminalSourceMixin {

    @Inject(method = "onChangeInventory", at = @At("HEAD"), require = 1)
    private void neoecoae$patternLoaded(IInventory inventory, int slot, InvOperation operation, ItemStack removed,
        ItemStack added, CallbackInfo ci) {
        IPatternTerminal terminal = (IPatternTerminal) (Object) this;
        if (slot != 1 || inventory != terminal.getInventoryByName(StorageName.CRAFTING_PATTERN.getName())) return;
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) return;
        if (ServerTasks.isServerThread()) PatternUploadSessions.patternChanged(inventory, added);
        else {
            ItemStack pattern = added == null ? null : added.copy();
            ServerTasks.captureSession()
                .execute(() -> PatternUploadSessions.patternChanged(inventory, pattern));
        }
    }
}
