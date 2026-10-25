package cn.dancingsnow.neoecoae.crafting.planner.growth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class NetGrowthPatternValidationRegistryTest {

    @AfterEach
    void cleanup() {
        NetGrowthPatternValidationRegistry.clear();
    }

    @Test
    void standardProcessingPatternCanProveGrowthWithoutConcreteClassOrOptimizationRegistration() {
        AEKey resource = mock(AEKey.class);
        IPatternDetails pattern = pattern(resource, resource, 2L);
        assertTrue(NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(pattern));
        assertTrue(NetGrowthPatternValidationRegistry.isValidated(pattern));
        assertTrue(NetGrowthPatternValidationRegistry.isSelfGrowingPattern(pattern));
        NetGrowthPatternValidationRegistry.clear();
        assertFalse(NetGrowthPatternValidationRegistry.isValidated(pattern));
    }

    @Test
    void ambiguousSubstitutionDoesNotGainStaticGrowthProof() {
        AEKey resource = mock(AEKey.class);
        IPatternDetails pattern = pattern(resource, resource, 2L);
        when(pattern.getInputs()[0].getPossibleInputs())
            .thenReturn(new GenericStack[] { new GenericStack(resource, 1L), new GenericStack(mock(AEKey.class), 1L) });
        assertFalse(NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(pattern));
        assertFalse(NetGrowthPatternValidationRegistry.isSelfGrowingPattern(pattern));
    }

    @Test
    void unprovenChangedRemainderDoesNotGainCycleAuthorization() {
        AEKey resource = mock(AEKey.class);
        IPatternDetails pattern = pattern(resource, resource, 2L);
        when(pattern.getInputs()[0].getRemainingKey(resource)).thenReturn(mock(AEKey.class));
        assertFalse(NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(pattern));
    }

    @Test
    void balancedFeedbackIsNotSelfGrowthAndMalformedContractIsRejected() {
        AEKey resource = mock(AEKey.class);
        IPatternDetails balanced = pattern(resource, resource, 1L);
        assertFalse(NetGrowthPatternValidationRegistry.isSelfGrowingPattern(balanced));
        IPatternDetails malformed = pattern(resource, resource, 0L);
        assertFalse(NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(malformed));
    }

    @Test
    void analysisCrossingContractReloadCannotReauthorizeOldPattern() {
        AEKey resource = mock(AEKey.class);
        IPatternDetails pattern = pattern(resource, resource, 2L);
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        AtomicBoolean firstRead = new AtomicBoolean(true);
        when(pattern.getInputs()).thenAnswer(invocation -> {
            if (firstRead.getAndSet(false)) PatternRuntime.onRecipeReloadOrServerReload();
            return inputs;
        });
        assertFalse(NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(pattern));
        assertFalse(NetGrowthPatternValidationRegistry.isValidated(pattern));
        assertTrue(NetGrowthPatternValidationRegistry.validateAndRegisterFromPlanner(pattern));
        assertTrue(NetGrowthPatternValidationRegistry.isValidated(pattern));
        PatternRuntime.onRecipeReloadOrServerReload();
        assertFalse(NetGrowthPatternValidationRegistry.isValidated(pattern));
    }

    private static IPatternDetails pattern(AEKey input, AEKey output, long amount) {
        IPatternDetails.IInput slot = mock(IPatternDetails.IInput.class);
        when(slot.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(input, 1L) });
        when(slot.getMultiplier()).thenReturn(1L);
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { slot });
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(output, amount)));
        return pattern;
    }
}
