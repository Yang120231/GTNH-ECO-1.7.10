package cn.dancingsnow.neoecoae.crafting.fastpath;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

public final class ECOFastPathPatternInspector {

    public ECOFastPathPatternProfile inspect(ICraftingPatternDetails patternDetails)
        throws ECOFastPathPatternException {
        if (patternDetails == null) {
            throw new ECOFastPathPatternException("missing pattern");
        }
        ECOFastPathPatternKey key = ECOFastPathPatternKey.of(patternDetails);
        if (key == null) {
            throw new ECOFastPathPatternException("missing encoded pattern key");
        }
        boolean craftable = safeCraftable(patternDetails);
        boolean substitutionAllowed = safeCanSubstitute(patternDetails);
        if (!craftable) {
            throw new ECOFastPathPatternException("processing pattern");
        }
        if (substitutionAllowed) {
            throw new ECOFastPathPatternException("substitution pattern");
        }
        IAEItemStack[] inputs = compact(patternDetails.getCondensedInputs());
        IAEItemStack[] outputs = compact(patternDetails.getCondensedOutputs());
        if (inputs.length == 0) {
            throw new ECOFastPathPatternException("missing inputs");
        }
        if (outputs.length != 1) {
            throw new ECOFastPathPatternException("non-single output");
        }
        if (inputs.length > ECOFastPathConfig.MAX_PATTERN_INPUTS) {
            throw new ECOFastPathPatternException("too many inputs");
        }
        if (outputs.length > ECOFastPathConfig.MAX_PATTERN_OUTPUTS) {
            throw new ECOFastPathPatternException("too many outputs");
        }
        requireMeaningful(inputs, "input");
        requireMeaningful(outputs, "output");
        requireFastPathSafe(inputs, true, "input");
        requireFastPathSafe(outputs, false, "output");
        return new ECOFastPathPatternProfile(key, inputs, outputs, craftable, substitutionAllowed);
    }

    private static boolean safeCraftable(ICraftingPatternDetails patternDetails) throws ECOFastPathPatternException {
        try {
            return patternDetails.isCraftable();
        } catch (RuntimeException e) {
            throw new ECOFastPathPatternException("craftable check failed");
        }
    }

    private static boolean safeCanSubstitute(ICraftingPatternDetails patternDetails)
        throws ECOFastPathPatternException {
        try {
            return patternDetails.canSubstitute();
        } catch (RuntimeException e) {
            throw new ECOFastPathPatternException("substitution check failed");
        }
    }

    private static IAEItemStack[] compact(IAEItemStack[] stacks) throws ECOFastPathPatternException {
        if (stacks == null || stacks.length == 0) {
            return new IAEItemStack[0];
        }
        int count = 0;
        for (IAEItemStack stack : stacks) {
            if (stack != null) {
                count++;
            }
        }
        IAEItemStack[] compacted = new IAEItemStack[count];
        int index = 0;
        for (IAEItemStack stack : stacks) {
            if (stack != null) {
                compacted[index++] = stack.copy();
            }
        }
        return compacted;
    }

    private static void requireMeaningful(IAEItemStack[] stacks, String label) throws ECOFastPathPatternException {
        for (IAEItemStack stack : stacks) {
            if (stack.getStackSize() <= 0L || !stack.isMeaningful()) {
                throw new ECOFastPathPatternException("invalid " + label);
            }
        }
    }

    private static void requireFastPathSafe(IAEItemStack[] stacks, boolean input, String label)
        throws ECOFastPathPatternException {
        for (IAEItemStack stack : stacks) {
            net.minecraft.item.ItemStack item = stack.getItemStack();
            if (!isFastPathSafe(item, input)) {
                throw new ECOFastPathPatternException("unsafe " + label);
            }
        }
    }

    static boolean isFastPathSafe(net.minecraft.item.ItemStack item, boolean input) {
        return item != null && item.getItem() != null && !item.hasTagCompound() && !item.isItemStackDamageable()
            && (!input || !item.getItem()
                .hasContainerItem(item));
    }
}
