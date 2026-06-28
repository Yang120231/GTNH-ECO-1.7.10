package cn.dancingsnow.neoecoae.client.render.model;

import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cn.dancingsnow.neoecoae.NeoECOAE;

public final class ModernIconRegistrar {

    private ModernIconRegistrar() {}

    public static void registerIcons(IIconRegister register, String[] textureNames, Map<String, IIcon> modelIcons) {
        modelIcons.clear();
        for (String texture : textureNames) {
            modelIcons.put(texture, register.registerIcon(toLegacyIconName(texture)));
        }
    }

    public static String toLegacyIconName(String modernTexture) {
        String prefix = NeoECOAE.MODID + ":block/";
        if (modernTexture.startsWith(prefix)) {
            return NeoECOAE.MODID + ":" + modernTexture.substring(prefix.length());
        }
        return modernTexture.replace(":block/", ":");
    }
}
