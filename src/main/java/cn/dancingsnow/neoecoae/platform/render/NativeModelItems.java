package cn.dancingsnow.neoecoae.platform.render;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.TextureStitchEvent;

import org.lwjgl.opengl.GL11;

import cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Source cuboid item models use the same preserved loader/baker/drawer and the native block texture atlas. */
@SideOnly(Side.CLIENT)
public final class NativeModelItems implements IItemRenderer {

    private static final Map<Item, NativeModelItems> MODELS = new IdentityHashMap<>();
    private BakedEcoModel model;
    private final Map<String, IIcon> icons = new LinkedHashMap<>();

    public static void install() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new NativeModelItems());
        for (Item item : NativeContent.items()
            .values()) if (item instanceof IECOComputationCellItem) {
                NativeModelItems renderer = new NativeModelItems();
                MODELS.put(item, renderer);
                MinecraftForgeClient.registerItemRenderer(item, renderer);
            }
    }

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) return;
        for (var entry : MODELS.entrySet()) {
            NativeModelItems renderer = entry.getValue();
            renderer.icons.clear();
            renderer.model = new BakedEcoModel(
                ModernModelLoader.loadModel(
                    new ResourceLocation("neoecoae", "models/item/" + NativeContent.id(entry.getKey()) + ".json"),
                    0));
            for (var quad : renderer.model.getQuads(ModelFacing.NORTH)) {
                String texture = quad.getTexture();
                renderer.icons.computeIfAbsent(texture, key -> event.map.registerIcon(key.replace(":block/", ":")));
            }
            if (renderer.icons.isEmpty())
                throw new IllegalStateException("Empty source item mesh: " + NativeContent.id(entry.getKey()));
        }
    }

    public static IIcon icon(Item item) {
        NativeModelItems renderer = MODELS.get(item);
        return renderer == null || renderer.icons.isEmpty() ? null
            : renderer.icons.values()
                .iterator()
                .next();
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (model == null) throw new IllegalStateException("Source item model not stitched");
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON)
                GL11.glTranslated(0.5, 0.5, 0.5);
            EcoModelRenderer.renderInventoryBlock(model, icons);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
