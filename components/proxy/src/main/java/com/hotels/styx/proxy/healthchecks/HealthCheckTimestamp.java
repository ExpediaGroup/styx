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
package com.hotels.styx.proxy.healthchecks;

import com.codahale.metrics.health.HealthCheck;
import com.hotels.styx.api.Clock;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.codahale.metrics.health.HealthCheck.Result.healthy;
import static com.hotels.styx.api.Clocks.systemClock;
import static java.time.ZoneOffset.UTC;

/**
 * A health check that returns a healthy result with a timestamp as its message.
 *
 */
public class HealthCheckTimestamp extends HealthCheck {
    public static final String NAME = "healthCheckTimestamp";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final Clock clock;

    public HealthCheckTimestamp() {
        this(systemClock());
    }

    public HealthCheckTimestamp(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected Result check() {
        ZonedDateTime now = Instant.ofEpochMilli(clock.tickMillis()).atZone(UTC);

        return healthy(DATE_TIME_FORMATTER.format(now));
    }
}
