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
package com.hotels.styx.common;

import java.util.stream.Stream;

import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.lang.Thread.currentThread;
import static java.util.stream.Collectors.joining;

/**
 * Useful for debugging.
 */
public final class StackDump {
    private static final String LINE_SEPARATOR = lineSeparator();

    private StackDump() {
    }

    public static Stream<String> stack() {
        return Stream.of(currentThread().getStackTrace())
                .map(Object::toString)
                .filter(StackDump::notFromThisClass)
                .filter(StackDump::notFromThreadClass);
    }

    public static String string(Stream<String> stack) {
        return stack
                .map(stackTraceElement -> "  at " + stackTraceElement)
                .collect(joining(LINE_SEPARATOR));
    }

    public static void dump(Stream<String> stack) {
        System
                .out
                .println(string(stack));
    }

    public static void dump(Stream<String> stack, String prefix, Object... args) {
        System
                .out
                .println(format(prefix, args) + LINE_SEPARATOR + string(stack));
    }

    private static boolean notFromThisClass(String stackTraceElement) {
        return !stackTraceElement.contains(StackDump.class.getName());
    }

    private static boolean notFromThreadClass(String stackTraceElement) {
        return !stackTraceElement.contains(Thread.class.getName());
    }
}
