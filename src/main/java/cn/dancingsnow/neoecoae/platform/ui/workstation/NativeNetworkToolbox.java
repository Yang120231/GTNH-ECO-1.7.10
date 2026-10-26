package cn.dancingsnow.neoecoae.platform.ui.workstation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import appeng.api.implementations.items.INetworkToolItem;
import appeng.items.contents.NetworkToolViewer;

/** Actual native AE2 toolbox inventory; the owning player slot remains locked while this menu is open. */
final class NativeNetworkToolbox {

    private final int playerSlot;
    private final NetworkToolViewer viewer;

    private NativeNetworkToolbox(int playerSlot, NetworkToolViewer viewer) {
        this.playerSlot = playerSlot;
        this.viewer = viewer;
    }

    int playerSlot() {
        return playerSlot;
    }

    static NativeNetworkToolbox find(EntityPlayer player) {
        for (int index = 0; index < player.inventory.mainInventory.length; index++) {
            ItemStack stack = player.inventory.getStackInSlot(index);
            if (stack != null && stack.getItem() instanceof INetworkToolItem) {
                if (!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
                int size = ((INetworkToolItem) stack.getItem()).getInventorySize();
                return new NativeNetworkToolbox(index, new NetworkToolViewer(stack, null, size));
            }
        }
        return null;
    }

    void attach(ModularPanel panel) {
        InvWrapper inventory = new InvWrapper(viewer) {

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                super.setStackInSlot(slot, stack);
                viewer.markDirty();
            }
        };
        for (int index = 0; index < viewer.getSizeInventory(); index++) panel.child(
            new cn.dancingsnow.neoecoae.platform.ui.NativeUpgradeSlot().slot(new ModularSlot(inventory, index))
                .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
                .hoverBackground(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
                .pos(174 + index % 3 * 18, 101 + index / 3 * 18));
    }
}
