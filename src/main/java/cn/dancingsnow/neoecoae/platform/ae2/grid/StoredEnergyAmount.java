package cn.dancingsnow.neoecoae.platform.ae2.grid;

import java.util.function.Consumer;

import appeng.api.networking.events.MENetworkPowerStorage.PowerEventType;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.sync.ManagedValue;

/** Native double energy storage with capacity transition events consumed by EnergyGridCache. */
public final class StoredEnergyAmount implements ManagedValue {

    private double amount;
    private final double maximum;
    private final Consumer<PowerEventType> changed;

    public StoredEnergyAmount(double initial, double maximum, Consumer<PowerEventType> changed) {
        if (!Double.isFinite(maximum) || maximum <= 0) throw new IllegalArgumentException("Invalid capacity");
        this.maximum = maximum;
        this.amount = Math.min(maximum, Math.max(0, initial));
        this.changed = changed;
    }

    public double getAmount() {
        return amount;
    }

    public double getMaximum() {
        return maximum;
    }

    public double insert(double requested, boolean modulate) {
        if (Double.isNaN(requested) || requested <= 0) return 0;
        double inserted = Math.min(requested, maximum - amount);
        if (modulate && inserted > 0) {
            boolean wasEmpty = amount < 0.01;
            amount += inserted;
            if (wasEmpty) changed.accept(PowerEventType.PROVIDE_POWER);
        }
        return inserted;
    }

    public double extract(double requested, boolean modulate) {
        if (Double.isNaN(requested) || requested <= 0) return 0;
        double extracted = Math.min(requested, amount);
        if (modulate && extracted > 0) {
            boolean wasFull = amount >= maximum - 0.001;
            amount -= extracted;
            if (wasFull) changed.accept(PowerEventType.REQUEST_POWER);
        }
        return extracted;
    }

    @Override
    public CompoundTag writeManaged() {
        CompoundTag tag = new CompoundTag();
        tag.nativeTag()
            .setDouble("energy", amount);
        return tag;
    }

    @Override
    public void readManaged(CompoundTag tag) {
        double stored = tag.nativeTag()
            .getDouble("energy");
        if (!Double.isFinite(stored) || stored < 0 || stored > maximum)
            throw new IllegalStateException("Invalid saved energy");
        amount = stored;
    }
}
