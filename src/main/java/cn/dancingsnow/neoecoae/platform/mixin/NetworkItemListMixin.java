package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.NetworkItemList;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeStackAmounts;

/** The final cross-network merge must obey the same bound after native subnet filtering/deduplication. */
@Mixin(value = NetworkItemList.class, remap = false)
public abstract class NetworkItemListMixin {

    @Redirect(
        method = "buildFinalItemList(Lappeng/api/storage/data/IItemList;)Lappeng/api/storage/data/IItemList;",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"),
        require = 1)
    private void neoecoae$mergeNetworks(Stream<IAEStack> stream, Consumer<IAEStack> original, IItemList out) {
        stream.forEach(stack -> NativeStackAmounts.add(out, stack));
    }
}
