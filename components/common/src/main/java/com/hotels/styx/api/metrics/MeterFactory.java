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
package com.hotels.styx.api.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Optional.of;

public final class MeterFactory {
    private static final Duration DEFAULT_MIN_HISTOGRAM_BUCKET = Duration.of(1, MILLIS);
    private static final Duration DEFAULT_MAX_HISTOGRAM_BUCKET = Duration.of(1, MINUTES);

    private static final String MIN_ENV_VAR_NAME = "STYX_TIMER_HISTO_MIN";
    private static final String MAX_ENV_VAR_NAME = "STYX_TIMER_HISTO_MIN";

    private static final Duration MIN_HISTOGRAM_BUCKET = of(MIN_ENV_VAR_NAME)
            .map(System::getenv)
            .map(Long::valueOf)
            .map(millis -> Duration.of(millis, MILLIS))
            .orElse(DEFAULT_MIN_HISTOGRAM_BUCKET);
    private static final Duration MAX_HISTOGRAM_BUCKET = of(MAX_ENV_VAR_NAME)
            .map(System::getenv)
            .map(Long::valueOf)
            .map(millis -> Duration.of(millis, MILLIS))
            .orElse(DEFAULT_MAX_HISTOGRAM_BUCKET);

    private MeterFactory() {
    }

    public static Timer timer(MeterRegistry registry, String name) {
        return timer(registry, name, Tags.empty());
    }

    public static Timer timer(MeterRegistry registry, String name, Iterable<Tag> tags) {
        return Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram()
                .minimumExpectedValue(MIN_HISTOGRAM_BUCKET)
                .maximumExpectedValue(MAX_HISTOGRAM_BUCKET)
                .register(registry);
    }
}
