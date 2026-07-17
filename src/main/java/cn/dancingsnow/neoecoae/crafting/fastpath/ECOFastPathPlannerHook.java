package cn.dancingsnow.neoecoae.crafting.fastpath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
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
        ECOFastPathPlan plan = tryPlan(controller, patternDetails, table);
        if (!plan.accepted()) {
            return plan;
        }
        RuntimeVerificationKey key = RuntimeVerificationKey.of(patternDetails, table, controller.getWorldObj());
        long tick = currentTick(controller.getWorldObj());
        synchronized (VERIFIED) {
            RuntimeVerification verification = VERIFIED.get(key);
            if (verification != null && !verification.accepted
                && ECOFastPathCache.negativeExpired(verification.createdTick, tick)) {
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
        boolean matches = actual != null && expected.length == 1
            && expected[0] != null
            && expected[0].isSameType(actual)
            && expected[0].getStackSize() == actual.getStackSize();
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
        private final int dimension;
        private final List<StackSignature> inputs;

        private RuntimeVerificationKey(ECOFastPathPatternKey pattern, int dimension, List<StackSignature> inputs) {
            this.pattern = pattern;
            this.dimension = dimension;
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
            int dimension = world == null || world.provider == null ? 0 : world.provider.dimensionId;
            return new RuntimeVerificationKey(ECOFastPathPatternKey.of(details), dimension, inputs);
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
            return this.dimension == that.dimension && this.inputs.equals(that.inputs)
                && (this.pattern == null ? that.pattern == null : this.pattern.equals(that.pattern));
        }

        @Override
        public int hashCode() {
            int result = this.pattern == null ? 0 : this.pattern.hashCode();
            result = 31 * result + this.dimension;
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
