package fr.maxlego08.zauctionhouse.api.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntArrayListTest {

    private static IntArrayList of(int... values) {
        IntArrayList list = new IntArrayList();
        for (int value : values) list.add(value);
        return list;
    }

    private static List<Integer> toList(IntList list) {
        List<Integer> result = new ArrayList<>();
        for (int value : list) result.add(value);
        return result;
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("une liste neuve est vide")
        void newList_isEmpty() {
            IntArrayList list = new IntArrayList();
            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertEquals("[]", list.toString());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 5, 1000})
        @DisplayName("la capacite initiale n'influe pas sur le contenu")
        void initialCapacity_doesNotAffectContent(int capacity) {
            IntArrayList list = new IntArrayList(capacity);
            assertTrue(list.isEmpty());
            list.add(1);
            assertEquals(1, list.size());
            assertEquals(1, list.getInt(0));
        }

        @Test
        @DisplayName("une capacite negative est refusee")
        void negativeCapacity_throws() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new IntArrayList(-1));
            assertTrue(exception.getMessage().contains("-1"));
        }
    }

    @Nested
    @DisplayName("Ajout et croissance")
    class AddAndGrowth {

        @Test
        @DisplayName("add conserve l'ordre d'insertion")
        void add_preservesOrder() {
            IntArrayList list = of(3, 1, 2);
            assertEquals(List.of(3, 1, 2), toList(list));
            assertEquals(3, list.size());
        }

        @ParameterizedTest
        @ValueSource(ints = {11, 16, 17, 100, 1001})
        @DisplayName("la liste grandit au-dela de la capacite par defaut (10) sans perdre d'element")
        void add_beyondDefaultCapacity(int count) {
            IntArrayList list = new IntArrayList();
            for (int i = 0; i < count; i++) list.add(i * 2);
            assertEquals(count, list.size());
            for (int i = 0; i < count; i++) assertEquals(i * 2, list.getInt(i));
        }

        @Test
        @DisplayName("une liste de capacite 0 grandit correctement")
        void add_fromZeroCapacity() {
            IntArrayList list = new IntArrayList(0);
            for (int i = 0; i < 25; i++) list.add(i);
            assertEquals(25, list.size());
            assertEquals(24, list.getInt(24));
        }

        @Test
        @DisplayName("les doublons et les valeurs negatives sont acceptes")
        void add_duplicatesAndNegatives() {
            IntArrayList list = of(-1, -1, 0, Integer.MAX_VALUE, Integer.MIN_VALUE);
            assertEquals(5, list.size());
            assertEquals(Integer.MIN_VALUE, list.getInt(4));
            assertTrue(list.contains(-1));
        }
    }

    @Nested
    @DisplayName("Lecture")
    class Reading {

        @Test
        @DisplayName("getInt rend l'element a l'index demande")
        void getInt_validIndex() {
            IntArrayList list = of(10, 20, 30);
            assertEquals(10, list.getInt(0));
            assertEquals(30, list.getInt(2));
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 3, 100})
        @DisplayName("getInt hors bornes leve IndexOutOfBoundsException")
        void getInt_outOfBounds_throws(int index) {
            IntArrayList list = of(1, 2, 3);
            assertThrows(IndexOutOfBoundsException.class, () -> list.getInt(index));
        }

        @Test
        @DisplayName("getInt sur une liste vide leve meme pour l'index 0")
        void getInt_empty_throws() {
            assertThrows(IndexOutOfBoundsException.class, () -> new IntArrayList().getInt(0));
        }

        @Test
        @DisplayName("contains et indexOf : present, absent, premiere occurrence")
        void contains_indexOf() {
            IntArrayList list = of(5, 7, 5, 9);
            assertTrue(list.contains(5));
            assertTrue(list.contains(9));
            assertFalse(list.contains(6));
            assertEquals(0, list.indexOf(5));
            assertEquals(3, list.indexOf(9));
            assertEquals(-1, list.indexOf(6));
            assertEquals(-1, new IntArrayList().indexOf(0));
        }

        @Test
        @DisplayName("toString affiche les elements separes par des virgules")
        void toString_format() {
            assertEquals("[1, 2, 3]", of(1, 2, 3).toString());
            assertEquals("[-4]", of(-4).toString());
        }
    }

    @Nested
    @DisplayName("Suppression")
    class Removal {

        @Test
        @DisplayName("rem supprime la premiere occurrence seulement et decale la suite")
        void rem_firstOccurrenceOnly() {
            IntArrayList list = of(1, 2, 1, 3);
            assertTrue(list.rem(1));
            assertEquals(List.of(2, 1, 3), toList(list));
            assertTrue(list.rem(1));
            assertEquals(List.of(2, 3), toList(list));
            assertFalse(list.rem(1));
            assertEquals(2, list.size());
        }

        @Test
        @DisplayName("rem du dernier element ne decale rien")
        void rem_lastElement() {
            IntArrayList list = of(1, 2, 3);
            assertTrue(list.rem(3));
            assertEquals(List.of(1, 2), toList(list));
        }

        @Test
        @DisplayName("rem sur une liste vide rend false")
        void rem_empty_false() {
            assertFalse(new IntArrayList().rem(1));
        }

        @Test
        @DisplayName("clear vide la liste et permet de reutiliser le stockage")
        void clear_thenReuse() {
            IntArrayList list = of(1, 2, 3);
            list.clear();
            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertFalse(list.contains(1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.getInt(0));
            list.add(9);
            assertEquals(List.of(9), toList(list));
        }
    }

    @Nested
    @DisplayName("Clone et iteration")
    class CloneAndIteration {

        @Test
        @DisplayName("clone est une copie independante avec le meme contenu")
        void clone_isIndependentCopy() {
            IntArrayList list = of(1, 2, 3);
            IntList copy = list.clone();

            assertNotSame(list, copy);
            assertEquals(toList(list), toList(copy));

            copy.add(4);
            list.rem(1);
            assertEquals(List.of(2, 3), toList(list));
            assertEquals(List.of(1, 2, 3, 4), toList(copy));
        }

        @Test
        @DisplayName("clone d'une liste vide")
        void clone_empty() {
            IntList copy = new IntArrayList().clone();
            assertTrue(copy.isEmpty());
            copy.add(1);
            assertEquals(1, copy.size());
        }

        @Test
        @DisplayName("clone d'une liste ayant grandi puis retrecie ne copie que les elements logiques")
        void clone_afterShrink() {
            IntArrayList list = new IntArrayList();
            for (int i = 0; i < 30; i++) list.add(i);
            for (int i = 0; i < 28; i++) list.rem(i);
            IntList copy = list.clone();
            assertEquals(List.of(28, 29), toList(copy));
        }

        @Test
        @DisplayName("l'iterateur parcourt tous les elements dans l'ordre puis leve NoSuchElementException")
        void iterator_fullTraversal() {
            IntArrayList list = of(4, 5, 6);
            Iterator<Integer> iterator = list.iterator();
            assertTrue(iterator.hasNext());
            assertEquals(4, iterator.next());
            assertEquals(5, iterator.next());
            assertEquals(6, iterator.next());
            assertFalse(iterator.hasNext());
            assertThrows(NoSuchElementException.class, iterator::next);
        }

        @Test
        @DisplayName("l'iterateur d'une liste vide n'a pas d'element")
        void iterator_empty() {
            Iterator<Integer> iterator = new IntArrayList().iterator();
            assertFalse(iterator.hasNext());
            assertThrows(NoSuchElementException.class, iterator::next);
        }

        @Test
        @DisplayName("l'iterateur ignore la capacite inutilisee du tableau interne")
        void iterator_ignoresSpareCapacity() {
            IntArrayList list = new IntArrayList(50);
            list.add(1);
            list.add(2);
            assertEquals(List.of(1, 2), toList(list));
        }
    }
}
