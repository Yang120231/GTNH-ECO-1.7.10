package cn.dancingsnow.neoecoae.block;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockFormedTexturedMachine extends NEBlock {

    private final String normalTextureName;
    private final String formedTextureName;

    @SideOnly(Side.CLIENT)
    private IIcon formedIcon;

    public BlockFormedTexturedMachine(String normalTextureName, String formedTextureName) {
        super(Material.iron);
        this.normalTextureName = normalTextureName;
        this.formedTextureName = formedTextureName;
        this.setBlockTextureName(normalTextureName);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        this.formedIcon = register.registerIcon(this.formedTextureName);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        if (ECOFormationVisibility.shouldRenderFormedMember(world, x, y, z) && this.formedIcon != null) {
            return this.formedIcon;
        }
        return super.getIcon(world, x, y, z, side);
    }

    public String getFormedTextureName() {
        return this.formedTextureName;
    }
}
