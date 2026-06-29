package cn.dancingsnow.neoecoae.crafting.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.crafting.ICraftingJob;

public final class ECOCraftingThread implements ECOBatchCraftingFallback {

    private static final String TAG_VERSION = "Version";
    private static final String TAG_ENABLED = "Enabled";
    private static final String TAG_FAILURE = "Failure";
    private static final String TAG_WORK = "Work";
    private static final int VERSION = 1;

    private final List<ECOBatchCraftingWork> workQueue = new ArrayList<ECOBatchCraftingWork>();
    private boolean enabled = true;
    private String failureReason = "";

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean hasWork() {
        return !this.workQueue.isEmpty();
    }

    public List<ECOBatchCraftingWork> workQueue() {
        return Collections.unmodifiableList(this.workQueue);
    }

    public ECOBatchCraftingWork enqueue(ICraftingJob<?> job) {
        if (!this.enabled) {
            return null;
        }
        ECOBatchCraftingWork work = ECOBatchCraftingWork.pending(ECOBatchCraftingRequest.fromJob(job));
        this.workQueue.add(work);
        if (work.getState() == ECOBatchCraftingState.FALLBACK) {
            this.disable(work.getFailureReason());
        }
        return work;
    }

    public void tick(int operations) {
        if (!this.enabled || operations <= 0) {
            return;
        }
        try {
            for (ECOBatchCraftingWork work : this.workQueue) {
                if (!work.isTerminal()) {
                    work.advance(operations);
                    return;
                }
            }
            this.removeTerminalWork();
        } catch (RuntimeException e) {
            this.disable(
                e.getClass()
                    .getSimpleName());
        }
    }

    public void completeCurrent() {
        ECOBatchCraftingWork work = this.currentWork();
        if (work != null) {
            work.complete();
        }
        this.removeTerminalWork();
    }

    public void failCurrent(String reason) {
        ECOBatchCraftingWork work = this.currentWork();
        if (work != null) {
            work.fail(reason);
        }
        this.disable(reason);
    }

    public void disable(String reason) {
        this.enabled = false;
        this.failureReason = reason == null ? "" : reason;
        for (ECOBatchCraftingWork work : this.workQueue) {
            if (!work.isTerminal()) {
                work.fallback(this.failureReason);
            }
        }
    }

    @Override
    public void fallback(String reason) {
        this.disable(reason);
    }

    @Override
    public boolean shouldFallback() {
        return !this.enabled;
    }

    @Override
    public String getFallbackReason() {
        return this.failureReason;
    }

    public String getFailureReason() {
        return this.failureReason;
    }

    public void readFromNBT(NBTTagCompound tag) {
        this.workQueue.clear();
        this.enabled = true;
        this.failureReason = "";
        if (tag == null || tag.getInteger(TAG_VERSION) != VERSION) {
            return;
        }
        this.enabled = tag.getBoolean(TAG_ENABLED);
        this.failureReason = tag.getString(TAG_FAILURE);
        NBTTagList workTags = tag.getTagList(TAG_WORK, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < workTags.tagCount(); i++) {
            this.workQueue.add(ECOBatchCraftingWork.readFromNBT(workTags.getCompoundTagAt(i)));
        }
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(TAG_VERSION, VERSION);
        tag.setBoolean(TAG_ENABLED, this.enabled);
        if (this.failureReason.length() > 0) {
            tag.setString(TAG_FAILURE, this.failureReason);
        }
        NBTTagList workTags = new NBTTagList();
        for (ECOBatchCraftingWork work : this.workQueue) {
            if (!work.isTerminal()) {
                workTags.appendTag(work.writeToNBT());
            }
        }
        tag.setTag(TAG_WORK, workTags);
    }

    private ECOBatchCraftingWork currentWork() {
        for (ECOBatchCraftingWork work : this.workQueue) {
            if (!work.isTerminal()) {
                return work;
            }
        }
        return null;
    }

    private void removeTerminalWork() {
        Iterator<ECOBatchCraftingWork> iterator = this.workQueue.iterator();
        while (iterator.hasNext()) {
            if (iterator.next()
                .isTerminal()) {
                iterator.remove();
            }
        }
    }
}
