package cn.dancingsnow.neoecoae.crafting.runtime;

/** Bridges the active ECO CPU job across AE2's synchronous medium push call. */
public final class ECOCraftingExecutionContext {

    private static final ThreadLocal<String> CURRENT_JOB = new ThreadLocal<String>();
    private static final ThreadLocal<ECOCraftingBatchCoordinator> CURRENT_COORDINATOR = new ThreadLocal<ECOCraftingBatchCoordinator>();

    private ECOCraftingExecutionContext() {}

    public static Scope enter(String craftingJobId) {
        return enter(craftingJobId, null);
    }

    public static Scope enter(String craftingJobId, ECOCraftingBatchCoordinator coordinator) {
        String previous = CURRENT_JOB.get();
        ECOCraftingBatchCoordinator previousCoordinator = CURRENT_COORDINATOR.get();
        if (craftingJobId == null || craftingJobId.length() == 0) {
            CURRENT_JOB.remove();
        } else {
            CURRENT_JOB.set(craftingJobId);
        }
        if (coordinator == null) {
            CURRENT_COORDINATOR.remove();
        } else {
            CURRENT_COORDINATOR.set(coordinator);
        }
        return new Scope(previous, previousCoordinator);
    }

    public static String currentJobId() {
        return CURRENT_JOB.get();
    }

    public static ECOCraftingBatchCoordinator currentBatchCoordinator() {
        return CURRENT_COORDINATOR.get();
    }

    public static final class Scope implements AutoCloseable {

        private final String previous;
        private final ECOCraftingBatchCoordinator previousCoordinator;
        private boolean closed;

        private Scope(String previous, ECOCraftingBatchCoordinator previousCoordinator) {
            this.previous = previous;
            this.previousCoordinator = previousCoordinator;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.previous == null) {
                CURRENT_JOB.remove();
            } else {
                CURRENT_JOB.set(this.previous);
            }
            if (this.previousCoordinator == null) {
                CURRENT_COORDINATOR.remove();
            } else {
                CURRENT_COORDINATOR.set(this.previousCoordinator);
            }
        }
    }
}
