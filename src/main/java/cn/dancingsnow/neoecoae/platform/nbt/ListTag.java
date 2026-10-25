package cn.dancingsnow.neoecoae.platform.nbt;

import java.util.Iterator;
import java.util.NoSuchElementException;

import net.minecraft.nbt.NBTTagList;

public final class ListTag extends Tag implements java.util.Collection<Tag> {

    private static final java.lang.reflect.Field ELEMENTS = cpw.mods.fml.relauncher.ReflectionHelper
        .findField(NBTTagList.class, "tagList", "field_74747_a");

    public ListTag() {
        this(new NBTTagList());
    }

    public ListTag(NBTTagList tag) {
        super(tag);
    }

    @Override
    public NBTTagList nativeTag() {
        return (NBTTagList) value;
    }

    @Override
    public ListTag copy() {
        return new ListTag((NBTTagList) value.copy());
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return nativeTag().tagCount();
    }

    public int getElementType() {
        return nativeTag().func_150303_d();
    }

    public boolean add(Tag item) {
        if (!isEmpty() && item.getId() != getElementType()) throw new IllegalArgumentException("Mixed NBT list types");
        nativeTag().appendTag(item.nativeTag());
        return true;
    }

    public Tag get(int index) {
        try {
            return Tag.wrap(((java.util.List<net.minecraft.nbt.NBTBase>) ELEMENTS.get(nativeTag())).get(index));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read native NBT list", e);
        }
    }

    public CompoundTag getCompound(int index) {
        return new CompoundTag(nativeTag().getCompoundTagAt(index));
    }

    public boolean contains(Object item) {
        for (Tag tag : this) if (tag.equals(item)) return true;
        return false;
    }

    private java.util.List<Tag> snapshot() {
        java.util.List<Tag> result = new java.util.ArrayList<>();
        for (Tag tag : this) result.add(tag);
        return result;
    }

    public Object[] toArray() {
        return snapshot().toArray();
    }

    public <T> T[] toArray(T[] array) {
        return snapshot().toArray(array);
    }

    public boolean addAll(java.util.Collection<? extends Tag> tags) {
        boolean changed = false;
        for (Tag tag : tags) {
            add(tag);
            changed = true;
        }
        return changed;
    }

    public boolean containsAll(java.util.Collection<?> tags) {
        for (Object tag : tags) if (!contains(tag)) return false;
        return true;
    }

    public boolean remove(Object value) {
        for (int i = 0; i < size(); i++) if (get(i).equals(value)) {
            nativeTag().removeTag(i);
            return true;
        }
        return false;
    }

    public boolean removeAll(java.util.Collection<?> values) {
        boolean changed = false;
        for (int i = size() - 1; i >= 0; i--) if (values.contains(get(i))) {
            nativeTag().removeTag(i);
            changed = true;
        }
        return changed;
    }

    public boolean retainAll(java.util.Collection<?> values) {
        boolean changed = false;
        for (int i = size() - 1; i >= 0; i--) if (!values.contains(get(i))) {
            nativeTag().removeTag(i);
            changed = true;
        }
        return changed;
    }

    public void clear() {
        while (!isEmpty()) nativeTag().removeTag(size() - 1);
    }

    @Override
    public Iterator<Tag> iterator() {
        return new Iterator<Tag>() {

            private int index;

            public boolean hasNext() {
                return index < size();
            }

            public Tag next() {
                if (!hasNext()) throw new NoSuchElementException();
                return get(index++);
            }
        };
    }
}
