package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

/** Native CPU hooks for both the pinned AE2 API and GTNH 2.9's lite-planning overload. */
public final class NativeCpuTargets {

    private NativeCpuTargets() {}

    public static final String PUBLISH = "updateCPUClusters()V";
    public static final String ADD = "addNode(Lappeng/api/networking/IGridNode;Lappeng/api/networking/IGridHost;)V";
    public static final String REMOVE = "removeNode(Lappeng/api/networking/IGridNode;Lappeng/api/networking/IGridHost;)V";
    public static final String PLAN = "beginCraftingJob(Lnet/minecraft/world/World;Lappeng/api/networking/IGrid;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/storage/data/IAEStack;Lappeng/api/config/CraftingMode;Lappeng/api/networking/crafting/ICraftingCallback;)Ljava/util/concurrent/Future;";
    public static final String PLAN_WITH_LITE_MODE = "beginCraftingJob(Lnet/minecraft/world/World;Lappeng/api/networking/IGrid;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/storage/data/IAEStack;Lappeng/api/config/CraftingMode;ZLappeng/api/networking/crafting/ICraftingCallback;)Ljava/util/concurrent/Future;";
    public static final String SUBMIT = "submitJob(Lappeng/api/networking/crafting/ICraftingJob;Lappeng/api/networking/crafting/ICraftingRequester;Lappeng/api/networking/crafting/ICraftingCPU;ZLappeng/api/networking/security/BaseActionSource;Z)Lappeng/api/networking/crafting/ICraftingLink;";
}
