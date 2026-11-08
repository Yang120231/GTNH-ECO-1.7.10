package cn.dancingsnow.neoecoae.compat.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;

import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.TemporaryCraftingOrder;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** One outstanding handoff, released on reply, menu closure or disconnect. Fallback runs only in its original menu. */
@SideOnly(Side.CLIENT)
public final class NEETemporaryCraftingClient {

    public interface PreviewLifecycle {

        void neoecoae$cancelPreview();
    }

    private static long sequence;
    private static Pending pending;
    private static volatile Reply reply;

    @com.github.bsideup.jabel.Desugar
    private record Pending(long sequence, World world, Container container, Runnable fallback) {}

    @com.github.bsideup.jabel.Desugar
    private record Reply(long sequence, boolean accepted) {}

    public static boolean request(List<IAEStack<?>> ingredients, boolean autoStart, Runnable fallback) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || ingredients.size() < 2) return false;
        List<GenericStack> requests = new ArrayList<>();
        for (var stack : ingredients) {
            AEKey key = stack == null ? null : AEKey.fromNative(stack);
            if (key == null || stack.getStackSize() <= 0) return false;
            requests.add(new GenericStack(key, stack.getStackSize()));
        }
        requests = TemporaryCraftingOrder.normalize(requests);
        if (requests.size() < 2) return false;
        pending = new Pending(++sequence, mc.theWorld, mc.thePlayer.openContainer, fallback);
        reply = null;
        ECOCraftConfirmPackets.requestIngredients(pending.container.windowId, pending.sequence, requests, autoStart);
        return true;
    }

    public static void accept(long sequence, boolean accepted) {
        reply = new Reply(sequence, accepted);
    }

    public static void clear() {
        pending = null;
        reply = null;
    }

    public static void cancelPreview() {
        clear();
        if (Loader.isModLoaded("neenergistics"))
            ((PreviewLifecycle) (Object) com.github.vfyjxf.nee.nei.NEECraftingPreviewHandler.instance)
                .neoecoae$cancelPreview();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        Pending request = pending;
        Reply response = reply;
        if (mc.thePlayer == null || mc.theWorld != request.world || mc.thePlayer.openContainer != request.container) {
            clear();
        } else if (response != null && response.sequence == request.sequence) {
            clear();
            if (!response.accepted) request.fallback.run();
        }
    }
}
