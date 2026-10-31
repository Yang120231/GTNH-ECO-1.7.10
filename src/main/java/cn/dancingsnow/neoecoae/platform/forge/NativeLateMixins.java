package cn.dancingsnow.neoecoae.platform.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Native mod classes are transformed after FML has discovered their actual jars. */
@LateMixin
public final class NativeLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.neoecoae.platform.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (!loadedMods.contains("appliedenergistics2")) return mixins;
        mixins.addAll(
            Arrays.asList(
                "NetworkInventoryHandlerMixin",
                "NetworkItemListMixin",
                "DriveCellRestrictionMixin",
                "StorageBusMountMixin",
                "CraftingCpuPublicationMixin",
                "PatternTermEncodeMixin",
                "ProcessingTargetMixin",
                "NetworkMonitorMixin",
                "ContainerCraftConfirmAccessor",
                "ContainerCraftConfirmStartMixin",
                "RemoteTileGuiReachMixin"));
        if (loadedMods.contains("gregtech")) {
            mixins.add("GTProcessingControllerMixin");
            mixins.add("GTRemoteGuiReachMixin");
        }
        // The NEI route capture only exists on the physical client.
        if (FMLLaunchHandler.side()
            .isClient() && loadedMods.contains("NotEnoughItems")) mixins.add("NeiPatternRouteMixin");
        return mixins;
    }
}
