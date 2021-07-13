/*
  Copyright (C) 2013-2021 Expedia Inc.

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

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

    @ParameterizedTest
    @MethodSource("lessThanTenSeconds")
    public void dataDoesNotUpdateWhenLessThanExpirationTimePasses(long time, TimeUnit timeUnit) {
        Supplier<Integer> dataSupplier = sequence(1, 2, 3, 4, 5);
        SettableClock clock = new SettableClock();
        clock.advanceTime(1, HOURS);

        supplier = new CachingSupplier<>(dataSupplier, Duration.ofSeconds(10), clock);
        assertThat(supplier.get(), is(1));

        clock.advanceTime(time, timeUnit);
        assertThat(supplier.get(), is(1));
    }

    @ParameterizedTest
    @MethodSource("atLeastTenSeconds")
    public void dataUpdatesWhenAtLeastExpirationTimePasses(long time, TimeUnit timeUnit) {
        Supplier<Integer> dataSupplier = sequence(1, 2, 3, 4, 5);
        SettableClock clock = new SettableClock();
        clock.advanceTime(1, HOURS);

        supplier = new CachingSupplier<>(dataSupplier, Duration.ofSeconds(10), clock);
        assertThat(supplier.get(), is(1));

        clock.advanceTime(time, timeUnit);
        assertThat(supplier.get(), is(2));
    }

    private static Stream<Arguments> lessThanTenSeconds() {
        return Stream.of(
                Arguments.of(0, SECONDS),
                Arguments.of(500, MILLISECONDS),
                Arguments.of(1, SECONDS),
                Arguments.of(2347, MILLISECONDS),
                Arguments.of(5038, MILLISECONDS),
                Arguments.of(9999, MILLISECONDS)
        );
    }

    private static Stream<Arguments> atLeastTenSeconds() {
        return Stream.of(
                Arguments.of(10, SECONDS),
                Arguments.of(10500, MILLISECONDS),
                Arguments.of(17, SECONDS),
                Arguments.of(3, MINUTES),
                Arguments.of(12, HOURS)
        );
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