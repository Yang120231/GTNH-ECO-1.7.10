package cn.dancingsnow.neoecoae.compat.nei;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.github.vfyjxf.nee.nei.NEEPatternTerminalHandler;
import com.github.vfyjxf.nee.nei.NEETerminalOverlayButton;

import appeng.container.implementations.ContainerPatternTerm;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** NEE supplies its overlays and input shortcuts; ECO retains the workstation's exact fluid/item transfer. */
public final class NEEWorkstationSupport {

    public static List<GuiOverlayButton.ItemOverlayState> presence(GuiContainer gui, IRecipeHandler recipe, int index) {
        return NEEPatternTerminalHandler.instance.presenceOverlay(gui, recipe, index);
    }

    public static void captureIngredients(WorkstationRecipeHandler handler, int index) {
        NEEPatternTerminalHandler.ingredients.clear();
        List<PositionedStack> inputs = handler.getIngredientStacks(index);
        for (int i = 0; i < inputs.size(); i++)
            NEEPatternTerminalHandler.ingredients.put(NEEPatternTerminalHandler.INPUT_KEY + i, inputs.get(i));
    }

    @SubscribeEvent
    public void recipeButtons(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        for (int i = 0; i < event.buttonList.size(); i++) {
            var button = event.buttonList.get(i);
            if (button instanceof GuiOverlayButton overlay && !(button instanceof NEETerminalOverlayButton)
                && overlay.handlerRef.handler instanceof WorkstationRecipeHandler
                && overlay.firstGui != null
                && overlay.firstGui.inventorySlots instanceof ContainerPatternTerm)
                event.buttonList.set(i, new NEETerminalOverlayButton(overlay));
        }
    }
}
