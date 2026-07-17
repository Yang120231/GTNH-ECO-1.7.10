package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;

/** Server-thread cache rebuilt whenever the formed crafting structure changes. */
final class CraftingMemberCache {

    public static final CraftingMemberCache EMPTY = new CraftingMemberCache(
        Collections.<TileCraftingWorker>emptyList(),
        Collections.<TileCraftingPatternBus>emptyList(),
        Collections.<TileECOInterface>emptyList(),
        Collections.<TileCraftingHatch>emptyList(),
        Collections.<TileCraftingHatch>emptyList(),
        0,
        false);

    private final List<TileCraftingWorker> workers;
    private final List<TileCraftingPatternBus> patternBuses;
    private final List<TileECOInterface> craftingInterfaces;
    private final List<TileCraftingHatch> inputHatches;
    private final List<TileCraftingHatch> outputHatches;
    private final int controllerRevision;
    private final boolean valid;

    private CraftingMemberCache(List<TileCraftingWorker> workers, List<TileCraftingPatternBus> patternBuses,
        List<TileECOInterface> craftingInterfaces, List<TileCraftingHatch> inputHatches,
        List<TileCraftingHatch> outputHatches, int controllerRevision, boolean valid) {
        this.workers = new ArrayList<>(workers);
        this.patternBuses = new ArrayList<>(patternBuses);
        this.craftingInterfaces = new ArrayList<>(craftingInterfaces);
        this.inputHatches = new ArrayList<>(inputHatches);
        this.outputHatches = new ArrayList<>(outputHatches);
        this.controllerRevision = controllerRevision;
        this.valid = valid;
    }

    static CraftingMemberCache build(TileECOController controller, List<ECOFormationBlockPos> formedMembers,
        List<ECOFormationBlockPos> hiddenMembers) {
        if (controller == null || controller.getWorldObj() == null) {
            return EMPTY;
        }

        World world = controller.getWorldObj();
        if (world.isRemote) {
            return EMPTY;
        }

        List<TileCraftingWorker> workers = new ArrayList<>();
        List<TileCraftingPatternBus> patternBuses = new ArrayList<>();

        for (ECOFormationBlockPos pos : formedMembers) {
            TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile == null || tile.isInvalid()) {
                continue;
            }

            if (tile instanceof TileCraftingWorker) {
                workers.add((TileCraftingWorker) tile);
            } else if (tile instanceof TileCraftingPatternBus) {
                patternBuses.add((TileCraftingPatternBus) tile);
            }
        }

        List<TileECOInterface> craftingInterfaces = new ArrayList<>();
        List<TileCraftingHatch> inputHatches = new ArrayList<>();
        List<TileCraftingHatch> outputHatches = new ArrayList<>();

        for (ECOFormationBlockPos pos : hiddenMembers) {
            TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile == null || tile.isInvalid()) {
                continue;
            }

            if (tile instanceof TileECOInterface) {
                TileECOInterface ecoInterface = (TileECOInterface) tile;
                if (ecoInterface.getSubsystem() == ECOControllerSubsystem.CRAFTING) {
                    craftingInterfaces.add(ecoInterface);
                }
            } else if (tile instanceof TileCraftingHatch) {
                TileCraftingHatch hatch = (TileCraftingHatch) tile;
                if (hatch.isInput()) {
                    inputHatches.add(hatch);
                } else {
                    outputHatches.add(hatch);
                }
            }
        }

        return new CraftingMemberCache(
            workers,
            patternBuses,
            craftingInterfaces,
            inputHatches,
            outputHatches,
            controller.getCraftingMemberCacheRevision(),
            true);
    }

    boolean isValid(int currentRevision) {
        return this.valid && this.controllerRevision == currentRevision;
    }

    List<TileCraftingWorker> workers() {
        return Collections.unmodifiableList(this.workers);
    }

    List<TileCraftingPatternBus> patternBuses() {
        return Collections.unmodifiableList(this.patternBuses);
    }

    List<TileECOInterface> craftingInterfaces() {
        return Collections.unmodifiableList(this.craftingInterfaces);
    }

    List<TileCraftingHatch> inputHatches() {
        return Collections.unmodifiableList(this.inputHatches);
    }

    List<TileCraftingHatch> outputHatches() {
        return Collections.unmodifiableList(this.outputHatches);
    }
}
