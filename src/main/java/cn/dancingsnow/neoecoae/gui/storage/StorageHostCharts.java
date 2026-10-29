package cn.dancingsnow.neoecoae.gui.storage;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

public final class StorageHostCharts {

    private static final int TEXT = 0xFF3F3D52;

    @com.github.bsideup.jabel.Desugar
    public record CapacitySegment(int swatch, int color, int width,
        cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation channel,
        cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation texture) {

        CapacitySegment(int swatch, int color, int width) {
            this(swatch, color, width, null, null);
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record ResourceColor(int swatch, int color,
        cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation channel,
        cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation texture) {}

    public static List<CapacitySegment> capacitySegments(StoragePanelState state, int availableWidth) {
        int width = Math.max(0, availableWidth);
        if (width <= 0) return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
        Map<ResourceColor, BigInteger> amounts = new LinkedHashMap<>();
        for (StoragePanelState.TypeLine type : state.types()) {
            if (type.exactUsedBytes()
                .signum() <= 0) continue;
            amounts.merge(
                new ResourceColor(type.swatch(), type.color(), type.channel(), type.swatchTexture()),
                type.exactUsedBytes(),
                BigInteger::add);
        }
        BigInteger total = amounts.values()
            .stream()
            .reduce(BigInteger.ZERO, BigInteger::add);
        if (total.signum() <= 0) return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
        var resources = amounts.entrySet()
            .stream()
            .sorted(
                Comparator.<Map.Entry<ResourceColor, BigInteger>, BigInteger>comparing(Map.Entry::getValue)
                    .reversed()
                    .thenComparingInt(
                        entry -> entry.getKey()
                            .swatch())
                    .thenComparingInt(
                        entry -> entry.getKey()
                            .color())
                    .thenComparing(
                        entry -> String.valueOf(
                            entry.getKey()
                                .channel())))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        List<CapacitySegment> segments = new ArrayList<>();
        BigInteger accumulated = BigInteger.ZERO;
        int previous = 0;
        for (var resource : resources) {
            accumulated = accumulated.add(resource.getValue());
            int end = accumulated.multiply(BigInteger.valueOf(width))
                .add(total.shiftRight(1))
                .divide(total)
                .intValue();
            if (end > previous) segments.add(
                new CapacitySegment(
                    resource.getKey()
                        .swatch(),
                    resource.getKey()
                        .color(),
                    end - previous,
                    resource.getKey()
                        .channel(),
                    resource.getKey()
                        .texture()));
            previous = end;
        }
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(segments);
    }

    public static Component capacityValue(StoragePanelState state) {
        var used = Component.literal(state.usedBytes())
            .withColor(
                state.unbounded() ? StoragePanelTextures.VALUE_TEXT : HostText.gaugeTextColor((float) state.usage()));
        if (state.unbounded()) return used;
        return used.append(
            Component.literal(" / ")
                .withColor(TEXT))
            .append(
                Component.literal(HostText.ae2Amount(state.totalBytes()))
                    .withColor(StoragePanelTextures.VALUE_TEXT));
    }

    private static Component fullCapacityValue(StoragePanelState state) {
        var used = Component.literal(fullUsedBytes(state))
            .withColor(state.unbounded() ? HostText.VALUE : HostText.gaugeTextColor((float) state.usage()));
        if (state.unbounded()) return used;
        return HostText.usedOfTotal(used, " / ", HostText.totalPart(HostText.fullBytes(state.totalBytes())));
    }

    private static String fullUsedBytes(StoragePanelState state) {
        BigInteger used = state.types()
            .stream()
            .map(StoragePanelState.TypeLine::exactUsedBytes)
            .reduce(BigInteger.ZERO, BigInteger::add);
        return HostText.fullBytes(used);
    }

    public List<Component> capacityTooltip() {
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            Component.translatable("gui.neoecoae.storage.legacy.graph.total_bytes", fullCapacityValue(state())),
            Component.translatable(
                "gui.neoecoae.storage.legacy.graph.total_usage",
                state().unbounded() ? "∞" : HostText.percent(state().usage())));
    }

    public List<Component> energyTooltip() {
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            Component.translatable(
                "gui.neoecoae.storage.ui.energy",
                HostText.usedPart(HostText.ae2Amount(state().energy()), state().energy(), state().maxEnergy()),
                HostText.totalPart(HostText.ae2Amount(state().maxEnergy()))),
            Component
                .translatable("gui.neoecoae.storage.legacy.graph.energy_usage", HostText.ae2Amount(state().drain())));
    }

    public List<Component> resourceTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.neoecoae.storage.ui.resource_distribution"));
        BigInteger total = state().types()
            .stream()
            .map(StoragePanelState.TypeLine::exactUsedBytes)
            .reduce(BigInteger.ZERO, BigInteger::add);
        if (total.signum() <= 0) return lines;
        for (StoragePanelState.TypeLine type : state().types()) {
            if (type.exactUsedBytes()
                .signum() <= 0) continue;
            double share = new BigDecimal(type.exactUsedBytes()).divide(new BigDecimal(total), MathContext.DECIMAL64)
                .doubleValue();
            lines.add(
                Component
                    .translatable(
                        "gui.neoecoae.storage.ui.resource_share",
                        type.name(),
                        HostText.fullBytes(type.exactUsedBytes()),
                        HostText.percent(share))
                    .withColor(type.color()));
        }
        return lines;
    }

    public List<Component> resourceTooltip(StoragePanelState snapshot, CapacitySegment segment) {
        var types = snapshot.types()
            .stream()
            .filter(
                type -> type.exactUsedBytes()
                    .signum() > 0 && type.swatch() == segment.swatch()
                    && type.color() == segment.color()
                    && java.util.Objects.equals(type.channel(), segment.channel())
                    && java.util.Objects.equals(type.swatchTexture(), segment.texture()))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        if (types.isEmpty()) return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
        BigInteger used = types.stream()
            .map(StoragePanelState.TypeLine::exactUsedBytes)
            .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger total = snapshot.types()
            .stream()
            .map(StoragePanelState.TypeLine::exactUsedBytes)
            .filter(amount -> amount.signum() > 0)
            .reduce(BigInteger.ZERO, BigInteger::add);
        var name = Component.empty();
        for (Component alias : types.stream()
            .map(StoragePanelState.TypeLine::name)
            .distinct()
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList())) {
            if (!name.getString()
                .isEmpty()) name.append(" / ");
            name.append(alias);
        }
        double share = new BigDecimal(used).divide(new BigDecimal(total), MathContext.DECIMAL64)
            .doubleValue();
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            Component
                .translatable(
                    "gui.neoecoae.storage.ui.resource_share",
                    name,
                    HostText.fullBytes(used),
                    HostText.percent(share))
                .withColor(segment.color()));
    }

    public List<Component> domainTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(
            Component.translatable(
                state().migrating() ? "gui.neoecoae.storage.ui.migrating"
                    : state().infinite() ? "gui.neoecoae.storage.ui.infinite" : "gui.neoecoae.storage.ui.normal"));
        if (state().migrating()) {
            lines.add(
                Component.translatable("gui.neoecoae.storage.ui.migration_progress", state().migrationProgress() + "%")
                    .withColor(0xFFD966));
            String blocked = state().migrationBlockedReason();
            if (blocked != null && !blocked.isEmpty()) lines.add(
                Component.translatable("gui.neoecoae.storage.ui.migration_blocked", Component.translatable(blocked))
                    .withColor(0xFF6A75));
        }
        if (!state().domainId()
            .isEmpty())
            lines.add(
                Component.literal("UUID: " + state().domainId())
                    .withColor(0x55FFFF));
        if (state().missingMembers() > 0) {
            lines.add(
                Component.translatable("gui.neoecoae.storage.members_missing", state().missingMembers())
                    .withColor(0xFF6A75));
            lines.add(
                Component.translatable("gui.neoecoae.storage.members_return")
                    .withColor(0xFF6A75));
        }
        return lines;
    }

    private final java.util.function.Supplier<StoragePanelState> snapshot;

    public StorageHostCharts(java.util.function.Supplier<StoragePanelState> snapshot) {
        this.snapshot = snapshot;
    }

    private StoragePanelState state() {
        return snapshot.get();
    }
}
