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
package com.hotels.styx.api.extension.service.spi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapDifference;
import com.hotels.styx.api.Announcer;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.difference;
import static com.google.common.collect.Maps.filterKeys;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Abstract registry.
 *
 * @param <T> the type of resource to store
 */
public abstract class AbstractRegistry<T extends Identifiable> implements Registry<T> {
    private static final Logger LOG = getLogger(AbstractRegistry.class);

    private final Announcer<Registry.ChangeListener> announcer = Announcer.to(ChangeListener.class);
    private final AtomicReference<Iterable<T>> snapshot = new AtomicReference<>(emptyList());
    private final Predicate<Collection<T>> resourceConstraint;

    /**
     * Constructs an abstract registry with no resource constraint.
     */
    public AbstractRegistry() {
        this(any -> true);
    }

    /**
     * Constructs an abstract registry that will not allow resources to be set if they do not
     * satisfy a particular constraint.
     *
     * @param resourceConstraint a constraint to be satisfied
     */
    public AbstractRegistry(Predicate<Collection<T>> resourceConstraint) {
        this.resourceConstraint = requireNonNull(resourceConstraint);
    }

    @Override
    public Iterable<T> get() {
        return snapshot.get();
    }

    protected void notifyListeners(Changes<T> changes) {
        LOG.info("notifying about services={} to listeners={}", changes, announcer.listeners());
        announcer.announce().onChange(changes);
    }

    @Override
    public Registry<T> addListener(ChangeListener<T> changeListener) {
        announcer.addListener(changeListener);
        changeListener.onChange(added(snapshot.get()));
        return this;
    }

    /**
     * Sets the contents of this registry to the supplied resources.
     *
     * @param newObjects new contents
     * @throws IllegalStateException if the resource constraint is not satisfied
     */
    public void set(Iterable<T> newObjects) throws IllegalStateException {
        ImmutableList<T> newSnapshot = ImmutableList.copyOf(newObjects);

        checkState(resourceConstraint.test(newSnapshot), "Resource constraint failure");

        Iterable<T> oldSnapshot = snapshot.get();
        snapshot.set(newSnapshot);

        Changes<T> changes = changes(newSnapshot, oldSnapshot);
        if (!changes.isEmpty()) {
            notifyListeners(changes);
        }
    }

    private Changes<T> added(Iterable<T> ch) {
        return new Changes.Builder<T>().added(ch).build();
    }

    @Override
    public Registry<T> removeListener(ChangeListener<T> changeListener) {
        announcer.removeListener(changeListener);
        return this;
    }

    protected static <T extends Identifiable> Changes<T> changes(Iterable<T> newResources, Iterable<T> currentResources) {
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
