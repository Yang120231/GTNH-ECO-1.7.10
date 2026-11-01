package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;

/**
 * Header of the ECO crafting report: everything the 1.21 confirmation menu synchronizes besides the material and
 * cycle lists. It is resent whenever it changes, so it carries only small values.
 */
public final class ECOCraftConfirmState {

    public static final int MAX_DIAGNOSTIC_LENGTH = 4096;
    /** Most shortage rows a failed start carries to the client. */
    public static final int MAX_SHORTAGES = 64;

    /** Not an ECO confirmation; the native AE2 screen stays in charge. */
    public static final ECOCraftConfirmState NONE = new ECOCraftConfirmState(
        false,
        false,
        false,
        false,
        0L,
        BigInteger.ZERO,
        null,
        "",
        0,
        true,
        0L,
        0L,
        null,
        "");

    /** The request was routed to the ECO planner; the ECO report owns this confirmation. */
    public final boolean plannerAvailable;
    /** The ECO plan has arrived and its report rows were built. */
    public final boolean reportReady;
    public final boolean cyclePlanningEnabled;
    public final boolean cycleDetected;
    public final long calculationNanos;
    public final BigInteger theoreticalBytes;
    @Nullable
    public final PlanningStatus status;
    public final String diagnostic;
    /** CPU tier glyph for the report header: 4, 6, 9, or 0 when the selected CPU is not ECO. */
    public final int selectedCpuTier;
    public final boolean simulation;
    public final long usedBytes;
    /** Version of the material/cycle report this header belongs to. */
    public final long reportVersion;
    @Nullable
    public final ECOCraftConfirmError error;
    public final String errorDetail;
    /** An idle, online ECO CPU can take this plan as a big order (1.21's neoecoae$bigOrderCpu). */
    public final boolean bigOrderCpuAvailable;
    /** Inputs the network could no longer supply when the start was rejected; empty unless one was. */
    public final List<GenericStack> shortages;

    public ECOCraftConfirmState(boolean plannerAvailable, boolean reportReady, boolean cyclePlanningEnabled,
        boolean cycleDetected, long calculationNanos, BigInteger theoreticalBytes, @Nullable PlanningStatus status,
        String diagnostic, int selectedCpuTier, boolean simulation, long usedBytes, long reportVersion,
        @Nullable ECOCraftConfirmError error, String errorDetail) {
        this(
            plannerAvailable,
            reportReady,
            cyclePlanningEnabled,
            cycleDetected,
            calculationNanos,
            theoreticalBytes,
            status,
            diagnostic,
            selectedCpuTier,
            simulation,
            usedBytes,
            reportVersion,
            error,
            errorDetail,
            false);
    }

    public ECOCraftConfirmState(boolean plannerAvailable, boolean reportReady, boolean cyclePlanningEnabled,
        boolean cycleDetected, long calculationNanos, BigInteger theoreticalBytes, @Nullable PlanningStatus status,
        String diagnostic, int selectedCpuTier, boolean simulation, long usedBytes, long reportVersion,
        @Nullable ECOCraftConfirmError error, String errorDetail, boolean bigOrderCpuAvailable) {
        this(
            plannerAvailable,
            reportReady,
            cyclePlanningEnabled,
            cycleDetected,
            calculationNanos,
            theoreticalBytes,
            status,
            diagnostic,
            selectedCpuTier,
            simulation,
            usedBytes,
            reportVersion,
            error,
            errorDetail,
            bigOrderCpuAvailable,
            Collections.emptyList());
    }

    public ECOCraftConfirmState(boolean plannerAvailable, boolean reportReady, boolean cyclePlanningEnabled,
        boolean cycleDetected, long calculationNanos, BigInteger theoreticalBytes, @Nullable PlanningStatus status,
        String diagnostic, int selectedCpuTier, boolean simulation, long usedBytes, long reportVersion,
        @Nullable ECOCraftConfirmError error, String errorDetail, boolean bigOrderCpuAvailable,
        @Nullable List<GenericStack> shortages) {
        this.plannerAvailable = plannerAvailable;
        this.reportReady = reportReady;
        this.cyclePlanningEnabled = cyclePlanningEnabled;
        this.cycleDetected = cycleDetected;
        this.calculationNanos = Math.max(0L, calculationNanos);
        this.theoreticalBytes = theoreticalBytes == null || theoreticalBytes.signum() < 0 ? BigInteger.ZERO
            : theoreticalBytes;
        this.status = status;
        this.diagnostic = truncate(diagnostic);
        this.selectedCpuTier = selectedCpuTier;
        this.simulation = simulation;
        this.usedBytes = Math.max(0L, usedBytes);
        this.reportVersion = reportVersion;
        this.error = error;
        this.errorDetail = truncate(errorDetail);
        this.bigOrderCpuAvailable = bigOrderCpuAvailable;
        this.shortages = error == null || shortages == null || shortages.isEmpty() ? Collections.emptyList()
            : Collections
                .unmodifiableList(new ArrayList<>(shortages.subList(0, Math.min(MAX_SHORTAGES, shortages.size()))));
    }

    public ECOCraftConfirmState withCpuTier(int tier) {
        if (tier == selectedCpuTier) return this;
        return new ECOCraftConfirmState(
            plannerAvailable,
            reportReady,
            cyclePlanningEnabled,
            cycleDetected,
            calculationNanos,
            theoreticalBytes,
            status,
            diagnostic,
            tier,
            simulation,
            usedBytes,
            reportVersion,
            error,
            errorDetail,
            bigOrderCpuAvailable,
            shortages);
    }

    public ECOCraftConfirmState withBigOrderCpu(boolean available) {
        if (available == bigOrderCpuAvailable) return this;
        return new ECOCraftConfirmState(
            plannerAvailable,
            reportReady,
            cyclePlanningEnabled,
            cycleDetected,
            calculationNanos,
            theoreticalBytes,
            status,
            diagnostic,
            selectedCpuTier,
            simulation,
            usedBytes,
            reportVersion,
            error,
            errorDetail,
            available,
            shortages);
    }

    public ECOCraftConfirmState withError(@Nullable ECOCraftConfirmError error, String detail) {
        return withError(error, detail, Collections.emptyList());
    }

    public ECOCraftConfirmState withError(@Nullable ECOCraftConfirmError error, String detail,
        @Nullable List<GenericStack> shortages) {
        return new ECOCraftConfirmState(
            plannerAvailable,
            reportReady,
            cyclePlanningEnabled,
            cycleDetected,
            calculationNanos,
            theoreticalBytes,
            status,
            diagnostic,
            selectedCpuTier,
            simulation,
            usedBytes,
            reportVersion,
            error,
            error == null ? "" : detail,
            bigOrderCpuAvailable,
            shortages);
    }

    /** Bytes shown in the header: the exact planner estimate when present, otherwise AE2's long total. */
    public BigInteger displayedBytes() {
        return theoreticalBytes.signum() > 0 ? theoreticalBytes : BigInteger.valueOf(usedBytes);
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeBoolean(plannerAvailable);
        data.writeBoolean(reportReady);
        data.writeBoolean(cyclePlanningEnabled);
        data.writeBoolean(cycleDetected);
        data.writeVarLong(calculationNanos);
        data.writeByteArray(theoreticalBytes.toByteArray());
        data.writeVarInt(status == null ? 0 : status.ordinal() + 1);
        data.writeUtf(diagnostic);
        data.writeVarInt(selectedCpuTier);
        data.writeBoolean(simulation);
        data.writeVarLong(usedBytes);
        data.writeVarLong(reportVersion);
        data.writeVarInt(error == null ? 0 : error.ordinal() + 1);
        data.writeUtf(errorDetail);
        data.writeBoolean(bigOrderCpuAvailable);
        data.writeVarInt(shortages.size());
        for (GenericStack shortage : shortages) {
            AEKey.writeKey(data, shortage.what());
            data.writeVarLong(shortage.amount());
        }
    }

    public static ECOCraftConfirmState read(RegistryFriendlyByteBuf data) {
        boolean plannerAvailable = data.readBoolean();
        boolean reportReady = data.readBoolean();
        boolean cyclePlanningEnabled = data.readBoolean();
        boolean cycleDetected = data.readBoolean();
        long calculationNanos = data.readVarLong();
        byte[] bytes = data.readByteArray(4096);
        BigInteger theoreticalBytes = bytes.length == 0 ? BigInteger.ZERO : new BigInteger(bytes);
        PlanningStatus status = byOrdinal(PlanningStatus.values(), data.readVarInt());
        String diagnostic = data.readUtf();
        int tier = data.readVarInt();
        boolean simulation = data.readBoolean();
        long usedBytes = data.readVarLong();
        long reportVersion = data.readVarLong();
        ECOCraftConfirmError error = byOrdinal(ECOCraftConfirmError.values(), data.readVarInt());
        String errorDetail = data.readUtf();
        boolean bigOrderCpuAvailable = data.readBoolean();
        int count = Math.min(MAX_SHORTAGES, Math.max(0, data.readVarInt()));
        List<GenericStack> shortages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AEKey key = AEKey.readKey(data);
            long amount = data.readVarLong();
            if (key != null && amount > 0) shortages.add(new GenericStack(key, amount));
        }
        return new ECOCraftConfirmState(
            plannerAvailable,
            reportReady,
            cyclePlanningEnabled,
            cycleDetected,
            calculationNanos,
            theoreticalBytes,
            status,
            diagnostic,
            tier,
            simulation,
            usedBytes,
            reportVersion,
            error,
            errorDetail,
            bigOrderCpuAvailable,
            shortages);
    }

    private static <T> @Nullable T byOrdinal(T[] values, int code) {
        int ordinal = code - 1;
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static String truncate(@Nullable String value) {
        if (value == null) return "";
        return value.length() > MAX_DIAGNOSTIC_LENGTH ? value.substring(0, MAX_DIAGNOSTIC_LENGTH) : value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ECOCraftConfirmState state)) return false;
        return plannerAvailable == state.plannerAvailable && reportReady == state.reportReady
            && cyclePlanningEnabled == state.cyclePlanningEnabled
            && cycleDetected == state.cycleDetected
            && calculationNanos == state.calculationNanos
            && selectedCpuTier == state.selectedCpuTier
            && simulation == state.simulation
            && usedBytes == state.usedBytes
            && reportVersion == state.reportVersion
            && theoreticalBytes.equals(state.theoreticalBytes)
            && status == state.status
            && diagnostic.equals(state.diagnostic)
            && error == state.error
            && errorDetail.equals(state.errorDetail)
            && bigOrderCpuAvailable == state.bigOrderCpuAvailable
            && shortages.equals(state.shortages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            plannerAvailable,
            reportReady,
            cyclePlanningEnabled,
            cycleDetected,
            calculationNanos,
            theoreticalBytes,
            status,
            diagnostic,
            selectedCpuTier,
            simulation,
            usedBytes,
            reportVersion,
            error,
            errorDetail,
            bigOrderCpuAvailable,
            shortages);
    }
}
