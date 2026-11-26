package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.security.PlayerSource;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitErrorCode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingSubmitResult;

/** One player submission: AE2 only returns a link, but the ECO CPU knows why it declined the exact plan. */
public final class ConfirmSubmitSource extends PlayerSource {

    private final ICraftingPlan plan;
    @Nullable
    ICraftingSubmitResult receipt;

    ConfirmSubmitSource(PlayerSource source, ICraftingPlan plan) {
        super(source.player, source.via);
        this.plan = plan;
    }

    public void record(ICraftingPlan submitted, ICraftingSubmitResult result) {
        if (submitted == plan) receipt = result;
    }

    public boolean hasAdmissionDecision(ICraftingPlan submitted) {
        return submitted == plan && receipt != null
            && (receipt.successful() || receipt.errorCode() == CraftingSubmitErrorCode.MISSING_INGREDIENT);
    }
}
