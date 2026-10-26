package cn.dancingsnow.neoecoae.api.me;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOParallelProviderAdapter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOStatefulBatchPlanner;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeProviderAdapters;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

public final class ECOFastPathFacade {

    private ECOFastPathFacade() {}

    public static boolean supports(ICraftingProvider provider) {
        return resolveProvider(provider) != null;
    }

    public static ECOFastPathDispatchProvider resolveProvider(ICraftingProvider provider) {
        return NativeProviderAdapters.resolve(provider);
    }

    public static boolean pushPatternForJob(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs,
        @Nullable UUID craftingJobId) {
        return provider instanceof ECOJobAwareCraftingProvider aware ? aware.pushPattern(pattern, inputs, craftingJobId)
            : provider.pushPattern(pattern, inputs);
    }

    @Nullable
    public static PreparedBatch prepare(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs,
        KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory, long maxCrafts, double singlePower,
        IEnergyGrid energy, World level, @Nullable UUID jobId) {
        return prepare(
            provider,
            pattern,
            inputs,
            outputs,
            remainders,
            inventory,
            maxCrafts,
            singlePower,
            energy,
            level,
            jobId,
            false);
    }

    @Nullable
    public static PreparedBatch prepare(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs,
        KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory, long maxCrafts, double singlePower,
        IEnergyGrid energy, World level, @Nullable UUID jobId, boolean exactOrder) {
        if (NativeProviderAdapters.isEnabled(provider)) return null;
        var target = cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeProviderAdapters.resolve(provider);
        if (target == null) return null;
        var batch = ECOStatefulBatchPlanner.prepare(
            target,
            pattern,
            inputs,
            outputs,
            remainders,
            inventory,
            maxCrafts,
            singlePower,
            energy,
            level,
            jobId,
            exactOrder);
        return batch == null ? null : new PreparedBatch(batch, inventory);
    }

    @Nullable
    public static PreparedBatch prepareParallel(ECOParallelCraftingProvider provider, IPatternDetails pattern,
        KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders, ListCraftingInventory inventory, long maxCrafts,
        double singlePower, IEnergyGrid energy, World level, @Nullable UUID jobId) {
        if (provider instanceof ICraftingProvider crafting && NativeProviderAdapters.isEnabled(crafting)) return null;
        var batch = ECOStatefulBatchPlanner.prepare(
            new ECOParallelProviderAdapter(provider),
            pattern,
            inputs,
            outputs,
            remainders,
            inventory,
            maxCrafts,
            singlePower,
            energy,
            level,
            jobId);
        return batch == null ? null : new PreparedBatch(batch, inventory);
    }

    @FunctionalInterface
    public interface EnergyAccount {

        @Nullable
        Reservation reserve(double amount);
    }

    public interface Reservation {

        void commit();

        void refund();
    }

    @Nullable
    public static PreparedBatch prepareAllocated(ICraftingProvider provider, IPatternDetails pattern,
        KeyCounter[] oneCopy, long allocatedCopies, World level, @Nullable UUID jobId) {
        if (!(provider instanceof ECOFastPathDispatchProvider) || allocatedCopies <= 0) return null;
        var execution = ECOExtractedPatternExecution.fromProviderPush(pattern, oneCopy, level);
        if (!execution.canUseFastPath() || !execution.expectedContainerItems()
            .isEmpty()
            || execution.fastPathType()
                != cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier.Type.NORMAL) {
            return null;
        }
        long bounded = Math.min(allocatedCopies, execution.arithmeticBatchLimit());
        if (bounded <= 0) return null;
        var allocated = new ListCraftingInventory(ignored -> {});
        for (var stack : ECOBatchCraftingHelper.multiply(execution.inputItems(), bounded)) {
            allocated.list.add(stack.what(), stack.amount());
        }
        var outputs = new KeyCounter();
        for (var stack : execution.expectedOutputs()) outputs.add(stack.what(), stack.amount());
        return prepare(
            provider,
            pattern,
            oneCopy,
            outputs,
            new KeyCounter(),
            allocated,
            bounded,
            0,
            null,
            level,
            jobId);
    }

    public static @Nullable PreparedExactBatch prepareExactAllocated(ICraftingProvider provider,
        IPatternDetails pattern, KeyCounter[] oneCopy, java.math.BigInteger allocatedCopies, World level, UUID jobId) {
        if (!(provider instanceof ECOFastPathDispatchProvider target) || allocatedCopies.signum() <= 0
            || jobId == null
            || NativeProviderAdapters.isEnabled(provider)) return null;
        var execution = ECOExtractedPatternExecution.fromProviderPush(pattern, oneCopy, level);
        if (!execution.canUseFastPath() || !execution.expectedContainerItems()
            .isEmpty()
            || execution.fastPathType()
                != cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier.Type.NORMAL)
            return null;
        var outputs = new KeyCounter();
        for (var stack : execution.expectedOutputs()) outputs.add(stack.what(), stack.amount());
        var context = cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext
            .create(pattern, oneCopy, outputs, new KeyCounter(), level, jobId);
        var admission = target.eco$prepareExactFastPath(context, allocatedCopies);
        if (admission == null) return null;
        if (admission.craftCount()
            .compareTo(allocatedCopies) > 0)
            throw new IllegalArgumentException("Exact provider exceeded allocated copies");
        return new PreparedExactBatch(admission);
    }

    public static final class PreparedExactBatch {

        private final ECOFastPathDispatchProvider.ExactPreparation admission;
        private boolean submitted;

        private PreparedExactBatch(ECOFastPathDispatchProvider.ExactPreparation admission) {
            this.admission = admission;
        }

        public java.math.BigInteger craftCount() {
            return admission.craftCount();
        }

        public boolean submit() {
            if (submitted) throw new IllegalStateException("Exact batch already submitted");
            submitted = true;
            return admission.dispatch()
                .getAsBoolean();
        }
    }

    public static final class PreparedBatch {

        private final ECOStatefulBatchPlanner.PreparedBatch batch;
        private final ListCraftingInventory inventory;
        private boolean submitted;

        private PreparedBatch(ECOStatefulBatchPlanner.PreparedBatch batch, ListCraftingInventory inventory) {
            this.batch = batch;
            this.inventory = inventory;
        }

        public long craftCount() {
            return batch.craftCount();
        }

        public double power() {
            return batch.power();
        }

        public List<GenericStack> inputTotal() {
            return batch.inputTotal();
        }

        public java.util.Map<cn.dancingsnow.neoecoae.platform.ae2.key.AEKey, java.math.BigInteger> exactInputTotal() {
            return batch.exactInputTotal();
        }

        public List<GenericStack> outputs() {
            return batch.outputs();
        }

        public List<GenericStack> remainders() {
            return batch.remainders();
        }

        /**
         * Single-use, including rejection. False restores inputs and energy. Ordinary provider exceptions
         * propagate after rollback. Indeterminate exceptions retain both resources and require the CPU to
         * stop the job for reconciliation, never fall back. Accounting failures after true cannot undo acceptance.
         */
        public boolean submit(EnergyAccount energy) {
            Objects.requireNonNull(energy);
            if (submitted) throw new IllegalStateException("Batch already submitted");
            submitted = true;
            var reservation = energy.reserve(power());
            if (reservation == null) return false;
            return batch.submit(inventory, reservation);
        }
    }
}
