package cn.dancingsnow.neoecoae.crafting.runtime;

import appeng.api.util.WorldCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.mixin.MixinCraftingTaskProgress;

/** Opt-in checks inside the real Forge/Mixin class loader, independent of a player's machines. */
public final class ECOCraftingIntegrationChecks {

    private ECOCraftingIntegrationChecks() {}

    public static void run() {
        CraftingCPUCluster cpu = new CraftingCPUCluster(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        require(cpu instanceof ECOCraftingBatchCoordinator, "GTNH CPU fastpath mixin");
        CraftingCPUCluster.TaskProgress progress = new CraftingCPUCluster.TaskProgress();
        require(progress instanceof MixinCraftingTaskProgress, "GTNH task progress accessor");
        MixinCraftingTaskProgress access = (MixinCraftingTaskProgress) progress;
        access.neoecoae$setValue(4_000_000_000L);
        require(access.neoecoae$getValue() == 4_000_000_000L, "64-bit task progress");
        verifyResourceIdentity();
        cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlannerHook.verifyIntegrationContract();
        NeoECOAE.LOG
            .info("ECO crafting integration checks PASSED: native CPU, fastpath coordinator, long task accessor");
    }

    private static void verifyResourceIdentity() {
        net.minecraft.item.ItemStack item = new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot);
        item.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        item.getTagCompound()
            .setString("variant", "one");
        cn.dancingsnow.neoecoae.crafting.fastpath.ECOResourceKey first = new cn.dancingsnow.neoecoae.crafting.fastpath.ECOResourceKey(
            appeng.util.item.AEItemStack.create(item));
        item.stackSize = 32;
        require(
            first.equals(
                new cn.dancingsnow.neoecoae.crafting.fastpath.ECOResourceKey(
                    appeng.util.item.AEItemStack.create(item))),
            "Amount-independent item identity");
        item.getTagCompound()
            .setString("variant", "two");
        require(
            !first.equals(
                new cn.dancingsnow.neoecoae.crafting.fastpath.ECOResourceKey(
                    appeng.util.item.AEItemStack.create(item))),
            "Exact immutable NBT identity");
        cn.dancingsnow.neoecoae.crafting.fastpath.ECOResourceKey fluid = new cn.dancingsnow.neoecoae.crafting.fastpath.ECOResourceKey(
            appeng.util.item.AEFluidStack
                .create(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000)));
        require(
            fluid.stack(4_000_000_000L)
                .getStackSize() == 4_000_000_000L,
            "Native fluid long amount");
        require(
            fluid.equals(new cn.dancingsnow.neoecoae.crafting.fastpath.ECOResourceKey(fluid.stack(1))),
            "Amount-independent fluid identity");
    }

    private static void require(boolean condition, String check) {
        if (!condition) throw new IllegalStateException("ECO crafting integration check failed: " + check);
    }
}
