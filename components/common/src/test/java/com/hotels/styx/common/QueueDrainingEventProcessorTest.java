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
        for (int i = 0; i < 1000; i++) {
            CyclicBarrier barrier1 = new CyclicBarrier(2);
            CyclicBarrier barrier2 = new CyclicBarrier(2);

            QueueDrainingEventProcessor eventProcessor = new QueueDrainingEventProcessor((event) -> ((Consumer<Void>) event).accept(null));

            Consumer<Void> event1 = mock(Consumer.class);
            Consumer<Void> event2 = mock(Consumer.class);
            Consumer<Void> event3 = mock(Consumer.class);

            startThread(() -> {
                await(barrier1);
                eventProcessor.submit(event2);
                eventProcessor.submit(event3);
                await(barrier2);
            });

            eventProcessor.submit(consumerEvent((x) -> {
                await(barrier1);
                event1.accept(null);
                await(barrier2);
            }));

            InOrder inOrder = Mockito.inOrder(event1, event2, event3);
            inOrder.verify(event1).accept(null);
            inOrder.verify(event2).accept(null);
            inOrder.verify(event3).accept(null);
        }
    }

    @Test
    public void handlesEventProcessorExceptions() throws Exception {
        for (int i = 0; i < 1000; i++) {
            CyclicBarrier barrier1 = new CyclicBarrier(2);
            CyclicBarrier barrier2 = new CyclicBarrier(2);
            CyclicBarrier barrier3 = new CyclicBarrier(2);

            QueueDrainingEventProcessor eventProcessor = new QueueDrainingEventProcessor((event) -> ((Consumer<Void>) event).accept(null), false);

            startThread(
                    () -> {
                        Consumer<Void> lockEvent = consumerEvent((x) -> {
                            await(barrier1);
                            try {
                                throw new RuntimeException("Something went wrong");
                            } finally {
                                await(barrier2);
                            }
                        });
                        eventProcessor.submit(lockEvent);
                    });

            barrier1.await();

            Consumer<Void> event2 = mock(Consumer.class);
            eventProcessor.submit(consumerEvent(x -> {
                event2.accept(null);
                await(barrier3);
            }));

            await(barrier2);
            await(barrier3);

            verify(event2).accept(eq(null));
        }
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            currentThread().interrupt();
            ;
            throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
            throw new RuntimeException(e);
        }

    }

    private <T> Consumer<T> consumerEvent(Consumer<T> action) {
        return action;
    }

    private static void startThread(Runnable runnable) {
        new Thread(runnable, "test-thread").start();
    }

}