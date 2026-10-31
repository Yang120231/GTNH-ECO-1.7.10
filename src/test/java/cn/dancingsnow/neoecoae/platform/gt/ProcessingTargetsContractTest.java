package cn.dancingsnow.neoecoae.platform.gt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;

/** Exact native descriptors from the pinned AE2 and GT JARs, without initializing optional mod classes. */
class ProcessingTargetsContractTest {

    @Test
    void acceptedAe2TargetsAndGtStructureHooksExistInTheActualDependencies() throws Exception {
        var ae2 = read("appeng/helpers/DualityInterface");
        assertTrue(
            ae2.methods.stream()
                .anyMatch(
                    method -> method.name.equals("pushPattern") && method.desc.equals(
                        "(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z")));
        assertTrue(
            ae2.methods.stream()
                .anyMatch(
                    method -> method.name.equals("onPushPatternSuccess") && method.desc.equals(
                        "(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraftforge/common/util/ForgeDirection;Lappeng/api/networking/crafting/ICraftingPatternDetails;)V")));
        var gt = read("gregtech/api/metatileentity/implementations/MTEMultiBlockBase");
        assertTrue(
            gt.methods.stream()
                .anyMatch(
                    method -> method.name.equals("checkStructure")
                        && method.desc.equals("(ZLgregtech/api/interfaces/tileentity/IGregTechTileEntity;)Z")));
        for (String field : new String[] { "mInputBusses", "mInputHatches", "mOutputItems", "mOutputFluids",
            "lastParallel" })
            assertTrue(
                gt.fields.stream()
                    .anyMatch(value -> value.name.equals(field)),
                field);
        var base = read("gregtech/api/metatileentity/BaseMetaTileEntity");
        for (String method : new String[] { "getProgress", "getMaxProgress", "getMetaTileEntity", "isActive",
            "isAllowedToWork" })
            assertTrue(
                base.methods.stream()
                    .anyMatch(value -> value.name.equals(method)),
                method);
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = ProcessingTargetsContractTest.class.getClassLoader()
            .getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }
}
