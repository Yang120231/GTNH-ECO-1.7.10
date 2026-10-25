package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import java.util.ArrayList;
import java.util.List;

import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Public optional semantics bridge, with AE2 as the unconditional fallback. */
public final class PatternSemanticAdapters {

    private PatternSemanticAdapters() {}

    public static List<PatternSemanticAdapter> defaults() {
        List<PatternSemanticAdapter> adapters = new ArrayList<>();
        for (var registration : ECOPatternSemanticsRegistry.snapshot()) {
            adapters.add(new RegisteredAdapter(registration));
        }
        adapters.add(new AE2PatternSemanticAdapter());
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(adapters);
    }

    /** Exception-safe entry point for consumers without explicit test adapters. */
    public static PatternSemantics analyze(IPatternDetails pattern) {
        long generation = PatternRuntime.reloadGeneration();
        PatternSemanticAdapter adapter = find(defaults(), pattern);
        if (adapter == null) return PatternSemantics.unsupported(pattern, null, "NO_PATTERN_SEMANTIC_ADAPTER");
        try {
            PatternSemantics semantics = adapter.analyze(pattern);
            if (semantics == null) return PatternSemantics.unsupported(pattern, null, "NULL_PATTERN_SEMANTICS");
            if (semantics.physicalPattern() != pattern) {
                return PatternSemantics.unsupported(pattern, null, "MISMATCHED_PHYSICAL_PATTERN");
            }
            if (generation != PatternRuntime.reloadGeneration()) {
                return PatternSemantics.unsupported(pattern, null, "PATTERN_CONTRACT_CHANGED_DURING_ANALYSIS");
            }
            return semantics;
        } catch (RuntimeException | LinkageError failure) {
            return PatternSemantics.unsupported(
                pattern,
                null,
                "PATTERN_SEMANTICS_FAILED:" + failure.getClass()
                    .getSimpleName());
        }
    }

    public static PatternSemanticAdapter find(List<PatternSemanticAdapter> adapters, IPatternDetails pattern) {
        for (PatternSemanticAdapter adapter : adapters) {
            try {
                if (adapter.supports(pattern)) return adapter;
            } catch (RuntimeException | LinkageError failure) {
                String name = adapter instanceof RegisteredAdapter registered ? registered.registration()
                    .id()
                    .toString()
                    : adapter.getClass()
                        .getSimpleName();
                return new FailedAdapter(
                    "PATTERN_SUPPORTS_FAILED:" + name
                        + ":"
                        + failure.getClass()
                            .getSimpleName());
            }
        }
        return null;
    }

    public static List<PatternSemanticAdapter> copy(List<PatternSemanticAdapter> adapters) {
        List<PatternSemanticAdapter> result = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(adapters);
        if (result.isEmpty()) throw new IllegalArgumentException("At least one pattern semantic adapter is required");
        return result;
    }

    public static ECOPatternSemantics toPublic(PatternSemantics semantics) {
        return new ECOPatternSemantics(
            semantics.physicalPattern(),
            semantics.physicalDefinition() instanceof AEItemKey definition ? definition : null,
            semantics.consumedInputs()
                .stream()
                .map(
                    input -> new ECOPatternSemantics.Input(
                        input.source(),
                        input.key(),
                        input.amountPerPattern()
                            .toBigInteger(),
                        input.returnedKey(),
                        input.returnedAmountPerPattern()
                            .toBigInteger()))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            semantics.producedOutputs(),
            semantics.returnedOutputs(),
            semantics.feedbackEdges()
                .stream()
                .map(
                    edge -> new ECOPatternSemantics.FeedbackEdge(
                        edge.returnedKey(),
                        edge.dependentOutput(),
                        edge.amountPerFiring()
                            .toBigInteger()))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            ECOPatternSemantics.MatchingMode.valueOf(
                semantics.matchingMode()
                    .name()),
            ECOPatternSemantics.ExecutionRestriction.valueOf(
                semantics.executionRestriction()
                    .name()),
            semantics.exactStaticAnalysis(),
            semantics.cycleSafe(),
            semantics.unsupportedReason());
    }

    public static PatternSemantics toInternal(ECOPatternSemantics semantics) {
        return new PatternSemantics(
            semantics.physicalPattern(),
            semantics.physicalDefinition(),
            semantics.consumedInputs()
                .stream()
                .map(
                    input -> new PatternSemantics.Input(
                        input.source(),
                        input.key(),
                        PlannerAmount.of(input.amountPerPattern()),
                        input.returnedKey(),
                        PlannerAmount.of(input.returnedAmountPerPattern())))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            semantics.producedOutputs(),
            semantics.returnedOutputs(),
            semantics.feedbackEdges()
                .stream()
                .map(
                    edge -> new PatternSemantics.FeedbackEdge(
                        edge.returnedKey(),
                        edge.dependentOutput(),
                        PlannerAmount.of(edge.amountPerFiring())))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            PatternSemantics.MatchingMode.valueOf(
                semantics.matchingMode()
                    .name()),
            PatternSemantics.ExecutionRestriction.valueOf(
                semantics.executionRestriction()
                    .name()),
            semantics.exactStaticAnalysis(),
            semantics.cycleSafe(),
            semantics.unsupportedReason());
    }

    @com.github.bsideup.jabel.Desugar
    private record RegisteredAdapter(ECOPatternSemanticsRegistry.Registration registration)
        implements PatternSemanticAdapter {

        @Override
        public boolean supports(IPatternDetails pattern) {
            return registration.adapter()
                .supports(pattern);
        }

        @Override
        public PatternSemantics analyze(IPatternDetails pattern) {
            try {
                if (!registration.adapter()
                    .supports(pattern)) return failed(pattern, "SUPPORTS_CHANGED");
                ECOPatternSemantics base = toPublic(new AE2PatternSemanticAdapter().analyze(pattern));
                ECOPatternSemantics result = registration.adapter()
                    .analyze(pattern, base);
                if (result == null) return failed(pattern, "NULL_RESULT");
                if (result.physicalPattern() != pattern) return failed(pattern, "MISMATCHED_PHYSICAL_PATTERN");
                return toInternal(result);
            } catch (RuntimeException | LinkageError failure) {
                return failed(
                    pattern,
                    failure.getClass()
                        .getSimpleName());
            }
        }

        private PatternSemantics failed(IPatternDetails pattern, String reason) {
            return PatternSemantics
                .unsupported(pattern, null, "PATTERN_ADAPTER_FAILED:" + registration.id() + ":" + reason);
        }

        @Override
        public boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
            return registration.adapter()
                .ignoresComponents(pattern, inputSlot);
        }

        @Override
        public AEKey preferredInputKey(IPatternDetails pattern, int inputSlot, AEKey encodedKey,
            ICraftingService craftingService) {
            return registration.adapter()
                .preferredInputKey(pattern, inputSlot, encodedKey, craftingService);
        }

        @Override
        public String name() {
            return registration.id()
                .toString();
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record FailedAdapter(String reason) implements PatternSemanticAdapter {

        @Override
        public boolean supports(IPatternDetails pattern) {
            return true;
        }

        @Override
        public PatternSemantics analyze(IPatternDetails pattern) {
            return PatternSemantics.unsupported(pattern, null, reason);
        }
    }
}
