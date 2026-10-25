package cn.dancingsnow.neoecoae.platform.registry;

import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;

import com.google.common.collect.Multimap;

/** Source tool damage is explicit; the legacy tool constructors use different per-tool constants. */
public final class NativeToolAttributes {

    private NativeToolAttributes() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Multimap damage(Multimap attributes, double sourceDamage) {
        String attribute = SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName();
        attributes.removeAll(attribute);
        attributes.put(
            attribute,
            new AttributeModifier(
                UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"),
                "Tool modifier",
                sourceDamage,
                0));
        return attributes;
    }
}
