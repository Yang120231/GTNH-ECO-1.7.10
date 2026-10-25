package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.List;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** Immutable planning view of a native pattern. NativePatternDetails captures it on the server thread. */
public interface IPatternDetails {

    AEItemKey getDefinition();

    IInput[] getInputs();

    List<GenericStack> getOutputs();

    default GenericStack getPrimaryOutput() {
        return getOutputs().get(0);
    }

    default Class<?> implementationClass() {
        return getClass();
    }

    default boolean supportsPushInputsToExternalInventory() {
        return true;
    }

    default void pushInputsToExternalInventory(cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter[] inputHolder,
        PatternInputSink inputSink) {
        for (var counter : inputHolder)
            for (var input : counter) inputSink.pushInput(input.getKey(), input.getLongValue());
    }

    interface IInput {

        GenericStack[] getPossibleInputs();

        long getMultiplier();

        AEKey getRemainingKey(AEKey input);

        default boolean isValid(AEKey key, net.minecraft.world.World world) {
            for (GenericStack input : getPossibleInputs()) if (input.what()
                .equals(key)) return true;
            return false;
        }
    }

    @FunctionalInterface
    interface PatternInputSink {

        void pushInput(AEKey key, long amount);
    }
}
