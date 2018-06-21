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
package com.hotels.styx.api.common;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.lang.Thread.currentThread;
import static java.util.stream.Collectors.joining;

/**
 * Sourceable event. Useful for debugging.
 */
public class SourceableEvent {
    private static final AtomicInteger CONSTRUCTION_ORDER = new AtomicInteger();

    private final int constructionNumber;
    private final String constructedAt;
    private final WriteOnce<SourceableEvent> parent = new WriteOnce<>();

    public SourceableEvent() {
        this.constructionNumber = CONSTRUCTION_ORDER.incrementAndGet();
        this.constructedAt = Stream.of(currentThread().getStackTrace())
                .skip(2)
                .map(Object::toString)
                .filter(string -> string.contains("com.hotels"))
                .collect(joining("\n  at ", "  at ", ""));
    }

    public String constructedAt() {
        return constructedAt;
    }

    public void setParent(SourceableEvent value) {
        if (value == this) {
            throw new IllegalArgumentException("That was stupid");
        }

        parent.set(value);
    }

    public String dump() {
        StringBuilder a = new StringBuilder()
                .append(getClass().getSimpleName())
                .append("[")
                .append(constructionNumber)
                .append("] ")
                .append(toString())
                .append(" {\n");

        StringBuilder properties = new StringBuilder()
                .append("constructedAt={\n")
                .append(constructedAt)
                .append("}\n");

        if (parent.isSet()) {
            SourceableEvent parentEvent = parent.get();

            if (parentEvent == null) {
                properties.append("parent=UNKNOWN (set to null)\n");
            } else {
                properties.append("parent={\n")
                        .append(indent(parentEvent.dump(), "  "))
                        .append("\n}\n");
            }
        }

        return a.append(indent(properties.toString(), "  "))
                .append("}\n").toString();
    }

    private static String indent(String original, String indentation) {
        return indentation + original.replaceAll("([\\n\\r]+)", "$1" + indentation);
    }

    private static class WriteOnce<E> {
        private final AtomicBoolean used = new AtomicBoolean();
        private volatile E value;

        public void set(E value) {
            if (!used.compareAndSet(false, true)) {
                throw new IllegalStateException("Extra call to set(" + value + ")");
            }

            this.value = value;
        }

        public boolean isSet() {
            return used.get();
        }

        public E get() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}
