package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.all.NEStorageItems;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;

public class ComputationCellItemRenderer implements IItemRenderer {

    private static final double GUI_SCALE = 0.625D;
    private static final double EQUIPPED_SCALE = 0.72D;
    private static final double ENTITY_SCALE = 0.42D;
    private static final double GUI_Z_ROTATION = 30.0D;
    private static final double GUI_Y_ROTATION = -135.0D;

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null && item.getItem() instanceof NEStorageItems.ECOComputationCellItem;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (!(item.getItem() instanceof NEStorageItems.ECOComputationCellItem)) {
            return;
        }
        NEStorageItems.ECOComputationCellItem cell = (NEStorageItems.ECOComputationCellItem) item.getItem();
        GL11.glPushMatrix();
        applyTransform(type);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationBlocksTexture);
        EcoModelRenderer.renderItemModel(
            ComputationCellItemModels.get(cell.getTier()),
            ModelFacing.NORTH,
            cell.getModelIcons());
        GL11.glPopMatrix();
    }

    private static void applyTransform(ItemRenderType type) {
        if (type == ItemRenderType.INVENTORY) {
            applyGuiTransform(GUI_SCALE);
            return;
        }
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            applyGuiTransform(EQUIPPED_SCALE);
            return;
        }
        if (type == ItemRenderType.ENTITY) {
            applyGuiTransform(ENTITY_SCALE);
        }
    }

    private static void applyGuiTransform(double scale) {
        GL11.glTranslated(0.5D, 0.5D, 0.5D);
        GL11.glRotated(GUI_Z_ROTATION, 0.0D, 0.0D, 1.0D);
        GL11.glRotated(GUI_Y_ROTATION, 0.0D, 1.0D, 0.0D);
        GL11.glScaled(scale, scale, scale);
        GL11.glTranslated(-0.5D, -0.5D, -0.5D);
    }

}
