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
package com.hotels.styx.client;

import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.applications.OriginStats;
import org.testng.annotations.Test;

import static com.hotels.styx.common.HostAndPorts.localhost;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class OriginStatsFactoryTest {

    final OriginStatsFactory originStatsFactory = new OriginStatsFactory(new CodaHaleMetricRegistry());

    @Test
    public void returnTheSameStatsForSameOrigin() {
        OriginStats originStatsOne = originStatsFactory.originStats(newOrigin(9090));
        OriginStats originStatsTwo = originStatsFactory.originStats(newOrigin(9090));
        assertThat(originStatsOne, sameInstance(originStatsTwo));
    }

    @Test
    public void createsANewOriginStatsForNewOrigins() {
        OriginStats originStatsOne = originStatsFactory.originStats(newOrigin(9090));
        OriginStats originStatsTwo = originStatsFactory.originStats(newOrigin(9091));
        assertThat(originStatsOne, not(sameInstance(originStatsTwo)));
    }

    private static Origin newOrigin(int port) {
        return newOriginBuilder(localhost(port)).build();
    }
}