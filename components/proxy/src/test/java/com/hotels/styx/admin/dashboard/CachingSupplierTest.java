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
package com.hotels.styx.admin.dashboard;

import com.hotels.styx.admin.CachingSupplier;
import com.hotels.styx.api.Clock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class CachingSupplierTest {
    CachingSupplier<Object> supplier;

    @Test
    public void dataIsNullIfNoUpdatesAreReceived() {
        Supplier<Object> dataSupplier = () -> "This should not be returned";
        Clock frozenAtTimeZero = () -> 0;

        supplier = new CachingSupplier<>(dataSupplier, Duration.ofSeconds(10), frozenAtTimeZero);

        assertThat(supplier.get(), is(nullValue()));
    }

    @Test(dataProvider = "lessThanTenSeconds")
    public void dataDoesNotUpdateWhenLessThanExpirationTimePasses(long time, TimeUnit timeUnit) {
        Supplier<Integer> dataSupplier = sequence(1, 2, 3, 4, 5);
        SettableClock clock = new SettableClock();
        clock.advanceTime(1, HOURS);

        supplier = new CachingSupplier<>(dataSupplier, Duration.ofSeconds(10), clock);
        assertThat(supplier.get(), is(1));

        clock.advanceTime(time, timeUnit);
        assertThat(supplier.get(), is(1));
    }

    @Test(dataProvider = "atLeastTenSeconds")
    public void dataUpdatesWhenAtLeastExpirationTimePasses(long time, TimeUnit timeUnit) {
        Supplier<Integer> dataSupplier = sequence(1, 2, 3, 4, 5);
        SettableClock clock = new SettableClock();
        clock.advanceTime(1, HOURS);

        supplier = new CachingSupplier<>(dataSupplier, Duration.ofSeconds(10), clock);
        assertThat(supplier.get(), is(1));

        clock.advanceTime(time, timeUnit);
        assertThat(supplier.get(), is(2));
    }

    @DataProvider()
    private Object[][] lessThanTenSeconds() {
        return new Object[][]{
                {0, SECONDS},
                {500, MILLISECONDS},
                {1, SECONDS},
                {2347, MILLISECONDS},
                {5038, MILLISECONDS},
                {9999, MILLISECONDS}
        };
    }

    @DataProvider()
    private Object[][] atLeastTenSeconds() {
        return new Object[][]{
                {10, SECONDS},
                {10500, MILLISECONDS},
                {17, SECONDS},
                {3, MINUTES},
                {12, HOURS}
        };
    }

    private static <T> Supplier<T> sequence(T... items) {
        Queue<T> sequence = new LinkedList<T>(asList(items));

        return sequence::poll;
    }

    static class SettableClock implements Clock {
        private long timeMillis;

        public void advanceTime(long time, TimeUnit timeUnit) {
            timeMillis += timeUnit.toMillis(time);
        }

        @Override
        public long tickMillis() {
            return timeMillis;
        }
    }
}