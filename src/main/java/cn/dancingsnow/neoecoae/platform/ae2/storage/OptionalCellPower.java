package cn.dancingsnow.neoecoae.platform.ae2.storage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;

/** Optional native integrations register their real cell identity and grid surcharge here. */
public final class OptionalCellPower {

    public static final OptionalCellPower INSTANCE = new OptionalCellPower();
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private OptionalCellPower() {}

    public static void register(Predicate<ItemStack> cells, ToDoubleFunction<NEBlockEntity<?, ?>> surcharge) {
        PROVIDERS.add(new Provider(cells, surcharge));
    }

    public static boolean isSingularity(ItemStack stack) {
        return PROVIDERS.stream()
            .anyMatch(provider -> provider.cells.test(stack));
    }

    public double getAdditionalIdleDrain(NEBlockEntity<?, ?> host) {
        return PROVIDERS.stream()
            .mapToDouble(provider -> provider.surcharge.applyAsDouble(host))
            .sum();
    }

    private static final class Provider {

        final Predicate<ItemStack> cells;
        final ToDoubleFunction<NEBlockEntity<?, ?>> surcharge;

        Provider(Predicate<ItemStack> cells, ToDoubleFunction<NEBlockEntity<?, ?>> surcharge) {
            this.cells = cells;
            this.surcharge = surcharge;
        }
    }
}
