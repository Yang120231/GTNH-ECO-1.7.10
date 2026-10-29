package cn.dancingsnow.neoecoae.crafting.upload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;

class PatternUploadDuplicateTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void separatelyEncodedCopiesAreDuplicatesWithoutChangingTheirTags() {
        ItemStack left = pattern(TestItems.DIAMOND, 100L);
        ItemStack right = pattern(TestItems.DIAMOND, 200L);
        NBTTagCompound leftBefore = (NBTTagCompound) left.getTagCompound()
            .copy();
        NBTTagCompound rightBefore = (NBTTagCompound) right.getTagCompound()
            .copy();
        right.stackSize = 2;

        assertTrue(PatternStacks.samePattern(left, right));
        assertTrue(PatternStacks.samePattern(right, left));
        assertFalse(PatternStacks.sameItem(left, right));
        assertFalse(PatternStacks.sameStack(left, right));
        assertEquals(leftBefore, left.getTagCompound());
        assertEquals(rightBefore, right.getTagCompound());
    }

    @Test
    void patternsWithoutEncodingTimestampsStillMatch() {
        ItemStack left = pattern(TestItems.DIAMOND, 100L);
        ItemStack right = pattern(TestItems.DIAMOND, 200L);
        left.getTagCompound()
            .removeTag("encodedAt");
        assertTrue(PatternStacks.samePattern(left, right));
        right.getTagCompound()
            .removeTag("encodedAt");
        assertTrue(PatternStacks.samePattern(left, right));
    }

    @Test
    void recipeFieldsAndNestedItemTimestampsRemainSignificant() {
        ItemStack left = pattern(TestItems.DIAMOND, 100L);
        ItemStack right = pattern(TestItems.DIAMOND, 200L);
        right.getTagCompound()
            .setBoolean("substitute", true);
        assertFalse(PatternStacks.samePattern(left, right));
        right = pattern(TestItems.DIAMOND, 200L);
        right.getTagCompound()
            .setString("author", "another player");
        assertFalse(PatternStacks.samePattern(left, right));
        right = pattern(TestItems.DIAMOND, 200L);
        right.getTagCompound()
            .getTagList("in", 10)
            .getCompoundTagAt(0)
            .setLong("encodedAt", 999L);
        assertFalse(PatternStacks.samePattern(left, right));
        right = pattern(TestItems.DIAMOND, 200L);
        right.getTagCompound()
            .getTagList("out", 10)
            .getCompoundTagAt(0)
            .setInteger("Count", 2);
        assertFalse(PatternStacks.samePattern(left, right));
    }

    @Test
    void differentItemsMetadataAndMissingTagsDoNotMatch() {
        ItemStack left = pattern(TestItems.DIAMOND, 100L);
        assertFalse(PatternStacks.samePattern(left, pattern(TestItems.STONE, 200L)));
        ItemStack right = pattern(TestItems.DIAMOND, 200L);
        right.setItemDamage(1);
        assertFalse(PatternStacks.samePattern(left, right));
        assertFalse(PatternStacks.samePattern(left, new ItemStack(TestItems.DIAMOND)));
        assertTrue(PatternStacks.samePattern(new ItemStack(TestItems.DIAMOND), new ItemStack(TestItems.DIAMOND)));
        assertTrue(PatternStacks.samePattern(null, null));
        assertFalse(PatternStacks.samePattern(left, null));
        assertFalse(PatternStacks.samePattern(null, left));
    }

    @Test
    void duplicateBlocksManualAndAutomaticUploadEvenWithAnEmptySlot() {
        Item item = new EncodedPattern();
        ItemStack incoming = pattern(item, 200L);
        PatternTargetSelector.Evaluation evaluation = evaluate(pattern(item, 100L), incoming, true);

        assertEquals(PatternTargetSelector.SlotState.DUPLICATE, evaluation.state);
        assertEquals(1, evaluation.emptySlots);
        assertEquals(-1, evaluation.freeSlot);
        assertFalse(evaluation.canUploadManually());
        assertFalse(evaluation.canUploadAutomatically());
        PatternTargetSelector.AutoChoice choice = PatternTargetSelector.choose(Collections.singletonList(evaluation));
        assertNull(choice.selected);
        assertEquals("gui.neoecoae.pattern_upload.reason.duplicate", choice.reason);
    }

    @Test
    void duplicateTakesPrecedenceWhenAllSlotsAreOccupied() {
        Item item = new EncodedPattern();
        assertEquals(
            PatternTargetSelector.SlotState.DUPLICATE,
            evaluate(pattern(item, 100L), pattern(item, 200L), false).state);
    }

    @Test
    void differentRecipeCanStillUseTheEmptySlot() {
        Item item = new EncodedPattern();
        ItemStack incoming = pattern(item, 200L);
        incoming.getTagCompound()
            .getTagList("out", 10)
            .getCompoundTagAt(0)
            .setInteger("Count", 2);
        PatternTargetSelector.Evaluation evaluation = evaluate(pattern(item, 100L), incoming, true);
        assertEquals(PatternTargetSelector.SlotState.FREE, evaluation.state);
        assertTrue(evaluation.canUploadManually());
        assertTrue(evaluation.canUploadAutomatically());
        assertEquals(1, evaluation.freeSlot);
    }

    @Test
    void timestampChangeStillRejectsAReplacedSourceWithoutWritingEitherSlot() {
        PatternSlotAccess source = mock(PatternSlotAccess.class);
        PatternSlotAccess target = mock(PatternSlotAccess.class);
        when(source.isLive()).thenReturn(true);
        when(target.isLive()).thenReturn(true);
        when(source.get(0)).thenReturn(pattern(TestItems.DIAMOND, 200L));

        PatternTransfer.Result result = PatternTransfer.move(source, 0, target, 0, pattern(TestItems.DIAMOND, 100L));

        assertEquals(PatternTransfer.Status.REJECTED, result.status);
        assertEquals(PatternTransfer.REASON_SOURCE_CHANGED, result.reason);
        verify(source, never()).set(anyInt(), any());
        verify(target, never()).set(anyInt(), any());
    }

    @Test
    void physicalStackMergingStillRequiresTheCompleteNbt() {
        IInventory inventory = mock(IInventory.class);
        when(inventory.getSizeInventory()).thenReturn(1);
        when(inventory.getInventoryStackLimit()).thenReturn(64);
        when(inventory.getStackInSlot(0)).thenReturn(pattern(TestItems.DIAMOND, 100L));
        PatternSlotAccess slots = PatternSlotAccess.of(inventory, 1, null);

        assertFalse(slots.accepts(0, pattern(TestItems.DIAMOND, 200L)));
        assertTrue(slots.accepts(0, pattern(TestItems.DIAMOND, 100L)));
    }

    private static PatternTargetSelector.Evaluation evaluate(ItemStack existing, ItemStack incoming, boolean empty) {
        IGrid grid = mock(IGrid.class);
        PatternUploadTarget target = mock(PatternUploadTarget.class);
        PatternSlotAccess slots = mock(PatternSlotAccess.class);
        when(target.isPresent(grid)).thenReturn(true);
        when(target.match(any())).thenReturn(MatchResult.of(MatchResult.Level.EXACT));
        when(target.slots()).thenReturn(slots);
        when(slots.size()).thenReturn(2);
        when(slots.get(0)).thenReturn(existing);
        if (!empty) when(slots.get(1)).thenReturn(existing.copy());
        when(slots.accepts(eq(1), any())).thenReturn(true);
        PatternContext context = PatternContext.of(incoming, null, null);
        assertNotNull(context);
        return PatternTargetSelector.evaluate(target, context, grid);
    }

    private static ItemStack pattern(Item item, long timestamp) {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("encodedAt", timestamp);
        tag.setString("author", "player");
        tag.setBoolean("crafting", true);
        tag.setBoolean("substitute", false);
        NBTTagCompound ingredient = new NBTTagCompound();
        ingredient.setString("id", "minecraft:stone");
        ingredient.setInteger("Count", 1);
        ingredient.setLong("encodedAt", 50L);
        NBTTagList inputs = new NBTTagList();
        inputs.appendTag(ingredient);
        tag.setTag("in", inputs);
        NBTTagList outputs = new NBTTagList();
        outputs.appendTag(ingredient.copy());
        tag.setTag("out", outputs);
        stack.setTagCompound(tag);
        return stack;
    }

    private static final class EncodedPattern extends Item implements ICraftingPatternItem {

        @Override
        public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
            ICraftingPatternDetails details = mock(ICraftingPatternDetails.class);
            when(details.isCraftable()).thenReturn(true);
            return details;
        }
    }
}
