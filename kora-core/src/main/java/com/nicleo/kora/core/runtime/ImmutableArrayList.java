package com.nicleo.kora.core.runtime;

import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.RandomAccess;

public final class ImmutableArrayList<E> extends AbstractList<E> implements RandomAccess, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final ImmutableArrayList<?> EMPTY = new ImmutableArrayList<>(new Object[0]);

    private final Object[] elements;

    private ImmutableArrayList(Object[] elements) {
        this.elements = elements;
    }

    @SuppressWarnings("unchecked")
    public static <E> ImmutableArrayList<E> empty() {
        return (ImmutableArrayList<E>) EMPTY;
    }

    public static <E> ImmutableArrayList<E> wrap(Object[] elements) {
        return elements.length == 0 ? empty() : new ImmutableArrayList<>(elements);
    }

    @Override
    public E get(int index) {
        @SuppressWarnings("unchecked")
        E element = (E) elements[index];
        return element;
    }

    @Override
    public int size() {
        return elements.length;
    }
}
