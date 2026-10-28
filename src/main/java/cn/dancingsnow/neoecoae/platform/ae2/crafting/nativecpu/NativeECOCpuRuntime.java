package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Native AE2 identity delegates all manufacturing and exact ownership to the imported ECO CPU. */
public interface NativeECOCpuRuntime extends ECOCraftingOutputRouter {

    World world();

    GridView grid();

    BaseActionSource actionSource();

    String name();

    boolean isActive();

    boolean isBusy();

    appeng.api.config.CraftingAllow selectionMode();

    void setSelectionMode(appeng.api.config.CraftingAllow mode);

    void markDirty();

    void addChangeListener(java.util.function.Consumer<AEKey> listener);

    boolean isSuspended();

    void setSuspended(boolean suspended);

    long availableStorage();

    long usedStorage();

    int coProcessors();

    @Nullable
    GenericStack finalOutput();

    long remainingOutput();

    long startedWork();

    long remainingWork();

    long elapsedTimeNanos();

    /** Called once per owning world tick by the native crafting cache, never by native AE2 execution. */
    void tick();

    void cancel();

    long insert(AEKey key, long amount, Actionable mode);

    long waitingFor(AEKey key);

    void getAllItems(KeyCounter out);

    /** Real active, pending and stored projections for native AE2's three status panes. */
    void getItems(KeyCounter out, CraftingItemList which);

    ECOPlannerOptions planningOptions();

    boolean ownsJob(UUID jobId);

    /** Full exact delivery must override the bounded compatibility default. Returns accepted quantity. */
    @Override
    BigInteger neoecoae$insertExactIntoCpuForJob(UUID jobId, AEKey key, BigInteger amount, Actionable mode);

    @Nullable
    ICraftingLink lastLink();

    @Nullable
    ICraftingLink submit(ICraftingPlan plan, BaseActionSource source, @Nullable ICraftingRequester requester);

    /** Supplies the same stable native CPU identity used when restoring CraftingLink/Nexus state. */
    void bindNativeCPU(CraftingCPUCluster cpu);

    /** Admission pool a submission to this identity really lands in; CPUs sharing it are tried once. */
    default Object submissionPool() {
        return this;
    }

    /** Whether the owning pool can still spawn a CPU for a new job (a free thread). */
    default boolean hasFreeThread() {
        return true;
    }

    /** Preferred-first CPU selection after the selection-mode filter. */
    default boolean isPreferredFor(BaseActionSource source) {
        return false;
    }

    /** Owner whose finished CPUs are released together; released once per crafting-cache tick. */
    default Object lifecycleOwner() {
        return this;
    }

    /** Releases finished CPUs of the owner; true when the published CPU set changed. */
    default boolean releaseFinishedCpus() {
        return false;
    }
}
