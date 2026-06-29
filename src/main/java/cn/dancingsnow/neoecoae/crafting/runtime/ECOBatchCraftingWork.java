package cn.dancingsnow.neoecoae.crafting.runtime;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

public final class ECOBatchCraftingWork implements ECOBatchCraftingFallback {

    private static final String TAG_VERSION = "Version";
    private static final String TAG_REQUEST = "Request";
    private static final String TAG_STATE = "State";
    private static final String TAG_PROCESSED = "Processed";
    private static final String TAG_FAILURE = "Failure";
    private static final int VERSION = 1;

    private final ECOBatchCraftingRequest request;
    private ECOBatchCraftingState state;
    private long processedOperations;
    private String failureReason;

    private ECOBatchCraftingWork(ECOBatchCraftingRequest request, ECOBatchCraftingState state, long processedOperations,
        String failureReason) {
        this.request = request == null ? ECOBatchCraftingRequest.unsupported("missing request") : request;
        this.state = state == null ? ECOBatchCraftingState.PENDING : state;
        this.processedOperations = Math.max(0L, processedOperations);
        this.failureReason = failureReason == null ? "" : failureReason;
    }

    public static ECOBatchCraftingWork pending(ECOBatchCraftingRequest request) {
        ECOBatchCraftingWork work = new ECOBatchCraftingWork(request, ECOBatchCraftingState.PENDING, 0L, "");
        if (work.request.requiresFallback()) {
            work.fallback(work.request.getFallbackReason());
        }
        return work;
    }

    public static ECOBatchCraftingWork readFromNBT(NBTTagCompound tag) {
        if (tag == null || tag.getInteger(TAG_VERSION) != VERSION) {
            return failed(ECOBatchCraftingRequest.unsupported("unsupported work nbt"), "unsupported work nbt");
        }
        ECOBatchCraftingRequest request = tag.hasKey(TAG_REQUEST, Constants.NBT.TAG_COMPOUND)
            ? ECOBatchCraftingRequest.readFromNBT(tag.getCompoundTag(TAG_REQUEST))
            : ECOBatchCraftingRequest.unsupported("missing request nbt");
        return new ECOBatchCraftingWork(
            request,
            ECOBatchCraftingState.byName(tag.getString(TAG_STATE)),
            tag.getLong(TAG_PROCESSED),
            tag.getString(TAG_FAILURE));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_VERSION, VERSION);
        tag.setTag(TAG_REQUEST, this.request.writeToNBT());
        tag.setString(TAG_STATE, this.state.name());
        tag.setLong(TAG_PROCESSED, this.processedOperations);
        if (this.failureReason.length() > 0) {
            tag.setString(TAG_FAILURE, this.failureReason);
        }
        return tag;
    }

    public void start() {
        if (this.state == ECOBatchCraftingState.PENDING) {
            this.state = ECOBatchCraftingState.RUNNING;
        }
    }

    public void advance(long operations) {
        if (operations <= 0L || this.isTerminal()) {
            return;
        }
        this.start();
        this.processedOperations = Long.MAX_VALUE - this.processedOperations < operations ? Long.MAX_VALUE
            : this.processedOperations + operations;
    }

    public void complete() {
        this.state = ECOBatchCraftingState.COMPLETE;
    }

    public void fail(String reason) {
        this.state = ECOBatchCraftingState.FAILED;
        this.failureReason = reason == null ? "" : reason;
    }

    @Override
    public void fallback(String reason) {
        this.state = ECOBatchCraftingState.FALLBACK;
        this.failureReason = reason == null ? "" : reason;
    }

    public ECOBatchCraftingRequest getRequest() {
        return this.request;
    }

    public ECOBatchCraftingState getState() {
        return this.state;
    }

    public long getProcessedOperations() {
        return this.processedOperations;
    }

    @Override
    public String getFallbackReason() {
        return this.failureReason;
    }

    public String getFailureReason() {
        return this.failureReason;
    }

    @Override
    public boolean shouldFallback() {
        return this.state == ECOBatchCraftingState.FALLBACK;
    }

    public boolean isTerminal() {
        return this.state == ECOBatchCraftingState.COMPLETE || this.state == ECOBatchCraftingState.FAILED
            || this.state == ECOBatchCraftingState.FALLBACK;
    }

    private static ECOBatchCraftingWork failed(ECOBatchCraftingRequest request, String reason) {
        ECOBatchCraftingWork work = new ECOBatchCraftingWork(request, ECOBatchCraftingState.FAILED, 0L, reason);
        return work;
    }
}
