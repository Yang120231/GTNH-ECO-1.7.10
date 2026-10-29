package cn.dancingsnow.neoecoae.api.me.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ECOCraftingLifecycleLinkageTest {

    @Test
    void addonLinkageFailureAfterJobStartDoesNotEscapeOrSkipLaterListeners() {
        // The job is already installed when listeners run; an escaping error would report a failed
        // submission for a running job and invite a duplicate submit.
        ECOCraftingLifecycleListener broken = new ECOCraftingLifecycleListener() {

            @Override
            public void onJobStarted(ECOCraftingJobContext context) {
                throw new NoSuchMethodError("missing addon method");
            }
        };
        AtomicInteger started = new AtomicInteger();
        ECOCraftingLifecycleListener healthy = new ECOCraftingLifecycleListener() {

            @Override
            public void onJobStarted(ECOCraftingJobContext context) {
                started.incrementAndGet();
            }
        };
        ECOCraftingLifecycle.register(broken);
        ECOCraftingLifecycle.register(healthy);
        try {
            assertDoesNotThrow(() -> ECOCraftingLifecycle.fireJobStarted(null));
            assertEquals(1, started.get());
        } finally {
            ECOCraftingLifecycle.unregister(broken);
            ECOCraftingLifecycle.unregister(healthy);
        }
    }
}
