package cn.dancingsnow.neoecoae.platform.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.dancingsnow.neoecoae.compat.nei.NEIBookmarkSupport;
import codechicken.nei.BookmarkContainerInfo;
import codechicken.nei.api.IBookmarkContainerHandler;

/** MUI shares one GUI wrapper among mods, so select only the actual ECO storage session. */
@Mixin(value = BookmarkContainerInfo.class, remap = false)
public abstract class NEIBookmarkContainerMixin {

    @Inject(method = "getBookmarkContainerHandler", at = @At("HEAD"), cancellable = true, require = 1)
    private static void neoecoae$storage(GuiContainer gui, CallbackInfoReturnable<IBookmarkContainerHandler> cir) {
        if (NEIBookmarkSupport.session(gui) != null) cir.setReturnValue(NEIBookmarkSupport.INSTANCE);
    }
}
