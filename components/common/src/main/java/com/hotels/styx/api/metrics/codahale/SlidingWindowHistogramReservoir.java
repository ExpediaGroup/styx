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

import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import com.hotels.styx.api.Clock;
import com.hotels.styx.api.metrics.SlidingWindowHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramIterationValue;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import static com.hotels.styx.api.Clocks.systemClock;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A {@link Reservoir} backed by a {@link SlidingWindowHistogram}.
 */
public class SlidingWindowHistogramReservoir implements Reservoir {
    private final SlidingWindowHistogram histogram;
    private final Clock clock;
    private volatile HistogramSnapshot snapshot;
    private boolean updated = true;
    private long snapshotCreationTime;

    public SlidingWindowHistogramReservoir() {
        this(new SlidingWindowHistogram.Builder()
                .numberOfIntervals(12)
                .intervalDuration(10, SECONDS)
                .autoResize(true)
                .build());
    }

    public SlidingWindowHistogramReservoir(SlidingWindowHistogram histogram) {
        this(histogram, systemClock());
    }

    public SlidingWindowHistogramReservoir(SlidingWindowHistogram histogram, Clock clock) {
        this.histogram = requireNonNull(histogram);
        this.clock = requireNonNull(clock);
        this.snapshotCreationTime = clock.tickMillis();
    }

    @Override
    public int size() {
        return getSnapshot().size();
    }

    @Override
    public synchronized void update(long value) {
        updated = true;
        histogram.recordValue(value);
    }

    @Override
    public synchronized Snapshot getSnapshot() {
        if (updated || snapshotExpired(clock.tickMillis())) {
            snapshot = new HistogramSnapshot(histogram);
            updated = false;
            snapshotCreationTime = clock.tickMillis();
        }
        return snapshot;
    }

    private boolean snapshotExpired(long currentTime) {
        return currentTime - snapshotCreationTime > histogram.windowSize() * histogram.timeIntervalMs();
    }

    static final class HistogramSnapshot extends Snapshot {
        private final Histogram histogram;

        HistogramSnapshot(SlidingWindowHistogram histogram) {
            this.histogram = histogram.copy();
        }

        @Override
        public double getValue(double quantile) {
            return histogram.getValueAtPercentile(quantile * 100.0);
        }

        @Override
        public long[] getValues() {
            long[] vals = new long[(int) histogram.getTotalCount()];
            int i = 0;
            for (HistogramIterationValue value : histogram.recordedValues()) {
                long val = value.getValueIteratedTo();
                for (int j = 0; j < value.getCountAddedInThisIterationStep(); j++) {
                    vals[i++] = val;
                }
            }
            if (i != vals.length) {
                throw new IllegalStateException(format("Total count was %d but iterating values produced is %d",
                        histogram.getTotalCount(), vals.length));
            }
            return vals;
        }

        @Override
        public int size() {
            return (int) histogram.getTotalCount();
        }

        @Override
        public long getMax() {
            return histogram.getMaxValue();
        }

        @Override
        public double getMean() {
            return histogram.getMean();
        }

        @Override
        public long getMin() {
            return histogram.getMinValue();
        }

        @Override
        public double getStdDev() {
            return histogram.getStdDeviation();
        }

        @Override
        public void dump(OutputStream output) {
            PrintWriter p = new PrintWriter(new OutputStreamWriter(output, UTF_8));
            for (HistogramIterationValue value : histogram.recordedValues()) {
                for (int j = 0; j < value.getCountAddedInThisIterationStep(); j++) {
                    p.printf("%d%n", value.getValueIteratedTo());
                }
            }
        }
    }

}
