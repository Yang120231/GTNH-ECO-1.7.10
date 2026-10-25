package cn.dancingsnow.neoecoae.platform.forge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/** Native mod classes are transformed after FML has discovered their actual jars. */
@LateMixin
public final class NativeLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.neoecoae.platform.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return loadedMods.contains("appliedenergistics2")
            ? Arrays.asList("NetworkInventoryHandlerMixin", "NetworkItemListMixin", "StorageBusMountMixin")
            : Collections.emptyList();
    }
}
