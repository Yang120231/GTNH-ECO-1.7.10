package cn.dancingsnow.neoecoae.platform.gt;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Observed GT structure membership. Retains coordinates, never loads chunks or searches surrounding machines. */
public final class NativeProcessingMachines {

    private static final Map<World, Relations> WORLDS = new WeakHashMap<>();

    private NativeProcessingMachines() {}

    /** Called only from GT's controller mixin, so loading the GT-linked accessor is safe here. */
    public static void observeGregTechController(Object machine, TileEntity tile, boolean formed) {
        observeController(
            tile,
            formed ? GregTechMachineAccess.inputs(machine) : java.util.Collections.emptyList(),
            formed);
    }

    public static synchronized void observeController(TileEntity tile, Iterable<TileEntity> inputTiles,
        boolean formed) {
        World world = tile.getWorldObj();
        if (world == null || world.isRemote) return;
        BlockPos position = new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord);
        Relations relations = WORLDS.computeIfAbsent(world, ignored -> new Relations());
        if (tile.isInvalid()) {
            relations.remove(position);
            return;
        }
        // GT clears its hatch lists before a failed check, so an unformed controller keeps its last known inputs:
        // the status scan must still reach it from those hatches to report the broken structure.
        if (!formed) return;
        Set<BlockPos> inputs = new LinkedHashSet<>();
        for (TileEntity input : inputTiles) if (input.getWorldObj() == world && !input.isInvalid())
            inputs.add(new BlockPos(input.xCoord, input.yCoord, input.zCoord));
        Controller previous = relations.controllers.get(position);
        if (previous != null && previous.tile.get() == tile && previous.inputs.equals(inputs)) return;
        relations.remove(position);
        relations.controllers.put(position, new Controller(new WeakReference<>(tile), inputs));
        for (var input : inputs) relations.inputs.computeIfAbsent(input, ignored -> new LinkedHashSet<>())
            .add(position);
    }

    public static synchronized List<Target> controllers(World world, BlockPos input) {
        var result = new ArrayList<Target>();
        var relations = WORLDS.get(world);
        if (relations == null) return result;
        var positions = relations.inputs.get(input);
        if (positions == null) return result;
        for (var position : new ArrayList<>(positions)) {
            var controller = relations.controllers.get(position);
            var tile = controller == null ? null : controller.tile.get();
            if (tile == null || tile.isInvalid()) relations.remove(position);
            else result.add(new Target(world.provider.dimensionId, position));
        }
        return result;
    }

    /** The scan already read this coordinate under its budget; a replacement must not inherit stale links. */
    public static synchronized boolean isCurrentController(World world, BlockPos position, TileEntity tile) {
        var relations = WORLDS.get(world);
        var controller = relations == null ? null : relations.controllers.get(position);
        return controller == null || controller.tile.get() == tile;
    }

    public static synchronized void clear() {
        WORLDS.clear();
    }

    private static final class Relations {

        final Map<BlockPos, Controller> controllers = new HashMap<>();
        final Map<BlockPos, Set<BlockPos>> inputs = new HashMap<>();

        void remove(BlockPos position) {
            var previous = controllers.remove(position);
            if (previous == null) return;
            for (var input : previous.inputs) {
                var owners = inputs.get(input);
                if (owners != null) {
                    owners.remove(position);
                    if (owners.isEmpty()) inputs.remove(input);
                }
            }
        }
    }

    private static final class Controller {

        final WeakReference<TileEntity> tile;
        final Set<BlockPos> inputs;

        Controller(WeakReference<TileEntity> tile, Set<BlockPos> inputs) {
            this.tile = tile;
            this.inputs = inputs;
        }
    }
}
