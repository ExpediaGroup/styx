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
package com.hotels.styx.client.healthcheck;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.TimeUnit;

/**
 * Represents a schedule for tasks that need to be executed at a fixed rate
 * (see {@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}.
 */
public class Schedule {
    private final long initialDelay;
    private final long period;
    private final TimeUnit unit;

    /**
     * Constructor. Constructs a Schedule with initialDelay=0.
     *
     * @param period       the period between successive executions
     * @param unit         the time unit of the initialDelay and period parameters
     */
    public Schedule(long period, TimeUnit unit) {
        this(0, period, unit);
    }

    /**
     * Constructor.
     *
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @param unit         the time unit of the initialDelay and period parameters
     */
    public Schedule(long initialDelay, long period, TimeUnit unit) {
        this.initialDelay = atLeast(initialDelay, 0);
        this.period = atLeast(period, 1);
        this.unit = requireNonNull(unit);
    }

    private static long atLeast(long parameter, long atLeast) {
        checkArgument(parameter >= atLeast, parameter + " < " + atLeast);
        return parameter;
    }

    /**
     * Initial delay in the schedule's time unit.
     *
     * @return initial delay
     */
    public long initialDelay() {
        return initialDelay;
    }

    /**
     * Period in the schedule's time unit.
     *
     * @return period
     */
    public long period() {
        return period;
    }

    /**
     * Time unit of initial delay and period.
     *
     * @return time unit of initial delay and period.
     */
    public TimeUnit unit() {
        return unit;
    }
}
