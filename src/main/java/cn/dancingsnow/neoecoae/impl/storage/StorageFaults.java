package cn.dancingsnow.neoecoae.impl.storage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded fault records; repeated failures retain their identity. */
public final class StorageFaults {

    @com.github.bsideup.jabel.Desugar
    public record Fault(UUID id, String component, String reason, long firstTick, long lastTick, long occurrences,
        String stackTrace) {}

    private final Map<String, Fault> faults = new LinkedHashMap<>();

    public void report(String component, String reason, long tick) {
        report(component, reason, tick, null);
    }

    public void report(String component, String reason, long tick, Throwable cause) {
        Fault previous = faults.get(component);
        String stackTrace = previous == null ? null : previous.stackTrace();
        if (cause != null && (previous == null || !previous.reason()
            .equals(reason))) {
            var trace = new java.io.StringWriter();
            cause.printStackTrace(new java.io.PrintWriter(trace));
            stackTrace = trace.toString();
            if (stackTrace.length() > 32_768) stackTrace = stackTrace.substring(0, 32_768);
        }
        if (previous == null && faults.size() >= 64) faults.remove(
            faults.keySet()
                .iterator()
                .next());
        Fault next = new Fault(
            previous == null ? UUID.randomUUID() : previous.id(),
            component,
            reason == null ? "Unknown failure" : reason,
            previous == null ? tick : previous.firstTick(),
            tick,
            previous == null ? 1L : previous.occurrences() + 1L,
            stackTrace);
        faults.put(component, next);
    }

    public void recovered(String component) {
        faults.remove(component);
    }

    public Collection<Fault> snapshot() {
        return faults.values();
    }
}
