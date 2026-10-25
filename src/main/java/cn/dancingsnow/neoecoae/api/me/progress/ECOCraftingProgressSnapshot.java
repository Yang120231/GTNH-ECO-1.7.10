package cn.dancingsnow.neoecoae.api.me.progress;

import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;

/** Progress snapshot detached from ECO's mutable elapsed-time tracker. */
@com.github.bsideup.jabel.Desugar
public record ECOCraftingProgressSnapshot(float progress, long elapsedTimeNanos, Map<AEKeyType, Long> startedWorkByType,
    Map<AEKeyType, Long> completedWorkByType) implements ECOCraftingProgressView {

    public ECOCraftingProgressSnapshot {
        progress = Float.isFinite(progress) ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.clamp(progress, 0.0F, 1.0F)
            : 0.0F;
        elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        startedWorkByType = Objects.requireNonNull(startedWorkByType, "startedWorkByType");
        completedWorkByType = Objects.requireNonNull(completedWorkByType, "completedWorkByType");
    }

    public ECOCraftingProgressSnapshot(float progress, long elapsedTimeNanos) {
        this(
            progress,
            elapsedTimeNanos,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
    }

    public static ECOCraftingProgressSnapshot empty() {
        return new ECOCraftingProgressSnapshot(
            0.0F,
            0L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
    }

    @Override
    public long startedWork(@Nullable AEKeyType keyType) {
        return keyType == null ? 0L : startedWorkByType.getOrDefault(keyType, 0L);
    }

    @Override
    public long completedWork(@Nullable AEKeyType keyType) {
        return keyType == null ? 0L : completedWorkByType.getOrDefault(keyType, 0L);
    }

}
