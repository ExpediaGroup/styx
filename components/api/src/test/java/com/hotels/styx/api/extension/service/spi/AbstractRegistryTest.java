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
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;
import org.testng.annotations.Test;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.spi.AbstractRegistryTest.IdObject.idObject;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class AbstractRegistryTest {

    @Test
    public void notifiesOfInitialChanges() {
        TestRegistry registry = new TestRegistry();
        AbstractRegistry.ChangeListener<IdObject> listener1 = mock(AbstractRegistry.ChangeListener.class);
        AbstractRegistry.ChangeListener<IdObject> listener2 = mock(AbstractRegistry.ChangeListener.class);

        registry.addListener(listener1);
        registry.addListener(listener2);

        registry.set(ImmutableList.of(idObject("a", "1"), idObject("b", "2")));

        Registry.Changes<IdObject> changes = changeSet()
                .added(idObject("a", "1"), idObject("b", "2"))
                .build();

        verify(listener1).onChange(eq(changes));
        verify(listener2).onChange(eq(changes));
    }

    @Test
    public void notifiesNewListenersImmediately() {
        TestRegistry registry = new TestRegistry();
        registry.set(ImmutableList.of(idObject("a", "1"), idObject("b", "2")));

        AbstractRegistry.ChangeListener<IdObject> listener1 = mock(AbstractRegistry.ChangeListener.class);
        registry.addListener(listener1);

        Registry.Changes<IdObject> changes = changeSet()
                .added(idObject("a", "1"), idObject("b", "2"))
                .build();

        verify(listener1).onChange(eq(changes));
    }

    @Test
    public void notifiesOfModifiedEntries() {
        TestRegistry registry = new TestRegistry();

        AbstractRegistry.ChangeListener<IdObject> listener1 = mock(AbstractRegistry.ChangeListener.class);
        registry.addListener(listener1);

        registry.set(ImmutableList.of(idObject("a", "1")));

        registry.set(ImmutableList.of(idObject("a", "2"), idObject("b", "2")));
        verify(listener1).onChange(eq(changeSet()
                .added(idObject("b", "2"))
                .updated(idObject("a", "2"))
                .build()));
    }

    @Test
    public void identifiableIsNotConsideredChangedWhenItsValueIsNotChanged() {
        TestRegistry registry = new TestRegistry();

        AbstractRegistry.ChangeListener<IdObject> listener1 = mock(AbstractRegistry.ChangeListener.class);
        registry.addListener(listener1);
        verify(listener1).onChange(any(Registry.Changes.class));

        registry.set(ImmutableList.of(idObject("a", "1"), idObject("b", "2")));
        verify(listener1, times(2)).onChange(any(Registry.Changes.class));

        // Does not change:
        registry.set(ImmutableList.of(idObject("a", "1"), idObject("b", "2")));
        verifyNoMoreInteractions(listener1);
    }

    @Test
    public void notifiesOfRemovedEntries() {
        TestRegistry registry = new TestRegistry();

        AbstractRegistry.ChangeListener<IdObject> listener1 = mock(AbstractRegistry.ChangeListener.class);
        AbstractRegistry.ChangeListener<IdObject> listener2 = mock(AbstractRegistry.ChangeListener.class);
        registry.addListener(listener1);
        registry.addListener(listener2);

        registry.set(ImmutableList.of(idObject("a", "1"), idObject("b", "2")));

        registry.set(ImmutableList.of(idObject("a", "1")));

        verify(listener1).onChange(changeSet().removed(idObject("b", "2")).build());
        verify(listener2).onChange(changeSet().removed(idObject("b", "2")).build());
    }


    @Test
    public void doesNotNotifyRemovedListeners() {
        TestRegistry registry = new TestRegistry();

        AbstractRegistry.ChangeListener<IdObject> listener1 = mock(AbstractRegistry.ChangeListener.class);
        AbstractRegistry.ChangeListener<IdObject> listener2 = mock(AbstractRegistry.ChangeListener.class);
        registry.addListener(listener1);
        registry.addListener(listener2);

        registry.set(ImmutableList.of(idObject("a", "1"), idObject("b", "2")));

        verify(listener1).onChange(changeSet().added(idObject("a", "1"), idObject("b", "2")).build());
        verify(listener2).onChange(changeSet().added(idObject("a", "1"), idObject("b", "2")).build());

        registry.removeListener(listener2);

        registry.set(ImmutableList.of(idObject("a", "1")));
        verify(listener1).onChange(changeSet().removed(idObject("b", "2")).build());
        verify(listener2, never()).onChange(changeSet().removed(idObject("b", "2")).build());

    }

    @Test
    public void calculatesTheDifferenceBetweenCurrentAndNewResources() {
        Iterable<BackendService> newResources = singletonList(backendService("one", 9090));
        Iterable<BackendService> currentResources = singletonList(backendService("two", 9091));
        Registry.Changes<Identifiable> expected = new Registry.Changes.Builder<>()
                .added(backendService("one", 9090))
                .removed(backendService("two", 9091))
                .build();

        Registry.Changes<BackendService> changes = AbstractRegistry.changes(newResources, currentResources);
        assertThat(changes.toString(), is(expected.toString()));
    }

    private BackendService backendService(String id, int port) {
        return new BackendService.Builder()
                .id(id)
                .origins(newOrigin(port))
                .build();
    }

    private Origin newOrigin(int port) {
        return newOriginBuilder("localhost", port).build();
    }

    private Registry.Changes.Builder<IdObject> changeSet() {
        return new Registry.Changes.Builder<IdObject>();
    }

    static class TestRegistry extends AbstractRegistry<IdObject> {
        @Override
        public CompletableFuture<ReloadResult> reload() {
            return null;
        }
    }

    static class IdObject implements Identifiable {
        private String key;
        private String value;

        private IdObject(String key, String value) {
            this.key = key;
            this.value = value;
        }

        static IdObject idObject(String key, String value) {
            return new IdObject(key, value);
        }

        public String key() {
            return key;
        }

        public String value() {
            return value;
        }

        @Override
        public Id id() {
            return Id.id(key);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IdObject idObject = (IdObject) o;
            return Objects.equals(key, idObject.key) &&
                    Objects.equals(value, idObject.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("IdObject{");
            sb.append("key='").append(key).append('\'');
            sb.append("value='").append(value).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}