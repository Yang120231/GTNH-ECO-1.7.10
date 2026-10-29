package cn.dancingsnow.neoecoae.api.me.diagnostics;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** The loaded physical destinations of the most recent accepted push, including split GTNH sends. */
public interface ECOProcessingTargetProvider {

    @Nullable
    Target neoecoae$getProcessingTarget();

    default List<Target> neoecoae$getProcessingTargets() {
        var target = neoecoae$getProcessingTarget();
        return target == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(target);
    }

    @com.github.bsideup.jabel.Desugar
    record Target(int dimension, BlockPos position) {
        public Target { position = position.immutable(); }
    }
}
