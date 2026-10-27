package cn.dancingsnow.neoecoae.platform.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourceLocationCompatibilityTest {

    @Test
    void preservesLegacyUppercaseItemRegistryPaths() {
        ResourceLocation id = ResourceLocation.parse("appliedenergistics2:ItemBlankPattern");

        assertEquals("appliedenergistics2", id.getNamespace());
        assertEquals("ItemBlankPattern", id.getPath());
        assertEquals("appliedenergistics2:ItemBlankPattern", id.toString());
    }
}
