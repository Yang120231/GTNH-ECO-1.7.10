package cn.dancingsnow.neoecoae.crafting.runtime;

public interface ECOBatchCraftingFallback {

    boolean shouldFallback();

    String getFallbackReason();

    void fallback(String reason);
}
