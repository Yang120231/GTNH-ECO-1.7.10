package cn.dancingsnow.neoecoae.platform.structurelib;

import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.registry.ComputationSystemL4;
import cn.dancingsnow.neoecoae.platform.registry.ComputationSystemL6;
import cn.dancingsnow.neoecoae.platform.registry.ComputationSystemL9;
import cn.dancingsnow.neoecoae.platform.registry.CraftingMachines;
import cn.dancingsnow.neoecoae.platform.registry.CraftingSystemL4;
import cn.dancingsnow.neoecoae.platform.registry.CraftingSystemL6;
import cn.dancingsnow.neoecoae.platform.registry.CraftingSystemL9;
import cn.dancingsnow.neoecoae.platform.registry.HostL4;
import cn.dancingsnow.neoecoae.platform.registry.HostL6;
import cn.dancingsnow.neoecoae.platform.registry.HostL9;
import cn.dancingsnow.neoecoae.platform.registry.StorageMachines;
import cn.dancingsnow.neoecoae.platform.registry.Workstation;
import cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines;

/**
 * StructureLib owns ECO multiblock construction: the hologram projector shows and builds the structure, and
 * BlockRenderer6343 previews it in NEI. Registration goes through IMultiblockInfoContainer because
 * BlockRenderer6343 only scans that registry; formation detection stays with the ECO calculators.
 */
public final class NEStructureLib {

    public static final String CHANNEL_LENGTH = "length";
    public static final String CHANNEL_MIRROR = "mirror";

    private NEStructureLib() {}

    public static void init() {
        Function<TileEntity, MultiBlockDefinition> storage = host -> ((ECOStorageSystemBlockEntity) host)
            .getBuildDefinition();
        register(HostL4.class, StorageMachines.STORAGE_SYSTEM_L4.value(), storage);
        register(HostL6.class, StorageMachines.STORAGE_SYSTEM_L6.value(), storage);
        register(HostL9.class, StorageMachines.STORAGE_SYSTEM_L9.value(), storage);
        Function<TileEntity, MultiBlockDefinition> crafting = host -> ((ECOCraftingSystemBlockEntity) host)
            .getBuildDefinition();
        register(CraftingSystemL4.class, CraftingMachines.CRAFTING_SYSTEM_L4.value(), crafting);
        register(CraftingSystemL6.class, CraftingMachines.CRAFTING_SYSTEM_L6.value(), crafting);
        register(CraftingSystemL9.class, CraftingMachines.CRAFTING_SYSTEM_L9.value(), crafting);
        Function<TileEntity, MultiBlockDefinition> computation = host -> ((ECOComputationSystemBlockEntity) host)
            .getBuildDefinition();
        register(ComputationSystemL4.class, CraftingMachines.COMPUTATION_SYSTEM_L4.value(), computation);
        register(ComputationSystemL6.class, CraftingMachines.COMPUTATION_SYSTEM_L6.value(), computation);
        register(ComputationSystemL9.class, CraftingMachines.COMPUTATION_SYSTEM_L9.value(), computation);
        register(
            Workstation.class,
            WorkstationMachines.WORKSTATION,
            host -> ((ECOLargeIntegratedWorkingStationBlockEntity) host).getBuildDefinition());
        StructureLibAPI.registerChannelDescription(CHANNEL_LENGTH, NeoECOAE.MODID, "neoecoae.structure.channel.length");
        StructureLibAPI.registerChannelDescription(CHANNEL_MIRROR, NeoECOAE.MODID, "neoecoae.structure.channel.mirror");
    }

    private static void register(Class<? extends TileEntity> type, net.minecraft.block.Block controller,
        Function<TileEntity, MultiBlockDefinition> definitions) {
        IMultiblockInfoContainer.registerTileClass(type, new NEConstructableContainer(controller, definitions));
    }

    /** Builds the whole structure without consuming items, clamping the length to the definition's range. */
    public static boolean creativeBuild(TileEntity host, int length) {
        if (!IMultiblockInfoContainer.contains(host.getClass())) return false;
        ItemStack trigger = new ItemStack(StructureLibAPI.getDefaultHologramItem());
        ChannelDataAccessor.setChannelData(trigger, CHANNEL_LENGTH, length);
        IMultiblockInfoContainer.<TileEntity>get(host.getClass())
            .construct(trigger, false, host, ExtendedFacing.DEFAULT);
        return true;
    }
}
