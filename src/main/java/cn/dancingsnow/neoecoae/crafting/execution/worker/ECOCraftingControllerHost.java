package cn.dancingsnow.neoecoae.crafting.execution.worker;

/** Live source controller contract; each decision is implemented by the formed crafting machine. */
public interface ECOCraftingControllerHost {

    int MAX_OVERCLOCK_TIMES = 9;
    int VIRTUAL_COOLANT_PER_LANE_TICK = 10_000;
    long VIRTUAL_CRAFTING_POWER_PER_TICK = 10_000_000L;

    enum VirtualLaneStartResult {
        STARTED,
        COOLANT_UNAVAILABLE,
        ENERGY_UNAVAILABLE
    }

    Object getDispatchScope();

    long getDisplayedCoolantAmount();

    long getDisplayedCoolantCapacity();

    int getDisplayedCoolingMaxOverclock();

    int getEffectiveOverclockTimes();

    boolean isActiveCooling();

    boolean isFullVirtualCraftingMode();

    boolean tryConsumeCoolant(int amount, int overclock);

    boolean tryConsumeTickBasedCoolant(int crafts, int progress, int overclock);

    boolean tryConsumeVirtualCraftingPower();

    VirtualLaneStartResult tryStartVirtualLaneTickWithResult();

    boolean usesTickBasedCoolant();
}
