package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;

public class ModernBlockItemRenderer implements IItemRenderer {

    private final BlockModernModel block;

    public ModernBlockItemRenderer(Block block) {
        this.block = (BlockModernModel) block;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        if (type == ItemRenderType.INVENTORY) {
            return helper == ItemRendererHelper.INVENTORY_BLOCK || helper == ItemRendererHelper.BLOCK_3D;
        }
        return helper == ItemRendererHelper.ENTITY_BOBBING || helper == ItemRendererHelper.ENTITY_ROTATION
            || helper == ItemRendererHelper.BLOCK_3D
            || helper == ItemRendererHelper.EQUIPPED_BLOCK;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        if (type == ItemRenderType.INVENTORY) {
            EcoModelRenderer
                .renderInventoryBlock(ModernBlockModels.get(this.block.getModelName()), this.block.getModelIcons());
        } else {
            applyContextTransform(type);
            EcoModelRenderer
                .renderInventory(ModernBlockModels.get(this.block.getModelName()), this.block.getModelIcons());
        }
        GL11.glPopMatrix();
    }

    private static void applyContextTransform(ItemRenderType type) {
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            GL11.glTranslated(0.5D, 0.5D, 0.5D);
        } else if (type == ItemRenderType.ENTITY) {
            GL11.glScaled(1.5D, 1.5D, 1.5D);
        }
    }
}
