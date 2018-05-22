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

import com.google.common.annotations.VisibleForTesting;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple implementation of EventNexus.
 */
public class SimpleEventNexus implements EventNexus {
    private final ConcurrentMap<String, PublishSubject<Event>> map = new ConcurrentHashMap<>();

    @Override
    public Observable<Event> events(String name) {
        return map.computeIfAbsent(name.toLowerCase(), ignored -> PublishSubject.create());
    }

    @Override
    public void publish(String name, Object value) {
        String nameLc = name.toLowerCase();

        // May need to optimise the performance of this part
        map.entrySet().stream()
                .filter(entry -> equalOrChild(nameLc, entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(listener -> listener.onNext(new Event(nameLc, value)));
    }

    @VisibleForTesting
    static boolean equalOrChild(String underExamination, String potentialParentOrEqual) {
        if (underExamination.equals(potentialParentOrEqual)) {
            return true;
        }

        int lastDot = underExamination.lastIndexOf('.');

        if (lastDot == -1) {
            return false;
        }

        String upOneLevel = underExamination.substring(0, lastDot);

        return equalOrChild(upOneLevel, potentialParentOrEqual);
    }
}
