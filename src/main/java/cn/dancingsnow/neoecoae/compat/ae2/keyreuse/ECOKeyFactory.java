package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** The legacy facade already owns its factories; native AE2 IAEStack construction is unaffected. */
public final class ECOKeyFactory {

    private ECOKeyFactory() {}

    public static AEKey canonicalize(AEKey key) {
        if (key instanceof AEItemKey item) return item.reusePlain();
        if (key instanceof AEFluidKey fluid) return fluid.reusePlain();
        return key;
    }
}
