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
package com.hotels.styx.api.metrics.codahale;

import com.codahale.metrics.Snapshot;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.primitives.Longs;
import com.hotels.styx.api.Clock;
import com.hotels.styx.api.metrics.SlidingWindowHistogram;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class SlidingWindowHistogramReservoirTest {
    @Test
    public void sizeReturnsNumberOfSamples() {
        SlidingWindowHistogramReservoir reservoir = new SlidingWindowHistogramReservoir();
        reservoir.update(5);
        reservoir.update(6);
        reservoir.update(7);

        assertThat(reservoir.size(), is(3));
    }

    @Test
    public void cachesTheSnapshotUnitlFurtherUpdates() {
        SlidingWindowHistogramReservoir reservoir = new SlidingWindowHistogramReservoir();
        reservoir.update(5);
        reservoir.update(6);
        reservoir.update(7);

        Snapshot snapshot1 = reservoir.getSnapshot();
        Snapshot snapshot2 = reservoir.getSnapshot();
        assertThat(snapshot1, sameInstance(snapshot2));

        reservoir.update(8);
        assertThat(reservoir.getSnapshot(), not(sameInstance(snapshot2)));
    }

    @Test
    public void invalidatesCachedSnapshotEverySlidingWindowTimeLength() {
        TestClock clock = new TestClock();
        SlidingWindowHistogram histogram = new SlidingWindowHistogram.Builder()
                .numberOfIntervals(12)
                .intervalDuration(10, SECONDS)
                .autoResize(true)
                .build();

        SlidingWindowHistogramReservoir reservoir = new SlidingWindowHistogramReservoir(histogram, clock);
        reservoir.update(5);
        reservoir.update(6);
        reservoir.update(7);

        Snapshot snapshot1 = reservoir.getSnapshot();
        Snapshot snapshot2 = reservoir.getSnapshot();

        clock.forward(120 * 1000);
        assertThat(snapshot1, sameInstance(snapshot2));

        clock.forward(1);
        assertThat(reservoir.getSnapshot(), not(sameInstance(snapshot2)));
    }

    @Test
    public void snapshotComputesPercentiles() {
        SlidingWindowHistogramReservoir reservoir = reservoirWithSamples(1, 100);

        assertThat(reservoir.getSnapshot().getMin(), is(1L));
        assertThat(reservoir.getSnapshot().getMax(), is(100L));
        assertThat(reservoir.getSnapshot().getMean(), is(closeTo(50.5, 1)));
        assertThat(reservoir.getSnapshot().get75thPercentile(), is(closeTo(75, 0.5)));
        assertThat(reservoir.getSnapshot().get95thPercentile(), is(closeTo(95, 0.5)));
        assertThat(reservoir.getSnapshot().get98thPercentile(), is(closeTo(98, 0.5)));
        assertThat(reservoir.getSnapshot().get99thPercentile(), is(closeTo(99, 0.5)));
        assertThat(reservoir.getSnapshot().getMedian(), is(closeTo(50L, 1)));
    }

    @Test
    public void snapshotExposesSamplesAsArray() {
        SlidingWindowHistogramReservoir reservoir = reservoirWithSamples(1, 100);

        assertThat(reservoir.getSnapshot().getValues(), is(toArray(Range.closed(1L, 100L))));
    }

    private long[] toArray(Range<Long> range) {
        return Longs.toArray(ContiguousSet.create(range, DiscreteDomain.longs()));
    }

    private SlidingWindowHistogramReservoir reservoirWithSamples(int from, int to) {
        SlidingWindowHistogramReservoir reservoir = new SlidingWindowHistogramReservoir();
        for (int i = from; i <= to; i++) {
            reservoir.update(i);
        }
        return reservoir;
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
    }
}
