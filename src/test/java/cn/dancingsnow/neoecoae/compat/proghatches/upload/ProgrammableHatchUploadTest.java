package cn.dancingsnow.neoecoae.compat.proghatches.upload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import cpw.mods.fml.common.Loader;
import reobf.proghatches.gt.cover.ProgrammingCover;
import reobf.proghatches.gt.metatileentity.DualInputHatch;
import reobf.proghatches.item.ItemProgrammingCircuit;

class ProgrammableHatchUploadTest {

    @Test
    void compatibilityRulesStayDisabledWhenTheModIsNotLoadedEvenIfItsClassesExist() {
        try (var loader = mockStatic(Loader.class)) {
            assertFalse(ProgrammableHatchUpload.isAvailable());
            assertFalse(ProgrammableHatchUpload.isCover(new ProgrammingCover()));
            assertFalse(ProgrammableHatchUpload.isProgrammingInput(new DualInputHatch()));
            var circuit = new ItemStack(new ItemProgrammingCircuit());
            assertFalse(ProgrammableHatchUpload.isProgrammingCircuit(circuit));
            assertNull(ProgrammableHatchUpload.unwrap(circuit));
        }
    }

    @Test
    void installedModRecognizesProgrammingCoversAndInheritedInputCapabilities() {
        try (var loader = mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("programmablehatches"))
                .thenReturn(true);
            assertTrue(ProgrammableHatchUpload.isCover(new ProgrammingCover()));
            assertTrue(ProgrammableHatchUpload.isCover(new ProgrammingCover() {}));
            assertTrue(ProgrammableHatchUpload.isProgrammingInput(new DualInputHatch() {}));
            assertFalse(ProgrammableHatchUpload.isCover(new Object()));
            assertFalse(ProgrammableHatchUpload.isProgrammingInput(new Object()));
            assertFalse(ProgrammableHatchUpload.isProgrammingCircuit(new ItemStack(new Item())));
            var circuit = new ItemStack(new ItemProgrammingCircuit());
            assertTrue(ProgrammableHatchUpload.isProgrammingCircuit(circuit));
            assertNull(ProgrammableHatchUpload.unwrap(circuit), "An empty programming circuit resets the selector");
        }
    }

    @Test
    void wrappedCircuitTwoIsDecodedWithoutChangingThePatternStack() {
        Item targetItem = new Item();
        var container = mock(cpw.mods.fml.common.ModContainer.class);
        when(container.getModId()).thenReturn("neoecoae_programming_upload_tests");
        var fml = mock(Loader.class);
        when(fml.activeModContainer()).thenReturn(container);
        try (var loader = mockStatic(Loader.class)) {
            loader.when(Loader::instance)
                .thenReturn(fml);
            loader.when(() -> Loader.isModLoaded("programmablehatches"))
                .thenReturn(true);
            Item.itemRegistry.addObject(29010, "neoecoae_programming_upload_tests:circuit", targetItem);
            var wrapped = new ItemStack(new ItemProgrammingCircuit(), 3);
            var tag = new NBTTagCompound();
            tag.setTag("targetCircuit", new ItemStack(targetItem, 1, 2).writeToNBT(new NBTTagCompound()));
            wrapped.setTagCompound(tag);
            var decoded = ProgrammableHatchUpload.unwrap(wrapped);
            assertNotNull(decoded);
            assertSame(targetItem, decoded.getItem());
            assertEquals(2, decoded.getItemDamage());
            decoded.setItemDamage(9);
            assertEquals(
                2,
                ProgrammableHatchUpload.unwrap(wrapped)
                    .getItemDamage());
            assertEquals(3, wrapped.stackSize);
        }
    }
}
