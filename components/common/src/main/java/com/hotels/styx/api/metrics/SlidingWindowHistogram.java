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
package com.hotels.styx.api.metrics;

import com.hotels.styx.api.Clock;
import com.hotels.styx.api.Clocks;
import org.HdrHistogram.Histogram;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Implements an HDR histogram with a sliding window behaviour. The sliding
 * window size is fixed to 10 seconds and window granularity is one second.
 */
public final class SlidingWindowHistogram {
    private static final int DEFAULT_NUMBER_OF_INTERVALS = 10;
    private final Clock clock;
    private final Histogram aggregateHistogram;
    private final IntervalBucket[] window;

    private final int numberOfIntervals;
    private final long intervalDurationMillis;
    private long lastUpdateTime;

    private SlidingWindowHistogram(Builder builder) {
        this.numberOfIntervals = builder.numberOfIntervals;
        this.intervalDurationMillis = builder.intervalDurationMillis;
        this.clock = builder.clock;

        this.aggregateHistogram = new Histogram(builder.lowestDiscernibleValue, builder.highestTrackableValue, builder.numberOfSignificantDigits);
        if (builder.autoResize) {
            this.aggregateHistogram.setAutoResize(true);
        }

        this.window = new IntervalBucket[this.numberOfIntervals];
        for (int i = 0; i < this.numberOfIntervals; i++) {
            this.window[i] = new IntervalBucket(this.aggregateHistogram, builder.lowestDiscernibleValue,
                    builder.highestTrackableValue, builder.numberOfSignificantDigits, builder.autoResize);
        }

        this.lastUpdateTime = currentTimeMillis();
    }

    public synchronized void recordValue(long msValue) {
        checkArgument(msValue >= 0, "Recorded value must be a positive number.");

        long currentTime = clock.tickMillis();
        purgeOldHistograms(currentTime);

        int bucket = bucketFromTime(currentTime);
        window[bucket].recordValue(msValue);

        lastUpdateTime = currentTime;
    }

    public synchronized double getMean() {
        return getAggregateHistogram().getMean();
    }

    public synchronized double getValueAtPercentile(double percentile) {
        return getAggregateHistogram().getValueAtPercentile(percentile);
    }

    public synchronized double getStdDeviation() {
        return getAggregateHistogram().getStdDeviation();
    }

    public synchronized Histogram copy() {
        return getAggregateHistogram().copy();
    }

    public int windowSize() {
        return numberOfIntervals;
    }

    public long timeIntervalMs() {
        return intervalDurationMillis;
    }

    private Histogram getAggregateHistogram() {
        long currentTime = clock.tickMillis();
        purgeOldHistograms(currentTime);

        aggregateHistograms();
        return aggregateHistogram;
    }

    private boolean timePassed(long currentTime) {
        return intervalNumber(lastUpdateTime) < intervalNumber(currentTime);
    }

    private void purgeOldHistograms(long currentTime) {
        if (timePassed(currentTime)) {
            int i = 0;
            long startTime = Math.min(lastUpdateTime + intervalDurationMillis, currentTime);
            for (long t = startTime; i < numberOfIntervals && t <= currentTime; i++, t += intervalDurationMillis) {
                int bucket = bucketFromTime(t);
                window[bucket].reset();
            }
        }
    }

    private int bucketFromTime(long timeMs) {
        return (int) (intervalNumber(timeMs) % numberOfIntervals);
    }

    private long intervalNumber(long timeMs) {
        return timeMs / intervalDurationMillis;
    }

    private void aggregateHistograms() {
        for (int i = 0; i < numberOfIntervals; i++) {
            window[i].aggregate();
        }
    }

    private static class IntervalBucket {
        private final Histogram aggregateHistogram;
        private final Histogram intervalHistogram;
        private IntervalState state;

        public IntervalBucket(Histogram aggregateHistogram, long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantDigits, Boolean autoResize) {
            this.aggregateHistogram = aggregateHistogram;
            this.intervalHistogram = new Histogram(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantDigits);
            if (autoResize) {
                this.intervalHistogram.setAutoResize(true);
            }
            this.state = IntervalState.EMPTY;
        }

        public void recordValue(long msValue) {
            intervalHistogram.recordValue(msValue);
            if (this.state == IntervalState.AGGREGATED) {
                aggregateHistogram.recordValue(msValue);
            } else {
                this.state = IntervalState.UPDATED;
            }
        }

        public void reset() {
            if (this.state == IntervalState.AGGREGATED) {
                aggregateHistogram.subtract(this.intervalHistogram);
            }
            this.intervalHistogram.reset();
            this.state = IntervalState.EMPTY;
        }

        public void aggregate() {
            if (this.state == IntervalState.UPDATED) {
                aggregateHistogram.add(this.intervalHistogram);
                this.state = IntervalState.AGGREGATED;
            }
        }
    }

    private enum IntervalState {
        AGGREGATED,
        UPDATED,
        EMPTY
    }

    /**
     * A builder object for constructing SlidingWindowHistogram instances.
     */
    public static class Builder {
        private int numberOfIntervals = DEFAULT_NUMBER_OF_INTERVALS;
        private long intervalDurationMillis = SECONDS.toMillis(1);
        private long lowestDiscernibleValue = 1;
        private long highestTrackableValue = 2;
        private int numberOfSignificantDigits = 2;
        private Clock clock = Clocks.systemClock();
        private Boolean autoResize = false;

        public Builder numberOfIntervals(int windowSizeIntervals) {
            this.numberOfIntervals = windowSizeIntervals;
            return this;
        }

        public Builder intervalDuration(long intervalDuration, TimeUnit timeUnit) {
            this.intervalDurationMillis = timeUnit.toMillis(intervalDuration);
            return this;
        }

        public Builder lowestDiscernibleValue(long lowestDiscernibleValue) {
            this.lowestDiscernibleValue = lowestDiscernibleValue;
            return this;
        }

        public Builder highestTrackableValue(long highestTrackableValue) {
            this.highestTrackableValue = highestTrackableValue;
            return this;
        }

        public Builder numberOfSignificantDigits(int numberOfSignificantDigits) {
            this.numberOfSignificantDigits = numberOfSignificantDigits;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = requireNonNull(clock);
            return this;
        }

        public Builder autoResize(Boolean enabled) {
            this.autoResize = enabled;
            return this;
        }

        public SlidingWindowHistogram build() {
            return new SlidingWindowHistogram(this);
        }
    }
}
