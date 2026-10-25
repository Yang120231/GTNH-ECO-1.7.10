package cn.dancingsnow.neoecoae.crafting.planner.provenance;

import java.util.Objects;
import java.util.UUID;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** An actual assignment, not a declaration that a producer may eventually return this material. */
@com.github.bsideup.jabel.Desugar
public record SupplyAllocation(UUID demandId, AEKey material, MaterialSource source, PlannerAmount amount) {

    public SupplyAllocation {
        Objects.requireNonNull(demandId, "demandId");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Allocation must be positive");
    }
}
