package cn.dancingsnow.neoecoae.crafting.fastpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cache.CraftingGridCache;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public final class ECOFastPathPlannerHook {

    private static final ECOFastPathPatternInspector INSPECTOR = new ECOFastPathPatternInspector();
    private static final ECOFastPathCache CACHE = new ECOFastPathCache();
    private static final Map<RuntimeVerificationKey, RuntimeVerification> VERIFIED = new LinkedHashMap<RuntimeVerificationKey, RuntimeVerification>(
        256,
        0.75F,
        true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<RuntimeVerificationKey, RuntimeVerification> eldest) {
            return this.size() > ECOFastPathConfig.patternCacheSize();
        }
    };

    private ECOFastPathPlannerHook() {}

    public static void verifyIntegrationContract() {
        appeng.api.storage.data.IAEItemStack output = AEItemStack
            .create(new ItemStack(net.minecraft.init.Items.iron_ingot));
        if (!matchesExpectedOutput(new appeng.api.storage.data.IAEItemStack[] { output }, output)
            || matchesExpectedOutput(new appeng.api.storage.data.IAEItemStack[] { output, output }, output)
            || matchesExpectedOutput(
                new appeng.api.storage.data.IAEItemStack[] { output.copy()
                    .setStackSize(2) },
                output))
            throw new IllegalStateException("Fastpath output contract");
        ItemStack encoded = new ItemStack(net.minecraft.init.Items.paper);
        ICraftingPatternDetails pattern = (ICraftingPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
            ICraftingPatternDetails.class.getClassLoader(),
            new Class<?>[] { ICraftingPatternDetails.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getPattern")) return encoded;
                if (method.getName()
                    .equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName()
                    .equals("equals")) return proxy == args[0];
                throw new UnsupportedOperationException(method.getName());
            });
        List<appeng.api.networking.crafting.ICraftingMedium> mediums = new ArrayList<>();
        CraftingGridCache cache = new CraftingGridCache(null) {

            @Override
            public com.google.common.collect.ImmutableMap<appeng.api.storage.data.IAEStack<?>, com.google.common.collect.ImmutableList<ICraftingPatternDetails>> getCraftingMultiPatterns() {
                return com.google.common.collect.ImmutableMap
                    .of(output, com.google.common.collect.ImmutableList.of(pattern));
            }

            @Override
            public List<appeng.api.networking.crafting.ICraftingMedium> getMediums(ICraftingPatternDetails details) {
                return mediums;
            }
        };
        IGrid grid = (IGrid) java.lang.reflect.Proxy.newProxyInstance(
            IGrid.class.getClassLoader(),
            new Class<?>[] { IGrid.class },
            (proxy, method, args) -> cache);
        for (int i = 0; i < 2; i++) {
            mediums.add(
                (appeng.api.networking.crafting.ICraftingMedium) java.lang.reflect.Proxy.newProxyInstance(
                    appeng.api.networking.crafting.ICraftingMedium.class.getClassLoader(),
                    new Class<?>[] { appeng.api.networking.crafting.ICraftingMedium.class },
                    (proxy, method, args) -> false));
            if (hasUniqueProcessingSource(grid, ECOFastPathPatternKey.of(pattern)) != (i == 0))
                throw new IllegalStateException("Fastpath provider cardinality");
        }
        cn.dancingsnow.neoecoae.NeoECOAE.LOG.info(
            "ECO fastpath integration checks PASSED: exact output, multi-output rejection, actual provider cardinality");
    }

    public static ECOFastPathPlan tryPlan(TileECOController controller, ICraftingPatternDetails patternDetails,
        InventoryCrafting table) {
        if (!ECOFastPathConfig.isPlannerHookEnabled()) {
            return ECOFastPathPlan.rejected(ECOFastPathDecision.DISABLED, "planner disabled");
        }
        if (!isEcoCraftingHost(controller)) {
            return ECOFastPathPlan.rejected(ECOFastPathDecision.NOT_ECO_CRAFTING_HOST, "not an ECO crafting host");
        }
        long tick = currentTick(controller.getWorldObj());
        ECOFastPathPatternKey key = ECOFastPathPatternKey.of(patternDetails);
        String negativeReason = CACHE.getNegativeReason(key, tick);
        if (negativeReason != null) {
            return ECOFastPathPlan.rejected(ECOFastPathDecision.CACHE_NEGATIVE, negativeReason);
        }
        ECOFastPathPatternProfile cached = CACHE.getProfile(key);
        if (cached != null) {
            if (!cached.isCraftable() && !ECOFastPathConfig.isProcessingPatternBatchEnabled()) {
                return ECOFastPathPlan.rejected(ECOFastPathDecision.DISABLED, "processing planner disabled");
            }
            return ECOFastPathPlan.accepted(cached);
        }
        try {
            ECOFastPathPatternProfile profile = INSPECTOR.inspect(patternDetails);
            CACHE.putProfile(profile);
            return ECOFastPathPlan.accepted(profile);
        } catch (ECOFastPathPatternException e) {
            CACHE.putNegative(key, e.getMessage(), tick);
            return ECOFastPathPlan.rejected(ECOFastPathDecision.UNSAFE_PATTERN, e.getMessage());
        } catch (RuntimeException e) {
            String reason = e.getClass()
                .getSimpleName();
            CACHE.putNegative(key, reason, tick);
            return ECOFastPathPlan.rejected(ECOFastPathDecision.ERROR, reason);
        }
    }

    public static ECOFastPathPlan tryVerifiedPlan(TileECOController controller, ICraftingPatternDetails patternDetails,
        InventoryCrafting table) {
        return tryVerifiedPlan(controller, patternDetails, table, null);
    }

    public static ECOFastPathPlan tryVerifiedPlan(TileECOController controller, ICraftingPatternDetails patternDetails,
        InventoryCrafting table, IGrid grid) {
        ECOFastPathPlan plan = tryPlan(controller, patternDetails, table);
        if (!plan.accepted()) {
            return plan;
        }
        ECOFastPathPatternProfile profile = plan.getPatternProfile();
        if (profile != null && !profile.isCraftable() && !hasUniqueProcessingSource(grid, profile.getKey())) {
            return ECOFastPathPlan
                .rejected(ECOFastPathDecision.NON_UNIQUE_PROCESSING_SOURCE, "processing pattern source is not unique");
        }
        RuntimeVerificationKey key = RuntimeVerificationKey.of(patternDetails, table, controller.getWorldObj());
        long tick = currentTick(controller.getWorldObj());
        synchronized (VERIFIED) {
            RuntimeVerification verification = VERIFIED.get(key);
            if (verification != null && ECOFastPathCache.negativeExpired(verification.createdTick, tick)) {
                VERIFIED.remove(key);
                verification = null;
            }
            return verification != null && verification.accepted ? plan
                : ECOFastPathPlan.rejected(ECOFastPathDecision.CACHE_NEGATIVE, "pattern not runtime-verified");
        }
    }

    public static void recordRuntimeResult(ICraftingPatternDetails details, InventoryCrafting table, World world,
        ItemStack output) {
        if (details == null || table == null || output == null) {
            return;
        }
        ECOFastPathPatternKey patternKey = ECOFastPathPatternKey.of(details);
        ECOFastPathPatternProfile profile = CACHE.getProfile(patternKey);
        if (profile == null) {
            return;
        }
        RuntimeVerificationKey key = RuntimeVerificationKey.of(details, table, world);
        appeng.api.storage.data.IAEItemStack actual = AEItemStack.create(output);
        appeng.api.storage.data.IAEItemStack[] expected = profile.getOutputs();
        boolean matches = actual != null && matchesExpectedOutput(expected, actual);
        synchronized (VERIFIED) {
            VERIFIED.put(key, new RuntimeVerification(matches, currentTick(world)));
        }
    }

    public static void clearCaches() {
        CACHE.clear();
        synchronized (VERIFIED) {
            VERIFIED.clear();
        }
    }

    public static int profileCacheSize() {
        return CACHE.profileSize();
    }

    public static int negativeCacheSize() {
        return CACHE.negativeSize();
    }

    static boolean matchesExpectedOutput(appeng.api.storage.data.IAEItemStack[] expected,
        appeng.api.storage.data.IAEItemStack actual) {
        if (expected == null || expected.length != 1 || actual == null) {
            return false;
        }
        for (appeng.api.storage.data.IAEItemStack candidate : expected) {
            if (candidate != null && candidate.isSameType(actual)
                && candidate.getStackSize() == actual.getStackSize()) {
                return true;
            }
        }
        return false;
    }

    static boolean hasUniqueProcessingSource(IGrid grid, ECOFastPathPatternKey key) {
        if (grid == null || key == null) {
            return false;
        }
        try {
            CraftingGridCache cache = grid.getCache(CraftingGridCache.class);
            if (cache == null) {
                return false;
            }
            Set<appeng.api.networking.crafting.ICraftingMedium> matches = Collections
                .newSetFromMap(new IdentityHashMap<appeng.api.networking.crafting.ICraftingMedium, Boolean>());
            for (List<ICraftingPatternDetails> patterns : cache.getCraftingMultiPatterns()
                .values()) {
                if (patterns == null) {
                    continue;
                }
                for (ICraftingPatternDetails candidate : patterns) {
                    if (candidate != null && key.equals(ECOFastPathPatternKey.of(candidate))) {
                        matches.addAll(cache.getMediums(candidate));
                    }
                }
            }
            return matches.size() == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isEcoCraftingHost(TileECOController controller) {
        return controller != null && controller.getSubsystem() == ECOControllerSubsystem.CRAFTING
            && controller.isFormed();
    }

    private static long currentTick(World world) {
        return world == null ? 0L : Math.max(0L, world.getTotalWorldTime());
    }

    private static final class RuntimeVerification {

        private final boolean accepted;
        private final long createdTick;

        private RuntimeVerification(boolean accepted, long createdTick) {
            this.accepted = accepted;
            this.createdTick = createdTick;
        }
    }

    private static final class RuntimeVerificationKey {

        private final ECOFastPathPatternKey pattern;
        private final World world;
        private final ICraftingPatternDetails providerPattern;
        private final List<StackSignature> inputs;

        private RuntimeVerificationKey(ECOFastPathPatternKey pattern, World world,
            ICraftingPatternDetails providerPattern, List<StackSignature> inputs) {
            this.pattern = pattern;
            this.world = world;
            this.providerPattern = providerPattern;
            this.inputs = inputs;
        }

        private static RuntimeVerificationKey of(ICraftingPatternDetails details, InventoryCrafting table,
            World world) {
            List<StackSignature> inputs = new ArrayList<StackSignature>(table.getSizeInventory());
            for (int slot = 0; slot < table.getSizeInventory(); slot++) {
                ItemStack stack = table.getStackInSlot(slot);
                if (stack == null) {
                    inputs.add(StackSignature.EMPTY);
                    continue;
                }
                NBTTagCompound tag = stack.getTagCompound();
                inputs.add(
                    new StackSignature(
                        net.minecraft.item.Item.getIdFromItem(stack.getItem()),
                        stack.getItemDamage(),
                        stack.stackSize,
                        tag == null ? null : (NBTTagCompound) tag.copy()));
            }
            return new RuntimeVerificationKey(ECOFastPathPatternKey.of(details), world, details, inputs);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RuntimeVerificationKey)) {
                return false;
            }
            RuntimeVerificationKey that = (RuntimeVerificationKey) other;
            return this.world == that.world && this.providerPattern == that.providerPattern
                && this.inputs.equals(that.inputs)
                && (this.pattern == null ? that.pattern == null : this.pattern.equals(that.pattern));
        }

        @Override
        public int hashCode() {
            int result = this.pattern == null ? 0 : this.pattern.hashCode();
            result = 31 * result + System.identityHashCode(this.world);
            result = 31 * result + System.identityHashCode(this.providerPattern);
            result = 31 * result + this.inputs.hashCode();
            return result;
        }
    }

    private static final class StackSignature {

        private static final StackSignature EMPTY = new StackSignature(0, 0, 0, null);

        private final int itemId;
        private final int damage;
        private final int amount;
        private final NBTTagCompound tag;

        private StackSignature(int itemId, int damage, int amount, NBTTagCompound tag) {
            this.itemId = itemId;
            this.damage = damage;
            this.amount = amount;
            this.tag = tag;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StackSignature)) {
                return false;
            }
            StackSignature that = (StackSignature) other;
            return this.itemId == that.itemId && this.damage == that.damage
                && this.amount == that.amount
                && (this.tag == null ? that.tag == null : this.tag.equals(that.tag));
        }

        @Override
        public int hashCode() {
            int result = this.itemId;
            result = 31 * result + this.damage;
            result = 31 * result + this.amount;
            result = 31 * result + (this.tag == null ? 0 : this.tag.hashCode());
            return result;
        }
    }
}
