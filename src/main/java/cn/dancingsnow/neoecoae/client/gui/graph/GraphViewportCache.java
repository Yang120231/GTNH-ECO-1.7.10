package cn.dancingsnow.neoecoae.client.gui.graph;

import java.nio.IntBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * GPU adapter for the bounded tile scheduler on 1.7.10 framebuffers. Pan and nearby zoom levels retain completed
 * tiles; every framebuffer, matrix and viewport change is undone before the screen continues drawing.
 */
@SideOnly(Side.CLIENT)
final class GraphViewportCache implements AutoCloseable {

    private static final int GUARD = 2;
    private static final int TARGET_SIZE = GraphTileCache.TILE_SIZE + 2 * GUARD;
    private final GraphTileCache<TextureTile> tiles = new GraphTileCache<>();

    interface Painter {

        boolean advance(GraphCanvas canvas, long deadline);
    }

    interface Factory {

        Painter create(Bounds area, double pixelsPerUnit);
    }

    /** Raster tiles need framebuffer objects; without them the renderer falls back to simplified live drawing. */
    static boolean supported() {
        return OpenGlHelper.isFramebufferEnabled();
    }

    void prepare(GraphCanvas canvas, Object content, Bounds viewport, Bounds drawing, float zoom, int guiScale,
        Factory factory) {
        tiles.select(content, viewport, drawing, zoom, guiScale);
        if (!tiles.needsWork()) return;
        canvas.flush();
        // Target creation/disposal and item renderers alter GL state. Restore the exact caller state.
        RenderState state = new RenderState();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            tiles.advance(spec -> new TextureTile(spec, factory, canvas), System::nanoTime);
        } finally {
            canvas.flush();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            state.restore();
        }
    }

    /** Draws the drawable tiles in world space; the caller has applied the camera transform. */
    void draw(GraphCanvas canvas) {
        canvas.flush();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        try {
            for (var entry : tiles.drawable()) entry.tile.draw();
        } finally {
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    @Override
    public void close() {
        tiles.close();
    }

    private static int boundFramebuffer() {
        return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    private static void bindFramebuffer(int id) {
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, id);
    }

    private static final class TextureTile implements GraphTileCache.Tile {

        private final Framebuffer target;
        private final GraphTileCache.Spec spec;
        private final Bounds raster;
        private final Painter painter;
        private final GraphCanvas canvas;

        TextureTile(GraphTileCache.Spec spec, Factory factory, GraphCanvas canvas) {
            this.spec = spec;
            this.canvas = canvas;
            raster = spec.bounds()
                .padded((float) (GUARD / spec.pixelsPerUnit()));
            painter = factory.create(raster.padded(32), spec.pixelsPerUnit());
            target = new Framebuffer(TARGET_SIZE, TARGET_SIZE, true);
            target.setFramebufferFilter(GL11.GL_LINEAR);
            target.setFramebufferColor(14 / 255F, 17 / 255F, 21 / 255F, 1);
            target.framebufferClear();
        }

        @Override
        public boolean advance(long deadline) {
            target.bindFramebuffer(true);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, TARGET_SIZE, TARGET_SIZE, 0, 1000, 3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glTranslatef(0, 0, -2000);
            float scale = (float) spec.pixelsPerUnit();
            GL11.glScalef(scale, scale, 1);
            GL11.glTranslatef(-raster.left(), -raster.top(), 0);
            try {
                return painter.advance(canvas, deadline);
            } finally {
                canvas.flush();
            }
        }

        void draw() {
            target.bindFramebufferTexture();
            var bounds = spec.bounds();
            float inset = (float) GUARD / TARGET_SIZE;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setColorOpaque_I(0xFFFFFF);
            tessellator.addVertexWithUV(bounds.left(), bounds.bottom(), 0, inset, inset);
            tessellator.addVertexWithUV(bounds.right(), bounds.bottom(), 0, 1 - inset, inset);
            tessellator.addVertexWithUV(bounds.right(), bounds.top(), 0, 1 - inset, 1 - inset);
            tessellator.addVertexWithUV(bounds.left(), bounds.top(), 0, inset, 1 - inset);
            tessellator.draw();
            target.unbindFramebufferTexture();
        }

        @Override
        public void close() {
            int bound = boundFramebuffer();
            target.deleteFramebuffer();
            bindFramebuffer(bound);
        }
    }

    private static final class RenderState {

        private final int framebuffer = boundFramebuffer();
        private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        private final IntBuffer scissor = BufferUtils.createIntBuffer(16);
        private final boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        RenderState() {
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissor);
        }

        void restore() {
            bindFramebuffer(framebuffer);
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            if (clipped) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(scissor.get(0), scissor.get(1), scissor.get(2), scissor.get(3));
            } else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }
}
