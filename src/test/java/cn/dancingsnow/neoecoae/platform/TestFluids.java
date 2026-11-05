package cn.dancingsnow.neoecoae.platform;

import static org.mockito.Mockito.*;

public final class TestFluids {

    public static final net.minecraftforge.fluids.Fluid WATER = unregistered("water"), LAVA = unregistered("lava");

    /** Standalone fixtures have no Forge registry; key reuse must take the unregistered-fluid fallback. */
    private static net.minecraftforge.fluids.Fluid unregistered(String name) {
        var fluid = spy(new net.minecraftforge.fluids.Fluid(name));
        doReturn(-1).when(fluid)
            .getID();
        return fluid;
    }

    private TestFluids() {}
}
