package cn.dancingsnow.neoecoae.api.me.dispatch;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingCPU;

/** Read-only CPU state supplied to dispatch-policy callbacks. */
@com.github.bsideup.jabel.Desugar
public record ECOCraftingCpuContext(ICraftingCPU cpu, @Nullable UUID craftingJobId, boolean active, int coProcessors,
    long gameTick) {

    public ECOCraftingCpuContext {
        Objects.requireNonNull(cpu, "cpu");
        if (coProcessors < 0L) {
            throw new IllegalArgumentException("coProcessors must not be negative");
        }
    }
}
