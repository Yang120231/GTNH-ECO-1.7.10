package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageHelper;
import cn.dancingsnow.neoecoae.platform.fluid.LongFluidTank;

/** Reconciles the existing hatch inventory with its ME stock target, keeping rejected returns locally. */
final class FluidHatchNetworkPull {

    private FluidHatchNetworkPull() {}

    static void sync(LongFluidTank tank, @Nullable AEFluidKey selected, long target, MEStorage network,
        IEnergySource energy, IActionSource source) {
        if (target < 0) return;
        long stored = tank.getStoredAmount();
        if (stored > 0) {
            AEFluidKey current = AEFluidKey.of(tank.getFluid());
            long excess = !current.equals(selected) ? stored : Math.max(0L, stored - target);
            if (excess > 0) {
                long returned = network.insert(current, excess, Actionable.MODULATE, source);
                if (returned < 0 || returned > excess) throw new IllegalStateException("Invalid fluid return amount");
                if (returned > 0) tank.remove(returned);
                if (returned < excess) return;
            }
        }
        if (selected == null || target <= tank.getStoredAmount()) return;
        long extracted = StorageHelper
            .poweredExtraction(energy, network, selected, target - tank.getStoredAmount(), source);
        if (extracted > 0) tank.add(selected.toStack(1), extracted);
    }
}
