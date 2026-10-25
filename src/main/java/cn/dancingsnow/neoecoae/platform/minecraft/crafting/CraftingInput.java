package cn.dancingsnow.neoecoae.platform.minecraft.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

/** Modern recipe coordinates backed by real legacy stacks and a native crafting inventory. */
public final class CraftingInput {

    private final int width, height;
    private final List<ItemStack> items;

    private CraftingInput(int width, int height, List<ItemStack> items) {
        if (width < 0 || height < 0 || items.size() != width * height)
            throw new IllegalArgumentException("Crafting grid dimensions");
        this.width = width;
        this.height = height;
        this.items = new ArrayList<>(items);
    }

    public static CraftingInput of(int width, int height, List<ItemStack> items) {
        return ofPositioned(width, height, items).input();
    }

    public static Positioned ofPositioned(int width, int height, List<ItemStack> items) {
        if (items.size() != width * height) throw new IllegalArgumentException("Crafting grid dimensions");
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) if (!ItemStacks.isEmpty(items.get(x + y * width))) {
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        if (right < 0) return new Positioned(new CraftingInput(0, 0, java.util.Collections.emptyList()), 0, 0);
        var cropped = new ArrayList<ItemStack>();
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) cropped.add(items.get(x + y * width));
        return new Positioned(new CraftingInput(right - left + 1, bottom - top + 1, cropped), left, top);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    public List<ItemStack> items() {
        return java.util.Collections.unmodifiableList(items);
    }

    public net.minecraft.inventory.InventoryCrafting nativeInventory() {
        var inventory = new TransientCraftingContainer(Math.max(1, width), Math.max(1, height));
        for (int i = 0; i < items.size(); i++) inventory.setItem(i, ItemStacks.copy(items.get(i)));
        return inventory;
    }

    @com.github.bsideup.jabel.Desugar
    public record Positioned(CraftingInput input, int left, int top) {}
}
