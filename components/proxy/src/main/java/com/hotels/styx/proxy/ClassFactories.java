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
package com.hotels.styx.proxy;

import com.google.common.base.Throwables;

import java.security.PrivilegedAction;

import static java.lang.String.format;
import static java.security.AccessController.doPrivileged;
import static java.util.Objects.requireNonNull;

/**
 * Support class for creating new instances from class names.
 */
public final class ClassFactories {
    private ClassFactories() {
    }

    /**
     * Factory for creating object of a class that extends type {@code type}.
     *
     * @param className name of a class that extends {@code type}
     * @param type      class type
     * @param <T>       generic type
     * @return new instance
     */
    public static <T> T newInstance(String className, Class<T> type) {
        try {
            Object instance = classForName(className).newInstance();

            return type.cast(instance);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(format("No such class '%s'", className));
        } catch (InstantiationException | IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }

    private static Class<?> classForName(String className) throws ClassNotFoundException {
        requireNonNull(className);
        PrivilegedAction<String> privilegedAction = () -> className;

        return Class.forName(doPrivileged(privilegedAction));
    }
}
