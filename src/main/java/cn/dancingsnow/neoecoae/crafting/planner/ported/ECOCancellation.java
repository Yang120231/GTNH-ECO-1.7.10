package cn.dancingsnow.neoecoae.crafting.planner.ported;

@FunctionalInterface
public interface ECOCancellation {

    ECOCancellation NONE = () -> {};

    void checkpoint() throws InterruptedException;
}
