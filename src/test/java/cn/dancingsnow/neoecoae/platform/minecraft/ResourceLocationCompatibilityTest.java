package cn.dancingsnow.neoecoae.platform.minecraft;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceLocationCompatibilityTest {

    @Test
    void preservesLegacyUppercaseItemRegistryPaths() {
        ResourceLocation id = ResourceLocation.parse("appliedenergistics2:ItemBlankPattern");

        assertEquals("appliedenergistics2", id.getNamespace());
        assertEquals("ItemBlankPattern", id.getPath());
        assertEquals("appliedenergistics2:ItemBlankPattern", id.toString());
    }

    @ParameterizedTest
    @ValueSource(
        strings = { "LegacyMod:Item With Spaces", "legacy:item(variant)", "legacy:item+variant", "legacy:item:variant",
            "legacy:材料", "legacy:item@variant" })
    void preservesLegacyRegistryCharactersWithoutRewritingTheIdentity(String value) {
        ResourceLocation id = ResourceLocation.parse(value);

        assertEquals(value, id.toString());
        assertEquals(id, ResourceLocation.tryParse(value));
        assertEquals(id, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath()));
        assertEquals(new net.minecraft.util.ResourceLocation(id.getNamespace(), id.getPath()), id);
    }

    @Test
    void retainsTheDefaultNamespace() {
        assertEquals(
            "minecraft:stone",
            ResourceLocation.parse("stone")
                .toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", ":stone", "minecraft:" })
    void rejectsMissingIdentityComponents(String value) {
        assertThrows(IllegalArgumentException.class, () -> ResourceLocation.parse(value));
        assertNull(ResourceLocation.tryParse(value));
    }

    @Test
    void rejectsNullAndAmbiguousNamespace() {
        assertThrows(IllegalArgumentException.class, () -> ResourceLocation.parse(null));
        assertNull(ResourceLocation.tryParse(null));
        assertThrows(IllegalArgumentException.class, () -> ResourceLocation.fromNamespaceAndPath("a:b", "c"));
    }
}
