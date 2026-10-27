package cn.dancingsnow.neoecoae.platform.ui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.inventory.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

class NativePatternInterfaceSessionTest {
    private static ECOCraftingPatternBusBlockEntity bus(int x) {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        when(bus.getBlockPos()).thenReturn(new BlockPos(x, 0, 0));
        var inventory = mock(AppEngInternalInventory.class);
        when(inventory.size()).thenReturn(63);
        when(bus.getInternalInventory()).thenReturn(inventory);
        return bus;
    }

    @SuppressWarnings("unchecked")
    private static NativePatternInterfaceSession session(NECraftingCluster cluster) {
        var host = (ECOMachineInterfaceBlockEntity<NECraftingCluster>) mock(ECOMachineInterfaceBlockEntity.class);
        when(host.getCluster()).thenReturn(cluster);
        return new NativePatternInterfaceSession(host);
    }

    @Test
    void previewIsBoundedAndPhysicalSlotTokensSurviveBusListReordering() {
        var cluster = mock(NECraftingCluster.class);
        var first = bus(1);
        var second = bus(2);
        var buses = new ArrayList<>(Arrays.asList(first, second));
        when(cluster.getPatternDomainBuses()).thenReturn(buses);
        var session = session(cluster);
        var initial = session.capture();
        assertEquals(14, initial.getInt("rows"));
        assertEquals(54, initial.getList("page", 10).size());
        int token = initial.getList("page", 10).getCompound(0).getInt("token");
        Collections.reverse(buses);
        assertEquals(token, session.capture().getList("page", 10).getCompound(0).getInt("token"));
        session.apply(0, Integer.MAX_VALUE);
        assertEquals(8, session.capture().getInt("offset"));
    }

    @Test
    void replacementAndRemovedBusInvalidateAnOldPreviewClickBeforeAnyInventoryTransfer() {
        var cluster = mock(NECraftingCluster.class);
        var bus = bus(1);
        when(cluster.getPatternDomainBuses()).thenReturn(Collections.singletonList(bus));
        var session = session(cluster);
        int token = session.capture().getList("page", 10).getCompound(0).getInt("token");
        when(bus.getInternalInventory().getStackInSlot(0)).thenReturn(new ItemStack(TestItems.STONE));
        assertDoesNotThrow(() -> session.apply(1, token));
        when(cluster.getPatternDomainBuses()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> session.apply(2, token));
        verify(bus.getInternalInventory(), never()).extractItem(anyInt(), anyInt(), anyBoolean());
        verify(bus.getInternalInventory(), never()).insertItem(anyInt(), any(), anyBoolean());
    }

    @Test
    void patternPreviewShowsThePrimaryProductWithoutReplacingThePhysicalPattern() {
        var details = mock(ICraftingPatternDetails.class);
        var output = mock(IAEItemStack.class);
        var product = new ItemStack(TestItems.DIAMOND, 4);
        product.setStackDisplayName("Primary output");
        when(output.getItemStack()).thenReturn(product);
        when(details.getOutputs()).thenReturn(new IAEItemStack[] { null, output });
        var pattern = new ItemStack(new PreviewPattern(details));
        pattern.setStackDisplayName("Physical pattern");
        var original = pattern.copy();

        assertSame(product, session(mock(NECraftingCluster.class)).displayStack(pattern));
        assertTrue(ItemStack.areItemStacksEqual(original, pattern));
    }

    @Test
    void emptyAndMalformedPatternsRemainVisibleAndRemovable() {
        var session = session(mock(NECraftingCluster.class));
        assertNull(session.displayStack(null));
        var ordinary = new ItemStack(TestItems.STONE);
        assertSame(ordinary, session.displayStack(ordinary));
        var details = mock(ICraftingPatternDetails.class);
        var pattern = new ItemStack(new PreviewPattern(details));
        assertSame(pattern, session.displayStack(pattern));
        when(details.getOutputs()).thenThrow(new IllegalArgumentException("Malformed pattern"));
        assertSame(pattern, session.displayStack(pattern));
    }

    private static final class PreviewPattern extends Item implements ICraftingPatternItem {

        private final ICraftingPatternDetails details;

        PreviewPattern(ICraftingPatternDetails details) {
            this.details = details;
        }

        @Override
        public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
            return details;
        }
    }
}
