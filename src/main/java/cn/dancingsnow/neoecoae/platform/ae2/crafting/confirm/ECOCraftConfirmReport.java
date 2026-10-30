package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;

/** Material table and cycle-item list of one ECO plan. Sent once per plan, identified by its version. */
public final class ECOCraftConfirmReport {

    /** Bounds keep the whole report inside one vanilla custom payload. */
    public static final int MAX_MATERIALS = 4096;
    public static final int MAX_CYCLE_ITEMS = 1024;

    public static final ECOCraftConfirmReport EMPTY = new ECOCraftConfirmReport(
        0L,
        Collections.emptyList(),
        ECOCycleItemList.EMPTY);

    public final long version;
    public final List<Row> materials;
    public final ECOCycleItemList cycles;

    public ECOCraftConfirmReport(long version, List<Row> materials, ECOCycleItemList cycles) {
        this.version = version;
        this.materials = Collections.unmodifiableList(
            new ArrayList<>(materials.size() > MAX_MATERIALS ? materials.subList(0, MAX_MATERIALS) : materials));
        this.cycles = cycles.items()
            .size() > MAX_CYCLE_ITEMS ? new ECOCycleItemList(
                new ArrayList<>(
                    cycles.items()
                        .subList(0, MAX_CYCLE_ITEMS)))
                : cycles;
    }

    public boolean isCycleParticipant(AEKey key) {
        for (ECOCycleItemList.Entry entry : cycles.items()) if (entry.what()
            .equals(key)) return true;
        return false;
    }

    /** A startup seed the cycle cannot produce by itself and the network does not hold. */
    public boolean isMissingStartupSeed(AEKey key) {
        for (ECOCycleItemList.Entry entry : cycles.items()) if (entry.what()
            .equals(key)
            && entry.exactMissing()
                .signum() > 0
            && entry.solveStatus()
                == cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
            && entry.exactSingleNetOutput()
                .signum() == 0
            && entry.exactTotalNetOutput()
                .signum() == 0)
            return true;
        return false;
    }

    public boolean isEstimatedStartupSeed(AEKey key) {
        for (ECOCycleItemList.Entry entry : cycles.items()) if (entry.what()
            .equals(key) && entry.seedShortfallIsEstimate()) return true;
        return false;
    }

    public boolean anySeedShortfallEstimate() {
        for (ECOCycleItemList.Entry entry : cycles.items()) if (entry.seedShortfallIsEstimate()) return true;
        return false;
    }

    public boolean hasMissing() {
        for (Row row : materials) if (row.missing.signum() > 0) return true;
        return false;
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeVarLong(version);
        data.writeVarInt(materials.size());
        for (Row row : materials) row.write(data);
        cycles.writeToPacket(data);
    }

    public static ECOCraftConfirmReport read(RegistryFriendlyByteBuf data) {
        long version = data.readVarLong();
        int size = data.readVarInt();
        if (size < 0 || size > MAX_MATERIALS) throw new IllegalArgumentException("Invalid material count " + size);
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) rows.add(Row.read(data));
        return new ECOCraftConfirmReport(version, rows, new ECOCycleItemList(data));
    }

    /** One cell of the 3x7 material table, with exact planner amounts. */
    public static final class Row {

        public final AEKey key;
        public final BigInteger stored;
        public final BigInteger missing;
        public final BigInteger craft;
        /** Exact requested amount for planner-backed rows; zero when the row came from AE2's summary. */
        public final BigInteger requested;
        /** Executions of the patterns producing this key in the plan (GTNH's "crafts" column). */
        public final BigInteger rounds;
        /** Network stock when the report was built; zero when unknown. */
        public final BigInteger available;

        public Row(AEKey key, BigInteger stored, BigInteger missing, BigInteger craft, BigInteger requested) {
            this(key, stored, missing, craft, requested, BigInteger.ZERO, BigInteger.ZERO);
        }

        public Row(AEKey key, BigInteger stored, BigInteger missing, BigInteger craft, BigInteger requested,
            BigInteger rounds, BigInteger available) {
            this.key = key;
            this.stored = nonNegative(stored);
            this.missing = nonNegative(missing);
            this.craft = nonNegative(craft);
            this.requested = nonNegative(requested);
            this.rounds = nonNegative(rounds);
            this.available = nonNegative(available);
        }

        Row with(BigInteger rounds, BigInteger available) {
            return new Row(key, stored, missing, craft, requested, rounds, available);
        }

        public Row(AEKey key, long stored, long missing, long craft) {
            this(
                key,
                BigInteger.valueOf(stored),
                BigInteger.valueOf(missing),
                BigInteger.valueOf(craft),
                BigInteger.ZERO);
        }

        void write(RegistryFriendlyByteBuf data) {
            AEKey.writeKey(data, key);
            writeBig(data, stored);
            writeBig(data, missing);
            writeBig(data, craft);
            writeBig(data, requested);
            writeBig(data, rounds);
            writeBig(data, available);
        }

        static Row read(RegistryFriendlyByteBuf data) {
            return new Row(
                AEKey.readKey(data),
                readBig(data),
                readBig(data),
                readBig(data),
                readBig(data),
                readBig(data),
                readBig(data));
        }

        private static BigInteger nonNegative(BigInteger value) {
            return value == null || value.signum() < 0 ? BigInteger.ZERO : value;
        }

        private static void writeBig(RegistryFriendlyByteBuf data, BigInteger value) {
            byte[] bytes = value.toByteArray();
            if (bytes.length > 4096) throw new IllegalArgumentException("Report amount is too large");
            data.writeByteArray(bytes);
        }

        private static BigInteger readBig(RegistryFriendlyByteBuf data) {
            byte[] bytes = data.readByteArray(4096);
            return bytes.length == 0 ? BigInteger.ZERO : new BigInteger(bytes);
        }
    }
}
