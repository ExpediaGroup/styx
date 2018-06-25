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

import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/*
  Please note -
  Only our syntactically sugared "Latch" class is being tested in this class.
  When you see CountDownLatch appear, it is purely for the test to check the concurrency behaviour
  - it is not the same latch being wrapped by our new class.
 */
public class LatchTest {

    @Test
    public void awaitCompletesUponCountDown() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);

        Latch latch = new Latch(1);

        new Thread(() -> {
            latch.await();
            completed.countDown();
        }).start();

        latch.countDown();

        assertThat("Timed out", completed.await(1, SECONDS), is(true));
    }

    @Test
    public void timedAwaitCompletesUponCountDown() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);

        Latch latch = new Latch(1);

        new Thread(() -> {
            latch.await(10, SECONDS); // any duration will do as we don't plan to actually time out
            completed.countDown();
        }).start();

        latch.countDown();

        assertThat("Timed out", completed.await(1, SECONDS), is(true));
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Timed out")
    public void timeOutCausesException() {
        Latch latch = new Latch(1);

        latch.await(1, MILLISECONDS);
    }
}