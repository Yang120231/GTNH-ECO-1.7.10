package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.Objects;

import appeng.api.storage.data.IAEStack;

/** Immutable identity for every registered GTNH AE stack type, including native fluids. */
public final class ECOResourceKey {

    private final IAEStack<?> template;

    public ECOResourceKey(IAEStack<?> stack) {
        template = Objects.requireNonNull(stack, "stack")
            .copy();
        template.reset();
        template.setStackSize(1);
    }

    public IAEStack<?> stack(long amount) {
        return template.copy()
            .setStackSize(amount);
    }

    public String displayName() {
        if (template instanceof appeng.api.storage.data.IAEItemStack) {
            return ((appeng.api.storage.data.IAEItemStack) template).getItemStack()
                .getDisplayName();
        }
        if (template instanceof appeng.api.storage.data.IAEFluidStack) {
            return ((appeng.api.storage.data.IAEFluidStack) template).getFluidStack()
                .getLocalizedName();
        }
        return template.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ECOResourceKey)) return false;
        ECOResourceKey key = (ECOResourceKey) other;
        return template.getStackType()
            .equals(key.template.getStackType()) && template.equals(key.template);
    }

    @Override
    public int hashCode() {
        return 31 * template.getStackType()
            .hashCode() + template.hashCode();
    }

    @Override
    public String toString() {
        return template.toString();
    }
}
