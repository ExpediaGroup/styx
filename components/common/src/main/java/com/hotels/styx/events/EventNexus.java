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
package com.hotels.styx.events;

import rx.Observable;

/**
 * An object that abstracts the concept of events away from the specific publishers and subscribers, allowing
 * different objects to communicate without being coupled to each others implementation details.
 *
 * Events have an identifying name to categorise them, using a dot-notation. Multiple events may be published using
 * the same name. Names will be handled in a case-insensitive fashion.
 *
 * Events also have a value object, which is any arbitrary object that may contain details relevant to the subscriber.
 * Ideally the value object should have a type that can be understood by the subscriber that is not aware of the publisher.
 *
 * Any event that is published to the nexus will be published to all the subscribers that have asked for:
 *
 * <ul>
 *     <li>Events with that exact name</li>
 *     <li>Events whose name is the parent of the specified name</li>
 * </ul>
 *
 * For example, something that subscribes to {@code foo.bar} will receive {@code foo.bar}, {@code foo.bar.baz}, etc.
 */
public interface EventNexus {
    /**
     * Gets an observable that supplies events under a certain name.
     *
     * @param name name
     * @return event observable
     */
    Observable<Event> events(String name);

    /**
     * Publishes an event to the subscribers that are listening for it.
     * The name identifies the event and the value can be any extra information needed.
     *
     * @param name name
     * @param value value
     */
    void publish(String name, Object value);
}
