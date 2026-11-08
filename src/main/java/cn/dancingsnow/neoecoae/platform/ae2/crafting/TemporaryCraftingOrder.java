package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A request-local collection pattern. Its display item is never inserted into an inventory or a pattern provider. */
public final class TemporaryCraftingOrder extends Item {

    public static final TemporaryCraftingOrder INSTANCE = new TemporaryCraftingOrder();
    public static final int MAX_INGREDIENTS = 256;

    private TemporaryCraftingOrder() {
        setMaxStackSize(1);
        setUnlocalizedName("neoecoae.temporary_crafting_order");
        setTextureName("minecraft:paper");
    }

    public static boolean isOrder(AEKey key) {
        return key instanceof AEItemKey item && item.getItem() == INSTANCE;
    }

    /** Repeated recipe slots become one exact request while preserving the recipe's ingredient order. */
    public static List<GenericStack> normalize(List<GenericStack> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > MAX_INGREDIENTS)
            throw new IllegalArgumentException("Invalid temporary order size");
        Map<AEKey, Long> merged = new LinkedHashMap<>();
        for (GenericStack stack : requests) {
            if (stack == null || stack.amount() <= 0 || isOrder(stack.what()))
                throw new IllegalArgumentException("Invalid temporary order ingredient");
            merged.merge(stack.what(), stack.amount(), Math::addExact);
        }
        List<GenericStack> result = new ArrayList<>(merged.size());
        merged.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
        return Collections.unmodifiableList(result);
    }

    public static AEItemKey create(List<GenericStack> requests, KeyCounter stock) {
        CompoundTag tag = new CompoundTag();
        tag.putString(
            "order",
            UUID.randomUUID()
                .toString());
        ListTag ingredients = new ListTag();
        for (GenericStack requested : normalize(requests)) {
            CompoundTag entry = GenericStack.writeTag(RegistryAccess.EMPTY, requested);
            // NEE requests additional material. Collecting only the shortage would let the planner satisfy it
            // from existing stock without producing anything. Return that stock together with the new material.
            entry.putLong("collect", Math.addExact(requested.amount(), Math.max(0L, stock.get(requested.what()))));
            ingredients.add(entry);
        }
        tag.put("ingredients", ingredients);
        ItemStack item = new ItemStack(INSTANCE);
        item.setTagCompound(tag.nativeTag());
        return AEItemKey.of(item);
    }

    public static Pattern decode(AEKey key) {
        if (!isOrder(key)) return null;
        ItemStack item = ((AEItemKey) key).toStack();
        if (!item.hasTagCompound()) return null;
        CompoundTag tag = new CompoundTag(item.getTagCompound());
        ListTag encoded = tag.getList("ingredients", Tag.TAG_COMPOUND);
        if (tag.getString("order")
            .isEmpty() || encoded.isEmpty()
            || encoded.size() > MAX_INGREDIENTS) return null;
        List<GenericStack> requested = new ArrayList<>(), collected = new ArrayList<>();
        java.util.Set<AEKey> seen = new java.util.HashSet<>();
        for (int i = 0; i < encoded.size(); i++) {
            CompoundTag entry = encoded.getCompound(i);
            GenericStack stack = GenericStack.readTag(RegistryAccess.EMPTY, entry);
            long total = entry.getLong("collect");
            if (stack == null || stack.amount() <= 0
                || total < stack.amount()
                || isOrder(stack.what())
                || !seen.add(stack.what())) return null;
            requested.add(stack);
            collected.add(new GenericStack(stack.what(), total));
        }
        return new Pattern((AEItemKey) key, requested, collected);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        Pattern order = decode(AEItemKey.of(stack));
        if (order != null) for (GenericStack request : order.requested) lines.add(
            request.what()
                .getDisplayName() + " × "
                + request.amount());
    }

    /** Only the owning ECO job executes this terminal collection step; decoding also supports saved jobs. */
    public static final class Pattern implements IPatternDetails {

        private final AEItemKey definition;
        private final List<GenericStack> requested, collected;
        private final IInput[] inputs;

        private Pattern(AEItemKey definition, List<GenericStack> requested, List<GenericStack> collected) {
            this.definition = definition;
            this.requested = Collections.unmodifiableList(requested);
            this.collected = Collections.unmodifiableList(collected);
            inputs = new IInput[collected.size()];
            for (int i = 0; i < inputs.length; i++) {
                GenericStack stack = collected.get(i);
                inputs[i] = new IInput() {

                    public GenericStack[] getPossibleInputs() {
                        return new GenericStack[] { stack };
                    }

                    public long getMultiplier() {
                        return 1;
                    }

                    public AEKey getRemainingKey(AEKey input) {
                        return null;
                    }
                };
            }
        }

        public List<GenericStack> collected() {
            return collected;
        }

        public AEItemKey getDefinition() {
            return definition;
        }

        public IInput[] getInputs() {
            return inputs.clone();
        }

        public List<GenericStack> getOutputs() {
            return Collections.singletonList(new GenericStack(definition, 1));
        }
    }
}
