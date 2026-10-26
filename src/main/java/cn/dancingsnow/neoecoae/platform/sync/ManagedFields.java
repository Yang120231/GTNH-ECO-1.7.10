package cn.dancingsnow.neoecoae.platform.sync;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Explicit codecs for the field types used by imported entities; unsupported data fails visibly. */
public final class ManagedFields {

    private static final ConcurrentHashMap<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private ManagedFields() {}

    private static List<Field> fields(Class<?> type) {
        return FIELDS.computeIfAbsent(type, key -> {
            List<Field> result = new ArrayList<>();
            for (Class<?> cursor = key; cursor != null; cursor = cursor.getSuperclass())
                for (Field field : cursor.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || (!field.isAnnotationPresent(Persisted.class)
                        && !field.isAnnotationPresent(DescSynced.class))) continue;
                    field.setAccessible(true);
                    result.add(field);
                }
            result.sort(java.util.Comparator.comparing(Field::getName));
            return java.util.Collections.unmodifiableList(result);
        });
    }

    private static String key(Field field, boolean description) {
        Persisted persisted = field.getAnnotation(Persisted.class);
        return !description && persisted != null
            && !persisted.key()
                .isEmpty() ? persisted.key() : field.getName();
    }

    public static CompoundTag write(Object owner, boolean description) {
        CompoundTag result = new CompoundTag();
        for (Field field : fields(owner.getClass())) {
            if (!(description ? field.isAnnotationPresent(DescSynced.class)
                : field.isAnnotationPresent(Persisted.class))) continue;
            try {
                CompoundTag value = encode(field.get(owner));
                String key = key(field, description);
                if (result.contains(key)) throw new IllegalStateException("Duplicate managed field: " + key);
                result.put(key, value);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot save managed field " + field, failure);
            }
        }
        return result;
    }

    public static void read(Object owner, CompoundTag values, boolean description) {
        for (Field field : fields(owner.getClass())) {
            if (!(description ? field.isAnnotationPresent(DescSynced.class)
                : field.isAnnotationPresent(Persisted.class))) continue;
            String key = key(field, description);
            if (!values.contains(key, 10)) continue;
            try {
                CompoundTag encoded = values.getCompound(key);
                Object previous = field.get(owner);
                if (previous instanceof ManagedValue && encoded.getBoolean("present"))
                    ((ManagedValue) previous).readManaged(encoded.getCompound("value"));
                else field.set(owner, decode(field.getType(), encoded));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot restore managed field " + field, failure);
            }
        }
    }

    private static CompoundTag encode(Object value) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("present", value != null);
        if (value == null) return tag;
        if (value instanceof Boolean) tag.putBoolean("value", (Boolean) value);
        else if (value instanceof Integer) tag.putInt("value", (Integer) value);
        else if (value instanceof Long) tag.putLong("value", (Long) value);
        else if (value instanceof Double) tag.nativeTag()
            .setDouble("value", (Double) value);
        else if (value instanceof Float) tag.nativeTag()
            .setFloat("value", (Float) value);
        else if (value instanceof String) tag.putString("value", (String) value);
        else if (value instanceof UUID) tag.putUUID("value", (UUID) value);
        else if (value instanceof Enum) tag.putString("value", ((Enum<?>) value).name());
        else if (value instanceof ItemStack)
            tag.put("value", new CompoundTag(((ItemStack) value).writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        else if (value instanceof cn.dancingsnow.neoecoae.platform.fluid.FluidStack) {
            var fluid = ((cn.dancingsnow.neoecoae.platform.fluid.FluidStack) value).toNative();
            if (fluid != null)
                tag.put("value", new CompoundTag(fluid.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        } else if (value instanceof CompoundTag) tag.put("value", ((CompoundTag) value).copy());
        else if (value instanceof ManagedValue) tag.put("value", ((ManagedValue) value).writeManaged());
        else throw new IllegalArgumentException(
            "No managed codec for " + value.getClass()
                .getName());
        return tag;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object decode(Class<?> type, CompoundTag tag) {
        if (!tag.getBoolean("present")) return null;
        if (type == boolean.class || type == Boolean.class) return tag.getBoolean("value");
        if (type == int.class || type == Integer.class) return tag.getInt("value");
        if (type == long.class || type == Long.class) return tag.getLong("value");
        if (type == double.class || type == Double.class) return tag.nativeTag()
            .getDouble("value");
        if (type == float.class || type == Float.class) return tag.nativeTag()
            .getFloat("value");
        if (type == String.class) return tag.getString("value");
        if (type == UUID.class) return tag.getUUID("value");
        if (type.isEnum()) return Enum.valueOf((Class) type, tag.getString("value"));
        if (type == ItemStack.class) return ItemStack.loadItemStackFromNBT(
            tag.getCompound("value")
                .nativeTag());
        if (type == cn.dancingsnow.neoecoae.platform.fluid.FluidStack.class)
            return cn.dancingsnow.neoecoae.platform.fluid.FluidStack.fromNative(
                net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(
                    tag.getCompound("value")
                        .nativeTag()));
        if (type == CompoundTag.class) return tag.getCompound("value")
            .copy();
        throw new IllegalArgumentException("No managed codec for " + type.getName());
    }
}
