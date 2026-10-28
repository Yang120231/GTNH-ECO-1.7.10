package cn.dancingsnow.neoecoae.client.gui.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class GraphDrawTest {

    private static final class Recorder implements GraphDraw.QuadSink {

        final List<float[]> quads = new ArrayList<>();

        @Override
        public void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int color) {
            quads.add(new float[] { ax, ay, bx, by, cx, cy, dx, dy });
        }
    }

    @Test
    void longDiagonalAndTriangleUseConstantGeometry() {
        var sink = new Recorder();
        GraphDraw.line(sink, -1_000_000, -1_000_000, 1_000_000, 1_000_000, 0xff123456, 0.5f);
        assertEquals(1, sink.quads.size());
        GraphDraw.triangle(sink, 0, 0, 10, 20, 20, 0, 0xff654321);
        assertEquals(2, sink.quads.size());
        GraphDraw.outline(sink, 0, 0, 100, 50, 0xffffffff, 1);
        assertEquals(6, sink.quads.size());
    }

    @Test
    void degenerateShapesEmitNothingAndFillsAreNormalised() {
        var sink = new Recorder();
        GraphDraw.line(sink, 5, 5, 5, 5, 0xff000000, 1);
        GraphDraw.fill(sink, 3, 3, 3, 9, 0xff000000);
        assertTrue(sink.quads.isEmpty());
        GraphDraw.fill(sink, 10, 20, 0, 0, 0xff000000);
        float[] quad = sink.quads.get(0);
        assertEquals(0, quad[0]);
        assertEquals(0, quad[1]);
        assertEquals(10, quad[4]);
        assertEquals(20, quad[5]);
        GraphDraw.hLine(sink, 9, 2, 4, 0xff000000);
        float[] line = sink.quads.get(1);
        assertEquals(2, line[0]);
        assertEquals(10, line[4]);
        assertEquals(5, line[5]);
    }

    @Test
    void lineQuadHasTheRequestedWidth() {
        var sink = new Recorder();
        GraphDraw.line(sink, 0, 0, 100, 0, 0xff000000, 2);
        float[] quad = sink.quads.get(0);
        assertEquals(4, Math.abs(quad[3] - quad[1]), 1e-4);
        assertEquals(100, Math.abs(quad[4] - quad[2]), 1e-4);
    }
}
