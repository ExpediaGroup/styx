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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * An event processor that is implemented using Queue Drain approach.
 *
 */
public class QueueDrainingEventProcessor implements EventProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueDrainingEventProcessor.class);

    private final Queue<Object> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger eventCount = new AtomicInteger(0);
    private final EventProcessor eventProcessor;
    private boolean logErrors;

    public QueueDrainingEventProcessor(EventProcessor eventProcessor) {
        this(eventProcessor, false);
    }

    public QueueDrainingEventProcessor(EventProcessor eventProcessor, boolean logErrors) {
        this.eventProcessor = requireNonNull(eventProcessor);
        this.logErrors = logErrors;
    }

    @Override
    public void submit(Object event) {
        events.add(event);
        if (eventCount.getAndIncrement() == 0) {
            do {
                Object e = events.poll();
                try {
                    eventProcessor.submit(e);
                } catch (RuntimeException cause) {
                    if (logErrors) {
                        LOGGER.warn("Event {} threw an exception {}.", event, cause);
                    }
                }
            } while (eventCount.decrementAndGet() > 0);
        }
    }
}
