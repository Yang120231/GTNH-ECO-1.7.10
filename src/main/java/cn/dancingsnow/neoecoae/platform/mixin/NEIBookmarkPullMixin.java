package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.dancingsnow.neoecoae.compat.nei.NEIBookmarkSupport;
import codechicken.nei.BookmarkPanel;
import codechicken.nei.recipe.chain.RecipeChainMath;

@Mixin(value = BookmarkPanel.class, remap = false)
public abstract class NEIBookmarkPullMixin {

    @Inject(
        method = "pullBookmarkItems(Lcodechicken/nei/recipe/chain/RecipeChainMath;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void neoecoae$readInventory(RecipeChainMath math, boolean shift, CallbackInfoReturnable<Boolean> cir) {
        if (NEIBookmarkSupport.deferPull((BookmarkPanel) (Object) this, math, shift)) cir.setReturnValue(true);
    }
}
