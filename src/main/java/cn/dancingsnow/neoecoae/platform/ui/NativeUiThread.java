package cn.dancingsnow.neoecoae.platform.ui;

public final class NativeUiThread {

    private NativeUiThread() {}

    public static boolean isClientSide() {
        return cpw.mods.fml.common.FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient();
    }
}
