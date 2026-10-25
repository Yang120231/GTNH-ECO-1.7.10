package cn.dancingsnow.neoecoae.crafting.planner.result;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

class ECOExecutionSchedulePatternContractTest {

    @Test
    void failedSpecialSemanticsCannotFallBackToRawPrimaryOutput() {
        var key = mock(AEKey.class);
        var pattern = mock(IPatternDetails.class);
        var output = new GenericStack(key, 1);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output));
        when(pattern.getPrimaryOutput()).thenReturn(output);
        var components = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            new ComponentPlanningResult(
                0,
                ComponentPlanningResult.Type.ACYCLIC,
                ComponentPlanningResult.Status.PLANNED,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(pattern),
                null,
                null,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                null,
                null));
        assertEquals(
            1,
            ECOExecutionSchedule
                .from(
                    components,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 1L))
                .phases()
                .size());
        var id = ResourceLocation.fromNamespaceAndPath("eco_schedule_test", "restricted");
        ECOPatternSemanticsRegistry.register(id, 1000, new ECOPatternSemanticAdapter() {

            @Override
            public boolean supports(IPatternDetails details) {
                return details == pattern;
            }

            @Override
            public ECOPatternSemantics analyze(IPatternDetails details, ECOPatternSemantics base) {
                return ECOPatternSemantics.unsupported(details, base.physicalDefinition(), "REJECTED_SPECIAL_CONTRACT");
            }
        });
        try {
            assertThrows(
                IllegalStateException.class,
                () -> ECOExecutionSchedule.from(
                    components,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 1L)));
        } finally {
            ECOPatternSemanticsRegistry.unregister(id);
        }
        assertEquals(
            1,
            ECOExecutionSchedule
                .from(
                    components,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 1L))
                .phases()
                .size());
    }
}
