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
package com.hotels.styx.common;

import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.common.QueueDrainingEventProcessorTest.ProcessedEvent.processedEvent;
import static com.hotels.styx.common.QueueDrainingEventProcessorTest.State.INIT;
import static com.hotels.styx.common.QueueDrainingEventProcessorTest.SubmissionEvent.DO_NOTHING;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class QueueDrainingEventProcessorTest {
    enum State {
        INIT
    }

    private BiConsumer<Throwable, State> errorHandler;
    private BlockingQueue<ProcessedEvent> processedEvents;
    private StateMachine<State> stateMachine;
    private QueueDrainingEventProcessor<State> eventProcessor;
    private CountDownLatch event1Processing;
    private CountDownLatch event2Submitted;

    @BeforeMethod
    public void setUp() throws Exception {
        errorHandler = mock(BiConsumer.class);
        processedEvents = new LinkedBlockingQueue<>();

        stateMachine = new StateMachine.Builder<State>()
                .initialState(INIT)
                .transition(INIT, SubmissionEvent.class, event -> {
                    event.handle();
                    processedEvents.add(new ProcessedEvent(event));
                    return INIT;
                })
                .transition(INIT, ErrorEvent.class, event -> {
                    throw new RuntimeException("Simulating error during state transition");
                })
                .onInappropriateEvent((x, y) -> INIT)
                .build();

        eventProcessor = new QueueDrainingEventProcessor<>(stateMachine, errorHandler, "myPrefix");
        event1Processing = new CountDownLatch(1);
        event2Submitted = new CountDownLatch(1);
    }

    private void eventSubmittedToNonEmptyQueueTest() throws Exception {
        //
        // When an event is submitted to a non-empty queue, the event is queued, and processed later on
        // by the thread currently draining the queue.
        //
        startThread(
                () -> {
                    // Wait until "submitter-1" starts processing Event 1.
                    waitForLatch(event1Processing);

                    eventProcessor.submit(new SubmissionEvent("event2", DO_NOTHING));
                    eventProcessor.submit(new SubmissionEvent("event3", DO_NOTHING));

                    // Resume submitter-1, and let it to start processing remaining:
                    event2Submitted.countDown();
                }, "submitter-2");

        startThread(
                () -> eventProcessor.submit(new SubmissionEvent("event1", (ignore) -> {
                    event1Processing.countDown();
                    waitForLatch(event2Submitted);
                })), "submitter-1");

        ProcessedEvent result = processedEvents.take();
        if (result.submissionEvent.name.equals("event2")) {
            System.out.println("Remaining events in queue: ");
            ProcessedEvent event = processedEvents.poll();
            while (event != null) {
                System.out.println("event: " + event);
                event = processedEvents.poll();
            }
        }
        assertThat(result, is(processedEvent("event1", "submitter-1", "submitter-1")));

        result = processedEvents.take();
        assertThat(result, is(processedEvent("event2", "submitter-2", "submitter-1")));

        result = processedEvents.take();
        assertThat(result, is(processedEvent("event3", "submitter-2", "submitter-1")));

    }

    @Test
    public void eventSubmittedToNonEmptyQueue() throws Exception {
        for (int i = 0; i < 10000; i++) {
            setUp();
            eventSubmittedToNonEmptyQueueTest();
        }
    }

    @Test
    public void eventSubmittedToAnEmptyQueue() throws Exception {
        //
        // When event is submitted to an empty queue, it gets straight away processed by the submitting thread.
        //
        startThread(() -> eventProcessor.submit(new SubmissionEvent("event2", DO_NOTHING)), "submitter-1");

        ProcessedEvent result = processedEvents.take();
        assertThat(result, is(processedEvent("event2", "submitter-1", "submitter-1")));
    }

    @Test
    public void eventProcessingInvokesErrorHandler() throws Exception {
        //
        // Invokes error handler when an event throws an exception.
        //
        CountDownLatch latch = new CountDownLatch(1);

        startThread(() -> {
            eventProcessor.submit(new ErrorEvent());
            latch.countDown();
        }, "submitter-1");

        latch.await(5, SECONDS);
        verify(errorHandler).accept(any(RuntimeException.class), eq(INIT));
    }

    private static void startThread(Runnable runnable, String name) {
        new Thread(runnable, name).start();
    }

    static class SubmissionEvent {
        static final Consumer<Void> DO_NOTHING = (ignore) -> {};

        private final String name;
        private final Consumer<Void> action;

        String submitter;

        SubmissionEvent(String name, Consumer<Void> action) {
            this(name, Thread.currentThread().getName(), action);
        }

        SubmissionEvent(String name, String submitter) {
            this(name, submitter, DO_NOTHING);
        }

        SubmissionEvent(String name, String submitter, Consumer<Void> action) {
            this.name = name;
            this.submitter = submitter;
            this.action = action;
        }

        void handle() {
            action.accept(null);
        }
    }

    static class ProcessedEvent {
        private final SubmissionEvent submissionEvent;
        String processor;

        static ProcessedEvent processedEvent(String name, String submitter, String processor) {
            return new ProcessedEvent(processor, new SubmissionEvent(name, submitter));
        }

        ProcessedEvent(String processor, SubmissionEvent submissionEvent) {
            this.processor = processor;
            this.submissionEvent = submissionEvent;
        }

        ProcessedEvent(SubmissionEvent submissionEvent) {
            this(Thread.currentThread().getName(), submissionEvent);
        }

        @Override
        public String toString() {
            return com.google.common.base.Objects.toStringHelper(this)
                    .add("name", submissionEvent.name)
                    .add("submitter", submissionEvent.submitter)
                    .add("processor", processor)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProcessedEvent that = (ProcessedEvent) o;
            return Objects.equals(submissionEvent.name, that.submissionEvent.name) &&
                    Objects.equals(submissionEvent.submitter, that.submissionEvent.submitter) &&
                    Objects.equals(processor, that.processor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(submissionEvent.name, submissionEvent.submitter, processor);
        }
    }

    private static class ErrorEvent {
    }

    private void waitForLatch(CountDownLatch latch) {
        try {
            latch.await(5, SECONDS);
        } catch (InterruptedException e) {
            propagate(e);
        }
    }

}