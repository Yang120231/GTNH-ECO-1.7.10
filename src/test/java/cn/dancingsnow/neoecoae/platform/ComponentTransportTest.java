package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

class ComponentTransportTest {

    @Test
    void arbitraryTierColorsAndNestedTranslationArgumentsSurviveTransport() {
        Component name = Component.literal("Prototype")
            .withColor(0x12ab34);
        Component line = Component.translatable("test.tier", name)
            .withColor(0x456789)
            .append(
                Component.literal(" + ")
                    .withColor(0xabcdef))
            .append(
                Component.translatable(
                    "test.suffix",
                    Component.literal("RGB")
                        .withColor(0x123456)));
        Component received = Component.fromJson(line.toJson());
        assertEquals(line, received);
        assertEquals(
            0x456789,
            received.getStyle()
                .getColor()
                .getValue());
        assertEquals(line.toJson(), received.toJson());
        assertEquals(name, Component.fromJson(name.toJson()));
    }

    @Test
    void legacyChatJsonStillLoadsAlongsideTheRgbEnvelope() {
        assertEquals(
            "legacy",
            Component.fromJson("{\"text\":\"legacy\"}")
                .getString());
    }

    @Test
    void sourceResourceIdsAreUsedWithoutBreakingPreviousPortSnapshots() {
        var item = AEItemKey.of(TestItems.IRON_INGOT);
        var itemTag = item.toTagGeneric(TestItemRegistry.CONTEXT);
        assertEquals("ae2:i", itemTag.getString(AEKey.TYPE_FIELD));
        itemTag.putString(AEKey.TYPE_FIELD, "ae2:item");
        assertEquals(item, AEKey.fromTagGeneric(TestItemRegistry.CONTEXT, itemTag));
        assertSame(AEKeyType.items(), item.getType());
        var fluid = AEFluidKey.of(TestFluids.WATER);
        var fluidTag = fluid.toTagGeneric(TestItemRegistry.CONTEXT);
        assertEquals("ae2:f", fluidTag.getString(AEKey.TYPE_FIELD));
        fluidTag.putString(AEKey.TYPE_FIELD, "ae2:fluid");
        assertEquals(fluid, AEKey.fromTagGeneric(TestItemRegistry.CONTEXT, fluidTag));
    }
}
