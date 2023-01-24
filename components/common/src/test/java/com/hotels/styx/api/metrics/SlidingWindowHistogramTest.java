/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.api.metrics;

import com.hotels.styx.api.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SlidingWindowHistogramTest {
    private static final int INTERVAL_SIZE_MS = 1000;
    private final TestClock clock = new TestClock();
    private final int windowSize = 10;

    @Test
    public void computesMeanFromOneSample() {
        SlidingWindowHistogram histogram = newHistogram(windowSize, INTERVAL_SIZE_MS);
        histogram.recordValue(10);
        assertThat(histogram.getMean(), is(closeTo(10.0, 1.0)));
    }

    @ParameterizedTest
    @MethodSource("getWindowSizeAndInterval")
    public void aggregatesHistogramsOverTwoSeconds(int windowSize, int intervalSize) {
        SlidingWindowHistogram histogram = newHistogram(windowSize, intervalSize);
        histogram.recordValue(10);
        clock.forward(intervalSize);
        histogram.recordValue(20);

        assertThat(histogram.getMean(), is(closeTo(15.0, 1.0)));
    }

    @ParameterizedTest
    @MethodSource("getWindowSizeAndInterval")
    public void expiresSampleAfterSlidingWindowWidth(int windowSize, int intervalSize) {
        SlidingWindowHistogram histogram = newHistogram(windowSize, intervalSize);
        histogram.recordValue(10);

        clock.forward(windowSize * intervalSize);

        assertThat(histogram.getMean(), is(0.0));
    }

    @ParameterizedTest
    @MethodSource("getIntervalSize")
    public void expiresAnOldHistogramIntervalAndKeepsTheCurrentIntervalIntact(int intervalSize) {
        SlidingWindowHistogram histogram = newHistogram(2, intervalSize);

        histogram.recordValue(10);
        clock.forward(intervalSize);

        histogram.recordValue(20);
        assertThat(histogram.getMean(), is(closeTo(15.0, 1.0)));

        clock.forward(intervalSize);
        assertThat(histogram.getMean(), is(closeTo(20.0, 1.0)));

        clock.forward(intervalSize);
        assertThat(histogram.getMean(), is(0.0));
    }


    @ParameterizedTest
    @MethodSource("getIntervalSize")
    public void expiresTheCurrentIntervalIfWindowSizeWrapsAround(int intervalSize) {
        SlidingWindowHistogram histogram = newHistogram(2, intervalSize);

        histogram.recordValue(10);
        assertThat(histogram.getMean(), is(closeTo(10.0, 1.0)));
        clock.forward(2 * intervalSize);

        histogram.recordValue(20);
        assertThat(histogram.getMean(), is(closeTo(20.0, 1.0)));
    }

    @ParameterizedTest
    @MethodSource("getIntervalSize")
    public void doesNotExpireOldValuesOnUpdate(int intervalSize) {
        SlidingWindowHistogram histogram = newHistogram(2, intervalSize);

        histogram.recordValue(10);
        clock.forward(1);
        histogram.recordValue(20);
        clock.forward(2);
        histogram.recordValue(30);
        clock.forward(1);
        assertThat(histogram.getMean(), is(closeTo(20.0, 1.0)));
        clock.forward(intervalSize);

        histogram.recordValue(40);
        clock.forward(1);
        histogram.recordValue(50);
        clock.forward(1);
        histogram.recordValue(60);
        clock.forward(1);
        assertThat(histogram.getMean(), is(closeTo(35.0, 1.0)));
    }


    @ParameterizedTest
    @MethodSource("getIntervalSize")
    public void doesNotExpireOldValuesOnConsecutiveReads(int intervalSize) {
        SlidingWindowHistogram histogram = newHistogram(2, intervalSize);

        histogram.recordValue(10);
        histogram.recordValue(20);
        histogram.recordValue(30);
        assertThat(histogram.getMean(), is(closeTo(20.0, 1.0)));
        clock.forward(intervalSize);

        histogram.recordValue(40);
        histogram.recordValue(50);
        histogram.recordValue(60);
        assertThat(histogram.getMean(), is(closeTo(35.0, 1.0)));
        assertThat(histogram.getMean(), is(closeTo(35.0, 1.0)));
        clock.forward(1);
        assertThat(histogram.getMean(), is(closeTo(35.0, 1.0)));
    }

    @Test
    public void doesNotAcceptNegativeValuesAndContiunesToOperateWithoutLosingRecordedValues() {
        int intervalSize = 100;

        SlidingWindowHistogram histogram = newHistogram(2, intervalSize);

        histogram.recordValue(10);
        histogram.recordValue(20);
        histogram.recordValue(30);
        assertThat(histogram.getMean(), is(closeTo(20.0, 1.0)));

        try {
            histogram.recordValue(-10);
        } catch (Exception e) {
            // Ignore exception
        }
        histogram.recordValue(40);
        assertThat(histogram.getMean(), is(closeTo(25.0, 1.0)));
    }

    @Test
    public void doesNotAcceptNegativeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> newHistogram(2, 2).recordValue(-1));
    }

    @Test
    public void operatesAfterRecordingTooLargeValuesInAggregatedState() {
        int intervalSize = 100;

        SlidingWindowHistogram histogram = newHistogram(2, intervalSize);

        histogram.recordValue(10);
        histogram.recordValue(20);
        histogram.recordValue(30);
        assertThat(histogram.getMean(), is(closeTo(20.0, 1.0)));

        try {
            histogram.recordValue(12389348034090943L);
        } catch (Exception e) {
            // Ignore exception
        }
        histogram.recordValue(40);
        assertThat(histogram.getMean(), is(closeTo(25.0, 1.0)));
    }

    @Test
    public void operatesAfterRecordingNegativeValuesInUpdatedState() {
        int intervalSize = 100;

        SlidingWindowHistogram histogram = newHistogram(2, intervalSize);

        histogram.recordValue(10);
        histogram.recordValue(20);
        histogram.recordValue(30);
        assertThat(histogram.getMean(), is(closeTo(20.0, 1.0)));

        histogram.recordValue(40);

        try {
            histogram.recordValue(-10);
        } catch (Exception e) {
            // Ignore exception
        }
        histogram.recordValue(50);
        assertThat(histogram.getMean(), is(closeTo(30.0, 1.0)));
    }

    @Test
    public void progressesTimeOnIntervalBoundary() {
        int intervalSize = 100;

        SlidingWindowHistogram histogram = newHistogram(1, intervalSize);

        clock.forward(99);
        histogram.recordValue(10);
        assertThat(histogram.getMean(), is(closeTo(10, 1.0)));

        clock.forward(1);
        histogram.recordValue(20);
        assertThat(histogram.getMean(), is(closeTo(20, 1.0)));
    }

    @Test
    public void progressesTimeOnIntervalBoundaryWhenTimeIncresesMoreThanWindowSize() {
        int intervalSize = 100;

        SlidingWindowHistogram histogram = newHistogram(1, intervalSize);

        clock.forward(99);
        histogram.recordValue(10);
        assertThat(histogram.getMean(), is(closeTo(10, 1.0)));

        clock.forward(101);
        histogram.recordValue(20);
        assertThat(histogram.getMean(), is(closeTo(20, 1.0)));
    }

    private static Stream<Arguments> getWindowSizeAndInterval() {
        return Stream.of(
            Arguments.of(10, 1000),
            Arguments.of(10, 500),
            Arguments.of(5, 50)
        );
    }

    private static Stream<Arguments> getIntervalSize() {
        return Stream.of(
            Arguments.of(10),
            Arguments.of(11),
            Arguments.of(15),
            Arguments.of(100),
            Arguments.of(101),
            Arguments.of(999),
            Arguments.of(1000)
        );
    }

    class TestClock implements Clock {
        private long time = 0;

        @Override
        public long tickMillis() {
            return time;
        }

        public void forward(long delta) {
            time += delta;
        }

        public void reset() {
            time = 0;
        }
    }

    private SlidingWindowHistogram newHistogram(int windowSize, int intervalSize) {
        clock.reset();
        return new SlidingWindowHistogram.Builder()
                .clock(clock)
                .lowestDiscernibleValue(1)
                .highestTrackableValue(60000)
                .numberOfSignificantDigits(2)
                .numberOfIntervals(windowSize)
                .intervalDuration(intervalSize, MILLISECONDS)
                .build();
    }

}
