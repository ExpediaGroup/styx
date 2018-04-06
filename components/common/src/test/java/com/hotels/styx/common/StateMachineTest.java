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
package com.hotels.styx.common;

import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hotels.styx.common.StateMachineTest.State.EXPECTED_RESULT;
import static com.hotels.styx.common.StateMachineTest.State.STARTED;
import static com.hotels.styx.common.StateMachineTest.State.TEST_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StateMachineTest {
    private StateMachine.Builder<State> stateMachineBuilder;

    @BeforeMethod
    public void setUp() {
        stateMachineBuilder = new StateMachine.Builder<State>()
                .initialState(STARTED)
                .onInappropriateEvent((state, event) -> TEST_FAILED);
    }

    enum State {
        STARTED,
        EXPECTED_RESULT,
        TEST_FAILED;
    }

    @Test
    public void startsInInitialState() {
        StateMachine<State> stateMachine = stateMachineBuilder.build();

        assertThat(stateMachine.currentState(), Matchers.is(STARTED));
    }

    @Test
    public void handlesInappropriateEvents() {
        BiFunction<State, Object, State> inappropriateEventHandler = mock(BiFunction.class);
        when(inappropriateEventHandler.apply(any(State.class), any(Object.class))).thenReturn(EXPECTED_RESULT);

        StateMachine<State> stateMachine = stateMachineBuilder
                .onInappropriateEvent(inappropriateEventHandler)
                .build();

        stateMachine.handle(new TestEvent(), "");

        assertThat(stateMachine.currentState(), Matchers.is(EXPECTED_RESULT));
        verify(inappropriateEventHandler).apply(eq(STARTED), any(TestEvent.class));
    }

    @Test
    public void performsStateTransitions() {
        Function<TestEvent, State> mapper = mock(Function.class);
        when(mapper.apply(any(TestEvent.class))).thenReturn(EXPECTED_RESULT);

        StateMachine<State> stateMachine = stateMachineBuilder
                .transition(STARTED, TestEvent.class, mapper)
                .build();

        stateMachine.handle(new TestEvent(), "");

        assertThat(stateMachine.currentState(), Matchers.is(EXPECTED_RESULT));
        verify(mapper).apply(any(TestEvent.class));
    }

    private static class TestEvent {

    }
}