package cn.dancingsnow.neoecoae.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;

import cn.dancingsnow.neoecoae.platform.structurelib.NEPreviewChannels;

/** Supply structure-level channels to the existing NEI sliders and copy-to-held-projector button. */
@Mixin(targets = "blockrenderer6343.client.utils.ConstructableData", remap = false)
public abstract class StructurePreviewChannelsMixin {

    @Inject(method = "getTierData", at = @At("RETURN"), cancellable = true, require = 1)
    private static void neoecoae$channels(IConstructable constructable, CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(NEPreviewChannels.addChannels(constructable, cir.getReturnValue()));
    }
}
