package cn.dancingsnow.neoecoae.platform.ae2.storage;

import java.math.BigInteger;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** One native channel/priority projection of an imported inventory, preserving the original security source. */
public final class NativeStorageView<T extends IAEStack>
    implements IMEInventoryHandler<T>, MEStorage, DelegatingStorage, ECOBigIntegerStorage {

    private final MEStorage storage;
    private final StorageChannel channel;
    private final int priority;

    public NativeStorageView(MEStorage storage, StorageChannel channel, int priority) {
        this.storage = java.util.Objects.requireNonNull(storage);
        this.channel = channel;
        this.priority = priority;
    }

    private boolean accepts(AEKey key) {
        return key != null && key.getType()
            .nativeChannel() == channel;
    }

    @Override
    public MEStorage neoecoae$getDelegate() {
        return storage;
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return accepts(key) && amount > 0 ? storage.insert(key, amount, mode, source) : 0;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return accepts(key) && amount > 0 ? storage.extract(key, amount, mode, source) : 0;
    }

    @Override
    public BigInteger insertBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        return accepts(key) ? ECOBigIntegerStorage.insert(storage, key, amount, mode, source) : BigInteger.ZERO;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        visitAvailableStacks(
            (key, amount) -> cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator.add(out, key, amount));
    }

    @Override
    public long getAvailableAmount(AEKey key) {
        return accepts(key) ? storage.getAvailableAmount(key) : 0;
    }

    @Override
    public void visitAvailableStacks(java.util.function.ObjLongConsumer<AEKey> visitor) {
        storage.visitAvailableStacks((key, amount) -> { if (accepts(key)) visitor.accept(key, amount); });
    }

    @Override
    public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return accepts(key) && storage.isPreferredStorageFor(key, source);
    }

    @Override
    public Component getDescription() {
        return storage.getDescription();
    }

    @Override
    public T injectItems(T input, Actionable mode, BaseActionSource source) {
        if (input == null) return null;
        long offered = input.getStackSize();
        if (offered <= 0) return input;
        long inserted = insert(AEKey.fromNative(input), offered, mode, IActionSource.of(source));
        if (inserted < 0 || inserted > offered)
            throw new IllegalStateException("Invalid imported insertion: " + inserted + " / " + offered);
        return inserted == offered ? null
            : (T) input.copy()
                .setStackSize(offered - inserted);
    }

    @Override
    public T extractItems(T input, Actionable mode, BaseActionSource source) {
        if (input == null || input.getStackSize() <= 0) return null;
        long extracted = extract(AEKey.fromNative(input), input.getStackSize(), mode, IActionSource.of(source));
        if (extracted < 0 || extracted > input.getStackSize())
            throw new IllegalStateException("Invalid imported extraction: " + extracted);
        return extracted == 0 ? null
            : (T) input.copy()
                .setStackSize(extracted);
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out, int iteration) {
        visitAvailableStacks((key, amount) -> { if (amount > 0) NativeStackAmounts.add(out, key.toNative(amount)); });
        return out;
    }

    /** rv3's default lists everything into a scratch list and filters it; this filters while visiting. */
    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out, int iteration,
        java.util.Optional<java.util.function.Predicate<T>> filter) {
        if (!filter.isPresent()) return getAvailableItems(out, iteration);
        java.util.function.Predicate<T> predicate = filter.get();
        visitAvailableStacks((key, amount) -> {
            if (amount <= 0) return;
            T stack = (T) key.toNative(amount);
            if (predicate.test(stack)) NativeStackAmounts.add(out, stack);
        });
        return out;
    }

    /** rv3's default runs a filtered full listing per query; the imported storages answer from their index. */
    @Override
    public T getAvailableItem(T request, int iteration) {
        AEKey key = request == null ? null : AEKey.fromNative(request);
        long amount = getAvailableAmount(key);
        return amount > 0 ? (T) key.toNative(amount) : null;
    }

    @Override
    public StorageChannel getChannel() {
        return channel;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        return input != null && isPreferredStorageFor(AEKey.fromNative(input), IActionSource.empty());
    }

    @Override
    public boolean canAccept(T input) {
        return input != null && insert(AEKey.fromNative(input), 1, Actionable.SIMULATE, IActionSource.empty()) == 1;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return pass == 1 || pass == 2;
    }
}
