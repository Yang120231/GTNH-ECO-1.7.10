package cn.dancingsnow.neoecoae.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

class MultiBlockUtilTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void shortCandidatesDoNotDuplicateControllerPositions() {
        assertEquals(
            2,
            MultiBlockUtil.allPossibleController(new BlockPos(0, 0, 0), new BlockPos(2, 2, 1))
                .size());
        assertEquals(
            2,
            MultiBlockUtil.allPossibleController(new BlockPos(0, 0, 0), new BlockPos(1, 2, 2))
                .size());
    }
}
