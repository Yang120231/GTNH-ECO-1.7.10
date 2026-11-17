package cn.dancingsnow.neoecoae.compat.nei;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.glodblock.github.common.item.ItemFluidPacket;

import appeng.api.config.TerminalFontSize;
import appeng.client.render.StackSizeRenderer;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.util.Platform;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;
import codechicken.nei.PositionedStack;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Read-only NEI snapshots of finite ECO cells, using the existing cell inventory and accounting. */
public final class ECOCellViewHandler extends TemplateRecipeHandler {

    static final int WIDTH = 166;
    static final int HEIGHT = 160;
    private static final int COLUMNS = 9;
    private static final int ITEMS_PER_PAGE = COLUMNS * 7;
    private static final int ITEMS_Y = 28;
    private static final String SLOT_TEXTURE = "nei:textures/slot.png";
    private static final DrawableResource SLOT = new DrawableBuilder(SLOT_TEXTURE, 0, 0, 18, 18).setTextureSize(18, 18)
        .build();

    @Override
    public TemplateRecipeHandler newInstance() {
        return new ECOCellViewHandler();
    }

    @Override
    public String getRecipeName() {
        return GuiText.CellView.getLocal();
    }

    @Override
    public String getGuiTexture() {
        return SLOT_TEXTURE;
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null || !(ingredient.getItem() instanceof NativeStorageCellItem item)
            || item.getBytes() <= 0
            || item.getBytes() == Long.MAX_VALUE
            || ECOInfiniteStorageMember.isSealed(ingredient)) return;

        // Keep the query independent of subsequent slot changes and never write to the real cell's NBT.
        var cell = item.createCellInventory(ingredient.copy(), null);
        List<GenericStack> contents = new ArrayList<>();
        cell.visitAvailableStacks((key, amount) -> { if (amount > 0) contents.add(new GenericStack(key, amount)); });
        contents.sort(
            Comparator.comparingLong(GenericStack::amount)
                .reversed());
        // An empty cell still has one capacity page; populated cells can exceed AE2's 63-type layout.
        for (int offset = 0; offset < Math.max(1, contents.size()); offset += ITEMS_PER_PAGE) {
            arecipes.add(new CellPage(cell, contents, offset));
        }
    }

    @Override
    public int getRecipeHeight(int recipe) {
        int slots = ((CellPage) arecipes.get(recipe)).slots;
        return ITEMS_Y + (slots + COLUMNS - 1) / COLUMNS * 18 + 6;
    }

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1, 1, 1, 1);
        int slots = ((CellPage) arecipes.get(recipe)).slots;
        for (int i = 0; i < slots; i++) SLOT.draw(2 + i % COLUMNS * 18, ITEMS_Y + i / COLUMNS * 18);
    }

    @Override
    public void drawForeground(int recipe) {
        var cell = ((CellPage) arecipes.get(recipe)).cell;
        var font = Minecraft.getMinecraft().fontRenderer;
        int color = ColorUtils.neiCellView.getColor();
        GL11.glDisable(GL11.GL_LIGHTING);
        font.drawString(
            Platform.formatByteDouble(cell.getUsedBytes()) + " "
                + GuiText.Of.getLocal()
                + " "
                + Platform.formatByteDouble(cell.getTotalBytes())
                + " "
                + GuiText.BytesUsed.getLocal(),
            2,
            4,
            color);
        var numbers = NumberFormat.getIntegerInstance();
        font.drawString(
            numbers.format(cell.getStoredItemTypes()) + " "
                + GuiText.Of.getLocal()
                + " "
                + numbers.format(cell.getTotalItemTypes())
                + " "
                + GuiText.Types.getLocal(),
            2,
            15,
            color);
    }

    private final class CellPage extends CachedRecipe {

        private final ECOStorageCell cell;
        private final int slots;
        private final List<PositionedStack> stacks = new ArrayList<>();

        private CellPage(ECOStorageCell cell, List<GenericStack> contents, int offset) {
            this.cell = cell;
            slots = (int) Math.min(ITEMS_PER_PAGE, Math.max(contents.size(), cell.getTotalItemTypes()) - offset);
            for (int i = offset; i < Math.min(contents.size(), offset + ITEMS_PER_PAGE); i++) {
                var entry = contents.get(i);
                boolean fluid = entry.what() instanceof AEFluidKey;
                // Representative stacks carry identity only; quantities remain longs even above Integer.MAX_VALUE.
                var display = fluid ? ItemFluidPacket.newDisplayStack(((AEFluidKey) entry.what()).toStack(1))
                    : entry.what()
                        .toNative(1)
                        .getItemStackForNEI();
                if (display != null) stacks.add(new StoredStack(display, i - offset, entry.amount(), fluid));
            }
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return stacks;
        }
    }

    private static final class StoredStack extends PositionedStack {

        private final long amount;
        private final boolean fluid;

        private StoredStack(ItemStack display, int index, long amount, boolean fluid) {
            super(display, 3 + index % COLUMNS * 18, ITEMS_Y + 1 + index / COLUMNS * 18, false);
            this.amount = amount;
            this.fluid = fluid;
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            GuiContainerManager.drawItem(
                relx,
                rely,
                item,
                true,
                StackSizeRenderer.getToBeRenderedStackSize(amount, TerminalFontSize.SMALL));
        }

        @Override
        public List<String> getTooltip() {
            return Collections.singletonList(
                EnumChatFormatting.GRAY + GuiText.Stored.getLocal()
                    + ": "
                    + NumberFormat.getIntegerInstance()
                        .format(amount)
                    + (fluid ? " L" : ""));
        }
    }
}
