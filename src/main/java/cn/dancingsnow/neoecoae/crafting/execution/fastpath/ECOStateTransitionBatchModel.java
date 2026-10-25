package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.nbt.ByteTag;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.CustomData;
import cn.dancingsnow.neoecoae.platform.nbt.DoubleTag;
import cn.dancingsnow.neoecoae.platform.nbt.FloatTag;
import cn.dancingsnow.neoecoae.platform.nbt.IntTag;
import cn.dancingsnow.neoecoae.platform.nbt.LongTag;
import cn.dancingsnow.neoecoae.platform.nbt.NumericTag;
import cn.dancingsnow.neoecoae.platform.nbt.ShortTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

/** Linear count/identity state for reusable items whose observed state is not a durability mutation. */
public final class ECOStateTransitionBatchModel implements ECOReusableStateModel {

    private final List<Transition> transitions;
    private final long maxBatchSize;

    private ECOStateTransitionBatchModel(List<Transition> transitions) {
        this.transitions = transitions;
        long max = Long.MAX_VALUE;
        for (Transition transition : transitions) max = Math.min(max, transition.maxCrafts());
        this.maxBatchSize = max;
    }

    public static Optional<ECOStateTransitionBatchModel> analyze(List<ItemStack> before, List<ItemStack> after) {
        if (before.size() != after.size()) return Optional.empty();
        List<Transition> found = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            ItemStack initial = before.get(i);
            ItemStack result = after.get(i);
            if (initial == null || result == null
                || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(initial)
                || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(result)
                || !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(initial, result)) continue;
            boolean unchanged = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks
                .isSameItemSameComponents(initial, result);
            if (initial.isItemStackDamageable() && !unchanged) continue;
            NumericCompoundDelta customDataDelta = null;
            if (!unchanged) {
                customDataDelta = NumericCompoundDelta.analyze(initial, result)
                    .orElse(null);
                if (customDataDelta == null) return Optional.empty();
            }
            long delta = (long) cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(result)
                - cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(initial);
            long maxCrafts = delta >= 0L ? Long.MAX_VALUE
                : cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(initial) / -delta;
            found.add(new Transition(initial.copy(), result.copy(), delta, maxCrafts, customDataDelta));
        }
        return found.isEmpty() ? Optional.empty() : Optional.of(new ECOStateTransitionBatchModel(found));
    }

    @Override
    public long maxBatchSize() {
        return maxBatchSize;
    }

    @Override
    public List<GenericStack> batchInputs(List<GenericStack> ordinaryInputs, long crafts) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : ordinaryInputs) {
            if (!matchesInitial(stack.what())) counter.add(stack.what(), Math.multiplyExact(stack.amount(), crafts));
        }
        for (Transition transition : transitions) {
            GenericStack initial = GenericStack.fromItemStack(
                cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(transition.initial, 1));
            counter
                .add(initial.what(), cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(transition.initial));
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    @Override
    public List<GenericStack> batchRemainders(List<GenericStack> ordinaryRemainders, long crafts) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : ordinaryRemainders) {
            if (!matchesObservedResult(stack.what())) {
                counter.add(stack.what(), Math.multiplyExact(stack.amount(), crafts));
            }
        }
        for (Transition transition : transitions) {
            long finalCount = Math.addExact(
                cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(transition.initial),
                Math.multiplyExact(transition.countDelta, crafts));
            if (finalCount > 0L) {
                ItemStack finalStack = transition.apply(crafts);
                GenericStack result = GenericStack
                    .fromItemStack(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(finalStack, 1));
                counter.add(result.what(), finalCount);
            }
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    @Override
    public boolean requiresSecondStepProof() {
        return transitions.stream()
            .anyMatch(transition -> transition.countDelta != 0L || transition.customDataDelta != null);
    }

    @Override
    public boolean sameTransition(ECOReusableStateModel other) {
        if (!(other instanceof ECOStateTransitionBatchModel state) || transitions.size() != state.transitions.size())
            return false;
        for (int i = 0; i < transitions.size(); i++) {
            Transition left = transitions.get(i);
            Transition right = state.transitions.get(i);
            if ((left.customDataDelta == null) != (right.customDataDelta == null)
                || left.customDataDelta != null && !left.customDataDelta.sameRulesAs(right.customDataDelta)) {
                return false;
            }
            Map<String, NumericDelta> leftDeltas = left.customDataDelta == null
                ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf()
                : left.customDataDelta.deltas;
            Map<String, NumericDelta> rightDeltas = right.customDataDelta == null
                ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf()
                : right.customDataDelta.deltas;
            if (left.countDelta != right.countDelta || !sameRules(leftDeltas, rightDeltas)
                || !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(left.initial, right.initial))
                return false;
        }
        return true;
    }

    private static boolean sameRules(Map<String, NumericDelta> left, Map<String, NumericDelta> right) {
        if (!left.keySet()
            .equals(right.keySet())) return false;
        for (String key : left.keySet()) {
            NumericDelta a = left.get(key);
            NumericDelta b = right.get(key);
            if (a.type != b.type || a.deltaLong != b.deltaLong
                || Double.doubleToLongBits(a.deltaDouble) != Double.doubleToLongBits(b.deltaDouble)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesInitial(AEKey key) {
        return transitions.stream()
            .anyMatch(
                transition -> GenericStack
                    .fromItemStack(
                        cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(transition.initial, 1))
                    .what()
                    .equals(key));
    }

    private boolean matchesObservedResult(AEKey key) {
        return transitions.stream()
            .anyMatch(
                transition -> GenericStack.fromItemStack(
                    cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(transition.observedResult, 1))
                    .what()
                    .equals(key));
    }

    ItemStack applyTransition(int index, long crafts) {
        return transitions.get(index)
            .apply(crafts);
    }

    @com.github.bsideup.jabel.Desugar
    private record Transition(ItemStack initial, ItemStack observedResult, long countDelta, long maxCrafts,
        NumericCompoundDelta customDataDelta) {

        private Transition {
            initial = initial.copy();
            observedResult = observedResult.copy();
        }

        private ItemStack apply(long crafts) {
            ItemStack result = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(initial, 1);
            if (customDataDelta != null) {
                CellComponents.writeCustomData(result, CustomData.of(customDataDelta.apply(crafts)));
            }
            return result;
        }
    }

    /** Preserve unchanged metadata and model only numeric changes in equal-shape compounds. */
    @com.github.bsideup.jabel.Desugar
    private record NumericCompoundDelta(CompoundTag initial, Map<String, NumericDelta> deltas,
        Map<String, NumericCompoundDelta> children) {

        private static Optional<NumericCompoundDelta> analyze(ItemStack initialStack, ItemStack resultStack) {
            ItemStack initialBase = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks
                .copyWithCount(initialStack, 1);
            ItemStack resultBase = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(resultStack, 1);
            CustomData initialData = CellComponents.readCustomDataOrNull(initialBase);
            CustomData resultData = CellComponents.readCustomDataOrNull(resultBase);
            CellComponents.clearCustomData(initialBase);
            CellComponents.clearCustomData(resultBase);
            if (!cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItemSameComponents(initialBase, resultBase)
                || initialData == null
                || resultData == null) return Optional.empty();

            return analyze(initialData.copyTag(), resultData.copyTag());
        }

        private static Optional<NumericCompoundDelta> analyze(CompoundTag before, CompoundTag after) {
            if (!before.getAllKeys()
                .equals(after.getAllKeys())) return Optional.empty();
            Map<String, NumericDelta> deltas = new LinkedHashMap<>();
            Map<String, NumericCompoundDelta> children = new LinkedHashMap<>();
            for (String key : before.getAllKeys()) {
                Tag left = before.get(key);
                Tag right = after.get(key);
                if (java.util.Objects.equals(left, right)) continue;
                if (left instanceof CompoundTag leftCompound && right instanceof CompoundTag rightCompound) {
                    var child = analyze(leftCompound, rightCompound);
                    if (!child.isPresent()) return Optional.empty();
                    children.put(key, child.get());
                    continue;
                }
                if (!(left instanceof NumericTag leftNumber) || !(right instanceof NumericTag rightNumber)
                    || left.getId() != right.getId()) return Optional.empty();
                try {
                    NumericDelta delta = NumericDelta.of(leftNumber, rightNumber);
                    if (!Double.isFinite(delta.initialDouble()) || !Double.isFinite(delta.deltaDouble())) {
                        return Optional.empty();
                    }
                    deltas.put(key, delta);
                } catch (ArithmeticException overflow) {
                    return Optional.empty();
                }
            }
            return Optional.of(new NumericCompoundDelta(before.copy(), deltas, children));
        }

        private CompoundTag apply(long crafts) {
            CompoundTag result = initial.copy();
            for (Map.Entry<String, NumericDelta> entry : deltas.entrySet()) {
                result.put(
                    entry.getKey(),
                    entry.getValue()
                        .apply(crafts));
            }
            for (var entry : children.entrySet()) result.put(
                entry.getKey(),
                entry.getValue()
                    .apply(crafts));
            return result;
        }

        private boolean sameRulesAs(NumericCompoundDelta other) {
            if (!sameRules(deltas, other.deltas) || !children.keySet()
                .equals(other.children.keySet())) return false;
            for (var entry : children.entrySet()) {
                if (!entry.getValue()
                    .sameRulesAs(other.children.get(entry.getKey()))) return false;
            }
            return true;
        }
    }

    static boolean isUnchangedState(CompoundTag before, CompoundTag after) {
        return before.equals(after);
    }

    static Optional<CompoundTag> applyCustomDataTransition(CompoundTag before, CompoundTag after, long crafts) {
        return NumericCompoundDelta.analyze(before, after)
            .map(delta -> delta.apply(crafts));
    }

    @com.github.bsideup.jabel.Desugar
    private record NumericDelta(byte type, long initialLong, long deltaLong, double initialDouble, double deltaDouble) {

        private static NumericDelta of(NumericTag before, NumericTag after) {
            byte type = (byte) before.getId();
            if (type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE) {
                return new NumericDelta(type, 0L, 0L, before.getAsDouble(), after.getAsDouble() - before.getAsDouble());
            }
            long initial = before.getAsLong();
            return new NumericDelta(type, initial, Math.subtractExact(after.getAsLong(), initial), 0.0D, 0.0D);
        }

        private NumericTag apply(long crafts) {
            long integral = type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE ? 0L
                : Math.addExact(initialLong, Math.multiplyExact(deltaLong, crafts));
            double floating = initialDouble + deltaDouble * crafts;
            return switch (type) {
                case Tag.TAG_BYTE -> ByteTag.valueOf((byte) checkedRange(integral, Byte.MIN_VALUE, Byte.MAX_VALUE));
                case Tag.TAG_SHORT -> ShortTag
                    .valueOf((short) checkedRange(integral, Short.MIN_VALUE, Short.MAX_VALUE));
                case Tag.TAG_INT -> IntTag.valueOf((int) checkedRange(integral, Integer.MIN_VALUE, Integer.MAX_VALUE));
                case Tag.TAG_LONG -> LongTag.valueOf(integral);
                case Tag.TAG_FLOAT -> FloatTag.valueOf((float) checkedFinite(floating));
                case Tag.TAG_DOUBLE -> DoubleTag.valueOf(checkedFinite(floating));
                default -> throw new IllegalStateException("Unsupported numeric tag type " + type);
            };
        }

        private static long checkedRange(long value, long min, long max) {
            if (value < min || value > max) throw new ArithmeticException("Linear NBT transition overflow");
            return value;
        }

        private static double checkedFinite(double value) {
            if (!Double.isFinite(value)) throw new ArithmeticException("Linear NBT transition overflow");
            return value;
        }
    }
}
