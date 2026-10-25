package cn.dancingsnow.neoecoae.crafting.planner.compiled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class AE2ProcessingCycleSemanticsTest {

    @Test
    void ordinaryProcessingIsCycleSafeButAnUnprovenRemainderIsNot() {
        var raw = mock(AEKey.class);
        var output = mock(AEKey.class);
        var slot = mock(IPatternDetails.IInput.class);
        when(slot.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(raw, 1) });
        when(slot.getMultiplier()).thenReturn(1L);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { slot });
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(output, 1)));
        var adapter = new AE2PatternSemanticAdapter();
        assertTrue(
            adapter.analyze(pattern)
                .cycleSafeForStaticPlanning());
        when(slot.getRemainingKey(raw)).thenReturn(mock(AEKey.class));
        assertFalse(
            adapter.analyze(pattern)
                .cycleSafeForStaticPlanning(),
            "Changed/unknown returns must not gain a static cycle certificate");
    }
}
