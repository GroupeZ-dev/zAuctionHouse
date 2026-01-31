package fr.maxlego08.zauctionhouse.api.utils;

/**
 * A list of primitive int values.
 * <p>
 * This interface provides a lightweight alternative to FastUtil's IntList,
 * containing only the methods needed by the auction house plugin.
 */
public interface IntList extends Iterable<Integer> {

    /**
     * Adds an int value to the end of this list.
     *
     * @param value the value to add
     */
    void add(int value);

    /**
     * Removes the first occurrence of the specified value from this list.
     *
     * @param value the value to remove
     * @return {@code true} if the value was found and removed
     */
    boolean rem(int value);

    /**
     * Returns the value at the specified index.
     *
     * @param index the index of the value to return
     * @return the value at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    int getInt(int index);

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements
     */
    int size();

    /**
     * Returns {@code true} if this list contains no elements.
     *
     * @return {@code true} if this list is empty
     */
    boolean isEmpty();

    /**
     * Removes all elements from this list.
     */
    void clear();

    /**
     * Returns {@code true} if this list contains the specified value.
     *
     * @param value the value to search for
     * @return {@code true} if the value is found
     */
    boolean contains(int value);
}
