package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState;

class NativeControllerPanelStateTest {

    @Test
    void mirroredCraftingCellsKeepTheirPhysicalUpperAndLowerCoreTiersAcrossNetworkSerialization() {
        var state = CraftingControllerPanelState
            .of(3, Arrays.asList(1, 2), Arrays.asList(3, 1), 2, true, true, true, 345, 876);
        var restored = CraftingControllerPanelState.fromTag(state.toTag());
        assertEquals(state, restored);
        assertTrue(restored.hasFx(10));
        assertTrue(restored.hasFx(9));
        assertFalse(restored.hasFx(0));
        assertEquals(1, restored.upperTier(10));
        assertEquals(3, restored.lowerTier(10));
    }

    @Test
    void ratioCalculationCannotOverflowAtLongMax() {
        assertEquals(1000, CraftingControllerPanelState.permille(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(500, CraftingControllerPanelState.permille(Long.MAX_VALUE / 2, Long.MAX_VALUE));
        assertEquals(0, CraftingControllerPanelState.permille(1, 0));
    }

    @Test
    void explicitTierZeroComputationMemberRemainsPresentWhenMirroredAndSerialized() {
        var present = new ComputationControllerPanelState.Member(true, 0, null);
        var missing = ComputationControllerPanelState.Member.ABSENT;
        var state = ComputationControllerPanelState
            .ofMembers(2, Arrays.asList(missing), Arrays.asList(present), Arrays.asList(missing), true);
        var restored = ComputationControllerPanelState.fromTag(state.toTag());
        assertEquals(state, restored);
        assertTrue(restored.hasCell(10));
        assertTrue(
            restored.coreMember(10)
                .present());
        assertEquals(
            0,
            restored.coreMember(10)
                .tier());
        assertFalse(
            restored.upperMember(10)
                .present());
    }
}
