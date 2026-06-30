package cn.dancingsnow.neoecoae.all;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class NEFluids {

    public static final String CRYOTHEUM_SOLUTION = "cryotheum_solution";

    private static final Fluid CRYOTHEUM_SOLUTION_FLUID = new Fluid(CRYOTHEUM_SOLUTION).setLuminosity(5)
        .setDensity(1400)
        .setViscosity(900)
        .setTemperature(120);

    public static Block cryotheumSolutionBlock;

    private NEFluids() {}

    public static void register() {
        if (!FluidRegistry.registerFluid(CRYOTHEUM_SOLUTION_FLUID)) {
            Fluid existing = FluidRegistry.getFluid(CRYOTHEUM_SOLUTION);
            if (existing == null) {
                return;
            }
            CRYOTHEUM_SOLUTION_FLUID.setIcons(existing.getStillIcon(), existing.getFlowingIcon());
        }
        cryotheumSolutionBlock = new NEBlockFluid(cryotheumSolution(), Material.water, CRYOTHEUM_SOLUTION);
        GameRegistry.registerBlock(cryotheumSolutionBlock, CRYOTHEUM_SOLUTION);
    }

    public static void registerContainers() {
        Fluid fluid = cryotheumSolution();
        if (fluid == null) {
            return;
        }
        FluidContainerRegistry.registerFluidContainer(
            new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME),
            new ItemStack(NEItems.cryotheumSolutionBucket),
            new ItemStack(Items.bucket));
    }

    public static Fluid cryotheumSolution() {
        Fluid fluid = FluidRegistry.getFluid(CRYOTHEUM_SOLUTION);
        return fluid == null ? CRYOTHEUM_SOLUTION_FLUID : fluid;
    }

    private static final class NEBlockFluid extends BlockFluidClassic {

        private final String texture;

        private NEBlockFluid(Fluid fluid, Material material, String id) {
            super(fluid, material);
            this.texture = NeoECOAE.MODID + ":" + id;
            this.setBlockName(id);
            this.setBlockTextureName(this.texture);
            this.setCreativeTab(null);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister register) {
            IIcon still = register.registerIcon(this.texture + "_still");
            IIcon flowing = register.registerIcon(this.texture + "_flow");
            this.blockIcon = still;
            this.getFluid()
                .setIcons(still, flowing);
        }
    }
}
