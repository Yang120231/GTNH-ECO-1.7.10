package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class NativePageLayoutTest {

    @Test
    void acceptsWrappedSourceAndKeepsTheOriginalInventoryAndFilterCoordinates() {
        var layout = new NativePageLayout("computation_interface");
        assertEquals(176, layout.width());
        assertEquals(253, layout.height());
        assertEquals(new NativePageLayout.Bounds(7, 28, 162, 126), layout.bounds("computation-fuzzy-grid"));
        assertEquals(
            new NativePageLayout.Bounds(7, 167, 162, 72),
            layout.bounds("computation-interface-player-inventory"));
    }

    @Test
    void computationSidebarBelongsToTheMainPanelRatherThanTheOuterRoot() {
        var layout = new NativePageLayout("computation/controller");
        assertEquals(new NativePageLayout.Bounds(21, 0, 253, 231), layout.bounds("computation_main"));
        assertEquals(new NativePageLayout.Bounds(251, 0, 90, 164), layout.bounds("computation_cpu_selector"));
        assertEquals(new NativePageLayout.Bounds(7, 146, 162, 76), layout.bounds("computation_player_inventory"));
    }

    @Test
    void resolvesFractionalAndPercentageDimensionsAgainstTheImmediateContainingBlock() {
        var node = new JsonParser()
            .parse("{\"layout\":{\"width\":\"50%\",\"height\":12.4,\"right\":5,\"bottom\":\"10%\"}}")
            .getAsJsonObject();
        assertEquals(new NativePageLayout.Bounds(95, 78, 100, 12), NativePageLayout.bounds(node, 200, 100));
    }
}
