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
package com.hotels.styx.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.requireNonNull;


/**
 * A listener that propagates events to a list of event listeners that can be added or removed after the Announcer is constructed.
 *
 * @param <T> the type of the event listeners notified by this announcer
 */
public class Announcer<T extends EventListener> {
    private final T proxy;
    private final List<T> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a notifier that can announce the registered listeners.
     *
     * @param listenerType the type of the listeners
     * @param <T>          the type of the listeners
     * @return a notifier that can announce the registered listeners
     */
    public static <T extends EventListener> Announcer<T> to(Class<? extends T> listenerType) {
        return new Announcer<>(listenerType);
    }

    /**
     * Constructs an announcer with the specified listener type.
     *
     * @param listenerType the type of the listeners
     */
    public Announcer(Class<? extends T> listenerType) {
        proxy = listenerType.cast(Proxy.newProxyInstance(
                listenerType.getClassLoader(),
                new Class<?>[]{listenerType},
                (aProxy, method, args) -> {
                    announce(method, args);
                    return null;
                }
        ));
    }

    /**
     * Adds the given listener to this Announcer.
     *
     * @param listener the listener to be added
     */
    public void addListener(T listener) {
        requireNonNull(listener, "Cannot add a null listener.");
        listeners.add(listener);
    }

    /**
     * Removes the given listener from this Announcer.
     *
     * @param listener the listener to be removed
     */
    public void removeListener(T listener) {
        listeners.remove(listener);
    }

    /**
     * Return the EventListener to notify.
     *
     * @return the EventListener to notify
     */
    public T announce() {
        return proxy;
    }

    public List<T> listeners() {
        return listeners;
    }

    private void announce(Method m, Object[] args) {
        try {
            for (T listener : listeners) {
                m.invoke(listener, args);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("could not invoke listener", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();

            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new UnsupportedOperationException("listener threw exception", cause);
            }
        }
    }
}
