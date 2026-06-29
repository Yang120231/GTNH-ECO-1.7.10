package cn.dancingsnow.neoecoae.crafting.runtime;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import appeng.api.storage.data.IAEStack;

public final class ECOCraftingStackKey {

    private static final String TAG_STACK = "Stack";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_DISPLAY_NAME = "DisplayName";

    private final NBTTagCompound stackTag;
    private final long amount;
    private final String displayName;

    private ECOCraftingStackKey(NBTTagCompound stackTag, long amount, String displayName) {
        this.stackTag = stackTag == null ? new NBTTagCompound() : (NBTTagCompound) stackTag.copy();
        this.amount = Math.max(0L, amount);
        this.displayName = displayName == null ? "" : displayName;
    }

    public static ECOCraftingStackKey of(IAEStack<?> stack) {
        if (stack == null) {
            return empty();
        }
        NBTTagCompound tag = new NBTTagCompound();
        try {
            stack.writeToNBTGeneric(tag);
        } catch (RuntimeException e) {
            tag = new NBTTagCompound();
        }
        return new ECOCraftingStackKey(tag, Math.max(0L, stack.getStackSize()), safeDisplayName(stack));
    }

    public static ECOCraftingStackKey empty() {
        return new ECOCraftingStackKey(new NBTTagCompound(), 0L, "");
    }

    public static ECOCraftingStackKey readFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return empty();
        }
        NBTTagCompound stackTag = tag.hasKey(TAG_STACK, Constants.NBT.TAG_COMPOUND) ? tag.getCompoundTag(TAG_STACK)
            : new NBTTagCompound();
        return new ECOCraftingStackKey(stackTag, tag.getLong(TAG_AMOUNT), tag.getString(TAG_DISPLAY_NAME));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(TAG_STACK, this.stackTag.copy());
        tag.setLong(TAG_AMOUNT, this.amount);
        if (this.displayName.length() > 0) {
            tag.setString(TAG_DISPLAY_NAME, this.displayName);
        }
        return tag;
    }

    public NBTTagCompound getStackTag() {
        return (NBTTagCompound) this.stackTag.copy();
    }

    public long getAmount() {
        return this.amount;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public boolean isEmpty() {
        return this.stackTag.hasNoTags();
    }

    private static String safeDisplayName(IAEStack<?> stack) {
        try {
            String name = stack.getDisplayName();
            return name == null ? "" : name;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
