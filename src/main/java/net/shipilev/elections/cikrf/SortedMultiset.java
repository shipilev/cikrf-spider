package net.shipilev.elections.cikrf;

import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class SortedMultiset<E extends Comparable> implements Multiset<E> {

    private final Multiset<E> set;

    public SortedMultiset() {
        this.set = TreeMultiset.create();
    }

    @Override
    public int count(@Nullable Object o) {
        return set.count(o);
    }

    @Override
    public int add(@Nullable E e, int i) {
        return set.add(e, i);
    }

    @Override
    public int remove(@Nullable Object o, int i) {
        return set.remove(o, i);
    }

    @Override
    public int setCount(E e, int i) {
        return set.setCount(e, i);
    }

    @Override
    public boolean setCount(E e, int i, int i1) {
        return set.setCount(e, i, i1);
    }

    @Override
    public Set<E> elementSet() {
        return new TreeSet<E>(set.elementSet());
    }

    @Override
    public Set<Entry<E>> entrySet() {
        throw new UnsupportedOperationException("Do not use this method");
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException("Do not use this method");
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Do not use this method");
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("Do not use this method");
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(@Nullable Object o) {
        throw new UnsupportedOperationException("Do not use this method");
    }

    @Override
    public boolean containsAll(Collection<?> objects) {
        throw new UnsupportedOperationException("Do not use this method");
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("Do not use this method");
    }

    @Override
    public boolean add(E e) {
        return set.add(e);
    }

    @Override
    public boolean remove(@Nullable Object o) {
        return set.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> objects) {
        return set.removeAll(objects);
    }

    @Override
    public boolean retainAll(Collection<?> objects) {
        return set.retainAll(objects);
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SortedMultiset that = (SortedMultiset) o;

        if (set != null ? !set.equals(that.set) : that.set != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return set != null ? set.hashCode() : 0;
    }
}
