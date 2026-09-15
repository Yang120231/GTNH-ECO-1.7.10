package cn.dancingsnow.neoecoae.mixin;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.implementations.GuiCraftConfirm;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOPlanningReportAccess;

@Mixin(value = GuiCraftConfirm.class, remap = false)
public abstract class MixinGuiCraftConfirmReport extends GuiContainer {

    private boolean neoecoae$showReport;
    private int neoecoae$page;

    protected MixinGuiCraftConfirmReport(Container container) {
        super(container);
    }

    @Inject(method = "initGui", at = @At("TAIL"), remap = true)
    private void neoecoae$buttons(CallbackInfo ci) {
        buttonList.add(new GuiButton(19710, 4, 4, 80, 20, "ECO"));
        buttonList.add(new GuiButton(19711, 86, 4, 20, 20, "<"));
        buttonList.add(new GuiButton(19712, 108, 4, 20, 20, ">"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, remap = true)
    private void neoecoae$action(GuiButton button, CallbackInfo ci) {
        if (button.id == 19710) neoecoae$showReport = !neoecoae$showReport;
        else if (button.id == 19711) neoecoae$page = Math.max(0, neoecoae$page - 1);
        else if (button.id == 19712) neoecoae$page++;
        else {
            if (neoecoae$showReport) ci.cancel();
            return;
        }
        ci.cancel();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), remap = true)
    private void neoecoae$report(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!neoecoae$showReport || !(inventorySlots instanceof ECOPlanningReportAccess)) return;
        String json = ((ECOPlanningReportAccess) inventorySlots).neoecoae$getPlanningReport();
        drawRect(4, 28, width - 4, height - 4, 0xFF211F2A);
        if (json.isEmpty()) {
            fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.neoecoae.planner.unavailable"),
                10,
                34,
                0xFFFFFF);
            return;
        }
        com.google.gson.JsonObject report = new com.google.gson.JsonParser().parse(json)
            .getAsJsonObject();
        fontRendererObj.drawString(
            "ECO " + report.get("status")
                .getAsString()
                + " | "
                + report.get("bytes")
                    .getAsLong()
                + " B",
            10,
            34,
            0xFFFFFF);
        if (report.get("fallback")
            .getAsBoolean())
            fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.neoecoae.planner.fallback"),
                10,
                47,
                0xFFAA00);
        com.google.gson.JsonArray steps = report.getAsJsonArray("steps");
        int rows = Math.max(1, (height - 80) / 14);
        neoecoae$page = Math.min(neoecoae$page, Math.max(0, (steps.size() - 1) / rows));
        for (int i = 0; i < rows && neoecoae$page * rows + i < steps.size(); i++) {
            com.google.gson.JsonObject row = steps.get(neoecoae$page * rows + i)
                .getAsJsonObject();
            String text = (neoecoae$page * rows + i + 1) + ". "
                + (row.get("cycle")
                    .getAsBoolean() ? "[C] " : "")
                + row.get("crafts")
                    .getAsLong()
                + " x "
                + row.get("output")
                    .getAsString();
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(text, width - 20), 10, 62 + i * 14, 0xFFFFFF);
        }
        fontRendererObj.drawString(
            (neoecoae$page + 1) + " | "
                + steps.size()
                + "/"
                + report.get("total")
                    .getAsInt(),
            width - 100,
            34,
            0xAAAAAA);
    }
}
