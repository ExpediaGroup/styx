/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.common

import java.util.function.BiFunction
import java.util.function.Function


/**
 * A general-purpose state-machine.
 *
 * @param <S> state type
 */
class StateMachine<S> private constructor(
    initialState: S,
    private val transitions: Map<Key<S>, Function<Any, S>>,
    private val inappropriateEventHandler: BiFunction<S, Any, S>,
    private val stateChangeListener: StateChangeListener<S>
) {
    /**
     * Current state.
     */
    @Volatile
    @get:JvmName("currentState")
    var currentState: S = initialState
        private set

    /**
     * Handles an event by performing the a state transition and side-effects associated with the event's type.
     *
     * @param event         an event
     * @param loggingPrefix a prefix to prepend to the beginning of log lines
     */
    /**
     * Handles an event by performing the a state transition and side-effects associated with the event's type.
     *
     * @param event an event
     */
    fun handle(event: Any) {
        val transition = transitions[Key(currentState, event.javaClass)]
        val oldState = currentState
        currentState = if (transition == null) {
            inappropriateEventHandler.apply(oldState, event)
        } else {
            transition.apply(event)
        }
        stateChangeListener.onStateChange(oldState, currentState, event)
    }

    private data class Key<S>(private val state: S, private val eventClass: Class<*>)

    /**
     * StateMachine builder.
     *
     * @param <S> state type
    </S> */
    class Builder<S> {
        private val stateEventHandlers: MutableMap<Key<S>, Function<Any, S>> = HashMap()
        private var inappropriateEventHandler: BiFunction<S, Any, S>? = null
        private var initialState: S? = null
        private var stateChangeListener = StateChangeListener<S> { _, _, _ -> }

        /**
         * Sets the state that the state-machine should start in.
         *
         * @param initialState initial state
         * @return this builder
         */
        fun initialState(initialState: S): Builder<S> {
            this.initialState = initialState
            return this
        }

        /**
         * Associates a state and event type with a function that returns a new state and possibly side-effects.
         *
         * @param state      state to transition from
         * @param eventClass event class
         * @param mapper     function that returns the new state
         * @param <E>        event type
         * @return this builder
        </E> */
        fun <E> transition(state: S, eventClass: Class<E>, mapper: Function<E, S>): Builder<S> {
            stateEventHandlers[Key(state, eventClass)] = Function { mapper.apply(it as E) }
            return this
        }

        /**
         * Determines how to handle an inappropriate event. That is, an event that has no transition associated with the current state.
         *
         * @param mapper function that returns the new state
         * @param <E>    event type
         * @return this builder
        </E> */
        fun <E> onInappropriateEvent(mapper: BiFunction<S, E, S>): Builder<S> {
            inappropriateEventHandler = BiFunction { state, event -> mapper.apply(state, event as E) }
            return this
        }

        /**
         * Add state-change-listener to be informed about state changes, including due to inappropriate events.
         *
         * @param stateChangeListener state-change-listener
         * @return this builder
         */
        fun onStateChange(stateChangeListener: StateChangeListener<S>): Builder<S> {
            this.stateChangeListener = stateChangeListener
            return this
        }

        /**
         * Builds a new state-machine with on the configuration provided to this builder.
         *
         * @return a new state-machine
         */
        fun build(): StateMachine<S> {
            return StateMachine(initialState!!, stateEventHandlers, inappropriateEventHandler!!, stateChangeListener)
        }
    }
}
