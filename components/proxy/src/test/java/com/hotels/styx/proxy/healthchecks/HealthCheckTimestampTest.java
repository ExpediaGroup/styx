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

import org.testng.annotations.Test;

import java.time.Instant;

import static com.hotels.styx.api.Clocks.stoppedClock;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static org.hamcrest.MatcherAssert.assertThat;

public class HealthCheckTimestampTest {
    final Instant now = Instant.ofEpochMilli(1);
    final HealthCheckTimestamp healthCheckTimestamp = new HealthCheckTimestamp(stoppedClock(now));

    @Test
    public void printsTheCurrentTime() throws Exception {
        assertThat(healthCheckTimestamp.check().toString(), matchesRegex(
                "Result\\{isHealthy=true, message=1970-01-01T00:00:00.001\\+0000, timestamp=.*\\}"));
    }
}