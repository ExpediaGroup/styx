/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.lang.Math.abs;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * A wrapper for collections that allows them to be easily logged in a readable format.
 * <p>
 * Using the {@link #toString()} method will put each item on a separate line (note that this is called automatically by String concatenation).
 * <p>
 * The list can also be indented to allow it to stand out from other log output.
 * <p>
 * Two {@link ItemsDump} objects can be diffed to quickly see the difference between two collections.
 */
public class ItemsDump {
    private final Collection<?> items;
    private final String indentation;

    private ItemsDump(Collection<?> items, String indentation) {
        this.items = requireNonNull(items);
        this.indentation = requireNonNull(indentation);
    }

    private ItemsDump(Collection<?> items) {
        this(items, "");
    }

    /**
     * Create an instance from a collection.
     *
     * @param items items
     * @return a new instance
     */
    public static ItemsDump dump(Collection<?> items) {
        return new ItemsDump(items);
    }

    /**
     * Create a new instance which will indent each item using a given string.
     * Note that the original {@link ItemsDump} will not be mutated.
     *
     * @param indentation indentation string
     * @return indented {@link ItemsDump}
     */
    public ItemsDump indentWith(String indentation) {
        return new ItemsDump(items, indentation);
    }

    /**
     * Get the difference between two {@link ItemsDump} objects.
     * Items that are present in this instance, but not the argument will be prefixed with a plus ({@code +}).
     * Items that are present in the argument, but not this instance will be prefixed with a minus ({@code -}).
     *
     * @param from another {@link ItemsDump}
     * @return a diff of the items
     */
    public ItemsDump diff(ItemsDump from) {
        List<String> differences = new ArrayList<>();

        Collection<String> ours = strings();
        Collection<String> theirs = from.strings();

        for (String value : merge(ours, theirs)) {
            long ourAppearances = appearances(value, ours);
            long theirAppearances = appearances(value, theirs);

            if (ourAppearances != theirAppearances) {
                String sign = ourAppearances > theirAppearances ? "+" : "-";
                long count = abs(ourAppearances - theirAppearances);

                for (int i = 0; i < count; i++) {
                    differences.add(sign + " " + value);
                }
            }
        }

        return dump(differences);
    }

    private static Set<String> merge(Collection<String> ours, Collection<String> theirs) {
        Set<String> values = new HashSet<>();
        values.addAll(ours);
        values.addAll(theirs);
        return values;
    }

    private static <T> long appearances(T item, Collection<T> collection) {
        return collection.stream()
                .filter(element -> Objects.equals(element, item))
                .count();
    }

    /**
     * Get the number of items.
     *
     * @return the number of items
     */
    public int size() {
        return items.size();
    }

    /**
     * Filters the items to only include ones whose String representation contains the provided string.
     *
     * @param contained string that must be present in items
     * @return filtered dump
     */
    public ItemsDump filter(String contained) {
        return dump(items.stream()
                .map(Object::toString)
                .filter(s -> s.contains(contained))
                .collect(toList()));
    }

    private List<String> strings() {
        return items.stream()
                .map(Object::toString)
                .collect(toList());
    }

    @Override
    public String toString() {
        return items.stream()
                .map(Object::toString)
                .map(s -> indentation + s)
                .sorted()
                .collect(joining("\n"))
                + "\n";
    }
}
