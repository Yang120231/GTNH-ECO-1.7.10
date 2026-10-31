package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.container.implementations.ContainerCraftingStatus;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
import cn.dancingsnow.neoecoae.platform.gt.NativeProcessingMachines;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;

/**
 * Right-click menu of an ECO crafting status cell (1.21.1 ComputationItemActions): the pattern providers that hold the
 * running job's patterns for an output, and the recipe machines those patterns feed. Only blocks the running job
 * really uses are reported, and every open request is re-resolved and validated against that same list.
 */
public final class ECOCraftingStatusItemActions {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");
    static final int MAX_PLACES = 64;

    public static final String INSPECT = "inspect", OPEN_PROVIDER = "open_provider", OPEN_MACHINE = "open_machine";

    private ECOCraftingStatusItemActions() {}

    /** A block the menu can name, highlight and open. */
    static final class Place {

        final String id;
        final String name;
        final int dimension;
        final BlockPos position;
        @Nullable
        final ForgeDirection side;
        final TileEntity tile;

        Place(TileEntity tile, @Nullable ForgeDirection side, String name) {
            this.tile = tile;
            this.side = side;
            this.name = name;
            this.dimension = tile.getWorldObj().provider.dimensionId;
            this.position = new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord);
            this.id = dimension + "@" + position.asLong() + ":" + (side == null ? "-" : side.name());
        }

        CompoundTag describe() {
            var tag = new CompoundTag();
            tag.putString("id", id);
            tag.putString("name", name);
            tag.putInt("dimension", dimension);
            tag.putInt("x", position.getX());
            tag.putInt("y", position.getY());
            tag.putInt("z", position.getZ());
            if (side != null) tag.putString("side", side.name());
            return tag;
        }
    }

    /** Client request; answered with one result packet for the same sequence. */
    public static void handle(EntityPlayerMP player, int windowId, long sequence, String action, @Nullable AEKey key,
        @Nullable String id) {
        if (!(player.openContainer instanceof ContainerCraftingStatus container) || container.windowId != windowId)
            return;
        var response = new CompoundTag();
        response.putLong("sequence", sequence);
        response.putString("action", action);
        if (key != null) response.put("key", key.toTagGeneric(RegistryAccess.EMPTY));
        try {
            var runtime = ECOCraftingStatusTracker.ecoRuntime(ECOCraftingStatusTracker.selectedCpu(container));
            if (runtime == null || key == null) {
                response.putString("error", "gui.neoecoae.processing.task_changed");
            } else if (INSPECT.equals(action)) {
                inspect(runtime, key, response);
            } else if (OPEN_PROVIDER.equals(action) || OPEN_MACHINE.equals(action)) {
                var places = OPEN_PROVIDER.equals(action) ? providers(runtime, key) : machines(runtime, key);
                Place target = null;
                for (var place : places) if (place.id.equals(id)) target = place;
                String error = target == null ? "gui.neoecoae.processing.task_changed"
                    : ECORemoteGuiOpener.open(player, target.tile, target.side);
                if (error != null) response.putString("error", error);
                else response.putBoolean("close", true);
            }
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[craft-status] Item action {} failed for {}", action, player.getCommandSenderName(), failure);
            response.putString("error", "gui.neoecoae.processing.open_unavailable");
        }
        // A successful open replaced the status container; its window no longer receives this answer.
        if (!response.getBoolean("close")) ECOCraftConfirmPackets.sendItemActionResult(player, windowId, response);
    }

    private static void inspect(ECOCraftingNativeRuntime runtime, AEKey key, CompoundTag response) {
        UUID job = logic(runtime).runningJobId();
        if (job != null) response.putString("job", job.toString());
        response.put("sources", describe(providers(runtime, key)));
        response.put("machines", describe(machines(runtime, key)));
    }

    private static ListTag describe(List<Place> places) {
        var list = new ListTag();
        for (var place : places) list.add(place.describe());
        return list;
    }

    private static ECOCraftingCPULogic logic(ECOCraftingNativeRuntime runtime) {
        return runtime.cpu()
            .getLogic();
    }

    /** The providers (interfaces, pattern hatches, ECO workstations) holding the job's patterns for this output. */
    static List<Place> providers(ECOCraftingNativeRuntime runtime, AEKey key) {
        Map<String, Place> places = new LinkedHashMap<>();
        var logic = logic(runtime);
        var grid = runtime.grid();
        if (grid == null) return new ArrayList<>();
        var crafting = grid.getCraftingService();
        for (IPatternDetails pattern : logic.patternsProducing(key)) {
            List<ICraftingProvider> providers;
            try {
                providers = crafting.getProviders(pattern);
            } catch (RuntimeException unsupported) {
                continue;
            }
            for (ICraftingProvider provider : providers) {
                Place place = providerPlace(provider);
                if (place != null) places.putIfAbsent(place.id, place);
                if (places.size() >= MAX_PLACES) return new ArrayList<>(places.values());
            }
        }
        return new ArrayList<>(places.values());
    }

    /**
     * Recipe machines fed by this output's patterns: controllers the status scan resolved, controllers that own the
     * providers' GT hatches, and recorded destinations that are machines themselves.
     */
    static List<Place> machines(ECOCraftingNativeRuntime runtime, AEKey key) {
        Map<String, Place> places = new LinkedHashMap<>();
        var monitor = logic(runtime).getProcessingMonitor();
        List<Target> candidates = new ArrayList<>(monitor.machineTargets(key));
        for (Place provider : providers(runtime, key)) {
            candidates.addAll(NativeProcessingMachines.controllers(provider.tile.getWorldObj(), provider.position));
        }
        for (Target destination : monitor.destinations(key)) {
            World world = net.minecraftforge.common.DimensionManager.getWorld(destination.dimension());
            if (world == null) continue;
            var controllers = NativeProcessingMachines.controllers(world, destination.position());
            if (!controllers.isEmpty()) candidates.addAll(controllers);
            else candidates.add(destination);
        }
        for (Target target : candidates) {
            Place place = machinePlace(target);
            if (place != null) places.putIfAbsent(place.id, place);
            if (places.size() >= MAX_PLACES) break;
        }
        return new ArrayList<>(places.values());
    }

    @Nullable
    private static Place machinePlace(Target target) {
        World world = net.minecraftforge.common.DimensionManager.getWorld(target.dimension());
        var pos = target.position();
        if (world == null || !world.getChunkProvider()
            .chunkExists(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (tile == null || tile.isInvalid()) return null;
        // Only actual recipe machines: a GT controller/basic machine, or an ECO workstation controller.
        boolean machine = NativeGregTech.sample(tile) != null
            || tile instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
        if (!machine) return null;
        return new Place(tile, null, displayName(tile));
    }

    @Nullable
    private static Place providerPlace(ICraftingProvider provider) {
        if (provider instanceof cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider workstation) {
            TileEntity tile = workstation.getWorkstationInterface();
            return valid(tile) ? new Place(tile, null, displayName(tile)) : null;
        }
        if (!(provider instanceof NativeCraftingProvider nativeProvider)) return null;
        ICraftingMedium medium = nativeProvider.nativeMedium();
        if (medium instanceof appeng.helpers.DualityInterface duality) {
            Object host = duality.getHost();
            ForgeDirection side = host instanceof appeng.parts.AEBasePart base ? base.getSide() : null;
            TileEntity tile = duality.getTile();
            if (!valid(tile)) return null;
            String name = duality.getTermName();
            return new Place(tile, side, name == null || name.isEmpty() ? displayName(tile) : name);
        }
        TileEntity tile = medium instanceof TileEntity direct ? direct : NativeGregTech.hostTile(medium);
        return valid(tile) ? new Place(tile, null, displayName(tile)) : null;
    }

    private static boolean valid(@Nullable TileEntity tile) {
        return tile != null && !tile.isInvalid() && tile.getWorldObj() != null;
    }

    static String displayName(TileEntity tile) {
        String gt = NativeGregTech.machineName(tile);
        if (gt != null) return gt;
        try {
            var world = tile.getWorldObj();
            var block = world.getBlock(tile.xCoord, tile.yCoord, tile.zCoord);
            var stack = new ItemStack(block, 1, world.getBlockMetadata(tile.xCoord, tile.yCoord, tile.zCoord));
            return stack.getItem() == null ? block.getLocalizedName() : stack.getDisplayName();
        } catch (RuntimeException unnamed) {
            return tile.getClass()
                .getSimpleName();
        }
    }
}
