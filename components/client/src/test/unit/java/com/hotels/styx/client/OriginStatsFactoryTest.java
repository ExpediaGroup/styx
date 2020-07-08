/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.client;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.OriginStatsFactory.CachingOriginStatsFactory;
import com.hotels.styx.client.applications.RequestStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class OriginStatsFactoryTest {

    final OriginStatsFactory originStatsFactory = new CachingOriginStatsFactory(new SimpleMeterRegistry());

    @Test
    public void returnTheSameStatsForSameOrigin() {
        RequestStats originStatsOne = originStatsFactory.originStats(newOrigin(9090));
        RequestStats originStatsTwo = originStatsFactory.originStats(newOrigin(9090));
        assertThat(originStatsOne, sameInstance(originStatsTwo));
    }

    @Test
    public void createsANewOriginStatsForNewOrigins() {
        RequestStats originStatsOne = originStatsFactory.originStats(newOrigin(9090));
        RequestStats originStatsTwo = originStatsFactory.originStats(newOrigin(9091));
        assertThat(originStatsOne, not(sameInstance(originStatsTwo)));
    }

    private static Origin newOrigin(int port) {
        return newOriginBuilder("localhost", port).build();
    }
}
