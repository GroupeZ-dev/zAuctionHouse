package fr.maxlego08.zauctionhouse.api.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Couvre la methode {@code default} de l'interface via une implementation minimale tierce :
 * c'est le contrat que les addons compiles contre une ancienne API recoivent sans recompilation.
 */
class IntListTest {

    /**
     * Implementation volontairement naive, qui N'OVERRIDE PAS indexOf.
     */
    private static final class ThirdPartyIntList implements IntList {
        private final List<Integer> values = new ArrayList<>();

        @Override
        public void add(int value) {
            values.add(value);
        }

        @Override
        public boolean rem(int value) {
            return values.remove(Integer.valueOf(value));
        }

        @Override
        public int getInt(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public boolean contains(int value) {
            return values.contains(value);
        }

        @Override
        public IntList clone() {
            ThirdPartyIntList copy = new ThirdPartyIntList();
            copy.values.addAll(values);
            return copy;
        }

        @Override
        public Iterator<Integer> iterator() {
            return values.iterator();
        }
    }

    @Test
    @DisplayName("indexOf par defaut rend l'index de la premiere occurrence")
    void defaultIndexOf_firstOccurrence() {
        IntList list = new ThirdPartyIntList();
        list.add(8);
        list.add(3);
        list.add(8);
        assertEquals(0, list.indexOf(8));
        assertEquals(1, list.indexOf(3));
    }

    @Test
    @DisplayName("indexOf par defaut rend -1 quand la valeur est absente ou la liste vide")
    void defaultIndexOf_absent() {
        IntList list = new ThirdPartyIntList();
        assertEquals(-1, list.indexOf(1));
        list.add(2);
        assertEquals(-1, list.indexOf(1));
    }

    @Test
    @DisplayName("IntArrayList et l'implementation par defaut s'accordent sur indexOf")
    void indexOf_consistentWithIntArrayList() {
        IntList custom = new ThirdPartyIntList();
        IntList array = new IntArrayList();
        for (int value : new int[]{5, 9, 5, 0, -2}) {
            custom.add(value);
            array.add(value);
        }
        for (int probe : new int[]{5, 9, 0, -2, 7}) {
            assertEquals(array.indexOf(probe), custom.indexOf(probe), "probe " + probe);
        }
    }
}
