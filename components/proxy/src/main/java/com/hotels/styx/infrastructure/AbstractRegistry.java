/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.infrastructure;

import com.google.common.collect.MapDifference;
import com.google.common.util.concurrent.AbstractIdleService;
import com.hotels.styx.api.Announcer;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Maps.difference;
import static com.google.common.collect.Maps.filterKeys;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Abstract registry.
 *
 * @param <T> the type of resource to store
 */
public abstract class AbstractRegistry<T extends Identifiable> extends AbstractIdleService implements Registry<T> {
    private static final Logger LOG = getLogger(AbstractRegistry.class);

    private final Announcer<Registry.ChangeListener> announcer = Announcer.to(ChangeListener.class);
    protected final AtomicReference<Iterable<T>> snapshot = new AtomicReference<>(emptyList());

    @Override
    public Iterable<T> get() {
        return snapshot.get();
    }

    @Override
    protected void startUp() throws Exception {
    }

    @Override
    protected void shutDown() throws Exception {
    }

    protected void notifyListeners(Changes<T> changes) {
        LOG.info("notifying about services={} to listeners={}", announcer.listeners());
        announcer.announce().onChange(changes);
    }

    protected void notifyListenersOnError(Throwable ex) {
        announcer.announce().onError(ex);
    }

    @Override
    public Registry<T> addListener(ChangeListener<T> changeListener) {
        announcer.addListener(changeListener);
        if (this.isRunning()) {
            changeListener.onChange(added(snapshot.get()));
        }
        return this;
    }

    private Changes<T> added(Iterable<T> ch) {
        return new Changes.Builder<T>().added(ch).build();
    }

    @Override
    public Registry<T> removeListener(ChangeListener<T> changeListener) {
        announcer.removeListener(changeListener);
        return this;
    }

    static <T extends Identifiable> Changes<T> changes(Iterable<T> newResources, Iterable<T> currentResources) {
        Map<Id, T> newIdsToResource = mapById(newResources);
        Map<Id, T> currentIdsToResource = mapById(currentResources);

        MapDifference<Id, T> diff = difference(newIdsToResource, currentIdsToResource);

        Map<Id, MapDifference.ValueDifference<T>> diffs = diff.entriesDiffering();
        return new Changes.Builder<T>()
                .added(diff.entriesOnlyOnLeft().values())
                .removed(diff.entriesOnlyOnRight().values())
                .updated(filterKeys(newIdsToResource, diffs::containsKey).values())
                .build();
    }

    private static <T extends Identifiable> Map<Id, T> mapById(Iterable<T> resources) {
        return stream(resources.spliterator(), false)
                .collect(toMap(T::id, identity()));
    }
}
