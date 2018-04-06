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

import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.BiConsumer;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class FsmEventProcessorTest {
    private BiConsumer<Throwable, String> errorHandler;
    private StateMachine<String> stateMachine;
    private LoggingTestSupport logger;

    @BeforeMethod
    public void setUp() {
        stateMachine = new StateMachine.Builder<String>()
                .initialState("start")
                .transition( "start", TestEventOk.class, event -> "end")
                .transition( "start", TestEventError.class, event -> {
                    throw new RuntimeException("Test exception message");
                })
                .onInappropriateEvent((x, y) -> "error")
                .build();

        errorHandler = mock(BiConsumer.class);
        logger = new LoggingTestSupport(FsmEventProcessor.class);
    }


    @AfterMethod
    public void tearDown() {
        logger.stop();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void throwsNullPointerExceptionForNullEvents() {
        FsmEventProcessor<String> processor = new FsmEventProcessor<>(stateMachine, errorHandler, "prefix");

        processor.submit(null);
    }

    @Test
    public void propagatesEventToFsm() {
        FsmEventProcessor<String> processor = new FsmEventProcessor<>(stateMachine, errorHandler, "prefix");

        processor.submit(new TestEventOk());

        assertThat(stateMachine.currentState(), is("end"));
    }

    @Test
    public void handlesStateMachineExceptions() {
        FsmEventProcessor<String> processor = new FsmEventProcessor<>(stateMachine, errorHandler, "prefix");

        processor.submit(new TestEventError());

        verify(errorHandler).accept(any(RuntimeException.class), eq("start"));
        assertThat(logger.lastMessage(), is(
                loggingEvent(
                        ERROR,
                        "prefix start -> \\?\\?\\?: Exception occurred while processing event=TestEventError. Cause=.*",
                        RuntimeException.class,
                        "Test exception message")));
    }

    class TestEventOk {

    }

    class TestEventError {

    }

}
