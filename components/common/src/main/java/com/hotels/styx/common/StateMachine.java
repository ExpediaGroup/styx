/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A general-purpose state-machine.
 *
 * @param <S> state type
 */
public final class StateMachine<S> {
    private static final Logger LOGGER = getLogger(StateMachine.class);

    private final Map<Key<S>, Function<Object, S>> transitions;
    private final BiFunction<S, Object, S> inappropriateEventHandler;
    private final StateChangeListener<S> stateChangeListener;

    private volatile S currentState;

    private StateMachine(S initialState, Map<Key<S>, Function<Object, S>> transitions,
            BiFunction<S, Object, S> inappropriateEventHandler, StateChangeListener<S> stateChangeListener) {
        this.currentState = requireNonNull(initialState);
        this.transitions = requireNonNull(transitions);
        this.inappropriateEventHandler = requireNonNull(inappropriateEventHandler);
        this.stateChangeListener = requireNonNull(stateChangeListener);
    }

    /**
     * Returns the current state.
     *
     * @return current state
     */
    public S currentState() {
        return currentState;
    }

    /**
     * Handles an event by performing the a state transition and side-effects associated with the event's type.
     *
     * @param event         an event
     * @param loggingPrefix a prefix to prepend to the beginning of log lines
     */
    public void handle(Object event, String loggingPrefix) {
        Function<Object, S> transition = transitions.get(new Key<>(currentState, event.getClass()));

        S oldState = currentState;
        currentState = transition == null ? inappropriateEventHandler.apply(oldState, event) : transition.apply(event);

        stateChangeListener.onStateChange(oldState, currentState, event);
    }

    /**
     * Handles an event by performing the a state transition and side-effects associated with the event's type.
     *
     * @param event an event
     */
    public void handle(Object event) {
        this.handle(event, "");
    }

    private static final class Key<S> {
        private final S state;
        private final Class<?> eventClass;

        private Key(S state, Class<?> eventClass) {
            this.state = state;
            this.eventClass = eventClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key<?> key = (Key<?>) o;
            return Objects.equals(state, key.state)
                    && Objects.equals(eventClass, key.eventClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, eventClass);
        }
    }

    /**
     * StateMachine builder.
     *
     * @param <S> state type
     */
    public static final class Builder<S> {
        private final Map<Key<S>, Function<Object, S>> stateEventHandlers = new HashMap<>();
        private BiFunction<S, Object, S> inappropriateEventHandler;
        private S initialState;
        private StateChangeListener<S> stateChangeListener = (oldState, newState, event) -> {
        };

        /**
         * Sets the state that the state-machine should start in.
         *
         * @param initialState initial state
         * @return this builder
         */
        public Builder<S> initialState(S initialState) {
            this.initialState = initialState;
            return this;
        }

        /**
         * Associates a state and event type with a function that returns a new state and possibly side-effects.
         *
         * @param state      state to transition from
         * @param eventClass event class
         * @param mapper     function that returns the new state
         * @param <E>        event type
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        public <E> Builder<S> transition(S state, Class<E> eventClass, Function<E, S> mapper) {
            this.stateEventHandlers.put(new Key<>(state, eventClass), event -> mapper.apply((E) event));
            return this;
        }

        /**
         * Determines how to handle an inappropriate event. That is, an event that has no transition associated with the current state.
         *
         * @param mapper function that returns the new state
         * @param <E>    event type
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        public <E> Builder<S> onInappropriateEvent(BiFunction<S, E, S> mapper) {
            this.inappropriateEventHandler = (state, event) -> mapper.apply(state, (E) event);
            return this;
        }

        /**
         * Add state-change-listener to be informed about state changes, including due to inappropriate events.
         *
         * @param stateChangeListener state-change-listener
         * @return this builder
         */
        public Builder<S> onStateChange(StateChangeListener<S> stateChangeListener) {
            this.stateChangeListener = requireNonNull(stateChangeListener);
            return this;
        }

        /**
         * Builds a new state-machine with on the configuration provided to this builder.
         *
         * @return a new state-machine
         */
        public StateMachine<S> build() {
            return new StateMachine<>(initialState, stateEventHandlers, inappropriateEventHandler, stateChangeListener);
        }

        public Builder<S> debugTransitions(String messagePrefix) {
            return this.onStateChange((oldState, newState, event)-> {
                LOGGER.debug("{} {}: {} -> {}", new Object[] {messagePrefix, event, oldState, newState});
            });
        }
    }
}
