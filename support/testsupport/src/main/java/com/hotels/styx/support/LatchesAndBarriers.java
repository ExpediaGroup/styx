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
package com.hotels.styx.support;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;

/**
 * Makes `CountDownLatch` more convenient for unit tests.
 */
public final class LatchesAndBarriers {
    private LatchesAndBarriers() {
    }

    public static void await(CountDownLatch latch, long timeout, TimeUnit unit) throws IllegalStateException {
        try {
            boolean completed = latch.await(timeout, unit);

            if (!completed) {
                throw new IllegalStateException(format("Latch timed out: max wait was %s %s", timeout, unit));
            }
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public static void await(CyclicBarrier barrier, long timeout, TimeUnit unit) throws IllegalStateException {
        try {
            barrier.await(timeout, unit);
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
            throw new IllegalStateException(e);
        } catch (TimeoutException e) {
            throw new IllegalStateException(format("Barrier timed out: max wait was %s %s", timeout, unit), e);
        }
    }
}
