/**
 * Copyright (C) 2013-2018 Expedia Inc.
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

import org.mockito.InOrder;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class QueueDrainingEventProcessorTest {

    @Test
    public void processesEvents() {
        QueueDrainingEventProcessor eventProcessor = new QueueDrainingEventProcessor((event) -> ((Consumer<Void>) event).accept(null));

        Consumer<Void> event1 = mock(Consumer.class);
        eventProcessor.submit(event1);

        Consumer<Void> event2 = mock(Consumer.class);
        eventProcessor.submit(event2);

        InOrder inOrder = Mockito.inOrder(event1, event2);
        inOrder.verify(event1).accept(null);
        inOrder.verify(event2).accept(null);
    }

    @Test
    public void processesQueuedEvents() {
        CyclicBarrier barrier = new CyclicBarrier(2);

        QueueDrainingEventProcessor eventProcessor = new QueueDrainingEventProcessor((event) -> ((Consumer<Void>) event).accept(null));

        Consumer<Void> event1 = mock(Consumer.class);
        Consumer<Void> event2 = mock(Consumer.class);
        Consumer<Void> event3 = mock(Consumer.class);

        startThread(() -> {
            try {
                barrier.await();
            } catch (InterruptedException e) {
                currentThread().interrupt();
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
            eventProcessor.submit(event2);
            eventProcessor.submit(event3);
        });

        eventProcessor.submit(latchedEvent(barrier, event1));

        InOrder inOrder = Mockito.inOrder(event1, event2, event3);
        inOrder.verify(event1).accept(null);
        inOrder.verify(event2).accept(null);
        inOrder.verify(event3).accept(null);
    }

    @Test
    public void handlesEventProcessorExceptions() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);

        QueueDrainingEventProcessor eventProcessor = new QueueDrainingEventProcessor((event) -> ((Consumer<Void>) event).accept(null));

        startThread(
                () -> {
                    Consumer<Void> lockEvent = latchedEvent(barrier, (x) -> {
                        throw new RuntimeException("Something went wrong");
                    });
                    eventProcessor.submit(lockEvent);
                });


        barrier.await();

        Consumer<Void> event2 = mock(Consumer.class);
        eventProcessor.submit(event2);

        verify(event2).accept(eq(null));

    }

    private <T> Consumer<T> latchedEvent(CyclicBarrier barrier, Consumer<T> action) {
        return (actionArg) -> {
            try {
                barrier.await();
                action.accept(actionArg);
            } catch (InterruptedException e) {
                currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
        };
    }


    private static void startThread(Runnable runnable) {
        new Thread(runnable, "test-thread").start();
    }

}