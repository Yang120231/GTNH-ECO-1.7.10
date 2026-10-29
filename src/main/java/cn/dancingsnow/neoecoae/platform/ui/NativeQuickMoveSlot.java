package cn.dancingsnow.neoecoae.platform.ui;

import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.widget.Interactable;

import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Routes player Shift clicks to physical drives represented by the host's display slots. */
public final class NativeQuickMoveSlot extends NativeItemSlot {

    private final Predicate<ItemStack> accepts;
    private final Runnable quickMove;
    private boolean quickMoving;

    public NativeQuickMoveSlot(Predicate<ItemStack> accepts, Runnable quickMove) {
        this.accepts = accepts;
        this.quickMove = quickMove;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Result onMousePressed(int mouseButton) {
        quickMoving = (mouseButton == 0 || mouseButton == 1) && Interactable.hasShiftDown()
            && ItemStacks.isEmpty(net.minecraft.client.Minecraft.getMinecraft().thePlayer.inventory.getItemStack())
            && accepts.test(getSlot().getStack());
        if (!quickMoving) return super.onMousePressed(mouseButton);
        quickMove.run();
        return Result.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean onMouseRelease(int mouseButton) {
        if (!quickMoving) return super.onMouseRelease(mouseButton);
        quickMoving = false;
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (!quickMoving) super.onMouseDrag(mouseButton, timeSinceClick);
    }
}
