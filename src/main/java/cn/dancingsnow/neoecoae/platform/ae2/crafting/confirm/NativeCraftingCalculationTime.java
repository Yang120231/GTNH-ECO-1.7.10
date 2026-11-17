package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

/** Active native planning time, excluding time waiting between AE2 simulation ticks. */
public interface NativeCraftingCalculationTime {

    long neoecoae$calculationNanos();
}
