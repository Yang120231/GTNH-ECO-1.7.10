package cn.dancingsnow.neoecoae.crafting.planner.solve;

import java.util.Map;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

public final class SolveWorkspace {

    private final PlannerInventorySnapshot inventory;
    private final Map<AEKey, Integer> candidateChoice;

    public SolveWorkspace(KeyCounter inventory, Map<AEKey, Integer> candidateChoice) {
        this(PlannerInventorySnapshot.of(inventory), candidateChoice);
    }

    public SolveWorkspace(PlannerInventorySnapshot inventory, Map<AEKey, Integer> candidateChoice) {
        this.inventory = inventory;
        this.candidateChoice = candidateChoice;
    }

    PlannerInventorySnapshot inventory() {
        return inventory;
    }

    Map<AEKey, Integer> candidateChoice() {
        return candidateChoice;
    }

}
