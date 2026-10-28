package cn.dancingsnow.neoecoae.platform.ae2.key;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AESharedNBT;

/** Network ingress reuses rv3's interned tag instead of copying; identity must match the ItemStack path exactly. */
class AEItemKeyNativeTest {

    private static final Item ITEM = new Item();

    @Test
    void sharedTagKeysEqualPlainStackKeysAndNeverAliasTheTag() {
        AESharedNBT shared = new AESharedNBT(0);
        shared.setString("fluid", "water");
        shared.setInteger("amount", 8);
        AEKey fromNetwork = AEKey.fromNative(nativeStack(3, shared));

        ItemStack plain = new ItemStack(ITEM, 64, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("amount", 8);
        tag.setString("fluid", "water");
        plain.setTagCompound(tag);
        AEKey fromStack = AEItemKey.of(plain);

        assertEquals(fromStack, fromNetwork);
        assertEquals(fromNetwork, fromStack);
        assertEquals(fromStack.hashCode(), fromNetwork.hashCode());
        assertEquals(fromNetwork, AEKey.fromNative(nativeStack(3, shared)));
        assertNotEquals(fromNetwork, AEKey.fromNative(nativeStack(4, shared)));

        ItemStack copy = ((AEItemKey) fromNetwork).toStack();
        assertNotSame(shared, copy.getTagCompound());
        copy.getTagCompound()
            .setInteger("amount", 9);
        assertEquals(8, shared.getInteger("amount"));
        assertEquals(fromStack, fromNetwork);
    }

    @Test
    void untaggedNativeStacksMatchUntaggedItemStacks() {
        AEKey fromNetwork = AEKey.fromNative(nativeStack(0, null));
        AEKey fromStack = AEItemKey.of(new ItemStack(ITEM, 1, 0));
        assertEquals(fromStack, fromNetwork);
        assertEquals(fromStack.hashCode(), fromNetwork.hashCode());
    }

    private static IAEItemStack nativeStack(int damage, AESharedNBT tag) {
        IAEItemStack stack = mock(IAEItemStack.class);
        when(stack.getItem()).thenReturn(ITEM);
        when(stack.getItemDamage()).thenReturn(damage);
        when(stack.getTagCompound()).thenReturn(tag);
        return stack;
    }
}
