package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

/**
 * Physical FX core. It contributes capacity through the formed structure and intentionally owns no
 * crafting state; the controller's virtual pool is the sole executor and persistence owner.
 */
public class TileCraftingWorker extends TileCraftingMember {

    public static final int BASE_QUEUE_CAPACITY = 32;
    private static final String TAG_STACK_AMOUNT = "EcoAmount";

    @Override
    public void updateEntity() {
        // Intentionally empty. All work is executed once by ECOCraftingVirtualPool on the controller.
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
    }

    @Override
    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {}

    static List<ItemStack> multiplyStack(ItemStack stack, int multiplier) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        if (stack == null || stack.stackSize <= 0 || multiplier <= 0) {
            return result;
        }
        long amount = (long) stack.stackSize * multiplier;
        while (amount > 0L) {
            ItemStack part = stack.copy();
            part.stackSize = (int) Math.min(amount, Integer.MAX_VALUE);
            result.add(part);
            amount -= part.stackSize;
        }
        return result;
    }

    static boolean sameStackType(ItemStack left, ItemStack right) {
        return left != null && right != null && left.isItemEqual(right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    static long countMatching(List<ItemStack> stacks, ItemStack prototype) {
        long count = 0L;
        for (ItemStack stack : stacks) {
            if (sameStackType(prototype, stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    static void removeMatching(List<ItemStack> stacks, ItemStack prototype, long amount) {
        for (Iterator<ItemStack> it = stacks.iterator(); it.hasNext() && amount > 0L;) {
            ItemStack stack = it.next();
            if (!sameStackType(prototype, stack)) {
                continue;
            }
            int removed = (int) Math.min(amount, stack.stackSize);
            stack.stackSize -= removed;
            amount -= removed;
            if (stack.stackSize <= 0) {
                it.remove();
            }
        }
    }

    static NBTTagList writeStacks(List<ItemStack> stacks) {
        NBTTagList list = new NBTTagList();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0) {
                continue;
            }
            NBTTagCompound stackTag = new NBTTagCompound();
            ItemStack prototype = stack.copy();
            prototype.stackSize = 1;
            prototype.writeToNBT(stackTag);
            stackTag.setInteger(TAG_STACK_AMOUNT, stack.stackSize);
            list.appendTag(stackTag);
        }
        return list;
    }

    static List<ItemStack> readStacks(NBTTagList list) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound stackTag = list.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(stackTag);
            if (stack != null) {
                stack.stackSize = persistedStackAmount(stackTag, stack.stackSize);
                result.add(stack);
            }
        }
        return result;
    }

    static int persistedStackAmount(NBTTagCompound stackTag, int decodedAmount) {
        return stackTag.hasKey(TAG_STACK_AMOUNT) ? Math.max(1, stackTag.getInteger(TAG_STACK_AMOUNT))
            : Math.max(1, decodedAmount);
    }

    public static final class WorkSnapshot {

        public final ItemStack output;
        public final int queueSize;
        public final int queueCapacity;
        public final int progress;
        public final int totalProgress;

        private WorkSnapshot(ItemStack output, int queueSize, int queueCapacity, int progress, int totalProgress) {
            this.output = output == null ? null : output.copy();
            this.queueSize = queueSize;
            this.queueCapacity = queueCapacity;
            this.progress = progress;
            this.totalProgress = totalProgress;
        }

        static WorkSnapshot create(ItemStack output, int queueSize, int queueCapacity, int progress,
            int totalProgress) {
            return new WorkSnapshot(output, queueSize, queueCapacity, progress, totalProgress);
        }
    }
}
