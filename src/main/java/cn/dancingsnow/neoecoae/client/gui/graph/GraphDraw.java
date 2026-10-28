package cn.dancingsnow.neoecoae.client.gui.graph;

/**
 * Constant-size quad geometry for the graph. Every shape is one or two quads handed to a sink, so long diagonals and
 * arrow heads never degrade into per-pixel rectangles; the sink decides when the batch is flushed.
 */
final class GraphDraw {

    /** Receives one colored quad in the current model-view space (ARGB color). */
    interface QuadSink {

        void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int color);
    }

    private GraphDraw() {}

    static void fill(QuadSink sink, float x1, float y1, float x2, float y2, int color) {
        float left = Math.min(x1, x2), right = Math.max(x1, x2);
        float top = Math.min(y1, y2), bottom = Math.max(y1, y2);
        if (left == right || top == bottom) return;
        sink.quad(left, top, left, bottom, right, bottom, right, top, color);
    }

    static void hLine(QuadSink sink, int left, int right, int y, int color) {
        fill(sink, Math.min(left, right), y, Math.max(left, right) + 1, y + 1, color);
    }

    static void vLine(QuadSink sink, int x, int top, int bottom, int color) {
        fill(sink, x, Math.min(top, bottom) + 1, x + 1, Math.max(top, bottom), color);
    }

    static void line(QuadSink sink, float x1, float y1, float x2, float y2, int color, float halfWidth) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.0001f) return;
        float nx = -dy / length * halfWidth, ny = dx / length * halfWidth;
        sink.quad(x1 - nx, y1 - ny, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, color);
    }

    static void triangle(QuadSink sink, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        sink.quad(ax, ay, bx, by, cx, cy, cx, cy, color);
    }

    /** Outline of a world-space box as four thin lines. */
    static void outline(QuadSink sink, float x, float y, float right, float bottom, int color, float thickness) {
        float half = thickness / 2;
        line(sink, x, y, right, y, color, half);
        line(sink, right, y, right, bottom, color, half);
        line(sink, right, bottom, x, bottom, color, half);
        line(sink, x, bottom, x, y, color, half);
    }
}
