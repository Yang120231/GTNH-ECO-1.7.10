package cn.dancingsnow.neoecoae.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.multiblock.FormationPatternHelper.Pos;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;

class CraftingFormationPatternTest {

    @Test
    void parallelCoreMembersRetainControllerTier() {
        FormationDirections directions = new FormationDirections(
            ForgeDirection.NORTH,
            ForgeDirection.SOUTH,
            ForgeDirection.UP,
            ForgeDirection.DOWN,
            ForgeDirection.WEST,
            ForgeDirection.EAST,
            false);

        List<ECOFormationBlockPos> members = CraftingFormationPattern.INSTANCE.formedMembers(
            new Pos(0, 0, 0),
            new Pos(2, 1, 0),
            new Pos(2, -1, 0),
            new Pos(0, 1, 1),
            new Pos(2, 1, 1),
            new Pos(0, -1, 1),
            new Pos(2, -1, 1),
            new Pos(0, 0, 1),
            new Pos(2, 0, 1),
            Collections.<Pos>emptyList(),
            ECOControllerTier.L9,
            directions);

        assertEquals(ECOControllerTier.L9, tierAt(members, 1, 1, 0));
        assertEquals(ECOControllerTier.L9, tierAt(members, 1, -1, 0));
        assertNull(tierAt(members, 1, 0, 0));
    }

    private static ECOControllerTier tierAt(List<ECOFormationBlockPos> members, int x, int y, int z) {
        for (ECOFormationBlockPos member : members) {
            if (member.getX() == x && member.getY() == y && member.getZ() == z) {
                return member.getTier();
            }
        }
        throw new AssertionError("Missing formed member at " + x + ", " + y + ", " + z);
    }
}
