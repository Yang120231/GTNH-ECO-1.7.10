package cn.dancingsnow.neoecoae.platform.ae2.key;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.item.Item;
import net.minecraftforge.fluids.Fluid;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuseSlot;

/** Bounded identity routing, registered-ID fallback and unboxed metadata slots. */
final class PlainKeySlots {
    private static volatile AtomicReferenceArray<ItemSlots> items = new AtomicReferenceArray<>(4096);
    private static volatile AtomicReferenceArray<FluidSlot> fluids = new AtomicReferenceArray<>(1024);
    private static volatile ItemRoutes itemRoutes = new ItemRoutes(null);
    private static final int MAX_CACHED_ID = 1 << 20;

    private PlainKeySlots() {}

    static KeyReuseSlot item(Item item, int metadata, Object epoch) {
        // Keep raw metadata semantics: unusual values fall back, never mask/truncate into another variant.
        if (metadata < 0 || metadata > 65535) return null;
        ItemRoutes routes = itemRoutes;
        if (routes.epoch != epoch) routes = resetItemRoutes(epoch);
        int route = System.identityHashCode(item) & 4095;
        ItemSlots cached = routes.slots.get(route);
        if (cached != null && cached.item == item) return cached.metadata(metadata);
        int id = Item.getIdFromItem(item);
        if (id < 0 || id >= MAX_CACHED_ID) return null;
        AtomicReferenceArray<ItemSlots> table = items;
        ItemSlots slots = id < table.length() ? table.get(id) : null;
        if (slots == null || slots.item != item) slots = createItem(item, id);
        if (slots == null) return null;
        routes.slots.set(route, slots);
        return slots.metadata(metadata);
    }

    private static synchronized ItemRoutes resetItemRoutes(Object epoch) {
        ItemRoutes routes = itemRoutes;
        if (routes.epoch == epoch) return routes;
        routes = new ItemRoutes(epoch);
        // A creator crossing a reload cannot replace the current generation's routing table.
        if (epoch == cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse.epoch()) itemRoutes = routes;
        return routes;
    }

    private static final class ItemRoutes {
        final Object epoch;
        // Bound routing memory independently of item metadata; collisions use the registered-ID table.
        final AtomicReferenceArray<ItemSlots> slots = new AtomicReferenceArray<>(4096);
        ItemRoutes(Object epoch) { this.epoch = epoch; }
    }

    private static synchronized ItemSlots createItem(Item item, int id) {
        if (Item.getItemById(id) != item) return null;
        AtomicReferenceArray<ItemSlots> table = items;
        if (id >= table.length()) {
            AtomicReferenceArray<ItemSlots> grown = new AtomicReferenceArray<>(Math.max(id + 1, table.length() * 2));
            for (int i = 0; i < table.length(); i++) grown.set(i, table.get(i));
            items = table = grown;
        }
        ItemSlots slots = table.get(id);
        if (slots == null || slots.item != item) table.set(id, slots = new ItemSlots(item));
        return slots;
    }

    static KeyReuseSlot fluid(Fluid fluid) {
        int id = fluid.getID();
        if (id < 0 || id >= MAX_CACHED_ID) return null;
        AtomicReferenceArray<FluidSlot> table = fluids;
        FluidSlot slot = id < table.length() ? table.get(id) : null;
        return slot != null && slot.fluid == fluid ? slot.key : createFluid(fluid, id);
    }

    private static synchronized KeyReuseSlot createFluid(Fluid fluid, int id) {
        if (net.minecraftforge.fluids.FluidRegistry.getFluid(id) != fluid) return null;
        AtomicReferenceArray<FluidSlot> table = fluids;
        if (id >= table.length()) {
            AtomicReferenceArray<FluidSlot> grown = new AtomicReferenceArray<>(Math.max(id + 1, table.length() * 2));
            for (int i = 0; i < table.length(); i++) grown.set(i, table.get(i));
            fluids = table = grown;
        }
        FluidSlot slot = table.get(id);
        if (slot == null || slot.fluid != fluid) table.set(id, slot = new FluidSlot(fluid));
        return slot.key;
    }

    private static final class FluidSlot {
        final Fluid fluid;
        final KeyReuseSlot key = new KeyReuseSlot();
        FluidSlot(Fluid fluid) { this.fluid = fluid; }
    }

    private static final class ItemSlots {
        final Item item;
        final KeyReuseSlot zero = new KeyReuseSlot();
        private volatile AtomicReferenceArray<AtomicReferenceArray<KeyReuseSlot>> groups;

        ItemSlots(Item item) { this.item = item; }

        KeyReuseSlot metadata(int metadata) {
            if (metadata == 0) return zero;
            AtomicReferenceArray<AtomicReferenceArray<KeyReuseSlot>> table = groups;
            AtomicReferenceArray<KeyReuseSlot> group = table == null ? null : table.get(metadata >>> 8);
            KeyReuseSlot slot = group == null ? null : group.get(metadata & 255);
            return slot == null ? createMetadata(metadata) : slot;
        }

        private synchronized KeyReuseSlot createMetadata(int metadata) {
            if (groups == null) groups = new AtomicReferenceArray<>(256);
            AtomicReferenceArray<KeyReuseSlot> group = groups.get(metadata >>> 8);
            if (group == null) groups.set(metadata >>> 8, group = new AtomicReferenceArray<>(256));
            KeyReuseSlot slot = group.get(metadata & 255);
            if (slot == null) group.set(metadata & 255, slot = new KeyReuseSlot());
            return slot;
        }
    }
}
