package cn.dancingsnow.neoecoae.client;

import java.util.Map;

import net.minecraft.client.gui.GuiScreen;

import cn.dancingsnow.neoecoae.crafting.planner.ECOItemKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlan;

/** Lightweight 1.7.10 planner report screen. The renderer is intentionally independent of AE2 internals. */
public final class ECOPlannerScreen extends GuiScreen {

    private final GuiScreen parent;
    private final ECOPlan plan;
    private int scroll;

    public ECOPlannerScreen(GuiScreen parent, ECOPlan plan) {
        this.parent = parent;
        this.plan = plan;
    }

    @Override
    public void initGui() {
        scroll = 0;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "ECO Planner", width / 2, 12, 0xFFFFFF);
        drawString(
            fontRendererObj,
            plan != null && plan.success() ? "Plan available" : "Missing inputs",
            12,
            32,
            0xD0D0D0);
        if (plan != null) {
            int y = 52 - scroll;
            y = drawEntries("Crafts", plan.crafts(), y, 0x80FF80);
            drawEntries("Missing", plan.missing(), y + 10, 0xFF8080);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int drawEntries(String title, Map<ECOItemKey, Long> entries, int y, int color) {
        drawString(fontRendererObj, title, 12, y, color);
        y += 14;
        for (Map.Entry<ECOItemKey, Long> e : entries.entrySet()) {
            if (y > 35 && y < height - 12)
                drawString(fontRendererObj, e.getKey() + " x" + e.getValue(), 24, y, 0xFFFFFF);
            y += 12;
        }
        return y;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (button == 0 && y < 28) mc.displayGuiScreen(parent);
        else super.mouseClicked(x, y, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) scroll = Math.max(0, scroll - (wheel > 0 ? 24 : -24));
    }
}
