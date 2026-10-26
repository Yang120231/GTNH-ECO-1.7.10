package cn.dancingsnow.neoecoae.platform.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cpw.mods.fml.common.registry.GameRegistry;

/** Native identities backed by the source workstation host, never by a placeholder inventory. */
public final class WorkstationMachines {

    public static final Property<Boolean> WORKING = Property.bool("working");
    public static final Property<Module> MODULE = new Property<>("module", Module.class, value -> true);
    private static final BlockEntityType<Workstation> TYPE = new BlockEntityType<>(
        "integrated_working_station",
        Workstation::new);
    public static final NativeMachineBlock WORKSTATION = new NativeMachineBlock(
        TYPE,
        ECOMultiblockParts.Role.CRAFTING_WORKER,
        "integrated_working_station") {

        {
            define(WORKING, false);
            define(MODULE, Module.REGULAR);
        }

        @Override
        public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!world.isRemote && tile instanceof ECOIntegratedWorkingStationBlockEntity) {
                ECOIntegratedWorkingStationBlockEntity machine = (ECOIntegratedWorkingStationBlockEntity) tile;
                for (int slot = 0; slot < machine.getSizeInventory(); slot++) {
                    ItemStack drop = machine.decrStackSize(slot, Integer.MAX_VALUE);
                    if (drop != null) world.spawnEntityInWorld(new EntityItem(world, x + .5, y + .5, z + .5, drop));
                }
                for (int slot = 0; slot < machine.getUpgrades()
                    .getSizeInventory(); slot++) {
                    ItemStack drop = machine.getUpgrades()
                        .decrStackSize(slot, Integer.MAX_VALUE);
                    if (drop != null) world.spawnEntityInWorld(new EntityItem(world, x + .5, y + .5, z + .5, drop));
                }
            }
            super.breakBlock(world, x, y, z, block, metadata);
        }
    };
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    private WorkstationMachines() {}

    public enum Module {
        REGULAR
    }

    public static void register() {
        if (!BLOCKS.isEmpty()) throw new IllegalStateException("Workstation machines registered twice");
        WORKSTATION.setBlockName("neoecoae.integrated_working_station")
            .setCreativeTab(NativeContent.TAB);
        GameRegistry.registerBlock(WORKSTATION, NativeMachineItemBlock.class, TYPE.id());
        GameRegistry.registerTileEntity(Workstation.class, "neoecoae." + TYPE.id());
        BLOCKS.put(TYPE.id(), WORKSTATION);
    }

    public static Map<String, Block> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    public static boolean owns(String id, Block block, String sourceModel) {
        if (BLOCKS.get(id) != block) return false;
        if (!(block instanceof NativeMachineBlock)) return false;
        NativeMachineBlock machine = (NativeMachineBlock) block;
        return machine.partRole() == ECOMultiblockParts.Role.CRAFTING_WORKER
            && sourceModel.equals("neoecoae:block/" + machine.model())
            && machine.createNewTileEntity(null, 0) instanceof Workstation;
    }

    public static class Workstation extends ECOIntegratedWorkingStationBlockEntity
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

        public Workstation() {
            super(TYPE, BlockPos.ZERO, WORKSTATION.defaultBlockState());
        }

        @Override
        public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
            com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
            com.cleanroommc.modularui.screen.UISettings settings) {
            return cn.dancingsnow.neoecoae.platform.ui.workstation.NativeWorkstationPages
                .create(this, data.getPlayer(), sync);
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public com.cleanroommc.modularui.screen.ModularScreen createScreen(
            com.cleanroommc.modularui.factory.PosGuiData data, com.cleanroommc.modularui.screen.ModularPanel panel) {
            return new com.cleanroommc.modularui.screen.ModularScreen("neoecoae", panel);
        }
    }
}
