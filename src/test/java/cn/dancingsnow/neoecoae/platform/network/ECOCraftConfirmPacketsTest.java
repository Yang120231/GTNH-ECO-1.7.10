package cn.dancingsnow.neoecoae.platform.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class ECOCraftConfirmPacketsTest {

    @Test
    void bigOrderStartCarriesOnlyTheWindowAndTheForceFlag() {
        for (boolean forced : new boolean[] { false, true }) {
            ByteBuf buffer = Unpooled.buffer();
            new ECOCraftConfirmPackets.BigOrderStart(42, forced).toBytes(buffer);
            var decoded = new ECOCraftConfirmPackets.BigOrderStart();
            decoded.fromBytes(buffer);
            assertEquals(42, decoded.windowId);
            assertEquals(forced, decoded.forced);
            assertFalse(buffer.isReadable(), "The server resolves plan, admission and CPU itself");
        }
    }

    @Test
    void plainStartKeepsItsOwnEncoding() {
        ByteBuf buffer = Unpooled.buffer();
        new ECOCraftConfirmPackets.Start(-7, true).toBytes(buffer);
        var decoded = new ECOCraftConfirmPackets.Start();
        decoded.fromBytes(buffer);
        assertEquals(-7, decoded.windowId);
        assertTrue(decoded.forced);
        assertFalse(buffer.isReadable());
    }
}
