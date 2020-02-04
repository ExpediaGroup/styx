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
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exectuor that queues runnable tasks and executes them in order in a threadsafe manner.
 */
public class QueueDrainingExecutor implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(com.hotels.styx.common.QueueDrainingExecutor.class);
    private final Queue<Runnable> tasks = new ConcurrentLinkedDeque<>();
    private final AtomicInteger taskCount = new AtomicInteger(0);

    @Override
    public void execute(Runnable r) {
        tasks.add(r);
        if (taskCount.getAndIncrement() == 0) {
            do {
                Runnable task = tasks.poll();
                try {
                    task.run();
                } catch (RuntimeException cause) {
                    LOGGER.warn("Task {} threw an exception {}.", task, cause);
                }
            } while (taskCount.decrementAndGet() > 0);
        }
    }
}
