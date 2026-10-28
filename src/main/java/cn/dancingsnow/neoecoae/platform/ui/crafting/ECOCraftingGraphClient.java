package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.lang.ref.WeakReference;

import net.minecraft.inventory.Container;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftingGraphPackets;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftingGraphStream;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client store of the crafting graph: one request per (window, report version), and the last graph or status the
 * server answered. Chunks arrive on the network thread; the screen polls {@link #get} on the render thread.
 */
@SideOnly(Side.CLIENT)
public final class ECOCraftingGraphClient {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");
    private static final ECOCraftingGraphStream.Assembler ASSEMBLER = new ECOCraftingGraphStream.Assembler();
    private static WeakReference<Container> requestedContainer = new WeakReference<>(null);
    private static int requestedWindow = Integer.MIN_VALUE;
    private static long requestedVersion = Long.MIN_VALUE;
    private static volatile Result latest;

    private ECOCraftingGraphClient() {}

    /** A server answer for one plan. {@code snapshot} is set only for {@link ECOCraftingGraphStream.Status#READY}. */
    public static final class Result {

        final Container container;
        public final int windowId;
        public final long version;
        public final ECOCraftingGraphStream.Status status;
        @Nullable
        public final CraftingGraphSnapshot snapshot;

        Result(Container container, int windowId, long version, ECOCraftingGraphStream.Status status,
            @Nullable CraftingGraphSnapshot snapshot) {
            this.container = container;
            this.windowId = windowId;
            this.version = version;
            this.status = status;
            this.snapshot = snapshot;
        }
    }

    /** The answer for this plan, or {@code null} while it is on its way; asks the server once per plan. */
    public static @Nullable Result get(Container container, long version) {
        int windowId = container.windowId;
        Result result = latest;
        if (result != null && result.container == container && result.version == version) return result;
        synchronized (ASSEMBLER) {
            if (requestedContainer.get() != container || requestedWindow != windowId || requestedVersion != version) {
                requestedContainer = new WeakReference<>(container);
                requestedWindow = windowId;
                requestedVersion = version;
                ASSEMBLER.reset();
                ECOCraftingGraphPackets.request(windowId, version);
            }
        }
        return null;
    }

    /** Network thread. */
    public static void accept(ECOCraftingGraphPackets.Chunk chunk) {
        synchronized (ASSEMBLER) {
            // Only the plan last asked for is assembled; late chunks of an older request are dropped.
            Container container = requestedContainer.get();
            if (container == null || chunk.windowId != requestedWindow || chunk.version != requestedVersion) return;
            if (chunk.status != ECOCraftingGraphStream.Status.READY) {
                ASSEMBLER.reset();
                latest = new Result(container, chunk.windowId, chunk.version, chunk.status, null);
                return;
            }
            try {
                byte[] complete = ASSEMBLER.accept(chunk.transfer, chunk.total, chunk.offset, chunk.bytes);
                if (complete == null) return;
                CraftingGraphSnapshot snapshot = ECOCraftingGraphStream.decode(complete);
                latest = new Result(
                    container,
                    chunk.windowId,
                    chunk.version,
                    ECOCraftingGraphStream.Status.READY,
                    snapshot);
            } catch (RuntimeException failure) {
                LOGGER.warn("[craft-graph] Received an unreadable crafting graph", failure);
                ASSEMBLER.reset();
                latest = new Result(
                    container,
                    chunk.windowId,
                    chunk.version,
                    ECOCraftingGraphStream.Status.FAILED,
                    null);
            }
        }
    }

    /** Forget the held graph and any pending request (the next {@link #get} asks again). */
    public static void clear() {
        synchronized (ASSEMBLER) {
            ASSEMBLER.reset();
            requestedContainer = new WeakReference<>(null);
            requestedWindow = Integer.MIN_VALUE;
            requestedVersion = Long.MIN_VALUE;
            latest = null;
        }
    }
}
