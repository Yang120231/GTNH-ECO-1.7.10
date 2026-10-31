package cn.dancingsnow.neoecoae.multiblock.placement;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

/**
 * Shared state machine for the three system multiblock builders.
 * Persisted and description-synced values remain on the owning block entity so
 * their existing NBT keys and network field layout do not change.
 */
public final class MultiBlockBuildController {

    private final Host host;
    private MultiBlockBuildSession buildSession;
    private UUID buildPlayerId;

    public MultiBlockBuildController(Host host) {
        this.host = host;
    }

    public void tick(WorldView level) {
        if (!(level instanceof ServerWorldView serverLevel) || !host.isBuildInProgress() || buildSession == null) {
            return;
        }

        EntityPlayerMP buildPlayer = buildPlayerId == null ? null
            : cn.dancingsnow.neoecoae.platform.multiblock.NativePlacementPlatform.findPlayer(buildPlayerId);
        if (buildPlayer == null) {
            finish(false);
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING, ADVANCED -> {}
            case COMPLETED -> finish(true);
            case BLOCKED -> finish(false);
        }
    }

    public void increaseBuildLength(EntityPlayer player) {
        changeBuildLength(player, 1);
    }

    public void decreaseBuildLength(EntityPlayer player) {
        changeBuildLength(player, -1);
    }

    public void changeBuildLength(EntityPlayer player, int delta) {
        setBuildLength(
            player,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.clamp(
                (long) host.getSelectedBuildLength() + delta,
                host.getMinBuildLength(),
                host.getMaxBuildLength()));
    }

    public void setBuildLength(EntityPlayer player, int length) {
        if (!host.canPlayerInteract(player) || host.isBuildInProgress()) {
            return;
        }
        int clamped = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .clamp(length, host.getMinBuildLength(), host.getMaxBuildLength());
        if (clamped == host.getSelectedBuildLength()) return;
        host.setSelectedBuildLength(clamped);
        host.buildStateChanged();
    }

    public void autoBuild(EntityPlayer player) {
        if (!host.canPlayerInteract(player) || !(host.getBuildLevel() instanceof ServerWorldView serverLevel)
            || !(player instanceof EntityPlayerMP serverPlayer)
            || host.isFormed()
            || host.isBuildInProgress()) {
            return;
        }

        MultiBlockDefinition definition = host.getBuildDefinition();
        if (definition == null) {
            return;
        }
        int buildLength = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .clamp(host.getSelectedBuildLength(), definition.getExpandMin(), definition.getExpandMax());
        host.setSelectedBuildLength(buildLength);
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            serverLevel,
            host.getBuildPosition(),
            host.getBuildState(),
            definition,
            buildLength,
            host.isMirrorBuild());
        if (!plan.getConflictPositions()
            .isEmpty()) {
            return;
        }
        if (!serverPlayer.capabilities.isCreativeMode
            && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            return;
        }

        if (plan.getMissingBlocks()
            .isEmpty()) {
            host.rebuildAfterBuild();
            serverPlayer.closeScreen();
            return;
        }
        // Creative placement also triggers synchronous neighbor updates and Forge events. Yield between
        // blocks in both modes so an entire structure cannot monopolize one server tick.
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUniqueID();
        host.setBuildInProgress(true);
        host.buildStateChanged();
        serverPlayer.closeScreen();
    }

    public void setMirrorBuild(EntityPlayer player, boolean mirrorBuild) {
        if (!host.canPlayerInteract(player) || host.isBuildInProgress()) {
            return;
        }
        host.setMirrorBuild(mirrorBuild);
        host.buildStateChanged();
    }

    public @Nullable MultiBlockPlacementPlan createLocalPreviewPlan() {
        WorldView level = host.getBuildLevel();
        if (level == null || host.isFormed()) {
            return null;
        }
        MultiBlockDefinition definition = host.getBuildDefinition();
        if (definition == null) {
            return null;
        }
        int buildLength = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .clamp(host.getSelectedBuildLength(), definition.getExpandMin(), definition.getExpandMax());
        return MultiBlockPlacementService.preview(
            level,
            host.getBuildPosition(),
            host.getBuildState(),
            definition,
            buildLength,
            host.isMirrorBuild());
    }

    private void finish(boolean rebuild) {
        buildSession = null;
        buildPlayerId = null;
        host.setBuildInProgress(false);
        if (rebuild) {
            host.rebuildAfterBuild();
        }
        host.buildStateChanged();
    }

    public interface Host {

        @Nullable
        WorldView getBuildLevel();

        BlockPos getBuildPosition();

        BlockState getBuildState();

        @Nullable
        MultiBlockDefinition getBuildDefinition();

        int getMinBuildLength();

        int getMaxBuildLength();

        int getSelectedBuildLength();

        void setSelectedBuildLength(int length);

        boolean isMirrorBuild();

        void setMirrorBuild(boolean mirrorBuild);

        boolean isBuildInProgress();

        void setBuildInProgress(boolean buildInProgress);

        boolean isFormed();

        boolean canPlayerInteract(EntityPlayer player);

        void rebuildAfterBuild();

        void buildStateChanged();
    }
}
