/*
  Copyright (C) 2013-2020 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.common;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.hotels.styx.common.Collections.concat;
import static com.hotels.styx.common.Collections.getFirst;
import static com.hotels.styx.common.Collections.listOf;
import static com.hotels.styx.common.Collections.setOf;
import static com.hotels.styx.common.Collections.size;
import static com.hotels.styx.common.Collections.transform;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CollectionsTest {

    @Test
    public void iteratorToList() {
        Iterator<Integer> source = asList(1, 2, 3, 4, 5, 6).iterator();
        List<Integer> list = listOf(source);
        assertThat(list.size(), equalTo(6));
        assertThat(list, contains(1, 2, 3, 4, 5, 6));
        assertThrows(UnsupportedOperationException.class, () -> {
            list.set(3, 100);
        });
    }

    @Test
    public void iteratorToListDisallowsNulls() {
        Iterator<String> source = asList("1", "2", "3", null, "5", "6").iterator();
        assertThrows(NullPointerException.class, () -> {
            listOf(source);
        });
    }

    @Test
    public void iterableToList() {
        Iterable<Integer> source = asList(1, 2, 3, 4, 5, 6);
        List<Integer> list = listOf(source);
        ((List<Integer>) source).set(3, 100);

        assertThat(list.size(), equalTo(6));
        assertThat(list, contains(1, 2, 3, 4, 5, 6));
        assertThrows(UnsupportedOperationException.class, () -> {
            list.set(3, 100);
        });
    }

    @Test
    public void iterableToListDisallowsNulls() {
        Iterable<String> source = asList("1", "2", "3", null, "5", "6");
        assertThrows(NullPointerException.class, () -> {
            listOf(source);
        });
    }

    @Test
    public void arrayToList() {
        Integer[] source = new Integer[] {1, 2, 3, 4, 5, 6};
        List<Integer> list = listOf(source);
        source[3] = 100;

        assertThat(list.size(), equalTo(6));
        assertThat(list, contains(1, 2, 3, 4, 5, 6));
        assertThrows(UnsupportedOperationException.class, () -> {
            list.set(3, 100);
        });
    }

    @Test
    public void arrayToListDisallowsNulls() {
        Integer[] source = new Integer[] {1, 2, 3, null, 5, 6};
        assertThrows(NullPointerException.class, () -> {
            listOf(source);
        });
    }

    @Test
    public void iteratorToSet() {
        Iterator<Integer> source = asList(1, 2, 3, 4, 5, 6).iterator();
        Set<Integer> set = setOf(source);
        assertThat(set.size(), equalTo(6));
        assertThat(set, contains(1, 2, 3, 4, 5, 6)); // note - in order
        assertThrows(UnsupportedOperationException.class, () -> {
            set.add(100);
        });
    }

    @Test
    public void iteratorToSetDisallowsNulls() {
        Iterator<String> source = asList("1", "2", "3", null, "5", "6").iterator();
        assertThrows(NullPointerException.class, () -> {
            setOf(source);
        });
    }

    @Test
    public void iterableToSet() {
        Iterable<Integer> source = asList(1, 2, 3, 4, 5, 6);
        Set<Integer> set = setOf(source);
        ((List<Integer>) source).set(3, 100);

        assertThat(set.size(), equalTo(6));
        assertThat(set, contains(1, 2, 3, 4, 5, 6)); // note - in order
        assertThrows(UnsupportedOperationException.class, () -> {
            set.add(100);
        });
    }

    @Test
    public void iterableToSetDisallowsNulls() {
        Iterable<String> source = asList("1", "2", "3", null, "5", "6");
        assertThrows(NullPointerException.class, () -> {
            setOf(source);
        });
    }

    @Test
    public void arrayToSet() {
        Integer[] source = new Integer[] {1, 2, 3, 4, 5, 6};
        Set<Integer> set = setOf(source);
        source[3] = 100;

        assertThat(set.size(), equalTo(6));
        assertThat(set, contains(1, 2, 3, 4, 5, 6)); // note - in order
        assertThrows(UnsupportedOperationException.class, () -> {
            set.add(100);
        });
    }

    @Test
    public void arrayToSetDisallowsNulls() {
        Integer[] source = new Integer[] {1, 2, 3, null, 5, 6};
        assertThrows(NullPointerException.class, () -> {
            setOf(source);
        });
    }

    @Test
    public void iterableToStringAllowsNull() {
        Iterable<String> source = asList("1", "2", "3", null, "5", "6");
        assertThat(Collections.toString(source), equalTo("[1, 2, 3, null, 5, 6]"));
    }

    @Test
    public void iteratorSize() {
        Iterator<Integer> source = asList(1, 2, 3, 4, 5, 6).iterator();
        assertThat(size(source), equalTo(6));
    }

    @Test
    public void iterableSize() {
        Iterable<Integer> source = asList(1, 2, 3, 4, 5, 6);
        assertThat(size(source), equalTo(6));
    }

    @Test
    public void iteratorContains() {
        assertFalse(Collections.contains(asList(1, 2, 3, 4, 5, 6).iterator(), 7));
        assertFalse(Collections.contains(asList(1, 2, 3, 4, 5, 6).iterator(), null));
        assertTrue(Collections.contains(asList(1, 2, 3, 4, 5, 6).iterator(), 3));
        assertTrue(Collections.contains(asList(1, 2, 3, null, 5, 6).iterator(), null));
    }

    @Test
    public void iterableContains() {
        Iterable<Integer> source = asList(1, 2, 3, 4, 5, 6);
        assertFalse(Collections.contains(source, 7));
        assertFalse(Collections.contains(source, null));
        assertTrue(Collections.contains(source, 3));
        assertTrue(Collections.contains(asList(1, 2, 3, null, 5, 6), null));
    }

    @Test
    public void iteratorGetFirst() {
        assertThat(getFirst(asList(1, 2, 3, 4, 5, 6).iterator(), 10), equalTo(1));
        assertThat(getFirst(emptyIterator(), 10), equalTo(10));
    }

    @Test
    public void iterableGetFirst() {
        assertThat(getFirst(asList(1, 2, 3, 4, 5, 6), 10), equalTo(1));
        assertThat(getFirst(emptyList(), 10), equalTo(10));
    }

    @Test
    public void iteratorConcat() {
        Iterator<Integer> iter1 = asList(1, 2, 3).iterator();
        Iterator<Integer> iter2 = asList(4, 5).iterator();
        Iterator<Integer> iter3 = asList(6, 7, 8).iterator();
        Iterator<Integer> concat = concat(iter1, iter2, iter3);
        assertTrue(iter1.hasNext());
        assertTrue(iter2.hasNext());
        assertTrue(iter3.hasNext());
        assertTrue(concat.hasNext());
        List<Integer> iterated = listOf(concat);
        assertFalse(iter1.hasNext());
        assertFalse(iter2.hasNext());
        assertFalse(iter3.hasNext());
        assertFalse(concat.hasNext());
        assertThat(iterated, contains(1, 2, 3, 4, 5, 6, 7, 8));
    }

    @Test
    public void iterableConcat() {
        Iterable<Integer> iter1 = asList(1, 2, 3);
        Iterable<Integer> iter2 = asList(4, 5);
        Iterable<Integer> concat = concat(iter1, iter2);
        List<Integer> iterated = listOf(concat);
        assertThat(iterated, contains(1, 2, 3, 4, 5));
    }

    @Test
    public void iterableTransform() {
        Iterable<Integer> source = asList(1, 2, 3, 4, 5);
        Iterable<Integer> doubled = transform(source, i -> i * 2);
        List<Integer> iterated = listOf(doubled);

        ((List<Integer>) source).set(3, 10);
        List<Integer> iterated2 = listOf(doubled);

        assertThat(iterated, contains(2, 4, 6, 8, 10));
        assertThat(iterated2, contains(2, 4, 6, 20, 10));
    }
}
