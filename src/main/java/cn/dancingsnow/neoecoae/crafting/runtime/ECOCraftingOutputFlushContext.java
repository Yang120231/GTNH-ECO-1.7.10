package cn.dancingsnow.neoecoae.crafting.runtime;

/**
 * Marks the server thread while a worker is routing crafting outputs.
 *
 * AE2 completes a job synchronously while accepting the final output stack. A worker may still be
 * routing outputs from other FX cores later in the same tick, so the CPU must defer completion
 * until its next update instead of releasing the crafting link from inside the injection call.
 */
public final class ECOCraftingOutputFlushContext {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<Integer>();

    private ECOCraftingOutputFlushContext() {}

    public static Scope enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(Integer.valueOf(depth == null ? 1 : depth.intValue() + 1));
        return new Scope();
    }

    public static boolean isActive() {
        Integer depth = DEPTH.get();
        return depth != null && depth.intValue() > 0;
    }

    private static void exit() {
        Integer depth = DEPTH.get();
        if (depth == null || depth.intValue() <= 0) {
            return;
        }
        if (depth.intValue() == 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(Integer.valueOf(depth.intValue() - 1));
        }
    }

    public static final class Scope implements AutoCloseable {

        private boolean closed;

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            exit();
        }
    }
}
