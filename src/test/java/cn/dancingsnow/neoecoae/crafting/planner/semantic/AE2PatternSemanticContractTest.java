package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class AE2PatternSemanticContractTest {

    @Test
    void unregisteredFluidAndComponentInputsPreserveKeysAmountsAndAllOutputs() {
        AEFluidKey fluid = mock(AEFluidKey.class);
        AEItemKey componentItem = mock(AEItemKey.class);
        AEItemKey alternateComponents = mock(AEItemKey.class);
        AEItemKey container = mock(AEItemKey.class);
        IPatternDetails.IInput fluidInput = input(new GenericStack[] { new GenericStack(fluid, 250) }, 4);
        IPatternDetails.IInput itemInput = input(
            new GenericStack[] { new GenericStack(componentItem, 3), new GenericStack(alternateComponents, 3) },
            2);
        when(itemInput.getRemainingKey(componentItem)).thenReturn(container);
        GenericStack primary = new GenericStack(mock(AEKey.class), 5);
        GenericStack byproduct = new GenericStack(mock(AEFluidKey.class), 1000);
        IPatternDetails pattern = pattern(
            new IPatternDetails.IInput[] { fluidInput, itemInput },
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(primary, byproduct));

        PatternSemantics semantics = PatternSemanticAdapters.analyze(pattern);
        assertTrue(semantics.supported());
        assertSame(pattern, semantics.physicalPattern());
        assertSame(
            fluidInput,
            semantics.consumedInputs()
                .get(0)
                .source());
        assertSame(
            fluid,
            semantics.consumedInputs()
                .get(0)
                .key());
        assertEquals(
            BigInteger.valueOf(1000),
            semantics.consumedInputs()
                .get(0)
                .amountPerPattern()
                .toBigInteger());
        assertSame(
            componentItem,
            semantics.consumedInputs()
                .get(1)
                .key(),
            "The encoded primary component key remains the recursive demand");
        assertEquals(
            BigInteger.valueOf(6),
            semantics.consumedInputs()
                .get(1)
                .amountPerPattern()
                .toBigInteger());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(primary, byproduct),
            semantics.producedOutputs());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(container, 2)),
            semantics.returnedOutputs());
        assertSame(
            container,
            semantics.consumedInputs()
                .get(1)
                .returnedKey());
        assertEquals(
            BigInteger.valueOf(2),
            semantics.consumedInputs()
                .get(1)
                .returnedAmountPerPattern()
                .toBigInteger());
        assertEquals(PatternSemantics.MatchingMode.SUBSTITUTION, semantics.matchingMode());
        verify(itemInput, never()).getRemainingKey(alternateComponents);
    }

    @Test
    void multiplierOverflowRetainsExactInputAmount() {
        AEKey key = mock(AEKey.class);
        var slot = input(new GenericStack[] { new GenericStack(key, Long.MAX_VALUE) }, 3);
        var pattern = pattern(
            new IPatternDetails.IInput[] { slot },
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1)));
        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(3)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(
                PatternSemanticAdapters.analyze(pattern)
                    .consumedInputs())
                .amountPerPattern()
                .toBigInteger());
    }

    @Test
    void malformedOrdinaryPatternsFailClosed() {
        AEKey key = mock(AEKey.class);
        AE2PatternSemanticAdapter adapter = new AE2PatternSemanticAdapter();
        for (List<GenericStack> outputs : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.<GenericStack>listOf(),
            Arrays.asList((GenericStack) null),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 0)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, -1)))) {
            assertFalse(
                adapter.analyze(pattern(new IPatternDetails.IInput[0], outputs))
                    .supported());
        }
        assertFalse(
            adapter
                .analyze(pattern(null, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1))))
                .supported());
        assertFalse(
            adapter
                .analyze(
                    pattern(
                        new IPatternDetails.IInput[] { input(new GenericStack[] { new GenericStack(key, -1) }, 1) },
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1))))
                .supported());
        assertFalse(
            adapter
                .analyze(
                    pattern(
                        new IPatternDetails.IInput[] { input(new GenericStack[] { new GenericStack(key, 1) }, 0) },
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1))))
                .supported());
    }

    private static IPatternDetails.IInput input(GenericStack[] choices, long multiplier) {
        IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(choices);
        when(input.getMultiplier()).thenReturn(multiplier);
        return input;
    }

    private static IPatternDetails pattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs) {
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(outputs);
        return pattern;
    }
}
