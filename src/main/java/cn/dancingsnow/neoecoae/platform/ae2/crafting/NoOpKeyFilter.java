package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

class NoOpKeyFilter implements AEKeyFilter {

    static NoOpKeyFilter INSTANCE = new NoOpKeyFilter();

    @Override
    public boolean matches(AEKey what) {
        return true;
    }

}
