package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.List;
import java.util.Set;

import cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Modern node-service contract. NativeCraftingProvider binds native media to this used interface. */
public interface ICraftingProvider {

    List<IPatternDetails> getAvailablePatterns();

    default int getPatternPriority() {
        return 0;
    }

    boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs);

    boolean isBusy();

    default Set<AEKey> getEmitableItems() {
        return java.util.Collections.emptySet();
    }

    static void requestUpdate(ManagedNode managedNode) {
        var node = managedNode.getNode();
        if (node == null) return;
        Object host = node.getMachine();
        if (!(host instanceof appeng.api.networking.crafting.ICraftingProvider))
            throw new IllegalStateException("Source crafting service lacks its native host bridge");
        node.getGrid()
            .postEvent(
                new appeng.api.networking.events.MENetworkCraftingPatternChange(
                    (appeng.api.networking.crafting.ICraftingProvider) host,
                    node));
    }
}
