package cn.dancingsnow.neoecoae.mixin;

import java.util.concurrent.Future;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.Config;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOCraftingJob;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOCraftingSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class MixinCraftingGridPlanner {

    @Inject(
        method = "beginCraftingJob(Lnet/minecraft/world/World;Lappeng/api/networking/IGrid;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/storage/data/IAEStack;Lappeng/api/config/CraftingMode;Lappeng/api/networking/crafting/ICraftingCallback;)Ljava/util/concurrent/Future;",
        at = @At("HEAD"),
        cancellable = true)
    private void neoecoae$plan(World world, IGrid grid, BaseActionSource source, IAEStack<?> output, CraftingMode mode,
        ICraftingCallback callback, CallbackInfoReturnable<Future<ICraftingJob>> cir) {
        if (!Config.enableEcoPlanner || world == null
            || world.isRemote
            || grid == null
            || source == null
            || output == null
            || output.getStackSize() <= 0
            || mode != CraftingMode.STANDARD) return;
        try {
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            ECOCraftingSnapshot snapshot = new ECOCraftingSnapshot(
                (CraftingGridCache) (Object) this,
                new MECraftingInventory(storage, false, false, false),
                new ECOResourceKey(output));
            cir.setReturnValue(new ECOCraftingJob(world, grid, source, output, callback, snapshot).schedule());
        } catch (UnsupportedOperationException | ArithmeticException ignored) {
            // Preserve native addon semantics whenever an exact snapshot cannot represent them.
        }
    }
}
