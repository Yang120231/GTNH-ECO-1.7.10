package cn.dancingsnow.neoecoae.crafting.fastpath;

import java.util.Arrays;

import appeng.api.storage.data.IAEItemStack;

public final class ECOFastPathPatternProfile {

    private final ECOFastPathPatternKey key;
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final boolean craftable;
    private final boolean substitutionAllowed;

    ECOFastPathPatternProfile(ECOFastPathPatternKey key, IAEItemStack[] inputs, IAEItemStack[] outputs,
        boolean craftable, boolean substitutionAllowed) {
        this.key = key;
        this.inputs = copy(inputs);
        this.outputs = copy(outputs);
        this.craftable = craftable;
        this.substitutionAllowed = substitutionAllowed;
    }

    public ECOFastPathPatternKey getKey() {
        return this.key;
    }

    public IAEItemStack[] getInputs() {
        return copy(this.inputs);
    }

    public IAEItemStack[] getOutputs() {
        return copy(this.outputs);
    }

    public boolean isCraftable() {
        return this.craftable;
    }

    public boolean isSubstitutionAllowed() {
        return this.substitutionAllowed;
    }

    public int inputCount() {
        return this.inputs.length;
    }

    public int outputCount() {
        return this.outputs.length;
    }

    @Override
    public String toString() {
        return "ECOFastPathPatternProfile{inputs=" + this.inputs.length
            + ", outputs="
            + this.outputs.length
            + ", craftable="
            + this.craftable
            + ", substitutionAllowed="
            + this.substitutionAllowed
            + '}';
    }

    private static IAEItemStack[] copy(IAEItemStack[] stacks) {
        if (stacks == null || stacks.length == 0) {
            return new IAEItemStack[0];
        }
        return Arrays.copyOf(stacks, stacks.length);
    }
}
