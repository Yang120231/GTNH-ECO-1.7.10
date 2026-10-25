package cn.dancingsnow.neoecoae.api.me.progress;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;

/** Read-only progress contract for displays and integrations. */
public interface ECOCraftingProgressView {

    default java.util.Optional<cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress> bigOrder() {
        return java.util.Optional.empty();
    }

    float progress();

    long elapsedTimeNanos();

    long startedWork(AEKeyType keyType);

    long completedWork(AEKeyType keyType);
}
