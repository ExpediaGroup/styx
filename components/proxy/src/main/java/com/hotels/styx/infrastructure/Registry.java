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

import com.google.common.collect.Iterables;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.Identifiable;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ServiceFactory;

import java.util.EventListener;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.infrastructure.Registry.Outcome.RELOADED;
import static com.hotels.styx.infrastructure.Registry.Outcome.UNCHANGED;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * Registry for resources of type {@code T}.
 *
 * @param <T> the type of resource to store
 */
public interface Registry<T extends Identifiable> extends Supplier<Iterable<T>> {

    /**
     * Register a {@link com.hotels.styx.infrastructure.Registry.ChangeListener} to be notified when registry changes.
     *
     * @param changeListener {@link com.hotels.styx.infrastructure.Registry.ChangeListener} to register.
     */
    Registry<T> addListener(ChangeListener<T> changeListener);

    /**
     * Remove a {@link com.hotels.styx.infrastructure.Registry.ChangeListener}.
     *
     * @param changeListener {@link com.hotels.styx.infrastructure.Registry.ChangeListener} to remove.
     */
    Registry<T> removeListener(ChangeListener<T> changeListener);

    CompletableFuture<ReloadResult> reload();

    /**
     * Factory for creating a registry.
     *
     * @param <T>
     */
    interface Factory<T extends Identifiable> extends ServiceFactory<Registry<T>> {
        Registry<T> create(Environment environment, Configuration registryConfiguration);
    }

    /**
     * A base interface for notification of a change in the registry.
     *
     * @param <T> The type of configuration referred to by this ChangeListener
     */
    interface ChangeListener<T extends Identifiable> extends EventListener {

        void onChange(Changes<T> changes);

        void onError(Throwable ex);
    }

    /**
     * The set of changes between reloads.
     *
     * @param <T>
     */
    final class Changes<T extends Identifiable> {
        private final Iterable<T> added;
        private final Iterable<T> removed;
        private final Iterable<T> updated;

        private Changes(Builder builder) {
            this.added = nullToEmpty(builder.added);
            this.removed = nullToEmpty(builder.removed);
            this.updated = nullToEmpty(builder.updated);
        }

        private static <T> Iterable<T> nullToEmpty(Iterable iterable) {
            return iterable != null ? iterable : emptyList();
        }

        public Iterable<T> added() {
            return added;
        }

        public Iterable<T> removed() {
            return removed;
        }

        public Iterable<T> updated() {
            return updated;
        }

        @Override
        public int hashCode() {
            return Objects.hash(added, removed, updated);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Changes<T> other = (Changes<T>) obj;

            return equal(this.added, other.added)
                    && equal(this.removed, other.removed)
                    && equal(this.updated, other.updated);
        }

        private boolean equal(Iterable<T> iterable1, Iterable<T> iterable2) {
            return Iterables.size(iterable1) == Iterables.size(iterable2) && containsAll(iterable1, iterable2);
        }

        private boolean containsAll(Iterable<T> iterable1, Iterable<T> iterable2) {
            for (T item : iterable2) {
                if (!Iterables.contains(iterable1, item)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("added", added)
                    .add("removed", removed)
                    .add("updated", updated)
                    .toString();
        }

        public Iterable<T> addedAndUpdated() {
            return Iterables.concat(added, updated);
        }

        public boolean isEmpty() {
            return Iterables.isEmpty(added) && Iterables.isEmpty(removed) && Iterables.isEmpty(updated);
        }

        public static class Builder<T extends Identifiable> {
            private Iterable<T> added;
            private Iterable<T> removed;
            private Iterable<T> updated;

            public Builder<T> added(T... added) {
                return added(asList(added));
            }

            public Builder<T> added(Iterable<T> added) {
                this.added = checkNotNull(added);
                return this;
            }

            public Builder<T> removed(T... added) {
                return removed(asList(added));
            }

            public Builder<T> removed(Iterable<T> removed) {
                this.removed = checkNotNull(removed);
                return this;
            }

            public Builder<T> updated(T... updated) {
                return updated(asList(updated));
            }

            public Builder<T> updated(Iterable<T> updated) {
                this.updated = checkNotNull(updated);
                return this;
            }

            public Changes<T> build() {
                return new Changes<>(this);
            }
        }
    }

    enum Outcome {
        RELOADED,
        UNCHANGED
    }

    class ReloadResult {

        Outcome outcome;
        String message;

        private ReloadResult(Outcome outcome, String message) {
            this.outcome = outcome;
            this.message = message;
        }

        public static ReloadResult reloaded(String message) {
            return new ReloadResult(RELOADED, message);
        }

        public static ReloadResult unchanged(String message) {
            return new ReloadResult(UNCHANGED, message);
        }

        public Outcome outcome() {
            return outcome;
        }

        public String message() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ReloadResult that = (ReloadResult) o;
            return outcome == that.outcome && Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outcome, message);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ReloadResult{");
            sb.append("outcome=").append(outcome);
            sb.append(", message='").append(message).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
