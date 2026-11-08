package cn.dancingsnow.neoecoae.compat.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;

import cn.dancingsnow.neoecoae.platform.ui.NativeUiAccess;
import cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageSession;
import codechicken.nei.BookmarkPanel;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.api.IBookmarkContainerHandler;
import codechicken.nei.recipe.chain.RecipeChainMath;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** NEI keeps its recipe-chain and player-inventory calculations; ECO supplies only storage access. */
@SideOnly(Side.CLIENT)
public final class NEIBookmarkSupport implements IBookmarkContainerHandler {

    public static final NEIBookmarkSupport INSTANCE = new NEIBookmarkSupport();
    private static Pending pending;
    private static Container resuming;

    @com.github.bsideup.jabel.Desugar
    private record Pending(Container container, NativeStorageSession session, int sequence, BookmarkPanel panel,
        RecipeChainMath math, boolean shift) {}

    private NEIBookmarkSupport() {}

    public static NativeStorageSession session(GuiContainer gui) {
        if (gui == null || !(gui.inventorySlots instanceof ModularContainer container) || !container.isInitialized())
            return null;
        var sync = container.getSyncManager()
            .getMainPSM();
        return sync != null
            && sync.findSyncHandlerNullable("storage.snapshot", 0) instanceof NativeStorageSession session ? session
                : null;
    }

    public static boolean deferPull(BookmarkPanel panel, RecipeChainMath math, boolean shift) {
        GuiContainer gui = NEIClientUtils.getGuiContainer();
        NativeStorageSession session = session(gui);
        if (session == null || math == null || math.outputRecipes.isEmpty() || resuming == gui.inventorySlots)
            return false;
        List<ItemStack> keys = new ArrayList<>();
        for (var items : java.util.Arrays.asList(math.initialItems, math.recipeIngredients, math.recipeResults))
            for (var item : items) keys.add(item.itemStack);
        keys.addAll(math.containerItemsCrafting);
        keys.addAll(math.containerItemsInventory);
        pending = new Pending(gui.inventorySlots, session, session.queryBookmarkInventory(keys), panel, math, shift);
        return true;
    }

    @Override
    public List<ItemStack> getStorageStacks(GuiContainer gui) {
        NativeStorageSession session = session(gui);
        return session == null ? Collections.emptyList() : session.bookmarkInventory();
    }

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer gui, ArrayList<ItemStack> items) {
        NativeStorageSession session = session(gui);
        if (session != null) session.requestBookmarks(items);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Pending request = pending;
        if (NativeUiAccess.clientContainer() != request.container) {
            pending = null;
            return;
        }
        if (!request.session.bookmarkInventoryReady(request.sequence)) return;
        pending = null;
        resuming = request.container;
        try {
            request.panel.pullBookmarkItems(request.math, request.shift);
        } finally {
            resuming = null;
        }
    }
}
