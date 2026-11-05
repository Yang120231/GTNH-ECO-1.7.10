package cn.dancingsnow.neoecoae.dev;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.security.IActionHost;
import appeng.tile.AEBaseInvTile;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;

public final class GeneratorGridService implements IGridCache {

    private final GridView grid;
    private final Map<IGridNode, IGridHost> hosts = new IdentityHashMap<>();
    private final Map<ItemStack, GeneratorState> cells = new IdentityHashMap<>();

    public GeneratorGridService(IGrid grid) {
        this.grid = GridView.of(grid);
    }

    @Override
    public void addNode(IGridNode node, IGridHost host) {
        if (host instanceof IChestOrDrive || host instanceof DevResourceGenerator.GeneratorTile) hosts.put(node, host);
    }

    @Override
    public void removeNode(IGridNode node, IGridHost host) {
        hosts.remove(node);
    }

    @Override
    public void onSplit(IGridStorage data) {}

    @Override
    public void onJoin(IGridStorage data) {}

    @Override
    public void populateGridStorage(IGridStorage data) {}

    @Override
    public void onUpdateTick() {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ItemStack> installed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : hosts.entrySet()) {
            IGridNode node = entry.getKey();
            IGridHost host = entry.getValue();
            if (!node.isActive() || !seen.add(host)) continue;
            if (host instanceof DevResourceGenerator.GeneratorTile tile) {
                tile.state.tick(grid, IActionSource.ofMachine(tile), tile::markDirty);
            } else if (host instanceof IChestOrDrive drive && host instanceof AEBaseInvTile tile
                && host instanceof IActionHost machine) {
                    // The ME chest's cell lives in slot 1; the drive's slots map directly.
                    var inventory = tile.getInternalInventory();
                    for (int slot = 0; slot < drive.getCellCount(); slot++) {
                        ItemStack stack = inventory.getStackInSlot(drive.getCellCount() == 1 ? 1 : slot);
                        if (!DevResourceGenerator.isGenerator(stack)) continue;
                        installed.add(stack);
                        GeneratorState state = cells.computeIfAbsent(
                            stack,
                            key -> GeneratorState.read(DevResourceGenerator.readItem(key), RegistryAccess.EMPTY));
                        state.tick(grid, IActionSource.ofMachine(machine), () -> {
                            DevResourceGenerator.writeItem(stack, state.write(RegistryAccess.EMPTY));
                            tile.markDirty();
                        });
                    }
                }
        }
        cells.keySet()
            .retainAll(installed);
    }
}
