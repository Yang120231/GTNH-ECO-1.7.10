package cn.dancingsnow.neoecoae.platform.ui.multiblock;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockDefinitions;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Native controller adapter for the existing preview/material-check/build state machine. */
public final class NativeControllerBuilds {

    private static final NativeControllerBuilds EVENTS = new NativeControllerBuilds();
    private static final Map<NEBlockEntity<?, ?>, State> STATES = new IdentityHashMap<>();
    private static boolean registered;

    private NativeControllerBuilds() {}

    public static NativeBuilderConfig config(NEBlockEntity<?, ?> host, EntityPlayer player, ECOMachineKind kind) {
        State state;
        if (host.getWorldObj().isRemote) state = new State(host, kind);
        else {
            if (!registered) {
                FMLCommonHandler.instance()
                    .bus()
                    .register(EVENTS);
                MinecraftForge.EVENT_BUS.register(EVENTS);
                registered = true;
            }
            state = STATES.computeIfAbsent(host, tile -> new State(tile, kind));
        }
        State owned = state;
        return new NativeBuilderConfig(
            host,
            player,
            owned::getSelectedBuildLength,
            owned.getMinBuildLength(),
            owned.getMaxBuildLength(),
            length -> owned.controller.setBuildLength(player, length),
            delta -> owned.controller.changeBuildLength(player, delta),
            owned::isMirrorBuild,
            value -> owned.controller.setMirrorBuild(player, value),
            () -> owned.controller.autoBuild(player),
            host::isFormed,
            owned::isBuildInProgress,
            owned.controller::createLocalPreviewPlan);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ServerTasks.isRunning()) {
            STATES.clear();
            return;
        }
        var entries = STATES.entrySet()
            .iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            var host = entry.getKey();
            if (host.isInvalid() || host.getWorldObj() == null
                || host.getWorldObj().isRemote
                || net.minecraftforge.common.DimensionManager.getWorld(host.getWorldObj().provider.dimensionId)
                    != host.getWorldObj()
                || host.getWorldObj()
                    .getTileEntity(host.xCoord, host.yCoord, host.zCoord) != host) {
                entries.remove();
                continue;
            }
            entry.getValue().controller.tick(host.getLevel());
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (!event.world.isRemote) STATES.entrySet()
            .removeIf(
                entry -> entry.getKey()
                    .getWorldObj() == event.world);
    }

    private static final class State implements MultiBlockBuildController.Host {

        private final NEBlockEntity<?, ?> host;
        private final ECOMachineKind kind;
        private final MultiBlockBuildController controller = new MultiBlockBuildController(this);
        private boolean building;

        private State(NEBlockEntity<?, ?> host, ECOMachineKind kind) {
            this.host = host;
            this.kind = kind;
        }

        public WorldView getBuildLevel() {
            return host.getLevel();
        }

        public BlockPos getBuildPosition() {
            return host.getBlockPos();
        }

        public BlockState getBuildState() {
            return host.getBlockState();
        }

        public MultiBlockDefinition getBuildDefinition() {
            return ECOMultiblockDefinitions.resolve(
                host,
                kind,
                host.getTier(),
                () -> NativeControllerDefinitions.get(
                    kind,
                    host.getTier()
                        .getTier()));
        }

        public int getMinBuildLength() {
            MultiBlockDefinition definition = getBuildDefinition();
            return definition == null ? 1 : definition.getExpandMin();
        }

        public int getMaxBuildLength() {
            MultiBlockDefinition definition = getBuildDefinition();
            return definition == null ? 1 : definition.getExpandMax();
        }

        public int getSelectedBuildLength() {
            int stored = host instanceof ECOComputationSystemBlockEntity
                ? ((ECOComputationSystemBlockEntity) host).getSelectedBuildLength()
                : ((ECOCraftingSystemBlockEntity) host).getSelectedBuildLength();
            return Math
                .max(getMinBuildLength(), Math.min(getMaxBuildLength(), stored == 0 ? getMaxBuildLength() : stored));
        }

        public void setSelectedBuildLength(int length) {
            if (host instanceof ECOComputationSystemBlockEntity)
                ((ECOComputationSystemBlockEntity) host).setSelectedBuildLength(length);
            else((ECOCraftingSystemBlockEntity) host).setSelectedBuildLength(length);
        }

        public boolean isMirrorBuild() {
            return host instanceof ECOComputationSystemBlockEntity
                ? ((ECOComputationSystemBlockEntity) host).isMirrorBuild()
                : ((ECOCraftingSystemBlockEntity) host).isMirrorBuild();
        }

        public void setMirrorBuild(boolean mirror) {
            if (host instanceof ECOComputationSystemBlockEntity)
                ((ECOComputationSystemBlockEntity) host).setMirrorBuild(mirror);
            else((ECOCraftingSystemBlockEntity) host).setMirrorBuild(mirror);
        }

        public boolean isBuildInProgress() {
            return building;
        }

        public void setBuildInProgress(boolean value) {
            building = value;
        }

        public boolean isFormed() {
            return host.isFormed();
        }

        public boolean canPlayerInteract(EntityPlayer player) {
            return player != null && player.worldObj == host.getWorldObj()
                && !host.isInvalid()
                && !player.worldObj.isRemote
                && player.getDistanceSq(host.xCoord + .5, host.yCoord + .5, host.zCoord + .5) <= 64
                && host.getWorldObj()
                    .getTileEntity(host.xCoord, host.yCoord, host.zCoord) == host;
        }

        public void rebuildAfterBuild() {
            host.rebuildMultiblock();
        }

        public void buildStateChanged() {
            host.saveChanges();
        }
    }
}
