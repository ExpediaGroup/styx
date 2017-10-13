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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * An event processor that is implemented using Queue Drain approach.
 *
 * @param <S> state type
 */
public class QueueDrainingEventProcessor<S> extends AbstractEventProcessor<S> {
    private final Queue<Object> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger eventCount = new AtomicInteger(0);

    /**
     * Constructs a new instance.
     *
     * @param stateMachine  state machine
     * @param errorHandler  handler to use if an exception/error is thrown by the state machine (e.g. during a transition side-effect)
     * @param loggingPrefix a prefix to prepend to the beginning of log lines
     */
    public QueueDrainingEventProcessor(StateMachine<S> stateMachine, BiConsumer<Throwable, S> errorHandler, String loggingPrefix) {
        super(stateMachine, errorHandler, loggingPrefix);
    }

    @Override
    void eventSubmitted(Object event) {
        events.add(event);
        if (eventCount.getAndIncrement() == 0) {
            do {
                Object e = events.poll();
                processEvent(e);
            } while (eventCount.decrementAndGet() > 0);
        }
    }
}
