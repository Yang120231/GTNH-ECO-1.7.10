package cn.dancingsnow.neoecoae.platform.network;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockConstruction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class PatternUploadPacketsTest {

    @BeforeAll
    static void initializeCodecsWithoutStartingFmlTransport() throws ClassNotFoundException {
        // Exercise the real codecs and buffers. Only the unused transport requires a running FML class loader.
        try (var transport = mockConstruction(SimpleNetworkWrapper.class)) {
            Class.forName(PatternUploadPackets.class.getName());
        }
    }

    @Test
    void recipeCategoryIdentitySurvivesTheHintWhileLegacyHintsHaveNoCustomMappingIdentity() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new PatternUploadPackets.Hint(7, "", null, "压榨机", "nei:press").toBytes(buffer);
            var decoded = new PatternUploadPackets.Hint();
            decoded.fromBytes(buffer);
            assertEquals("nei:press", decoded.categoryId);
            assertEquals("压榨机", decoded.category);
            assertFalse(buffer.isReadable());

            buffer.clear();
            buffer.writeInt(7);
            ByteBufUtils.writeUTF8String(buffer, "gt.recipe.press");
            ByteBufUtils.writeItemStack(buffer, null);
            ByteBufUtils.writeUTF8String(buffer, "Press");
            decoded = new PatternUploadPackets.Hint();
            decoded.fromBytes(buffer);
            assertEquals("gt.recipe.press", decoded.recipeMapId);
            assertEquals("", decoded.categoryId);
            assertFalse(buffer.isReadable());

            buffer.clear();
            new PatternUploadPackets.Hint(
                7,
                "",
                null,
                "Press",
                String.join("", java.util.Collections.nCopies(513, "x"))).toBytes(buffer);
            decoded = new PatternUploadPackets.Hint();
            decoded.fromBytes(buffer);
            assertEquals("", decoded.categoryId, "An overlong identity must not be truncated into a valid one");
        } finally {
            buffer.release();
        }
    }

    @Test
    void mappingStateSurvivesTheCopiedMultipartHeaderAndAutoTarget() {
        var list = new PatternUploadPackets.TargetList();
        list.categoryId = "nei:press";
        list.canMap = list.hasMapping = list.mappingEnabled = list.autoMapped = true;
        list.mappingName = "Press";
        list.mappingNameSuffix = " [1]";
        list.mappingToken = list.autoToken = 42;
        var header = list.header();
        header.windowId = 7;
        header.parts = 2;
        ByteBuf buffer = Unpooled.buffer();
        try {
            header.toBytes(buffer);
            var decoded = new PatternUploadPackets.TargetList();
            decoded.fromBytes(buffer);
            assertEquals("nei:press", decoded.categoryId);
            assertEquals("Press", decoded.mappingName);
            assertEquals(" [1]", decoded.mappingNameSuffix);
            assertTrue(decoded.canMap && decoded.hasMapping && decoded.mappingEnabled && decoded.autoMapped);
            assertEquals(42, decoded.mappingToken);
            assertEquals(decoded.mappingToken, decoded.autoToken);
            assertEquals(2, decoded.parts);
            assertFalse(buffer.isReadable());

            buffer.clear();
            var part = new PatternUploadPackets.TargetList();
            part.part = 1;
            part.parts = 2;
            part.toBytes(buffer);
            decoded = new PatternUploadPackets.TargetList();
            decoded.fromBytes(buffer);
            assertFalse(decoded.hasMapping);
            assertFalse(buffer.isReadable());

            buffer.clear();
            var tooltip = new PatternUploadPackets.AutoTarget(7, "Press", 1, null, false, "");
            String verifiedKey = tooltip.key();
            tooltip.mapped = true;
            assertNotEquals(verifiedKey, tooltip.key());
            tooltip.toBytes(buffer);
            var decodedTooltip = new PatternUploadPackets.AutoTarget();
            decodedTooltip.fromBytes(buffer);
            assertTrue(decodedTooltip.mapped);
            assertEquals("Press", decodedTooltip.name);
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
