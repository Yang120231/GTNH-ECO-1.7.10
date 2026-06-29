package cn.dancingsnow.neoecoae.crafting.runtime;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.storage.data.IAEStack;

public final class ECOBatchCraftingRequest {

    private static final String TAG_VERSION = "Version";
    private static final String TAG_ID = "Id";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_BYTE_TOTAL = "ByteTotal";
    private static final String TAG_SIMULATION = "Simulation";
    private static final String TAG_REASON = "FallbackReason";
    private static final int VERSION = 1;

    private final UUID id;
    private final ECOCraftingStackKey output;
    private final long byteTotal;
    private final boolean simulation;
    private final String fallbackReason;

    private ECOBatchCraftingRequest(UUID id, ECOCraftingStackKey output, long byteTotal, boolean simulation,
        String fallbackReason) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.output = output == null ? ECOCraftingStackKey.empty() : output;
        this.byteTotal = Math.max(0L, byteTotal);
        this.simulation = simulation;
        this.fallbackReason = fallbackReason == null ? "" : fallbackReason;
    }

    public static ECOBatchCraftingRequest fromJob(ICraftingJob<?> job) {
        if (job == null) {
            return unsupported("missing crafting job");
        }
        try {
            IAEStack<?> output = job.getOutput();
            return new ECOBatchCraftingRequest(
                UUID.randomUUID(),
                ECOCraftingStackKey.of(output),
                job.getByteTotal(),
                job.isSimulation(),
                "");
        } catch (RuntimeException e) {
            return unsupported(
                e.getClass()
                    .getSimpleName());
        }
    }

    public static ECOBatchCraftingRequest unsupported(String reason) {
        return new ECOBatchCraftingRequest(UUID.randomUUID(), ECOCraftingStackKey.empty(), 0L, false, reason);
    }

    public static ECOBatchCraftingRequest readFromNBT(NBTTagCompound tag) {
        if (tag == null || tag.getInteger(TAG_VERSION) != VERSION) {
            return unsupported("unsupported request nbt");
        }
        ECOCraftingStackKey output = tag.hasKey(TAG_OUTPUT, Constants.NBT.TAG_COMPOUND)
            ? ECOCraftingStackKey.readFromNBT(tag.getCompoundTag(TAG_OUTPUT))
            : ECOCraftingStackKey.empty();
        return new ECOBatchCraftingRequest(
            readUuid(tag.getString(TAG_ID)),
            output,
            tag.getLong(TAG_BYTE_TOTAL),
            tag.getBoolean(TAG_SIMULATION),
            tag.getString(TAG_REASON));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_VERSION, VERSION);
        tag.setString(TAG_ID, this.id.toString());
        tag.setTag(TAG_OUTPUT, this.output.writeToNBT());
        tag.setLong(TAG_BYTE_TOTAL, this.byteTotal);
        tag.setBoolean(TAG_SIMULATION, this.simulation);
        if (this.fallbackReason.length() > 0) {
            tag.setString(TAG_REASON, this.fallbackReason);
        }
        return tag;
    }

    public UUID getId() {
        return this.id;
    }

    public ECOCraftingStackKey getOutput() {
        return this.output;
    }

    public long getByteTotal() {
        return this.byteTotal;
    }

    public boolean isSimulation() {
        return this.simulation;
    }

    public boolean requiresFallback() {
        return this.fallbackReason.length() > 0 || this.output.isEmpty();
    }

    public String getFallbackReason() {
        return this.fallbackReason;
    }

    private static UUID readUuid(String value) {
        try {
            return value == null || value.length() == 0 ? UUID.randomUUID() : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }
}
