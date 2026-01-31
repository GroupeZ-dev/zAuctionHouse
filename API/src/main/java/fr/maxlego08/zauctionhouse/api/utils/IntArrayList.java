package fr.maxlego08.zauctionhouse.api.utils;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A resizable array implementation of {@link IntList}.
 * <p>
 * This class provides a lightweight alternative to FastUtil's IntArrayList,
 * optimized for the auction house plugin's specific use cases.
 */
public class IntArrayList implements IntList {

    private static final int DEFAULT_CAPACITY = 10;

    private int[] data;
    private int size;

    /**
     * Creates an empty list with the default initial capacity.
     */
    public IntArrayList() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates an empty list with the specified initial capacity.
     *
     * @param initialCapacity the initial capacity of the list
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public IntArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.data = new int[initialCapacity];
        this.size = 0;
    }

    @Override
    public void add(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    @Override
    public boolean rem(int value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) {
                removeAtIndex(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getInt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return data[index];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        size = 0;
    }

    @Override
    public boolean contains(int value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Iterator<Integer> iterator() {
        return new IntIterator();
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = Math.max(data.length + (data.length >> 1), minCapacity);
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    private void removeAtIndex(int index) {
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(data, index + 1, data, index, numMoved);
        }
        size--;
    }

    @Override
    public String toString() {
        if (size == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(data[i]);
        }
        return sb.append("]").toString();
    }

    private class IntIterator implements Iterator<Integer> {
        private int cursor = 0;

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return data[cursor++];
        }
    }
}
