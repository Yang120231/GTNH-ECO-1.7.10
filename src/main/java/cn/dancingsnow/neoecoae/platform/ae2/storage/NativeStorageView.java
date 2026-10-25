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
        KeyCounter visible = storage.getAvailableStacks();
        for (var entry : visible)
            if (accepts(entry.getKey())) cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator
                .add(out, entry.getKey(), entry.getLongValue());
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
        for (var entry : getAvailableStacks()) if (entry.getLongValue() > 0) NativeStackAmounts.add(
            out,
            entry.getKey()
                .toNative(entry.getLongValue()));
        return out;
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
