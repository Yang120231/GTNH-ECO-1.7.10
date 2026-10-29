package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

@FunctionalInterface
public interface AEKeyFilter {

    static AEKeyFilter none() {
        return what -> true;
    }

    boolean matches(AEKey what);
}
