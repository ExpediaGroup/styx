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
package com.hotels.styx.admin;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Clock;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.function.Supplier;

import static com.hotels.styx.api.Clocks.systemClock;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Caches the output of another supplier until it expires. If the source supplier throws an exception, the exception will
 * be logged and the previous output will remain cached after expiration.
 *
 * @param <E> type of object supplied
 */
public class CachingSupplier<E> implements Supplier<E> {
    private static final Logger LOG = getLogger(CachingSupplier.class);

    private final Supplier<? extends E> sourceSupplier;
    private final long updateIntervalMillis;
    private final Clock clock;

    private volatile E json;
    private volatile long lastUpdateMillis;

    /**
     * Constructs an instance that wraps {@code sourceSupplier}.
     *
     * @param sourceSupplier a supplier that will provide an up-to-date object each time it is called, that can be transformed into JSON
     * @param updateInterval interval between calls to sourceSupplier
     */
    public CachingSupplier(Supplier<? extends E> sourceSupplier, Duration updateInterval) {
        this(sourceSupplier, updateInterval, systemClock());
    }

    /**
     * Constructs an instance that wraps {@code sourceSupplier}. This constructor is intended for testing only.
     *
     * @param sourceSupplier a supplier that will provide an up-to-date object each time it is called, that can be transformed into JSON
     * @param updateInterval interval between calls to sourceSupplier
     * @param clock allows you to specify a different clock (for testing).
     */
    @VisibleForTesting
    public CachingSupplier(Supplier<? extends E> sourceSupplier, Duration updateInterval, Clock clock) {
        this.sourceSupplier = requireNonNull(sourceSupplier);
        this.updateIntervalMillis = updateInterval.toMillis();
        this.clock = requireNonNull(clock);
    }

    @Override
    public E get() {
        updateIfIntervalHasElapsed();

        return json;
    }

    private synchronized void updateIfIntervalHasElapsed() {
        if (hasUpdateIntervalElapsed()) {
            try {
                this.json = sourceSupplier.get();
            } catch (Exception e) {
                LOG.error("Error generating data", e);
            }

            this.lastUpdateMillis = clock.tickMillis();
        }
    }

    private boolean hasUpdateIntervalElapsed() {
        return clock.tickMillis() >= this.lastUpdateMillis + updateIntervalMillis;
    }
}
