package cn.dancingsnow.neoecoae.platform.ae2.grid;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.networking.energy.IAEPowerStorage;

public interface IExternalPowerSink extends IAEPowerStorage {

    double injectExternalPower(PowerUnits unit, double amount, Actionable mode);

    double getExternalPowerDemand(PowerUnits unit, double maximum);

    void notifyPersistence();
}
