package cn.dancingsnow.neoecoae.api.me.output;

import java.util.UUID;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Accepts physical worker outputs, including surplus, into their owning CPU. */
public interface ECOJobOutputReceiver {

    long neoecoae$insertWorkerOutput(UUID jobId, AEKey what, long amount, Actionable type);
}
