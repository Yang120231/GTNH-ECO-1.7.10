package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.crafting.ICraftingIconProvider;
import appeng.helpers.IInterfaceHost;
import cn.dancingsnow.neoecoae.compat.proghatches.upload.ProgrammableHatchUpload;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** Reads GT destinations behind pattern hatches and ME interfaces, sharing the controller index across hosts. */
final class GtRouteDiscovery {

    static final char MODE_SEPARATOR = '\u001f';

    private static final long OWNER_INDEX_TICKS = 100;

    private final Map<World, OwnerIndex> ownersByWorld = new WeakHashMap<>();

    void invalidate() {
        ownersByWorld.clear();
    }

    List<Route> find(Object host) {
        List<Route> routes = new ArrayList<>();
        try {
            if (host instanceof IMetaTileEntity machine) addMachine(routes, machine);
            if (host instanceof IInterfaceHost interfaceHost) addAdjacentMachines(routes, interfaceHost);
            return routes;
        } catch (RuntimeException rebuilding) {
            // A partially inspected host is never considered a unique processing receiver.
            return Collections.emptyList();
        }
    }

    private void addAdjacentMachines(List<Route> routes, IInterfaceHost host) {
        TileEntity tile = host.getTileEntity();
        EnumSet<ForgeDirection> targets = host.getTargets();
        if (tile == null || tile.getWorldObj() == null || targets == null) return;
        for (ForgeDirection side : targets) {
            if (side == null || side == ForgeDirection.UNKNOWN) continue;
            TileEntity adjacent = tile.getWorldObj()
                .getTileEntity(tile.xCoord + side.offsetX, tile.yCoord + side.offsetY, tile.zCoord + side.offsetZ);
            if (!(adjacent instanceof IGregTechTileEntity gtTile)) continue;
            IMetaTileEntity machine = gtTile.getMetaTileEntity();
            if (machine instanceof MTEHatchInput || machine instanceof MTEHatchInputBus
                || machine instanceof RecipeMapWorkable) addMachine(routes, machine);
        }
    }

    private void addMachine(List<Route> routes, IMetaTileEntity machine) {
        boolean cover = hasProgrammingCover(machine.getBaseMetaTileEntity());
        boolean programmable = ProgrammableHatchUpload.isProgrammingInput(machine);
        // Programming overwrites this ghost slot for each recipe; its last value is not a fixed configuration.
        ItemStack circuit = cover || programmable ? null : circuit(machine);
        boolean direct = machine instanceof RecipeMapWorkable;
        List<MTEMultiBlockBase> controllers = direct ? Collections.emptyList() : ownersOf(machine);
        if (controllers.isEmpty()) {
            addRoute(routes, recipeMap(machine), circuit, machine, direct ? machine : null, cover, programmable);
            return;
        }
        for (MTEMultiBlockBase controller : controllers) {
            RecipeMap<?> map = controller.getRecipeMap();
            if (map != null) addRoute(routes, map, circuit, machine, controller, cover, programmable);
        }
    }

    private static void addRoute(List<Route> routes, RecipeMap<?> map, ItemStack circuit, IMetaTileEntity input,
        IMetaTileEntity receiver, boolean cover, boolean programmable) {
        IMetaTileEntity display = receiver == null ? input : receiver;
        ItemStack icon = machineIcon(display);
        Route candidate = new Route(
            map,
            circuit,
            icon,
            routeName(icon, display, map),
            receiver,
            input,
            cover,
            programmable);
        for (Route route : routes) if (route.sameDestination(candidate)) return;
        routes.add(candidate);
    }

    private static RecipeMap<?> recipeMap(IMetaTileEntity machine) {
        if (machine instanceof MTEHatchInput hatch) return hatch.mRecipeMap;
        if (machine instanceof MTEHatchInputBus bus) return bus.mRecipeMap;
        if (machine instanceof RecipeMapWorkable workable) return workable.getRecipeMap();
        return null;
    }

    private static ItemStack circuit(IMetaTileEntity machine) {
        if (!(machine instanceof IConfigurationCircuitSupport configurable)
            || !(machine instanceof MetaTileEntity meta)) return null;
        int slot = configurable.getCircuitSlot();
        var inventory = meta.getInventoryHandler();
        if (slot < 0 || inventory == null || slot >= inventory.getSlots()) return null;
        return GtCircuits.unwrap(inventory.getStackInSlot(slot));
    }

    static List<ItemStack> localInputs(IMetaTileEntity input) {
        if (input instanceof IDualInputHatch hatch) return Arrays.asList(hatch.getSharedItems());
        if (input instanceof MTEHatchInputBus bus) {
            List<ItemStack> items = new ArrayList<>();
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                if (slot != bus.getCircuitSlot()) items.add(bus.getStackInSlot(slot));
            }
            return items;
        }
        if (input instanceof MTEBasicMachine machine) {
            List<ItemStack> items = new ArrayList<>();
            int start = machine.getInputSlot();
            for (int slot = start; slot < start + machine.mInputSlotCount; slot++)
                items.add(machine.getStackInSlot(slot));
            return items;
        }
        return Collections.emptyList();
    }

    private static boolean hasProgrammingCover(IGregTechTileEntity tile) {
        if (tile == null || !ProgrammableHatchUpload.isAvailable()) return false;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS)
            if (ProgrammableHatchUpload.isCover(tile.getCoverAtSide(side))) return true;
        return false;
    }

    private static ItemStack machineIcon(IMetaTileEntity machine) {
        try {
            if (machine instanceof ICraftingIconProvider provider) {
                ItemStack icon = provider.getMachineCraftingIcon();
                if (icon != null) return icon.copy();
            }
            return machine.getStackForm(1);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** The machine's name, followed by its current recipe map when it switches between modes. */
    private static String routeName(ItemStack icon, IMetaTileEntity machine, RecipeMap<?> map) {
        String name = machineName(icon, machine);
        if (!(machine instanceof MTEMultiBlockBase controller) || map == null || map.unlocalizedName == null)
            return name;
        try {
            if (!controller.supportsMachineModeSwitch()) return name;
        } catch (RuntimeException | LinkageError unknown) {
            return name;
        }
        return name + MODE_SEPARATOR + map.unlocalizedName;
    }

    private static String machineName(ItemStack icon, IMetaTileEntity machine) {
        if (icon != null) {
            String name = icon.getUnlocalizedName();
            if (name != null && !name.isEmpty()) return name + ".name";
        }
        return machine.getMetaName();
    }

    /** Read the receiver's supply, never the input hatch's tier or voltage multiplied by amperage. */
    static long inputVoltage(IMetaTileEntity receiver) {
        if (receiver == null) return 0;
        try {
            long voltage;
            if (receiver instanceof MTEMultiBlockBase controller) {
                if (!controller.mMachine) return 0;
                // This is the voltage used by GT's processing logic; extended controllers include exotic hatches.
                voltage = controller.getAverageInputVoltage();
            } else {
                IGregTechTileEntity tile = receiver.getBaseMetaTileEntity();
                voltage = tile == null ? 0 : tile.getInputVoltage();
            }
            return Math.max(0, voltage);
        } catch (RuntimeException | LinkageError unavailable) {
            return 0;
        }
    }

    private List<MTEMultiBlockBase> ownersOf(IMetaTileEntity part) {
        IGregTechTileEntity base = part.getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (world == null) return Collections.emptyList();
        OwnerIndex index = ownersByWorld.get(world);
        long now = world.getTotalWorldTime();
        if (index == null || now - index.builtAt > OWNER_INDEX_TICKS || now < index.builtAt) {
            index = OwnerIndex.build(world, now);
            ownersByWorld.put(world, index);
        }
        return index.liveOwnersOf(part);
    }

    @com.github.bsideup.jabel.Desugar
    record Route(RecipeMap<?> map, ItemStack circuit, ItemStack icon, String name, IMetaTileEntity receiver,
        IMetaTileEntity input, boolean programmingCover, boolean programmable) {

        // The same map and circuit can be reached through different input parts. Keep programming capability:
        // an earlier unprogrammed bus (or fluid hatch) must not hide a later bus carrying a programming cover.
        boolean sameDestination(Route other) {
            return map == other.map && receiver == other.receiver
                && input == other.input
                && programmingCover == other.programmingCover
                && programmable == other.programmable
                && (circuit == null ? other.circuit == null : GtCircuits.same(circuit, other.circuit));
        }
    }

    /** Formed controllers by input part, indexed in one pass over the loaded tiles. */
    private static final class OwnerIndex {

        private final long builtAt;
        private final Map<IMetaTileEntity, List<MTEMultiBlockBase>> controllersByPart = new IdentityHashMap<>();

        private OwnerIndex(long builtAt) {
            this.builtAt = builtAt;
        }

        static OwnerIndex build(World world, long now) {
            OwnerIndex index = new OwnerIndex(now);
            for (Object loaded : new ArrayList<>(world.loadedTileEntityList)) {
                if (!(loaded instanceof IGregTechTileEntity tile)
                    || !(tile.getMetaTileEntity() instanceof MTEMultiBlockBase controller)
                    || !controller.mMachine) continue;
                for (TileEntity input : NativeGregTech.inputTiles(controller)) {
                    if (!(input instanceof IGregTechTileEntity hatch)) continue;
                    IMetaTileEntity part = hatch.getMetaTileEntity();
                    if (part == null) continue;
                    List<MTEMultiBlockBase> linked = index.controllersByPart
                        .computeIfAbsent(part, ignored -> new ArrayList<>());
                    if (!linked.contains(controller)) linked.add(controller);
                }
            }
            return index;
        }

        List<MTEMultiBlockBase> liveOwnersOf(IMetaTileEntity part) {
            List<MTEMultiBlockBase> found = controllersByPart.get(part);
            if (found == null) return Collections.emptyList();
            List<MTEMultiBlockBase> live = new ArrayList<>();
            for (MTEMultiBlockBase controller : found) {
                if (!controller.mMachine) continue;
                IGregTechTileEntity base = controller.getBaseMetaTileEntity();
                if (base != null && !base.isDead()) live.add(controller);
            }
            return live;
        }
    }
}
